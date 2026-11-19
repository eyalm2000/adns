package com.eyalm.adns.data.nextdns.api

import com.eyalm.adns.data.account.AccountCredentialRef
import com.eyalm.adns.data.account.FakeSecretCipher
import com.eyalm.adns.data.account.FakeSecureStore
import com.eyalm.adns.data.account.NextDnsAccountRepository
import com.eyalm.adns.data.account.newRepository
import com.eyalm.adns.data.nextdns.auth.NextDnsManagementSession
import com.eyalm.adns.data.nextdns.auth.NextDnsSessionManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs real requests through the chain built by [addAccountAuthentication], the same function
 * ApiClient uses, against a local server.
 */
class AccountRequestIsolationTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: NextDnsAccountRepository
    private lateinit var session: NextDnsSessionManager
    private lateinit var accountA: String
    private lateinit var accountB: String

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        repository = newRepository(FakeSecureStore(), FakeSecretCipher())
        repository.signIn("a@example.test", "key-a", "a@example.test")
        accountA = repository.snapshot.value.activeAccountId!!
        repository.signIn("b@example.test", "key-b", "b@example.test")
        accountB = repository.snapshot.value.activeAccountId!!
        repository.setActive(accountA)
        session = NextDnsSessionManager(repository)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** [betweenTaggingAndNetwork] runs after a request was stamped and before it is sent. */
    private fun client(
        betweenTaggingAndNetwork: (() -> Unit)? = null,
        allowedHosts: Set<String> = setOf(server.hostName),
    ): OkHttpClient = OkHttpClient.Builder()
        .addAccountAuthentication(
            currentRef = repository::currentRef,
            apiKeyFor = repository::apiKeyFor,
            onUnauthorized = session::unauthorized,
            allowedHosts = allowedHosts,
        )
        .addInterceptor { chain ->
            betweenTaggingAndNetwork?.invoke()
            chain.proceed(chain.request())
        }
        .build()

    private fun get(client: OkHttpClient, path: String = "/profiles") =
        client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    @Test
    fun `request is sent with the key of the active account`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        get(client()).close()

        assertEquals("key-a", server.takeRequest().getHeader("X-Api-Key"))
    }

    @Test
    fun `switching accounts while a request is in flight does not change its key`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        get(client(betweenTaggingAndNetwork = { repository.setActive(accountB) })).close()

        assertEquals("key-a", server.takeRequest().getHeader("X-Api-Key"))
        assertEquals(accountB, repository.snapshot.value.activeAccountId)
    }

    @Test
    fun `the next request after a switch uses the new account's key`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = client()

        get(client).close()
        repository.setActive(accountB)
        get(client).close()

        assertEquals("key-a", server.takeRequest().getHeader("X-Api-Key"))
        assertEquals("key-b", server.takeRequest().getHeader("X-Api-Key"))
    }

    @Test
    fun `unauthorized response of an in-flight request expires the issuing account only`() {
        server.enqueue(MockResponse().setResponseCode(401))

        get(client(betweenTaggingAndNetwork = { repository.setActive(accountB) })).close()

        val accounts = repository.snapshot.value.accounts
        assertFalse(accounts.first { it.id == accountA }.hasCredential)
        assertTrue(accounts.first { it.id == accountB }.hasCredential)
        assertEquals("key-b", repository.activeApiKey())
        assertEquals(NextDnsManagementSession.Active, session.state.value)
        assertFalse(session.reauthenticationRequested.value)
    }

    @Test
    fun `unauthorized response for the active account expires its session`() {
        server.enqueue(MockResponse().setResponseCode(401))

        get(client()).close()

        assertEquals(NextDnsManagementSession.Expired, session.state.value)
        assertTrue(session.reauthenticationRequested.value)
        assertTrue(repository.snapshot.value.accounts.first { it.id == accountB }.hasCredential)
    }

    @Test
    fun `a request whose credential was replaced is sent without a key and cannot expire the new one`() {
        server.enqueue(MockResponse().setResponseCode(401))
        var replaced = false

        get(
            client(
                betweenTaggingAndNetwork = {
                    if (!replaced) {
                        replaced = true
                        repository.expire(repository.currentRef()!!)
                        repository.signIn("a@example.test", "key-a2", "a@example.test")
                    }
                }
            )
        ).close()

        assertNull(server.takeRequest().getHeader("X-Api-Key"))
        // The late failure belongs to the old credential and must not expire the new one.
        assertEquals("key-a2", repository.activeApiKey())
        assertEquals(NextDnsManagementSession.Active, session.state.value)
    }

    @Test
    fun `a request whose account was removed is sent without a key`() {
        server.enqueue(MockResponse().setResponseCode(401))

        get(client(betweenTaggingAndNetwork = { repository.remove(accountA) })).close()

        assertNull(server.takeRequest().getHeader("X-Api-Key"))
        assertTrue(repository.snapshot.value.accounts.any { it.id == accountB })
        assertTrue(repository.snapshot.value.accounts.first { it.id == accountB }.hasCredential)
    }

    @Test
    fun `request without an active account carries no key and reports no account`() {
        repository.remove(accountA)
        repository.remove(accountB)
        server.enqueue(MockResponse().setResponseCode(401))

        get(client()).close()

        assertNull(server.takeRequest().getHeader("X-Api-Key"))
        assertTrue(repository.snapshot.value.accounts.isEmpty())
    }

    @Test
    fun `the key is not sent to a host that is not allowed`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        get(client(allowedHosts = setOf("api.nextdns.io"))).close()

        assertNull(server.takeRequest().getHeader("X-Api-Key"))
    }

    @Test
    fun `a key supplied by the caller is never forwarded`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val request = Request.Builder()
            .url(server.url("/profiles"))
            .header("X-Api-Key", "caller-supplied")
            .build()

        client(allowedHosts = setOf("api.nextdns.io")).newCall(request).execute().close()

        assertNull(server.takeRequest().getHeader("X-Api-Key"))
    }

    @Test
    fun `key lookup honours the allow-list and the credential reference`() {
        val interceptor = NextDnsApiKeyInterceptor(
            apiKeyProvider = repository::apiKeyFor,
            allowedHosts = setOf("api.nextdns.io"),
        )
        val ref: AccountCredentialRef = repository.currentRef()!!

        assertEquals("key-a", interceptor.apiKeyForHost("api.nextdns.io", ref))
        assertNull(interceptor.apiKeyForHost("example.test", ref))
        assertNull(interceptor.apiKeyForHost("api.nextdns.io", null))
        assertNull(interceptor.apiKeyForHost("api.nextdns.io", ref.copy(version = ref.version + 1)))
    }
}

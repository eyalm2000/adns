package com.eyalm.adns.data.nextdns.auth

import com.eyalm.adns.data.account.AccountStorageException
import com.eyalm.adns.data.nextdns.api.NextDnsApiKeyAuthApi
import com.eyalm.adns.data.nextdns.api.NextDnsCookieAuthApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Sign-in when the account cannot be stored. The NextDNS calls go to a local server; the
 * storage step is a function the test controls.
 */
class NextDnsAuthRepositorySignInTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: NextDnsAuthRepository

    private class Stored(val label: String, val apiKey: String, val loginEmail: String?)

    private val stored = mutableListOf<Stored>()
    private var storeBehaviour: () -> Unit = {}

    private val passwordState = NextDnsLoginUiState(
        mode = NextDnsLoginMode.Password,
        email = "user@example.test",
        password = "not-a-real-password",
    )

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = NextDnsAuthRepository(
            cookieAuthApi = retrofit.create(NextDnsCookieAuthApi::class.java),
            apiKeyAuthApi = retrofit.create(NextDnsApiKeyAuthApi::class.java),
            storeAccount = { label, apiKey, loginEmail ->
                storeBehaviour()
                stored += Stored(label, apiKey, loginEmail)
            },
            apiKeyAccountLabel = { "API key account" },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueuePasswordLogin() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("OK")
                .addHeader("Set-Cookie", "session=abc; Path=/; HttpOnly")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"apiKey":"issued-key"}"""))
        enqueueVerify()
    }

    private fun enqueueVerify() {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":[{"id":"abc123","name":"Home"}]}""")
        )
    }

    @Test
    fun `a repeated storage failure on retry returns a failure instead of throwing`() = runTest {
        storeBehaviour = { throw AccountStorageException() }
        enqueuePasswordLogin()

        // 1. Password sign-in works, storing the account fails.
        val first = repository.login(passwordState)
        assertEquals(NextDnsLoginOutcome.Failure(NextDnsLoginFailure.Unknown), first)
        assertEquals(3, server.requestCount)

        // 2. The user retries. The issued key is still pending, so only the key is verified.
        enqueueVerify()
        // 3. Storing fails again. This used to escape as an uncaught exception.
        val retry = repository.login(passwordState)

        assertEquals(NextDnsLoginOutcome.Failure(NextDnsLoginFailure.Unknown), retry)
        assertEquals(4, server.requestCount)
        assertTrue(stored.isEmpty())
    }

    @Test
    fun `a retry succeeds once storage works again and does not repeat the password sign-in`() =
        runTest {
            storeBehaviour = { throw AccountStorageException() }
            enqueuePasswordLogin()
            repository.login(passwordState)
            enqueueVerify()
            repository.login(passwordState)

            storeBehaviour = {}
            enqueueVerify()
            val result = repository.login(passwordState)

            assertTrue(result is NextDnsLoginOutcome.Authenticated)
            assertEquals(
                listOf("abc123"),
                (result as NextDnsLoginOutcome.Authenticated).profiles.map { it.id },
            )
            // Password sign-in and key exchange happened once; only verification was repeated.
            assertEquals(5, server.requestCount)
            assertEquals(1, stored.size)
            assertEquals("user@example.test", stored[0].label)
            assertEquals("user@example.test", stored[0].loginEmail)
            assertEquals("issued-key", stored[0].apiKey)
        }

    @Test
    fun `a storage failure with an API key returns a failure`() = runTest {
        storeBehaviour = { throw AccountStorageException() }
        enqueueVerify()

        val result = repository.login(
            NextDnsLoginUiState(mode = NextDnsLoginMode.ApiKey, apiKey = "pasted-key")
        )

        assertEquals(NextDnsLoginOutcome.Failure(NextDnsLoginFailure.Unknown), result)
        assertTrue(stored.isEmpty())
    }

    @Test
    fun `an API key sign-in stores the placeholder label and no login email`() = runTest {
        enqueueVerify()

        val result = repository.login(
            NextDnsLoginUiState(mode = NextDnsLoginMode.ApiKey, apiKey = "pasted-key")
        )

        assertTrue(result is NextDnsLoginOutcome.Authenticated)
        assertEquals("API key account", stored.single().label)
        assertEquals(null, stored.single().loginEmail)
    }

    @Test
    fun `cancellation during a retry is not turned into a login failure`() = runTest {
        storeBehaviour = { throw AccountStorageException() }
        enqueuePasswordLogin()
        repository.login(passwordState)
        enqueueVerify()
        storeBehaviour = { throw CancellationException("cancelled while storing") }

        var cancelled = false
        try {
            repository.login(passwordState)
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertTrue(stored.isEmpty())
    }

    @Test
    fun `other storage errors are not hidden on the retry path`() = runTest {
        storeBehaviour = { throw AccountStorageException() }
        enqueuePasswordLogin()
        repository.login(passwordState)
        enqueueVerify()
        storeBehaviour = { throw IllegalArgumentException("programming error") }

        var surfaced = false
        try {
            repository.login(passwordState)
        } catch (_: IllegalArgumentException) {
            surfaced = true
        }

        assertTrue(surfaced)
    }
}

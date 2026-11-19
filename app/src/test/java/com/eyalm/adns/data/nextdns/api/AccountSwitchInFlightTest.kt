package com.eyalm.adns.data.nextdns.api

import com.eyalm.adns.data.account.AccountEpoch
import com.eyalm.adns.data.account.AccountScopedCache
import com.eyalm.adns.data.account.FakeSecretCipher
import com.eyalm.adns.data.account.FakeSecureStore
import com.eyalm.adns.data.account.NextDnsAccountRepository
import com.eyalm.adns.data.account.newRepository
import com.eyalm.adns.data.nextdns.analytics.AnalyticsScope
import com.eyalm.adns.data.nextdns.analytics.NextDnsAnalyticsRepository
import com.eyalm.adns.data.nextdns.auth.NextDnsSessionManager
import com.eyalm.adns.data.nextdns.logs.LogsQuery
import com.eyalm.adns.data.nextdns.logs.NextDnsLogsRepository
import com.eyalm.adns.domain.nextdns.ApiResult
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Responses that arrive after the active account changed, produced by the real log and
 * statistics repositories and the production interceptor chain.
 *
 * The first request the server sees is held until the test releases it, so "late" is controlled
 * by the test and not by timing. The screens apply a result only while the epoch they started
 * under is still current; [Sink] does the same thing with the same signal.
 */
class AccountSwitchInFlightTest {
    private class GatedDispatcher : Dispatcher() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val keys = CopyOnWriteArrayList<String>()
        private val seen = AtomicInteger()

        override fun dispatch(request: RecordedRequest): MockResponse {
            val key = request.getHeader("X-Api-Key").orEmpty()
            keys += key
            if (seen.getAndIncrement() == 0) {
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
            val domain = if (key == "key-a") "a.example" else "b.example"
            val body = if (request.path.orEmpty().contains("/logs")) {
                """{"data":[{"timestamp":"2026-01-01T00:00:00Z","domain":"$domain",""" +
                    """"encrypted":true,"protocol":"DNS-over-HTTPS","status":"default"}]}"""
            } else {
                """{"data":[{"domain":"$domain","queries":1}]}"""
            }
            return MockResponse().setResponseCode(200).setBody(body)
        }
    }

    /** Stands for the visible state of a screen. */
    private inner class Sink {
        val shown = mutableListOf<String>()

        fun applyIfCurrent(startedUnder: AccountEpoch, value: String) {
            if (startedUnder == session.accountEpoch.value) shown += value
        }
    }

    private lateinit var server: MockWebServer
    private lateinit var dispatcher: GatedDispatcher
    private lateinit var repository: NextDnsAccountRepository
    private lateinit var session: NextDnsSessionManager
    private lateinit var accountA: String
    private lateinit var accountB: String
    private lateinit var logs: NextDnsLogsRepository
    private lateinit var analytics: NextDnsAnalyticsRepository

    @Before
    fun setUp() {
        dispatcher = GatedDispatcher()
        server = MockWebServer().also {
            it.dispatcher = dispatcher
            it.start()
        }
        repository = newRepository(FakeSecureStore(), FakeSecretCipher())
        repository.signIn("a@example.test", "key-a", "a@example.test")
        accountA = repository.snapshot.value.activeAccountId!!
        repository.signIn("b@example.test", "key-b", "b@example.test")
        accountB = repository.snapshot.value.activeAccountId!!
        repository.setActive(accountA)
        session = NextDnsSessionManager(repository)

        val client = OkHttpClient.Builder()
            .addAccountAuthentication(
                currentRef = repository::currentRef,
                apiKeyFor = repository::apiKeyFor,
                onUnauthorized = session::unauthorized,
                allowedHosts = setOf(server.hostName),
            )
            .build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NextDnsApi::class.java)
        logs = NextDnsLogsRepository(api)
        analytics = NextDnsAnalyticsRepository(api)
    }

    @After
    fun tearDown() {
        dispatcher.release.countDown()
        server.shutdown()
    }

    @Test
    fun `a late log response from account A is not shown after switching to B`() = runBlocking {
        val sink = Sink()
        val startedUnder = session.accountEpoch.value
        val late = async(Dispatchers.IO) { logs.getLogs("profile", LogsQuery()) }
        assertTrue(dispatcher.entered.await(10, TimeUnit.SECONDS))

        repository.setActive(accountB)
        // The screen starts its own request for B, which the server answers at once.
        val epochB = session.accountEpoch.value
        val forB = logs.getLogs("profile", LogsQuery())
        dispatcher.release.countDown()
        val fromA = late.await()

        assertTrue(fromA is ApiResult.Success)
        assertTrue(forB is ApiResult.Success)
        // A's request was sent with A's key and B's with B's, whatever the switch order was.
        assertEquals(listOf("key-a", "key-b"), dispatcher.keys.toList())
        sink.applyIfCurrent(startedUnder, (fromA as ApiResult.Success).value.data.first().domain!!)
        sink.applyIfCurrent(epochB, (forB as ApiResult.Success).value.data.first().domain!!)
        assertEquals(listOf("b.example"), sink.shown)
    }

    @Test
    fun `a late statistics response from account A is not shown or cached after switching to B`() =
        runBlocking {
            val sink = Sink()
            val cache = AccountScopedCache<String, String>(session.accountEpoch::value)
            val startedUnder = session.accountEpoch.value
            val late = async(Dispatchers.IO) {
                analytics.getCardData("profile", "domains", emptyMap(), AnalyticsScope())
            }
            assertTrue(dispatcher.entered.await(10, TimeUnit.SECONDS))

            repository.setActive(accountB)
            val epochB = session.accountEpoch.value
            val forB = analytics.getCardData("profile", "domains", emptyMap(), AnalyticsScope())
            dispatcher.release.countDown()
            val fromA = late.await()

            assertTrue(fromA is ApiResult.Success)
            assertTrue(forB is ApiResult.Success)
            assertEquals(listOf("key-a", "key-b"), dispatcher.keys.toList())
            val domainOfA = domain(fromA as ApiResult.Success)
            val domainOfB = domain(forB as ApiResult.Success)
            assertEquals("a.example", domainOfA)
            assertEquals("b.example", domainOfB)
            sink.applyIfCurrent(startedUnder, domainOfA)
            sink.applyIfCurrent(epochB, domainOfB)
            assertFalse(cache.put(startedUnder, "domains", domainOfA))
            assertTrue(cache.put(epochB, "domains", domainOfB))
            assertEquals(listOf("b.example"), sink.shown)
            assertEquals("b.example", cache["domains"])
        }

    @Test
    fun `switching away and back does not let the first request through`() = runBlocking {
        val sink = Sink()
        val firstUnderA = session.accountEpoch.value
        val late = async(Dispatchers.IO) { logs.getLogs("profile", LogsQuery()) }
        assertTrue(dispatcher.entered.await(10, TimeUnit.SECONDS))

        repository.setActive(accountB)
        repository.setActive(accountA)
        // A refresh under the second period of A.
        val secondUnderA = session.accountEpoch.value
        val refresh = logs.getLogs("profile", LogsQuery())
        dispatcher.release.countDown()
        val first = late.await()

        assertNotEquals(firstUnderA, secondUnderA)
        assertEquals(accountA, secondUnderA.accountId)
        assertTrue(first is ApiResult.Success)
        assertTrue(refresh is ApiResult.Success)
        sink.applyIfCurrent(firstUnderA, "first")
        sink.applyIfCurrent(secondUnderA, "refresh")
        assertEquals(listOf("refresh"), sink.shown)
    }

    @Test
    fun `signing out while a request is in flight drops its result`() = runBlocking {
        val sink = Sink()
        val startedUnder = session.accountEpoch.value
        val late = async(Dispatchers.IO) { logs.getLogs("profile", LogsQuery()) }
        assertTrue(dispatcher.entered.await(10, TimeUnit.SECONDS))

        session.signedOut()
        dispatcher.release.countDown()
        late.await()

        sink.applyIfCurrent(startedUnder, "late")
        assertTrue(sink.shown.isEmpty())
        assertNull(session.accountEpoch.value.accountId)
    }

    @Test
    fun `with a single account results are applied as before`() = runBlocking {
        val sink = Sink()
        dispatcher.release.countDown()
        val startedUnder = session.accountEpoch.value

        val result = logs.getLogs("profile", LogsQuery())
        sink.applyIfCurrent(startedUnder, (result as ApiResult.Success).value.data.first().domain!!)

        assertEquals(listOf("a.example"), sink.shown)
        assertEquals(listOf("key-a"), dispatcher.keys.toList())
    }

    private fun domain(result: ApiResult.Success<com.google.gson.JsonArray>): String =
        result.value.get(0).asJsonObject.get("domain").asString
}

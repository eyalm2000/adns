package com.eyalm.adns.data.nextdns.api

import com.eyalm.adns.data.account.AccountCredentialRef
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NextDnsSessionExpiryInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `unauthorized responses report the credential the request was issued with`() {
        val reported = mutableListOf<AccountCredentialRef?>()
        val issuedWith = AccountCredentialRef("account-a", 7)
        val client = OkHttpClient.Builder()
            .addInterceptor(AccountRequestTaggingInterceptor { issuedWith })
            .addInterceptor(NextDnsSessionExpiryInterceptor { reported += it })
            .build()
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        repeat(2) {
            client.newCall(
                Request.Builder().url(server.url("/profiles")).build()
            ).execute().close()
        }

        assertEquals(listOf<AccountCredentialRef?>(issuedWith, issuedWith), reported)
    }

    @Test
    fun `unauthorized response of an unauthenticated request reports no credential`() {
        val reported = mutableListOf<AccountCredentialRef?>()
        val client = OkHttpClient.Builder()
            .addInterceptor(AccountRequestTaggingInterceptor { null })
            .addInterceptor(NextDnsSessionExpiryInterceptor { reported += it })
            .build()
        server.enqueue(MockResponse().setResponseCode(401))

        client.newCall(
            Request.Builder().url(server.url("/profiles")).build()
        ).execute().close()

        assertEquals(listOf<AccountCredentialRef?>(null), reported)
    }
    @Test
    fun `authRequired forbidden response invalidates session without consuming body`() {
        val invalidations = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .addInterceptor(
                NextDnsSessionExpiryInterceptor { invalidations.incrementAndGet() }
            )
            .build()
        val body = """{"errors":[{"code":"authRequired"}]}"""
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )

        val response = client.newCall(
            Request.Builder().url(server.url("/profiles")).build()
        ).execute()

        assertEquals(1, invalidations.get())
        assertEquals(body, response.body.string())
        response.close()
    }

    @Test
    fun `role-related forbidden response does not invalidate session`() {
        val invalidations = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .addInterceptor(
                NextDnsSessionExpiryInterceptor { invalidations.incrementAndGet() }
            )
            .build()
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"errors":[{"code":"insufficientRole"}]}""")
        )

        client.newCall(
            Request.Builder().url(server.url("/profiles/profile-id")).build()
        ).execute().use { response ->
            assertTrue(response.code == 403)
        }

        assertEquals(0, invalidations.get())
    }
}

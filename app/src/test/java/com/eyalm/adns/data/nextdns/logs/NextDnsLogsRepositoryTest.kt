package com.eyalm.adns.data.nextdns.logs

import com.eyalm.adns.data.nextdns.api.NextDnsApi
import com.eyalm.adns.domain.nextdns.ApiResult
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

class NextDnsLogsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: NextDnsLogsRepository

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NextDnsApi::class.java)
        repository = NextDnsLogsRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `duplicate rule activates the existing hex encoded item`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"errors":[{"code":"duplicate"}]}""")
        )
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository.applyRule(
            profileId = "profile-id",
            list = DomainRuleList.Allow,
            domain = "example.com",
        )

        assertEquals(DomainRuleResult.Activated, result)
        val addRequest = server.takeRequest()
        assertEquals("POST", addRequest.method)
        assertEquals("/profiles/profile-id/allowlist", addRequest.requestUrl!!.encodedPath)

        val activateRequest = server.takeRequest()
        assertEquals("PATCH", activateRequest.method)
        assertEquals(
            "hex:6578616d706c652e636f6d",
            activateRequest.requestUrl!!.pathSegments.last(),
        )
        assertTrue(activateRequest.body.readUtf8().contains("\"active\":true"))
    }

    @Test
    fun `getLogs parses client field correctly`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{
                        "data": [
                            {
                                "timestamp": "2026-09-12T08:29:02.848Z",
                                "domain": "dc.services.visualstudio.com",
                                "encrypted": true,
                                "protocol": "DNS-over-HTTPS",
                                "client": "nextdns-windows",
                                "status": "default"
                            }
                        ]
                    }"""
                )
        )

        val result = repository.getLogs("profile-id", LogsQuery())
        assertTrue(result is ApiResult.Success)
        val entry = (result as ApiResult.Success).value.data.first()
        assertEquals("nextdns-windows", entry.client)
    }
}

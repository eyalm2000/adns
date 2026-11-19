package com.eyalm.adns.data.nextdns.analytics

import com.eyalm.adns.data.nextdns.api.NextDnsApi
import com.eyalm.adns.domain.nextdns.ApiResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * How pages of an analytics ranking are added to the list shown.
 *
 * Limit: these tests use made-up responses. Whether NextDNS really returns repeated rows or a
 * repeated cursor for resolved domains could not be checked without a real response.
 */
class AnalyticsPagingTest {
    private fun row(domain: String, queries: Int = 1) = StatRow(
        id = domain,
        title = domain,
        value = queries.toString(),
        rawQueries = queries,
    )

    @Test
    fun `a new page is added after the rows already shown`() {
        val merged = mergeAnalyticsPage(
            shown = listOf(row("a.example"), row("b.example")),
            page = listOf(row("c.example"), row("d.example")),
            requestedCursor = "c1",
            returnedCursor = "c2",
        )

        assertEquals(
            listOf("a.example", "b.example", "c.example", "d.example"),
            merged.rows.map { it.id },
        )
        assertEquals("c2", merged.nextCursor)
    }

    @Test
    fun `a row identical to one already shown is not added again`() {
        val merged = mergeAnalyticsPage(
            shown = listOf(row("a.example", 5), row("b.example", 4)),
            page = listOf(row("b.example", 4), row("c.example", 3)),
            requestedCursor = "c1",
            returnedCursor = "c2",
        )

        assertEquals(listOf("a.example", "b.example", "c.example"), merged.rows.map { it.id })
        assertEquals("c2", merged.nextCursor)
    }

    @Test
    fun `the same domain with a different count is kept, not merged away`() {
        val merged = mergeAnalyticsPage(
            shown = listOf(row("a.example", 5)),
            page = listOf(row("a.example", 6), row("b.example", 4)),
            requestedCursor = "c1",
            returnedCursor = "c2",
        )

        assertEquals(
            listOf("a.example" to 5, "a.example" to 6, "b.example" to 4),
            merged.rows.map { it.id to it.rawQueries },
        )
    }

    @Test
    fun `an empty page ends paging even if the API still offers a cursor`() {
        val merged = mergeAnalyticsPage(
            shown = listOf(row("a.example")),
            page = emptyList(),
            requestedCursor = "c1",
            returnedCursor = "c2",
        )

        assertEquals(1, merged.rows.size)
        assertNull(merged.nextCursor)
    }

    @Test
    fun `a page that only repeats shown rows ends paging`() {
        val shown = listOf(row("a.example", 5), row("b.example", 4))

        val merged = mergeAnalyticsPage(shown, shown, requestedCursor = "c1", returnedCursor = "c2")

        assertEquals(shown, merged.rows)
        assertNull(merged.nextCursor)
    }

    @Test
    fun `a cursor that does not move forward ends paging`() {
        val merged = mergeAnalyticsPage(
            shown = listOf(row("a.example")),
            page = listOf(row("b.example")),
            requestedCursor = "c1",
            returnedCursor = "c1",
        )

        assertEquals(listOf("a.example", "b.example"), merged.rows.map { it.id })
        assertNull(merged.nextCursor)
    }

    @Test
    fun `no cursor from the API ends paging`() {
        val merged = mergeAnalyticsPage(emptyList(), listOf(row("a.example")), null, null)

        assertNull(merged.nextCursor)
        assertEquals(1, merged.rows.size)
    }

    @Test
    fun `the first page continues when a cursor is returned`() {
        val merged = mergeAnalyticsPage(emptyList(), listOf(row("a.example")), null, "c1")

        assertEquals("c1", merged.nextCursor)
    }

    @Test
    fun `a first page that repeats a row inside itself shows it once`() {
        val merged = mergeAnalyticsPage(
            shown = emptyList(),
            page = listOf(row("a.example", 5), row("a.example", 5), row("b.example", 4)),
            requestedCursor = null,
            returnedCursor = "c1",
        )

        assertEquals(listOf("a.example", "b.example"), merged.rows.map { it.id })
    }

    // --- through the real request and parsing code -------------------------------------------

    private lateinit var server: MockWebServer
    private lateinit var repository: NextDnsAnalyticsRepository

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NextDnsApi::class.java)
        repository = NextDnsAnalyticsRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a server that keeps returning the same page and cursor stops after one repeat`() = runTest {
        val page = """
            {"data":[{"domain":"a.example","queries":5},{"domain":"b.example","queries":4}],
             "meta":{"pagination":{"cursor":"same"}}}
        """.trimIndent()
        repeat(4) { server.enqueue(MockResponse().setResponseCode(200).setBody(page)) }
        val card = StatsRegistry.cards.first { it.key == "domains.resolved" } as ListCard
        val scope = AnalyticsScope()
        var shown = emptyList<StatRow>()
        var cursor: String? = null
        var requests = 0

        do {
            val result = repository.getExpandedCardData("profile", card, scope, cursor)
            requests++
            assertTrue(result is ApiResult.Success)
            val page1 = (result as ApiResult.Success).value
            val merged = mergeAnalyticsPage(shown, page1.rows, cursor, page1.cursor)
            shown = merged.rows
            cursor = merged.nextCursor
        } while (cursor != null && requests < 4)

        // First page: cursor "same" is new. Second request returns the same page and cursor.
        assertEquals(2, requests)
        assertEquals(listOf("a.example", "b.example"), shown.map { it.id })
        assertNull(cursor)
    }
}

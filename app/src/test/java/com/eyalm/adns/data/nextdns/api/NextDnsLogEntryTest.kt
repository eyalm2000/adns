package com.eyalm.adns.data.nextdns.api

import com.eyalm.adns.ui.screens.settings.formatRawJson
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading log entries: the original JSON is kept, and incomplete entries do not crash. */
class NextDnsLogEntryTest {
    private val gson = Gson()

    private fun parse(json: String) = gson.fromJson(json, NextDnsLogsResponse::class.java)

    @Test
    fun `the raw JSON of an entry is kept exactly as returned, including unknown fields`() {
        val entryJson = """
            {"timestamp":"2026-03-18T03:00:10.338Z","domain":"example.test","root":"example.test",
             "encrypted":true,"protocol":"DNS-over-HTTPS","clientIp":"192.0.2.7",
             "status":"blocked","reasons":[{"id":"blocklist:test","name":"Test list"}],
             "futureField":{"nested":[1,2,3]}}
        """.trimIndent()

        val entry = parse("""{"data":[$entryJson]}""").data.single()

        assertEquals(JsonParser.parseString(entryJson), JsonParser.parseString(entry.rawJson))
        assertEquals("example.test", entry.domain)
        assertEquals("blocked", entry.status)
        assertEquals(listOf("Test list"), entry.reasons.map { it.name })
        assertTrue(entry.rawJson!!.contains("futureField"))
    }

    @Test
    fun `each entry keeps its own raw JSON`() {
        val response = parse(
            """{"data":[
                {"timestamp":"t1","domain":"one.test","encrypted":true,"protocol":"UDP","status":"default"},
                {"timestamp":"t2","domain":"two.test","encrypted":false,"protocol":"UDP","status":"default"}
            ]}"""
        )

        assertTrue(response.data[0].rawJson!!.contains("one.test"))
        assertFalse(response.data[0].rawJson!!.contains("two.test"))
        assertTrue(response.data[1].rawJson!!.contains("two.test"))
    }

    @Test
    fun `an entry without the usual fields is read without failing and shows nothing invented`() {
        val entry = parse("""{"data":[{"domain":"bare.test"}]}""").data.single()

        assertEquals("bare.test", entry.domain)
        assertEquals("", entry.protocol)
        assertEquals("", entry.timestamp)
        assertEquals("unknown", entry.status)
        assertFalse(entry.encrypted)
        assertTrue(entry.reasons.isEmpty())
        assertNull(entry.clientIp)
        assertNull(entry.device)
        assertNull(entry.type)
        // The raw JSON is what the API sent, without the neutral values added for reading.
        assertEquals(JsonParser.parseString("""{"domain":"bare.test"}"""), JsonParser.parseString(entry.rawJson))
    }

    @Test
    fun `null values are treated like missing ones`() {
        val entry = parse(
            """{"data":[{"domain":"x.test","protocol":null,"status":null,"reasons":null,
               "timestamp":null,"encrypted":null}]}"""
        ).data.single()

        assertEquals("", entry.protocol)
        assertEquals("unknown", entry.status)
        assertTrue(entry.reasons.isEmpty())
        assertTrue(entry.rawJson!!.contains("\"protocol\":null"))
    }

    @Test
    fun `entries compare by their fields, not by their raw text`() {
        val first = parse("""{"data":[{"domain":"a.test","status":"default","protocol":"UDP"}]}""").data.single()
        val second = parse("""{"data":[{"protocol":"UDP","status":"default","domain":"a.test"}]}""").data.single()

        assertEquals(first, second)
        assertNotNull(first.rawJson)
    }

    @Test
    fun `an entry built in code has no raw JSON`() {
        val entry = NextDnsLogEntry(
            timestamp = "t",
            encrypted = true,
            protocol = "UDP",
            status = "default",
        )

        assertNull(entry.rawJson)
    }

    @Test
    fun `the pagination cursor and later fields are still read`() {
        val response = parse(
            """{"data":[{"domain":"a.test","status":"default"}],
               "meta":{"pagination":{"cursor":"next"},"stream":{"id":"s"}}}"""
        )

        assertEquals("next", response.meta?.pagination?.cursor)
    }

    // --- display formatting ------------------------------------------------------------------

    @Test
    fun `formatting indents the JSON without changing it`() {
        val raw = """{"domain":"a.test","reasons":[{"id":"x","name":"X"}]}"""

        val formatted = formatRawJson(raw)

        assertTrue(formatted.contains("\n"))
        assertEquals(JsonParser.parseString(raw), JsonParser.parseString(formatted))
    }

    @Test
    fun `text that is not JSON is shown as it is`() {
        assertEquals("not json {", formatRawJson("not json {"))
    }
}

package com.eyalm.adns.data.nextdns.settings

import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextDnsSettingsTest {

    @Test
    fun `reads nested values and returns null for a missing path`() {
        val settings = JsonParser.parseString(
            """{ "logs": { "drop": { "ip": false } } }"""
        ).asJsonObject

        assertFalse(settings.valueAt(listOf("logs", "drop", "ip"))!!.asBoolean)
        assertNull(settings.valueAt(listOf("logs", "retention")))
    }

    @Test
    fun `builds canonical nested patch payloads`() {
        assertEquals(
            mapOf("logs" to mapOf("retention" to 63_072_000)),
            nestedPayload(listOf("logs", "retention"), 63_072_000),
        )
        assertEquals(
            mapOf("logs" to mapOf("drop" to mapOf("domain" to true))),
            nestedPayload(listOf("logs", "drop", "domain"), true),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an empty patch path`() {
        nestedPayload(emptyList(), true)
    }

    @Test
    fun `log privacy booleans invert the API drop fields`() {
        val clientIps = NextDnsSettingRegistry.settings.settings
            .filterIsInstance<BooleanSettingSpec>()
            .first { it.id == SettingId("settings.logs.logClientIps") }

        val apiFalse = JsonParser.parseString("false")
        val apiTrue = JsonParser.parseString("true")

        assertTrue(clientIps.decode(apiFalse)!!)
        assertFalse(clientIps.decode(apiTrue)!!)
        assertEquals(false, clientIps.encode(true))
        assertEquals(true, clientIps.encode(false))
    }

    @Test
    fun `retention and location preserve observed values`() {
        val settings = NextDnsSettingRegistry.settings.settings
        val retention = settings.filterIsInstance<IntSelectSettingSpec>().single()
        val location = settings.filterIsInstance<StringSelectSettingSpec>().single()

        assertEquals(
            listOf(3_600, 21_600, 86_400, 604_800, 2_592_000, 7_776_000, 15_552_000, 31_536_000, 63_072_000),
            retention.options.map { it.value },
        )
        assertEquals("settings", retention.api.page)
        assertEquals(listOf("logs", "retention"), retention.api.path)
        assertEquals(listOf("us", "eu", "ch"), location.options.map { it.value })
        assertEquals(listOf("logs", "location"), location.api.path)
        assertTrue(location.confirmation?.destructive == true)
    }

    @Test
    fun `log detail settings are visible only while logging is enabled`() {
        val retention = NextDnsSettingRegistry.settings.settings
            .filterIsInstance<IntSelectSettingSpec>()
            .single()
        val visibility = requireNotNull(retention.visibleWhen)
        val logsEnabled = SettingId("settings.logs.enabled")

        assertFalse(visibility(mapOf(logsEnabled to JsonPrimitive(false))))
        assertTrue(visibility(mapOf(logsEnabled to JsonPrimitive(true))))
        assertFalse(visibility(emptyMap()))
    }

    @Test
    fun `boolean setting spec defaults to isBeta false and supports beta label parameter`() {
        val standardSetting = BooleanSettingSpec(
            id = SettingId("test.standard"),
            api = ApiBinding("test", listOf("standard")),
            locale = LocaleBinding(titlePath = listOf("test", "title")),
        )
        assertFalse(standardSetting.isBeta)
        assertFalse(standardSetting.beta)

        val betaSetting = BooleanSettingSpec(
            id = SettingId("test.beta1"),
            api = ApiBinding("test", listOf("beta1")),
            locale = LocaleBinding(titlePath = listOf("test", "title")),
            isBeta = true,
        )
        assertTrue(betaSetting.isBeta)
        assertTrue(betaSetting.beta)
        assertEquals(FeatureMaturity.BETA, betaSetting.maturity)
    }

    @Test
    fun `security settings match exact API order and maturity levels`() {
        val expectedKeysAndMaturities = listOf(
            "threatIntelligenceFeeds" to FeatureMaturity.STABLE,
            "aiThreatDetection" to FeatureMaturity.BETA,
            "googleSafeBrowsing" to FeatureMaturity.STABLE,
            "cryptojacking" to FeatureMaturity.STABLE,
            "dnsRebinding" to FeatureMaturity.STABLE,
            "idnHomographs" to FeatureMaturity.STABLE,
            "typosquatting" to FeatureMaturity.STABLE,
            "dga" to FeatureMaturity.STABLE,
            "nrd" to FeatureMaturity.STABLE,
            "freeHostingDomains" to FeatureMaturity.EARLY_ACCESS,
            "ddns" to FeatureMaturity.BETA,
            "tunnelingEndpoints" to FeatureMaturity.EARLY_ACCESS,
            "dataDropServices" to FeatureMaturity.EARLY_ACCESS,
            "residentialHosting" to FeatureMaturity.EARLY_ACCESS,
            "untrustedCertificates" to FeatureMaturity.UNRELEASED,
            "fastFluxNetworks" to FeatureMaturity.UNRELEASED,
            "dnsDataExfiltration" to FeatureMaturity.UNRELEASED,
            "dnsPayloadDelivery" to FeatureMaturity.UNRELEASED,
            "decentralizedWebGateways" to FeatureMaturity.EARLY_ACCESS,
            "highRiskTlds" to FeatureMaturity.UNRELEASED,
            "parking" to FeatureMaturity.STABLE,
            "csam" to FeatureMaturity.STABLE,
        )

        val actual = NextDnsSettingRegistry.security.settings
            .filterIsInstance<BooleanSettingSpec>()
            .map { it.api.path.first() to it.maturity }

        assertEquals(expectedKeysAndMaturities, actual)
    }

    @Test
    fun `feature maturity correctly maps to isBeta`() {
        val earlyAccess = BooleanSettingSpec(
            id = SettingId("test.ea"),
            api = ApiBinding("test", listOf("ea")),
            locale = LocaleBinding(titlePath = listOf("test", "title")),
            maturity = FeatureMaturity.EARLY_ACCESS,
        )
        assertFalse(earlyAccess.isBeta)
        assertEquals(FeatureMaturity.EARLY_ACCESS, earlyAccess.maturity)

        val unreleased = BooleanSettingSpec(
            id = SettingId("test.unreleased"),
            api = ApiBinding("test", listOf("unreleased")),
            locale = LocaleBinding(titlePath = listOf("test", "title")),
            maturity = FeatureMaturity.UNRELEASED,
        )
        assertFalse(unreleased.isBeta)
        assertEquals(FeatureMaturity.UNRELEASED, unreleased.maturity)

        val beta = BooleanSettingSpec(
            id = SettingId("test.beta"),
            api = ApiBinding("test", listOf("beta")),
            locale = LocaleBinding(titlePath = listOf("test", "title")),
            maturity = FeatureMaturity.BETA,
        )
        assertTrue(beta.isBeta)
        assertEquals(FeatureMaturity.BETA, beta.maturity)
    }

    @Test
    fun `select specs maturity and isBeta resolve without recursion`() {
        val intSpec = NextDnsSettingRegistry.settings.settings
            .filterIsInstance<IntSelectSettingSpec>()
            .first()
        assertEquals(FeatureMaturity.STABLE, intSpec.maturity)
        assertFalse(intSpec.isBeta)

        val stringSpec = NextDnsSettingRegistry.settings.settings
            .filterIsInstance<StringSelectSettingSpec>()
            .first()
        assertEquals(FeatureMaturity.STABLE, stringSpec.maturity)
        assertFalse(stringSpec.isBeta)
    }
}

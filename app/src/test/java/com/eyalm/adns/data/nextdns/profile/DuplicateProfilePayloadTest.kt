package com.eyalm.adns.data.nextdns.profile

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateProfilePayloadTest {

    @Test
    fun `duplicate payload keeps createable fields and removes detail metadata`() {
        val detail = JsonParser.parseString(
            """
            {
              "security": {
                "threatIntelligenceFeeds": true,
                "aiThreatDetection": true,
                "googleSafeBrowsing": false,
                "cryptojacking": false,
                "dnsRebinding": false,
                "idnHomographs": true,
                "typosquatting": true,
                "dga": true,
                "nrd": false,
                "ddns": false,
                "parking": false,
                "csam": true,
                "tlds": [{"id": "review", "name": "Review"}]
              },
              "privacy": {
                "disguisedTrackers": false,
                "allowAffiliate": true,
                "blocklists": [{"id": "goodbye-ads", "name": "Goodbye Ads", "entries": 42}],
                "natives": [{"id": "huawei", "devices": "Huawei"}]
              },
              "parentalControl": {
                "safeSearch": false,
                "youtubeRestrictedMode": true,
                "blockBypass": false,
                "services": [{"id": "roblox", "recreation": true, "active": true, "website": "https://roblox.com"}],
                "categories": [{"id": "social-networks", "recreation": false, "active": true, "name": "Social networks"}],
                "recreation": {"timezone": "Asia/Jerusalem"}
              },
              "settings": {
                "logs": {"enabled": true, "drop": {"ip": false, "domain": false}, "retention": 31536000, "location": "us"},
                "blockPage": {"enabled": false},
                "performance": {"ecs": true, "cacheBoost": false, "cnameFlattening": false},
                "bav": true,
                "web3": false
              },
              "denylist": [{"id": "ok.cim", "active": true}],
              "allowlist": [{"id": "igxigx.google", "active": false}]
            }
            """.trimIndent(),
        ).asJsonObject

        val payload = detail.toDuplicateProfilePayload("  Copy of profile  ")

        assertEquals("Copy of profile", payload["name"].asString)
        assertEquals("review", payload["security"].asJsonObject["tlds"].asJsonArray[0].asJsonObject["id"].asString)
        assertFalse(payload["security"].asJsonObject["tlds"].asJsonArray[0].asJsonObject.has("name"))

        val blocklist = payload["privacy"].asJsonObject["blocklists"].asJsonArray[0].asJsonObject
        assertEquals("goodbye-ads", blocklist["id"].asString)
        assertFalse(blocklist.has("name"))
        assertFalse(blocklist.has("entries"))

        val service = payload["parentalControl"].asJsonObject["services"].asJsonArray[0].asJsonObject
        assertTrue(service["recreation"].asBoolean)
        assertFalse(service.has("website"))
        assertFalse(payload["parentalControl"].asJsonObject.has("recreation"))
        assertEquals(true, payload["denylist"].asJsonArray[0].asJsonObject["active"].asBoolean)
    }

    @Test
    fun `duplicate payload preserves all 22 security settings when present`() {
        val detail = JsonParser.parseString(
            """
            {
              "security": {
                "threatIntelligenceFeeds": true,
                "aiThreatDetection": false,
                "googleSafeBrowsing": true,
                "cryptojacking": false,
                "dnsRebinding": true,
                "idnHomographs": false,
                "typosquatting": true,
                "dga": false,
                "nrd": true,
                "freeHostingDomains": true,
                "ddns": false,
                "tunnelingEndpoints": true,
                "dataDropServices": false,
                "residentialHosting": true,
                "untrustedCertificates": true,
                "fastFluxNetworks": false,
                "dnsDataExfiltration": true,
                "dnsPayloadDelivery": false,
                "decentralizedWebGateways": true,
                "highRiskTlds": true,
                "parking": false,
                "csam": true,
                "tlds": []
              },
              "privacy": {
                "disguisedTrackers": false,
                "allowAffiliate": true,
                "blocklists": [],
                "natives": []
              },
              "parentalControl": {
                "safeSearch": false,
                "youtubeRestrictedMode": false,
                "blockBypass": false
              },
              "settings": {
                "logs": {"enabled": false, "drop": {"ip": false, "domain": false}, "retention": 3600, "location": "us"},
                "blockPage": {"enabled": false},
                "performance": {"ecs": false, "cacheBoost": false, "cnameFlattening": false},
                "bav": false,
                "web3": false
              }
            }
            """.trimIndent(),
        ).asJsonObject

        val payload = detail.toDuplicateProfilePayload("Cloned")
        val security = payload["security"].asJsonObject

        assertTrue(security["threatIntelligenceFeeds"].asBoolean)
        assertFalse(security["aiThreatDetection"].asBoolean)
        assertTrue(security["freeHostingDomains"].asBoolean)
        assertTrue(security["tunnelingEndpoints"].asBoolean)
        assertFalse(security["dataDropServices"].asBoolean)
        assertTrue(security["residentialHosting"].asBoolean)
        assertTrue(security["untrustedCertificates"].asBoolean)
        assertFalse(security["fastFluxNetworks"].asBoolean)
        assertTrue(security["dnsDataExfiltration"].asBoolean)
        assertFalse(security["dnsPayloadDelivery"].asBoolean)
        assertTrue(security["decentralizedWebGateways"].asBoolean)
        assertTrue(security["highRiskTlds"].asBoolean)
    }
}

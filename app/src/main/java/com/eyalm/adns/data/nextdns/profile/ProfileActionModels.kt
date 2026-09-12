package com.eyalm.adns.data.nextdns.profile

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

private val securityBooleanKeys = listOf(
    "threatIntelligenceFeeds",
    "aiThreatDetection",
    "googleSafeBrowsing",
    "cryptojacking",
    "dnsRebinding",
    "idnHomographs",
    "typosquatting",
    "dga",
    "nrd",
    "freeHostingDomains",
    "ddns",
    "tunnelingEndpoints",
    "dataDropServices",
    "residentialHosting",
    "untrustedCertificates",
    "fastFluxNetworks",
    "dnsDataExfiltration",
    "dnsPayloadDelivery",
    "decentralizedWebGateways",
    "highRiskTlds",
    "parking",
    "csam",
)

private val parentalBooleanKeys = listOf(
    "safeSearch",
    "youtubeRestrictedMode",
    "blockBypass",
)

/**
 * Produces the documented `POST /profiles` duplicate payload from a profile detail response.
 *
 * The detail response includes read-only metadata on list items. Copy only the fields accepted
 * by the create endpoint so a duplicate remains stable if NextDNS adds more display metadata.
 */
fun JsonObject.toDuplicateProfilePayload(newName: String): JsonObject {
    val detail = this
    val security = detail.requiredObject("security")
    val privacy = detail.requiredObject("privacy")
    val parentalControl = detail.requiredObject("parentalControl")

    return JsonObject().apply {
        addProperty("name", newName.trim())
        add(
            "security",
            security.copyFields(securityBooleanKeys).apply {
                add("tlds", security.itemReferences("tlds", listOf("id")))
            },
        )
        add(
            "privacy",
            privacy.copyFields(listOf("disguisedTrackers", "allowAffiliate")).apply {
                add("blocklists", privacy.itemReferences("blocklists", listOf("id")))
                add("natives", privacy.itemReferences("natives", listOf("id")))
            },
        )
        add(
            "parentalControl",
            parentalControl.copyFields(parentalBooleanKeys).apply {
                add(
                    "services",
                    parentalControl.itemReferences("services", listOf("id", "recreation", "active")),
                )
                add(
                    "categories",
                    parentalControl.itemReferences("categories", listOf("id", "recreation", "active")),
                )
            },
        )
        add("settings", detail.requiredObject("settings").settingsPayload())
        add("denylist", detail.itemReferences("denylist", listOf("id", "active")))
        add("allowlist", detail.itemReferences("allowlist", listOf("id", "active")))
    }
}

private fun JsonObject.settingsPayload(): JsonObject {
    val settings = this
    val logs = settings.requiredObject("logs")
    val blockPage = settings.requiredObject("blockPage")
    val performance = settings.requiredObject("performance")

    return JsonObject().apply {
        add(
            "logs",
            logs.copyFields(listOf("enabled", "retention", "location")).apply {
                add("drop", logs.requiredObject("drop").copyFields(listOf("ip", "domain")))
            },
        )
        add("blockPage", blockPage.copyFields(listOf("enabled")))
        add(
            "performance",
            performance.copyFields(listOf("ecs", "cacheBoost", "cnameFlattening")),
        )
        add("bav", settings.required("bav").deepCopy())
        add("web3", settings.required("web3").deepCopy())
    }
}

private fun JsonObject.itemReferences(name: String, fields: List<String>): JsonArray {
    val source = get(name)
    if (source == null || source.isJsonNull) return JsonArray()
    if (!source.isJsonArray) throw IllegalArgumentException("Expected $name to be an array")

    return JsonArray().apply {
        source.asJsonArray.forEach { item ->
            if (!item.isJsonObject) throw IllegalArgumentException("Expected an object in $name")
            add(item.asJsonObject.copyFields(fields))
        }
    }
}

private fun JsonObject.copyFields(fields: List<String>): JsonObject {
    val source = this
    return JsonObject().apply {
        fields.forEach { field ->
            source.get(field)?.deepCopy()?.let { add(field, it) }
        }
    }
}

private fun JsonObject.requiredObject(name: String): JsonObject {
    val value = required(name)
    if (!value.isJsonObject) throw IllegalArgumentException("Expected $name to be an object")
    return value.asJsonObject
}

private fun JsonObject.required(name: String): JsonElement =
    get(name) ?: throw IllegalArgumentException("Missing $name")

package com.eyalm.adns.data.nextdns.settings

import com.eyalm.adns.R

object NextDnsSettingRegistry {
    val security = SettingsPageSpec(
        page = "security",
        settings = listOf(
            booleanSetting("security", "threatIntelligenceFeeds", "security", "feeds"),
            booleanSetting("security", "aiThreatDetection", "security", "ai", true),
            booleanSetting("security", "googleSafeBrowsing", "security", "googleSafeBrowsing"),
            booleanSetting("security", "cryptojacking", "security", "cryptojacking"),
            booleanSetting("security", "dnsRebinding", "security", "dnsRebinding"),
            booleanSetting("security", "idnHomographs", "security", "homograph"),
            booleanSetting("security", "typosquatting", "security", "typosquatting"),
            booleanSetting("security", "dga", "security", "dga"),
            booleanSetting("security", "nrd", "security", "nrd"),
            booleanSetting("security", "ddns", "security", "ddns", true),
            booleanSetting("security", "parking", "security", "parked"),
            booleanSetting("security", "csam", "security", "csam"),
        ),
    )

    val privacy = SettingsPageSpec(
        page = "privacy",
        settings = listOf(
            booleanSetting("privacy", "disguisedTrackers", "privacy", "disguised"),
            booleanSetting("privacy", "allowAffiliate", "privacy", "affiliate"),
        ),
    )

    val parentalControl = SettingsPageSpec(
        page = "parentalControl",
        settings = listOf(
            booleanSetting("parentalControl", "safeSearch", "parentalControl", "safesearch"),
            booleanSetting("parentalControl", "youtubeRestrictedMode", "parentalControl", "youtubeRestricted"),
            booleanSetting("parentalControl", "blockBypass", "parentalControl", "bypass"),
        ),
    )

    val settings = SettingsPageSpec(
        page = "settings",
        settings = listOf(
            BooleanSettingSpec(
                id = SettingId("settings.logs.enabled"),
                api = ApiBinding("settings", listOf("logs", "enabled")),
                locale = LocaleBinding(
                    titlePath = listOf("settings", "logs", "enable"),
                    descriptionPath = listOf("settings", "logs", "description"),
                ),
            ),
            BooleanSettingSpec(
                id = SettingId("settings.logs.logClientIps"),
                api = ApiBinding("settings", listOf("logs", "drop", "ip")),
                locale = LocaleBinding(titlePath = listOf("settings", "logs", "privacy", "ip")),
                inverted = true,
                visibleWhen = ::logsAreEnabled,
            ),
            BooleanSettingSpec(
                id = SettingId("settings.logs.logDomains"),
                api = ApiBinding("settings", listOf("logs", "drop", "domain")),
                locale = LocaleBinding(titlePath = listOf("settings", "logs", "privacy", "domains")),
                inverted = true,
                visibleWhen = ::logsAreEnabled,
            ),
            IntSelectSettingSpec(
                id = SettingId("settings.logs.retention"),
                api = ApiBinding("settings", listOf("logs", "retention")),
                locale = LocaleBinding(titlePath = listOf("settings", "logs", "retention", "name")),
                options = listOf(
                    retentionOption(3_600, "1h"),
                    retentionOption(21_600, "6h"),
                    retentionOption(86_400, "1d"),
                    retentionOption(604_800, "1w"),
                    retentionOption(2_592_000, "1m"),
                    retentionOption(7_776_000, "3m"),
                    retentionOption(15_552_000, "6m"),
                    retentionOption(31_536_000, "1y"),
                    retentionOption(63_072_000, "2y"),
                ),
                visibleWhen = ::logsAreEnabled,
            ),
            StringSelectSettingSpec(
                id = SettingId("settings.logs.location"),
                api = ApiBinding("settings", listOf("logs", "location")),
                locale = LocaleBinding(titlePath = listOf("settings", "logs", "location", "name")),
                options = listOf("us", "eu", "ch").map { code ->
                    SelectOption(
                        value = code,
                        labelPath = listOf("settings", "logs", "location", "locations", code, "name"),
                        descriptionPath = listOf(
                            "settings", "logs", "location", "locations", code, "description"
                        ),
                        iconKey = code,
                    )
                },
                confirmation = ConfirmationSpec(
                    titlePath = listOf("settings", "logs", "location", "confirm", "title"),
                    bodyPath = listOf("settings", "logs", "location", "confirm", "text"),
                    destructive = true,
                ),
                visibleWhen = ::logsAreEnabled,
            ),
            BooleanSettingSpec(
                id = SettingId("settings.blockPage.enabled"),
                api = ApiBinding("settings", listOf("blockPage", "enabled")),
                locale = LocaleBinding(
                    titlePath = listOf("settings", "blockpage", "enable"),
                    descriptionPath = listOf("settings", "blockpage", "description"),
                ),
            ),
            BooleanSettingSpec(
                id = SettingId("settings.performance.ecs"),
                api = ApiBinding("settings", listOf("performance", "ecs")),
                locale = LocaleBinding(
                    titlePath = listOf("settings", "performance", "ecs", "name"),
                    descriptionPath = listOf("settings", "performance", "ecs", "description"),
                ),
            ),
            BooleanSettingSpec(
                id = SettingId("settings.performance.cacheBoost"),
                api = ApiBinding("settings", listOf("performance", "cacheBoost")),
                locale = LocaleBinding(
                    titlePath = listOf("settings", "performance", "cache", "name"),
                    descriptionPath = listOf("settings", "performance", "cache", "description"),
                ),
            ),
            BooleanSettingSpec(
                id = SettingId("settings.performance.cnameFlattening"),
                api = ApiBinding("settings", listOf("performance", "cnameFlattening")),
                locale = LocaleBinding(
                    titlePath = listOf("settings", "performance", "flatten", "name"),
                    descriptionPath = listOf("settings", "performance", "flatten", "description"),
                ),
            ),
            BooleanSettingSpec(
                id = SettingId("settings.bav"),
                api = ApiBinding("settings", listOf("bav")),
                locale = LocaleBinding(
                    titlePath = listOf("settings", "ageVerification", "name"),
                    descriptionPath = listOf("settings", "ageVerification", "description"),
                ),
                isBeta = true
            ),
            BooleanSettingSpec(
                id = SettingId("settings.web3"),
                api = ApiBinding("settings", listOf("web3")),
                locale = LocaleBinding(
                    titlePath = listOf("settings", "web3", "name"),
                    descriptionPath = listOf("settings", "web3", "description"),
                ),
                isBeta = true
            ),
        ),
    )
}

private fun booleanSetting(
    page: String,
    apiKey: String,
    localeCategory: String,
    localeKey: String,
    isBeta: Boolean = false,
): BooleanSettingSpec = BooleanSettingSpec(
    id = SettingId("$page.$apiKey"),
    api = ApiBinding(page = page, path = listOf(apiKey)),
    locale = LocaleBinding(
        titlePath = listOf(localeCategory, localeKey, "name"),
        descriptionPath = listOf(localeCategory, localeKey, "description"),
    ),
    isBeta = isBeta,
)

private fun retentionOption(value: Int, localeKey: String): SelectOption<Int> = SelectOption(
    value = value,
    labelPath = listOf("settings", "logs", "retention", "options", localeKey),
)

private fun logsAreEnabled(values: Map<SettingId, com.google.gson.JsonElement>): Boolean =
    values[SettingId("settings.logs.enabled")]
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
        ?.asBoolean == true

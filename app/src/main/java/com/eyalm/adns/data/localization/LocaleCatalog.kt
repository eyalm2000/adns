package com.eyalm.adns.data.localization

import java.util.Locale

data class LocaleDescriptor(
    val tag: String,
    val nativeDisplayName: String,
    val hasAndroidResources: Boolean,
    val hasNextDnsCatalog: Boolean,
)

fun canonicalLocaleTag(input: String): String {
    val normalized = input.trim().replace('_', '-')
    if (normalized.equals("iw", ignoreCase = true) ||
        normalized.equals("he", ignoreCase = true)
    ) {
        return "he"
    }
    val tag = Locale.forLanguageTag(normalized).toLanguageTag()
    return when {
        tag.startsWith("zh-Hans", ignoreCase = true) || tag.equals("zh-CN", ignoreCase = true) || tag.equals("zh", ignoreCase = true) -> "zh-Hans"
        tag.startsWith("zh-Hant", ignoreCase = true) || tag.equals("zh-TW", ignoreCase = true) || tag.equals("zh-HK", ignoreCase = true) -> "zh-Hant"
        else -> tag
    }
}

fun normalizeSupportedLocaleTags(tags: Iterable<String>): Set<String> =
    tags.asSequence()
        .map(::canonicalLocaleTag)
        .filter { it.isNotBlank() && it != "und" }
        .toSortedSet()

fun resolveSelectedLocaleTag(
    requestedTag: String?,
    configurationTag: String?,
    supportedAndroidTags: Set<String>,
): String {
    val supported = supportedAndroidTags.map(::canonicalLocaleTag).toSet()
    val candidate = requestedTag ?: configurationTag
    val canonicalCandidate = candidate?.let(::canonicalLocaleTag)
    return canonicalCandidate?.takeIf { it in supported }
        ?: "en".takeIf { it in supported }
        ?: supported.sorted().firstOrNull()
        ?: "en"
}

fun buildLocaleDescriptors(
    androidLocaleTags: Set<String>,
    nextDnsAssetNames: List<String>,
): List<LocaleDescriptor> {
    val androidTags = androidLocaleTags.map(::canonicalLocaleTag).toSet()
    val nextDnsTags = nextDnsAssetNames
        .asSequence()
        .filter { it.endsWith(".json", ignoreCase = true) }
        .map { canonicalLocaleTag(it.substringBeforeLast('.')) }
        .toSet()

    return (androidTags + nextDnsTags)
        .sorted()
        .map { tag ->
            val locale = Locale.forLanguageTag(tag)
            LocaleDescriptor(
                tag = tag,
                nativeDisplayName = locale.getDisplayName(locale)
                    .replaceFirstChar { it.titlecase(locale) },
                hasAndroidResources = tag in androidTags,
                hasNextDnsCatalog = tag in nextDnsTags,
            )
        }
}

fun releaseReadyLocaleDescriptors(
    discovered: List<LocaleDescriptor>,
    releasedTags: Set<String>,
): List<LocaleDescriptor> {
    val released = releasedTags.map(::canonicalLocaleTag).toSet()
    return discovered.filter { locale ->
        locale.tag in released &&
            locale.hasAndroidResources &&
            locale.hasNextDnsCatalog
    }
}

fun mergeCatalog(
    fallback: Map<String, Any>,
    selected: Map<String, Any>,
): Map<String, Any> = buildMap {
    putAll(fallback)
    selected.forEach { (key, value) ->
        val base = fallback[key]
        if (value is Map<*, *>) {
            put(
                key,
                if (base is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    mergeCatalog(
                        base as Map<String, Any>,
                        value as Map<String, Any>,
                    )
                } else {
                    value
                },
            )
        } else if (value is String) {
            if (value.isNotBlank() || base == null) {
                put(key, value)
            }
        } else {
            put(key, value)
        }
    }
}

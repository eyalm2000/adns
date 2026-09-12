package com.eyalm.adns.data.nextdns.analytics

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eyalm.adns.R
import com.eyalm.adns.data.Locales
import com.eyalm.adns.data.nextdns.model.ListIcon

// [icon] title / subtitle ........ value
data class StatRow(
    val id: String,
    val title: String,
    val value: String,
    val icon: ListIcon = ListIcon.None,
    val highlightDomain: Boolean = false,
    val tracker: String? = null,
    val rawQueries: Int = 0,
    val percentage: Float? = null,
    val isp: String? = null,
    val location: String? = null,
    val countryCode: String? = null,
    val topDomains: List<String> = emptyList(),
    val isCellular: Boolean? = null,
)

sealed class StatCard {
    abstract val key: String  // e.g. "domains.blocked"
    abstract val feature: String
    abstract val params: Map<String, String>   // base params, WITHOUT "from"
    open val localePath: List<String> = emptyList() // -> name/description node
    open val emptyPath: List<String> = emptyList()  // -> "empty" string
    @get:StringRes open val titleRes: Int? = null
    @get:StringRes open val descriptionRes: Int? = null
    @get:StringRes open val emptyRes: Int? = null

    @Composable
    fun title(): String = titleRes?.let { stringResource(it) }
        ?: Locales.getString(*(localePath + "name").toTypedArray())

    @Composable
    fun description(): String? = descriptionRes?.let { stringResource(it) }
        ?: if (localePath.isNotEmpty()) Locales.getString(*(localePath + "description").toTypedArray()) else null

    @Composable
    fun emptyText(): String = emptyRes?.let { stringResource(it) }
        ?: if (emptyPath.isNotEmpty()) Locales.getString(*emptyPath.toTypedArray()) else ""
}

enum class ListKind { DOMAINS, REASONS, DEVICES, IPS, COUNTRIES, GAFAM, PROTOCOLS, QUERY_TYPES, IP_VERSIONS }

data class ListCard(
    override val key: String,
    override val feature: String,
    override val params: Map<String, String>,
    override val localePath: List<String> = emptyList(),
    override val emptyPath: List<String> = emptyList(),
    val kind: ListKind,
    val limit: Int? = null,
    @get:StringRes override val titleRes: Int? = null,
    @get:StringRes override val descriptionRes: Int? = null,
    @get:StringRes override val emptyRes: Int? = null,
) : StatCard()

enum class PercentKind { ENCRYPTED, DNSSEC }

data class PercentCard(
    override val key: String,
    override val feature: String,
    override val params: Map<String, String>,
    override val localePath: List<String> = emptyList(),
    override val emptyPath: List<String> = emptyList(),
    val kind: PercentKind,
    @get:StringRes override val titleRes: Int? = null,
    @get:StringRes override val descriptionRes: Int? = null,
    @get:StringRes override val emptyRes: Int? = null,
) : StatCard()


object StatsRegistry {
    val cards: List<StatCard> = listOf(
        ListCard("domains.resolved", "domains",
            params = mapOf("status" to "default,allowed", "limit" to "6"),
            localePath = listOf("analytics", "domains", "resolved"),
            emptyPath  = listOf("analytics", "domains", "empty"),
            kind = ListKind.DOMAINS),

        ListCard("domains.blocked", "domains",
            params = mapOf("status" to "blocked", "limit" to "6"),
            localePath = listOf("analytics", "domains", "blocked"),
            emptyPath  = listOf("analytics", "domains", "empty"),
            kind = ListKind.DOMAINS),

        ListCard("reasons", "reasons",
            params = mapOf("limit" to "6", "lang" to "en"),
            localePath = listOf("analytics", "reasons"),
            emptyPath  = listOf("analytics", "reasons", "empty"),
            kind = ListKind.REASONS),

        ListCard("devices", "devices",
            params = mapOf("limit" to "4"),
            localePath = listOf("analytics", "devices"),
            emptyPath  = listOf("analytics", "devices", "empty"),
            kind = ListKind.DEVICES),

        ListCard("ips", "ips",
            params = mapOf("limit" to "4", "lang" to "en"),
            localePath = listOf("analytics", "ips"),
            emptyPath  = listOf("analytics", "ips", "empty"),
            kind = ListKind.IPS),

        ListCard(
            key = "ipVersions",
            feature = "ipVersions",
            params = emptyMap(),
            titleRes = R.string.analytics_ip_versions_title,
            descriptionRes = R.string.analytics_ip_versions_description,
            emptyRes = R.string.analytics_ip_versions_empty,
            kind = ListKind.IP_VERSIONS,
        ),

        ListCard("destinations.gafam", "destinations",
            params = mapOf("type" to "gafam"),
            localePath = listOf("analytics", "gafam"),
            emptyPath  = listOf("analytics", "domains", "empty"),
            kind = ListKind.GAFAM),

        ListCard("domains.root", "domains",
            params = mapOf("root" to "true", "limit" to "6"),
            localePath = listOf("analytics", "domains", "root"),
            emptyPath  = listOf("analytics", "domains", "empty"),
            kind = ListKind.DOMAINS),

        PercentCard("encryption", "encryption",
            params = emptyMap(),
            localePath = listOf("analytics", "encrypted"),
            emptyPath  = listOf("analytics", "encrypted", "empty"),
            kind = PercentKind.ENCRYPTED),

        PercentCard("dnssec", "dnssec",
            params = emptyMap(),
            localePath = listOf("analytics", "dnssec"),
            emptyPath  = listOf("analytics", "dnssec", "empty"),
            kind = PercentKind.DNSSEC),

        ListCard(
            key = "queryTypes",
            feature = "queryTypes",
            params = mapOf("limit" to "6"),
            titleRes = R.string.analytics_query_types_title,
            descriptionRes = R.string.analytics_query_types_description,
            emptyRes = R.string.analytics_query_types_empty,
            kind = ListKind.QUERY_TYPES,
        ),

        ListCard(
            key = "protocols",
            feature = "protocols",
            params = mapOf("limit" to "6"),
            titleRes = R.string.analytics_protocols_title,
            descriptionRes = R.string.analytics_protocols_description,
            emptyRes = R.string.analytics_protocols_empty,
            kind = ListKind.PROTOCOLS,
        ),

        ListCard("destinations.countries", "destinations",
            params = mapOf("type" to "countries", "limit" to "20", "lang" to "en"),
            localePath = listOf("analytics", "destination"),
            emptyPath  = listOf("analytics", "encrypted", "empty"),
            kind = ListKind.COUNTRIES,
            limit = 8),
    )
}
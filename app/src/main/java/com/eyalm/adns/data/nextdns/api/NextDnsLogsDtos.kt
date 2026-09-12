package com.eyalm.adns.data.nextdns.api

import com.google.gson.annotations.SerializedName

data class NextDnsLogsResponse(
    @SerializedName("data") val data: List<NextDnsLogEntry>,
    @SerializedName("meta") val meta: NextDnsLogsMeta? = null,
)

data class NextDnsLogEntry(
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("domain") val domain: String? = null,
    @SerializedName("root") val root: String? = null,
    @SerializedName("tracker") val tracker: String? = null,
    @SerializedName("encrypted") val encrypted: Boolean,
    @SerializedName("protocol") val protocol: String,
    @SerializedName("clientIp") val clientIp: String? = null,
    @SerializedName("client") val client: String? = null,
    @SerializedName("status") val status: String,
    @SerializedName("reasons") val reasons: List<NextDnsLogReason> = emptyList(),
    @SerializedName("device") val device: NextDnsLogDevice? = null,
    @SerializedName("type") val type: String? = null,
)

data class NextDnsLogReason(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
)

data class NextDnsLogDevice(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("model") val model: String?,
)

data class NextDnsLogsMeta(
    @SerializedName("pagination") val pagination: NextDnsLogsPagination? = null,
)

data class NextDnsLogsPagination(
    @SerializedName("cursor") val cursor: String? = null,
)

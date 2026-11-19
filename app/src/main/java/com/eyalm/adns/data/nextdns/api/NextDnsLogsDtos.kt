package com.eyalm.adns.data.nextdns.api

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

data class NextDnsLogsResponse(
    @SerializedName("data") val data: List<NextDnsLogEntry>,
    @SerializedName("meta") val meta: NextDnsLogsMeta? = null,
)

/**
 * One entry of the NextDNS log API.
 *
 * [rawJson] is the entry exactly as the API returned it, so troubleshooting views can show the
 * real response instead of something rebuilt from the fields below. Fields the API left out are
 * given neutral values in the typed copy only (empty text, `false`, no reasons, status
 * `unknown`), never in [rawJson].
 */
@JsonAdapter(LogEntryJsonAdapterFactory::class)
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
) {
    /** The entry as returned by the API, or null if it was not read from an API response. */
    @Transient
    var rawJson: String? = null
}

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

/** Keeps the original JSON of a log entry and tolerates entries that omit required fields. */
internal class LogEntryJsonAdapterFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType != NextDnsLogEntry::class.java) return null
        val delegate = gson.getDelegateAdapter(this, type)
        val elements = gson.getAdapter(JsonElement::class.java)
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) {
                delegate.write(out, value)
            }

            override fun read(reader: JsonReader): T? {
                val element = elements.read(reader)
                if (element == null || element is JsonNull || !element.isJsonObject) return null
                val original = element.asJsonObject
                val entry = delegate.fromJsonTree(withNeutralDefaults(original.deepCopy()))
                (entry as? NextDnsLogEntry)?.rawJson = original.toString()
                return entry
            }
        }
    }

    private fun withNeutralDefaults(entry: JsonObject): JsonObject {
        fun missing(name: String) = !entry.has(name) || entry.get(name).isJsonNull
        if (missing("timestamp")) entry.addProperty("timestamp", "")
        if (missing("protocol")) entry.addProperty("protocol", "")
        if (missing("status")) entry.addProperty("status", "unknown")
        if (missing("encrypted")) entry.addProperty("encrypted", false)
        if (missing("reasons")) entry.add("reasons", JsonArray())
        return entry
    }
}

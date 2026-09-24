package com.eyalm.adns.data

import android.content.Context
import com.eyalm.adns.BuildConfig
import com.eyalm.adns.data.localization.canonicalLocaleTag
import com.eyalm.adns.data.localization.mergeCatalog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Locales {
    private const val DIRECTORY = "locales/nextdns"
    private const val ENGLISH_ASSET = "$DIRECTORY/en.json"

    @Volatile
    private var data: Map<String, Any> = emptyMap()
    private var english: Map<String, Any> = emptyMap()
    private var selectedTag: String = "en"

    @Synchronized
    fun init(context: Context) {
        if (english.isEmpty()) {
            english = readCatalog(context, ENGLISH_ASSET)
        }
        if (data.isEmpty()) data = english
    }

    @Synchronized
    fun select(context: Context, languageTag: String) {
        init(context)
        val canonicalTag = canonicalLocaleTag(languageTag)
        val selectedAsset = context.assets.list(DIRECTORY)
            ?.firstOrNull { file ->
                file.endsWith(".json", ignoreCase = true) &&
                    canonicalLocaleTag(file.substringBeforeLast('.')) == canonicalTag
            }

        data = if (canonicalTag == "en" || selectedAsset == null) {
            english
        } else {
            mergeCatalog(english, readCatalog(context, "$DIRECTORY/$selectedAsset"))
        }
        selectedTag = canonicalTag
    }

    @Synchronized
    fun sync(context: Context, languageTag: String) {
        val canonicalTag = canonicalLocaleTag(languageTag)
        if (data.isEmpty() || selectedTag != canonicalTag) {
            select(context, canonicalTag)
        }
    }

    private fun readCatalog(context: Context, path: String): Map<String, Any> =
        context.assets.open(path).bufferedReader().use { reader ->
            Gson().fromJson(
                reader,
                object : TypeToken<Map<String, Any>>() {}.type,
            )
        }

    private fun missing(path: Array<out String>): String =
        if (BuildConfig.DEBUG) {
            "[missing:${path.joinToString(".")}]"
        } else {
            path.lastOrNull().orEmpty()
        }

    fun getString(vararg path: String): String =
        (getNode(*path) as? String)?.takeIf { it.isNotBlank() } ?: missing(path)

    fun getPlainString(
        path: Array<out String>,
        values: Map<String, String> = emptyMap(),
    ): String {
        val input = getString(*path)
        val builder = StringBuilder()
        var index = 0
        while (index < input.length) {
            val char = input[index]
            when {
                char == '{' && index + 1 < input.length && input[index + 1] == '{' -> {
                    val end = input.indexOf("}}", index + 2)
                    if (end != -1) {
                        val key = input.substring(index + 2, end)
                        builder.append(values[key].orEmpty())
                        index = end + 2
                    } else {
                        builder.append(char)
                        index++
                    }
                }

                char == '<' -> {
                    var cursor = index + 1
                    if (cursor < input.length && input[cursor] == '/') cursor++
                    val startDigits = cursor
                    while (cursor < input.length && input[cursor].isDigit()) cursor++
                    if (cursor > startDigits && cursor < input.length && input[cursor] == '>') {
                        index = cursor + 1
                    } else {
                        builder.append(char)
                        index++
                    }
                }

                else -> {
                    builder.append(char)
                    index++
                }
            }
        }
        return builder.toString()
    }

    @Suppress("UNCHECKED_CAST")
    fun getMap(vararg path: String): Map<String, Any>? = getNode(*path) as? Map<String, Any>

    fun getNode(vararg path: String): Any? {
        var current: Any? = data
        for (key in path) {
            current = if (current is Map<*, *>) current[key] else return null
        }
        return current
    }
}

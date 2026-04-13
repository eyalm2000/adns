package com.eyalm.adns.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class DnsRepository(context: Context) {

    private val resolver = context.contentResolver
    private val sharedPrefs = context.getSharedPreferences("adns_settings", Context.MODE_PRIVATE)

    fun isAdBlockingActive(): Boolean {
        return try {
            val mode = Settings.Global.getString(resolver, DnsConstants.MODE_KEY)
            val host = Settings.Global.getString(resolver, DnsConstants.SPECIFIER_KEY)

            mode == DnsConstants.MODE_HOSTNAME && host == getDnsUrl()
        } catch (e: Exception) {
            Log.e("DnsRepository", "Error checking DNS settings")
            return false
        }
    }

    fun getDnsStatusFlow(): Flow<Boolean> = callbackFlow {

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {


            override fun onChange(selfChange: Boolean) {

                val isActive = isAdBlockingActive()
                if (!isActive) {
                    saveStartTime(0L)
                } else if (isActive && getStartTime() == 0L) {
                    saveStartTime(System.currentTimeMillis())
                }

                trySend(isActive)
            }
        }

        resolver.registerContentObserver(Settings.Global.getUriFor(DnsConstants.MODE_KEY), false, observer)
        resolver.registerContentObserver(Settings.Global.getUriFor(DnsConstants.SPECIFIER_KEY), false, observer)


        trySend(isAdBlockingActive())

        awaitClose {
            resolver.unregisterContentObserver(observer)
        }

    }.distinctUntilChanged()


    fun setAdBlockingState(enabled: Boolean, url: String = getDnsUrl()) {
        try {
            if (enabled) {
                Settings.Global.putString(
                    resolver,
                    DnsConstants.SPECIFIER_KEY,
                    url
                )
                Settings.Global.putString(
                    resolver,
                    DnsConstants.MODE_KEY,
                    DnsConstants.MODE_HOSTNAME
                )
                saveStartTime(System.currentTimeMillis())
            } else {
                Settings.Global.putString(resolver, DnsConstants.MODE_KEY, DnsConstants.MODE_OFF)
                saveStartTime(0L)
            }
        } catch (e: SecurityException) {
            Log.e("DnsRepository", "Permission denied: app activated?")
        }
    }

    fun setCustomUrl(url: String) {

        if (isAdBlockingActive()) {
            setAdBlockingState(true, url)
        }

        sharedPrefs.edit().putString("custom_url", url).apply()

    }

    fun getDnsUrl(): String {
        return sharedPrefs.getString("custom_url", DnsConstants.ADGUARD_DNS) ?: DnsConstants.ADGUARD_DNS
    }

    fun saveStartTime(time: Long) {
        sharedPrefs.edit().putLong("start_time", time).apply()
    }

    fun getStartTime(): Long {
        val startTime = sharedPrefs.getLong("start_time", 0L)
        if (isAdBlockingActive() && startTime == 0L) {
            val now = System.currentTimeMillis()
            saveStartTime(now)
            return now
        }

        return startTime
    }
}
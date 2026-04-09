package com.eyalm.addns.data

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

    fun isAdBlockingActive(): Boolean {
        return try {
            val mode = Settings.Global.getString(resolver, DnsConstants.MODE_KEY)
            val host = Settings.Global.getString(resolver, DnsConstants.SPECIFIER_KEY)

            mode == DnsConstants.MODE_HOSTNAME && host == DnsConstants.ADGUARD_DNS
        } catch (e: Exception) {
            Log.e("DnsRepository", "Error checking DNS settings")
            return false
        }
    }

    fun getDnsStatusFlow(): Flow<Boolean> = callbackFlow {

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(isAdBlockingActive())
            }
        }

        resolver.registerContentObserver(Settings.Global.getUriFor(DnsConstants.MODE_KEY), false, observer)
        resolver.registerContentObserver(Settings.Global.getUriFor(DnsConstants.SPECIFIER_KEY), false, observer)


        trySend(isAdBlockingActive())

        awaitClose {
            resolver.unregisterContentObserver(observer)
        }

    }.distinctUntilChanged()


    fun setAdBlockingState(enabled: Boolean) {
        try {
            if (enabled) {
                Settings.Global.putString(
                    resolver,
                    DnsConstants.SPECIFIER_KEY,
                    DnsConstants.ADGUARD_DNS
                )
                Settings.Global.putString(
                    resolver,
                    DnsConstants.MODE_KEY,
                    DnsConstants.MODE_HOSTNAME
                )
            } else {
                Settings.Global.putString(resolver, DnsConstants.MODE_KEY, DnsConstants.MODE_OFF)
            }
        } catch (e: SecurityException) {
            Log.e("DnsRepository", "Permission denied: app activated?")
        }
    }
}
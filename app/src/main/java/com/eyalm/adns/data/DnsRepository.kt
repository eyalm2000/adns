package com.eyalm.adns.data

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.eyalm.adns.MainActivity
import com.eyalm.adns.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class DnsRepository(private val context: Context) {

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
                updateShortcuts(isActive)
                trySend(isActive)
            }
        }

        resolver.registerContentObserver(Settings.Global.getUriFor(DnsConstants.MODE_KEY), false, observer)
        resolver.registerContentObserver(Settings.Global.getUriFor(DnsConstants.SPECIFIER_KEY), false, observer)

        val initialActive = isAdBlockingActive()
        updateShortcuts(initialActive)
        trySend(initialActive)

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

    fun updateShortcuts(isActive: Boolean) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return

        val toggleShortcut = ShortcutInfo.Builder(context, "toggle_dns")
            .setShortLabel(if (isActive) "Disable Blocker" else "Enable Blocker")
            .setLongLabel(if (isActive) "Disable Ad Blocker" else "Enable Ad Blocker")
            .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher_monochrome))
            .setIntent(Intent(context, MainActivity::class.java).apply {
                action = "com.eyalm.adns.TOGGLE_ACTION"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            .build()

        shortcutManager.dynamicShortcuts = listOf(toggleShortcut)
    }
}
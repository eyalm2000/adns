package com.eyalm.adns.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.eyalm.adns.MainActivity
import com.eyalm.adns.R
import com.eyalm.adns.services.AdnsTileService
import com.eyalm.adns.services.ToggleReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class DnsRepository(private val context: Context) {

    private val resolver = context.contentResolver
    private val sharedPrefs = context.getSharedPreferences("adns_settings", Context.MODE_PRIVATE)
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    init {
        createNotificationChannel()
    }

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
                
                repositoryScope.launch {
                    updateShortcuts()
                    updateNotification()
                }
                trySend(isActive)
            }
        }

        resolver.registerContentObserver(Settings.Global.getUriFor(DnsConstants.MODE_KEY), false, observer)
        resolver.registerContentObserver(Settings.Global.getUriFor(DnsConstants.SPECIFIER_KEY), false, observer)

        val initialActive = isAdBlockingActive()
        repositoryScope.launch {
            updateShortcuts()
            updateNotification()
        }
        trySend(initialActive)

        awaitClose {
            resolver.unregisterContentObserver(observer)
        }

    }.distinctUntilChanged()


    fun setAdBlockingState(enabled: Boolean, url: String = getDnsUrl()): kotlinx.coroutines.Job {
        return repositoryScope.launch {
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
                updateNotification()
                updateShortcuts()
                // Notify the system that the tile state might have changed
                TileService.requestListeningState(context, ComponentName(context, AdnsTileService::class.java))
            } catch (e: SecurityException) {
                Log.e("DnsRepository", "Permission denied: app activated?")
            }
        }
    }

    fun setCustomUrl(url: String) {
        val isActive = isAdBlockingActive()
        sharedPrefs.edit().putString("custom_url", url).apply()
        Log.d("adnsp", "$isActive")
        if (isActive) {
            setAdBlockingState(true, url)
        }
    }

    fun getDnsUrl(): String {
        return sharedPrefs.getString("custom_url", DnsConstants.ADGUARD_DNS) ?: DnsConstants.ADGUARD_DNS
    }

    fun getDnsUrlFlow(): Flow<String> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "custom_url") {
                trySend(getDnsUrl())
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getDnsUrl())
        awaitClose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
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

    fun updateShortcuts() {
        val isActive = isAdBlockingActive()
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

        try {
            shortcutManager.dynamicShortcuts = listOf(toggleShortcut)
        } catch (e: Exception) {
            Log.e("DnsRepository", "Failed to update shortcuts")
        }
    }

    private fun createNotificationChannel() {
        val channelId = "dns_status_channel"
        val name = "Ad Blocker state"
        val importance = NotificationManager.IMPORTANCE_LOW

        val channel = NotificationChannel(channelId, name, importance).apply {
            description = "Shows the state of the Ad Blocker"
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun updateNotification() {

        val isActive = isAdBlockingActive()
        val channelId = "dns_status_channel"
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val buttonIntent = Intent(context, ToggleReceiver::class.java).apply {
            action = "TOGGLE_DNS"
        }

        val buttonPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            buttonIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_qs_adns)
            .setContentTitle("Ad Blocker")
            .setContentText(if (isActive) "Blocker Enabled" else "Blocker Disabled")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isActive)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_qs_adns,
                if (isActive) "Disable Blocker" else "Enable Blocker",
                buttonPendingIntent
            )
            .build()

        notificationManager.notify(1, notification)
    }
}
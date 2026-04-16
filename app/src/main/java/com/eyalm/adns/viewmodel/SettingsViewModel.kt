package com.eyalm.adns.viewmodel

import android.app.Application
import android.app.StatusBarManager
import android.content.ComponentName
import android.util.Patterns
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.eyalm.adns.R
import com.eyalm.adns.data.DnsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DnsRepository(application)
    private val _dnsUrl = MutableStateFlow(repository.getDnsUrl())
    val dnsUrl: StateFlow<String> = _dnsUrl.asStateFlow()

    fun setDnsUrl(url: String) {
        repository.setCustomUrl(url)
        _dnsUrl.value = url
    }

    fun isValidHostname(hostname: String): Boolean {
        return hostname.isNotEmpty() && Patterns.DOMAIN_NAME.matcher(hostname).matches()
    }

    fun addQuickTile() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager =
                getApplication<Application>().getSystemService(StatusBarManager::class.java)
            statusBarManager?.requestAddTileService(
                ComponentName(
                    getApplication(),
                    com.eyalm.adns.services.AdnsTileService::class.java
                ),
                "ADNS AdBlock",
                android.graphics.drawable.Icon.createWithResource(
                    getApplication(),
                    R.drawable.ic_launcher_foreground
                ),
                getApplication<Application>().mainExecutor
            ) { result ->
                val message = when (result) {
                    1 -> "Tile already added!"
                    2 -> "Tile added!"
                    else -> ""
                }
                if (message.isNotEmpty())
                    Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(
                getApplication(),
                "Feature not supported on this version",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun refreshNotification() {
        repository.updateNotification()

    }

}
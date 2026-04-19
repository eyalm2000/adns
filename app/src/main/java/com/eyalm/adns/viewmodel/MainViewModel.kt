package com.eyalm.adns.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eyalm.adns.BuildConfig
import com.eyalm.adns.data.DnsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.Locale


class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DnsRepository(application)
    private val sharedPrefs = application.getSharedPreferences("adns_settings", Context.MODE_PRIVATE)


    val adBlockingState: StateFlow<Boolean> = repository.getDnsStatusFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = repository.isAdBlockingActive()
        )

    fun toggleDns() {

        repository.setAdBlockingState(!adBlockingState.value)

    }

    val runningTimeFlow: StateFlow<String> = flow {
        while (true) {
            val start = repository.getStartTime()
            if (start > 0 && repository.isAdBlockingActive()) {
                val duration = System.currentTimeMillis() - start
                emit(formatDuration(duration))
            } else {
                emit("00:00:00")
            }
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "00:00:00")


    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = (durationMs / (1000 * 60 * 60))
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun getHostname(): String {
        return repository.getDnsUrl()
    }

    fun setDnsUrl(url: String) {
        repository.setCustomUrl(url)
    }

    fun checkForUpdate(onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.github.com/repos/eyalm2000/adns/releases")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()

                val releases = JSONArray(body)

                val latest = releases.getJSONObject(0)
                var latestVersion = latest.getString("tag_name")

                if (latestVersion[0] == 'v') { latestVersion = latestVersion.substring(1) }

                if (BuildConfig.VERSION_NAME == latestVersion) {
                    Log.d("update", "No new update, version from github: $latestVersion")
                    Log.d("update", "is new update dismissed: false}")
                    sharedPrefs.edit { putBoolean("isNewUpdateDismissed", false) }
                    withContext(Dispatchers.Main) { onResult(null) }
                } else {

                    if (sharedPrefs.getBoolean("isNewUpdateDismissed", false) ) {
                        Log.d("update", "isNewUpdateDismissed: true")
                        withContext(Dispatchers.Main) { onResult(null) }
                    } else {
                        sharedPrefs.edit { putBoolean("isNewUpdateDismissed", true) }
                        withContext(Dispatchers.Main) { onResult(latestVersion) }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
    }
}

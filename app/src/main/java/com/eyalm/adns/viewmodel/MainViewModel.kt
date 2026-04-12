package com.eyalm.adns.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eyalm.adns.data.DnsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.Locale


class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DnsRepository(application)

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



}

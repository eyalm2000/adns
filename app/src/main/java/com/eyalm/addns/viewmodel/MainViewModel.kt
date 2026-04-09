package com.eyalm.addns.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eyalm.addns.data.DnsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn


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
}

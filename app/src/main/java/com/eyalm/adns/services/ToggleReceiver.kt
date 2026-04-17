package com.eyalm.adns.services

import android.content.BroadcastReceiver
import android.util.Log
import com.eyalm.adns.data.DnsRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
        Log.d("ToggleReceiver", "Received broadcast")

        if (intent?.action == "TOGGLE_DNS") {
            Log.d("ToggleReceiver", "click broadcast")

            val pendingResult = goAsync()
            val repository = DnsRepository(context!!)
            val newState = !repository.isAdBlockingActive()

            GlobalScope.launch {
                repository.setAdBlockingState(newState).join()
                pendingResult.finish()
            }
        }
    }
}
package com.eyalm.adns.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eyalm.adns.data.DnsRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repository = DnsRepository(context)
            repository.updateNotification(repository.isAdBlockingActive())
        }
    }
}
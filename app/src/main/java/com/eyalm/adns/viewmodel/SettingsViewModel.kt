package com.eyalm.adns.viewmodel

import android.app.Application
import android.app.StatusBarManager
import android.content.ComponentName
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.eyalm.adns.R

class SettingsViewModel(application: Application) : AndroidViewModel(application) {


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
                    else -> "Tile not added"
                }
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

}
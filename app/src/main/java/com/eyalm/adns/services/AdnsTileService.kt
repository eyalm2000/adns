package com.eyalm.adns.services

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.eyalm.adns.data.DnsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdnsTileService : TileService() {

    private val repository by lazy { DnsRepository(this) }
    private var job: Job? = null
    
    private var lastKnownState: Boolean? = null

    override fun onStartListening() {
        super.onStartListening()

        job?.cancel()
        job = CoroutineScope(Dispatchers.Main).launch {
            // Get initial state in background to avoid blocking the cold start sequence
            val initialState = withContext(Dispatchers.IO) {
                repository.isAdBlockingActive()
            }
            lastKnownState = initialState
            updateTile(initialState)

            repository.getDnsStatusFlow().collect { isActive ->
                lastKnownState = isActive
                updateTile(isActive)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        job?.cancel()
        job = null
    }

    override fun onClick() {
        super.onClick()
        Log.d("tile", "Tile clicked")

        val currentState = lastKnownState ?: false
        val newState = !currentState
        
        lastKnownState = newState
        updateTile(newState)

        repository.setAdBlockingState(newState)
    }

    private fun updateTile(isActive: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "ADNS AdBlock"
        tile.updateTile()
    }
}

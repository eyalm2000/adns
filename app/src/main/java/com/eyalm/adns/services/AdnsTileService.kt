package com.eyalm.adns.services

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.eyalm.adns.data.DnsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AdnsTileService : TileService() {

    private val repository by lazy { DnsRepository(this) }
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()

        updateTile(repository.isAdBlockingActive())
        job?.cancel()
        job = CoroutineScope(Dispatchers.Main).launch {
            repository.getDnsStatusFlow().collect { isActive ->
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

        val currentState = repository.isAdBlockingActive()
        val newState = !currentState

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
package com.frerox.toolz.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CaffeinateTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val isServiceRunning = isServiceRunning(CaffeinateService::class.java)
        if (isServiceRunning) {
            val intent = Intent(this, CaffeinateService::class.java).apply {
                action = CaffeinateService.ACTION_STOP
            }
            startService(intent)
        } else {
            val intent = Intent(this, CaffeinateService::class.java).apply {
                action = CaffeinateService.ACTION_START
                putExtra(CaffeinateService.EXTRA_INTERVAL, 30) // Default
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        // Small delay to allow service to update state before we refresh the tile
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updateTile()
        }, 500)
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isRunning = isServiceRunning(CaffeinateService::class.java)
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}

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
        if (CaffeinateService.isRunning) {
            val intent = Intent(this, CaffeinateService::class.java).apply {
                action = CaffeinateService.ACTION_STOP
            }
            startService(intent)
        } else {
            val intent = Intent(this, CaffeinateService::class.java).apply {
                action = CaffeinateService.ACTION_START
                putExtra(CaffeinateService.EXTRA_INTERVAL, 30) // Default
                putExtra(CaffeinateService.EXTRA_INFINITE, false)
                putExtra(CaffeinateService.EXTRA_COLOR, android.graphics.Color.BLUE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        
        // Immediate update and another one after a short delay
        updateTile()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updateTile()
        }, 500)
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isRunning = CaffeinateService.isRunning
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isRunning) "Active" else "Inactive"
        }
        tile.updateTile()
    }
}

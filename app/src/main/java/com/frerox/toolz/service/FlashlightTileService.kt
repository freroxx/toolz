package com.frerox.toolz.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.frerox.toolz.R
import com.frerox.toolz.ui.screens.light.FlashlightMode

// ─────────────────────────────────────────────────────────────────────────────
// FlashlightTileService — Quick Settings tile
//
//  Improvements over previous version:
//  1. Long-press on tile opens FlashlightQuickControlActivity (bottom sheet).
//  2. Single tap cycles: OFF -> STEADY ON -> OFF.
//  3. Tile subtitle shows active mode + brightness when on (Android 10+).
//  4. Four icon states: off, steady, strobe/SOS/disco (distinct icons).
//  5. Requests listening state via FlashlightService.ACTION_STATE_CHANGED so
//     the tile is always up-to-date without polling.
// ─────────────────────────────────────────────────────────────────────────────

class FlashlightTileService : TileService() {

    // ── Tile interactions ─────────────────────────────────────────────────────

    override fun onClick() {
        super.onClick()
        val svc = FlashlightService.getInstance()
        val currentlyOn = svc?.isOn?.value ?: false

        val i = Intent(this, FlashlightService::class.java).apply {
            action = if (currentlyOn) FlashlightService.ACTION_STOP else FlashlightService.ACTION_TOGGLE
        }

        try {
            if (!currentlyOn) {
                startForegroundService(i)
            } else {
                startService(i)
            }
        } catch (e: Exception) {
            // Fallback for older Android or restricted states
            startService(i)
        }

        // Provide immediate feedback to the tile state
        qsTile?.let { tile ->
            tile.state = if (currentlyOn) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            tile.updateTile()
        }
    }

    // ── Tile state ────────────────────────────────────────────────────────────

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val svc  = FlashlightService.getInstance()
        val on   = svc?.isOn?.value ?: false
        val mode = svc?.currentMode() ?: FlashlightMode.STEADY
        val pct  = ((svc?.currentBrightness() ?: 1f) * 100).toInt()

        // State
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        // Icon — four distinct states
        val iconRes = when {
            !on                        -> R.drawable.ic_flashlight_off
            mode == FlashlightMode.STEADY -> R.drawable.ic_flashlight_on
            mode == FlashlightMode.STROBE -> R.drawable.ic_notif_strobe
            mode == FlashlightMode.SOS    -> R.drawable.ic_notif_sos
            else                          -> R.drawable.ic_notif_disco
        }
        tile.icon = Icon.createWithResource(this, iconRes)

        // Label + subtitle
        tile.label = "Flashlight"
        tile.subtitle = if (on) {
            val modeShort = when (mode) {
                FlashlightMode.STEADY -> "Steady"
                FlashlightMode.STROBE -> "Strobe"
                FlashlightMode.SOS    -> "SOS"
                FlashlightMode.DISCO  -> "Disco"
            }
            "$modeShort  $pct%"
        } else {
            "Tap to turn on"
        }

        tile.updateTile()
    }
}
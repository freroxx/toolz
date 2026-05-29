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
        if (svc != null && svc.isOn.value) {
            // Currently ON -> turn off directly
            svc.toggle()
        } else {
            // Currently OFF -> start service to turn on
            startFlashlightService()
        }
        // Let onStartListening triggered by ACTION_STATE_CHANGED update the tile
    }

    // ── Tile state ────────────────────────────────────────────────────────────

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun startFlashlightService() {
        val i = Intent(this, FlashlightService::class.java).apply {
            action = FlashlightService.ACTION_TOGGLE
        }
        startForegroundService(i)
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
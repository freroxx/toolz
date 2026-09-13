/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.service

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Caffeinate Quick Settings tile — bulletproof edition.
 *
 * Fixes the "can't be disabled" class of bugs:
 * 1. Works from the lock screen via [unlockAndRun] (plain startService from a
 *    locked tile is dropped on many OEM builds, leaving the tile stuck ON).
 * 2. Never trusts the in-process static alone: falls back to persisted
 *    DataStore mode so the tile is correct even after process death.
 * 3. Delivers STOP with an explicit startService + tile refresh retry, and
 *    START with startForegroundService + graceful FGS-restriction fallback.
 * 4. Cancels its coroutine scope in onDestroy (was leaking one scope per
 *    TileService bind before).
 */
@AndroidEntryPoint
class CaffeinateTileService : TileService() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val tileScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val TAG = "CaffeinateTile"
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isLocked) {
                // Must unlock first — otherwise the start/stop intent is silently
                // dropped and the tile appears stuck.
                unlockAndRun { handleClick() }
            } else {
                handleClick()
            }
        } catch (e: Exception) {
            Log.w(TAG, "onClick unlockAndRun failed, trying direct", e)
            handleClick()
        }
    }

    private fun handleClick() {
        val running = CaffeinateService.isRunning
        if (running) {
            // STOP path: deliver to the running foreground service. Plain
            // startService is correct here (no FGS-start restriction applies to
            // commands sent to an already-running FGS).
            try {
                val intent = Intent(this, CaffeinateService::class.java).apply {
                    action = CaffeinateService.ACTION_STOP
                }
                startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deliver STOP to CaffeinateService", e)
            }
            // Optimistic feedback + verified refresh (service teardown is async).
            setTileStateOptimistic(active = false)
            tileScope.launch {
                delay(350)
                updateTile()
                delay(1200)
                updateTile()
                requestTileRefresh()
            }
        } else {
            // START path: needs a foreground-service start; QS interaction is a
            // valid FGS exemption, but guard against OEM/Doze edge cases.
            try {
                val intent = Intent(this, CaffeinateService::class.java).apply {
                    action = CaffeinateService.ACTION_START_INFINITE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "FGS start failed, retrying as regular start", e)
                try {
                    val fallback = Intent(this, CaffeinateService::class.java).apply {
                        action = CaffeinateService.ACTION_START_INFINITE
                    }
                    startService(fallback)
                } catch (_: Exception) {}
            }
            setTileStateOptimistic(active = true)
            tileScope.launch {
                delay(350)
                updateTile()
                delay(1200)
                updateTile()
                requestTileRefresh()
            }
        }
    }

    private fun setTileStateOptimistic(active: Boolean) {
        try {
            val tile = qsTile ?: return
            tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_caffeinate)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (active) "On" else "Off"
            }
            tile.updateTile()
        } catch (_: Exception) {}
    }

    private fun requestTileRefresh() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestListeningState(
                    this@CaffeinateTileService,
                    ComponentName(this@CaffeinateTileService, CaffeinateTileService::class.java)
                )
            }
        } catch (_: Exception) {}
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tileScope.launch {
            val (isActive, isAuto, elapsedMs) = resolveState()
            try {
                tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.icon = Icon.createWithResource(this@CaffeinateTileService, R.drawable.ic_tile_caffeinate)
                tile.label = "Caffeinate"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = when {
                        !isActive -> "Off"
                        isAuto -> "Auto"
                        elapsedMs > 0L -> "On • ${formatElapsedShort(elapsedMs)}"
                        else -> "On"
                    }
                }
                tile.updateTile()
            } catch (e: Exception) {
                Log.w(TAG, "tile.updateTile failed", e)
            }
        }
    }

    private data class TileState(val active: Boolean, val auto: Boolean, val elapsedMs: Long)

    private suspend fun resolveState(): TileState {
        // Fast path: same-process service state.
        if (CaffeinateService.isRunning) {
            val auto = try { CaffeinateService.isAutoRunningFlow.value } catch (_: Exception) { false }
            val elapsed = try { CaffeinateService.elapsedTimeFlow.value } catch (_: Exception) { 0L }
            return TileState(active = true, auto = auto, elapsedMs = elapsed.coerceAtLeast(0L))
        }
        // Slow path: process was recreated (or service died) — read persisted mode.
        return try {
            val mode = withTimeoutOrNull(1500L) {
                settingsRepository.caffeinateMode.first()
            } ?: "OFF"
            if (mode == "OFF") {
                TileState(active = false, auto = false, elapsedMs = 0L)
            } else {
                val start = withTimeoutOrNull(1500L) {
                    settingsRepository.caffeinateStartTime.first()
                } ?: 0L
                val elapsed = if (start > 0L) {
                    (System.currentTimeMillis() - start).coerceAtLeast(0L)
                } else 0L
                TileState(active = true, auto = mode == "AUTO", elapsedMs = elapsed)
            }
        } catch (_: Exception) {
            TileState(active = false, auto = false, elapsedMs = 0L)
        }
    }

    private fun formatElapsedShort(elapsedMs: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(elapsedMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMs) % 60
        return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
    }

    override fun onDestroy() {
        try { tileScope.cancel() } catch (_: Exception) {}
        super.onDestroy()
    }
}

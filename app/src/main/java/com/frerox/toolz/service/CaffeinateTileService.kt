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
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CaffeinateTileService : TileService() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
                action = CaffeinateService.ACTION_START_INFINITE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        // Give service a moment to register before updating tile
        serviceScope.launch {
            kotlinx.coroutines.delay(200)
            updateTile()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestListeningState(this@CaffeinateTileService, ComponentName(this@CaffeinateTileService, CaffeinateTileService::class.java))
            }
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        serviceScope.launch {
            val isActive = if (CaffeinateService.isRunning) {
                true
            } else {
                try {
                    val mode = settingsRepository.caffeinateMode.first()
                    mode != "OFF"
                } catch (_: Exception) {
                    false
                }
            }

            tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (isActive) "On" else "Off"
            }
            tile.updateTile()
        }
    }
}

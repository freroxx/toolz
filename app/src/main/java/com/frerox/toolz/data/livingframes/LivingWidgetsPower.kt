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

package com.frerox.toolz.data.livingframes

import android.content.Context
import android.os.PowerManager
import com.frerox.toolz.util.shizuku.ShizukuShellExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ShizukuState(
    val running: Boolean = false,
    val authorized: Boolean = false
)

/**
 * Battery-saver control for Living Widgets.
 * No third-party app can toggle system Battery Saver normally — with Shizuku
 * (ADB-level shell) we can write Settings.Global LOW_POWER_MODE directly,
 * the same key the system toggle flips.
 */
@Singleton
class LivingWidgetsPower @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuExecutor: ShizukuShellExecutor
) {
    fun isSaverOn(): Boolean = try {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
    } catch (_: Exception) { false }

    suspend fun shizukuState(): ShizukuState = withContext(Dispatchers.IO) {
        try {
            val running = shizukuExecutor.isShizukuAvailable() ||
                com.frerox.toolz.util.shizuku.ShizukuHelper.isRunning()
            val authorized = try {
                shizukuExecutor.isShizukuAvailable()
            } catch (_: Exception) {
                com.frerox.toolz.util.shizuku.ShizukuHelper.isAuthorized()
            }
            ShizukuState(running, authorized)
        } catch (_: Exception) {
            ShizukuState(false, false)
        }
    }

    suspend fun ensureShell(): Boolean = withContext(Dispatchers.IO) {
        try {
            shizukuExecutor.ensureService()
        } catch (_: Exception) { false }
    }

    /**
     * Turn Battery Saver off via privileged shell. Returns null on success,
     * human-readable error otherwise.
     */
    suspend fun disableSaver(): String? = withContext(Dispatchers.IO) {
        try {
            if (!shizukuExecutor.ensureService()) {
                return@withContext "Shizuku shell not ready — open the Shizuku app and start it first."
            }
            val res = shizukuExecutor.executeForResult("settings put global low_power 0")
            if (!res.isSuccess) {
                return@withContext "Command failed: ${res.stderr.ifBlank { "exit ${res.exitCode}" }}"
            }
            kotlinx.coroutines.delay(800L)
            val check = shizukuExecutor.executeForResult("settings get global low_power")
            val value = try {
                check.stdout.lineSequence().firstOrNull { it.trim().isNotEmpty() }?.trim()
            } catch (_: Exception) { null }
            if (value == "0" || !isSaverOn()) return@withContext null
            return@withContext "System re-enabled it — turn it off in Settings → Battery."
        } catch (e: Exception) {
            return@withContext (e.message ?: "Unknown error")
        }
    }
}

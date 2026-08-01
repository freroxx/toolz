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

package com.frerox.toolz.util

import android.content.Context
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

enum class OfflineState {
    ONLINE, OFFLINE
}

@Singleton
class OfflineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val connectivityObserver = NetworkConnectivityObserver(context)

    val offlineState: Flow<OfflineState> = combine(
        connectivityObserver.observe(),
        settingsRepository.offlineModeEnabled
    ) { networkStatus, manualOffline ->
        val isInternetAvailable = networkStatus == ConnectivityObserver.Status.Available
        if (manualOffline || !isInternetAvailable) {
            OfflineState.OFFLINE
        } else {
            OfflineState.ONLINE
        }
    }.distinctUntilChanged()

    suspend fun setOfflineMode(enabled: Boolean) {
        settingsRepository.setOfflineModeEnabled(enabled)
    }
}

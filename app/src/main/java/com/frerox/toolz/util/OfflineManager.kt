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

/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * App-wide spoof gate. Caches master flag, per-tool flags and structured
 * params as hot StateFlows so tool ViewModels can check sandbox state
 * synchronously (no runBlocking, no per-VM boilerplate):
 *
 *   if (gate.sandboxed(DevToolId.SENSORS)) { emitFixture(gate.params.value); return }
 */

package com.frerox.toolz.data.developer

import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpoofGate @Inject constructor(
    private val repository: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    val devMode: StateFlow<Boolean> = repository.developerModeEnabled
        .stateIn(scope, SharingStarted.Eagerly, false)

    val spoofs: StateFlow<Map<String, Boolean>> = repository.allSpoofFlags
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val params: StateFlow<SpoofParams> = repository.spoofParamsJson
        .map { SpoofParams.parse(it) }
        .stateIn(scope, SharingStarted.Eagerly, SpoofParams())

    /** Synchronous sandbox check for non-suspend entry points. */
    fun sandboxed(tool: DevToolId): Boolean =
        devMode.value && spoofs.value[tool.prefKey] == true

    /** Reactive sandbox check for collectors. */
    fun active(tool: DevToolId): Flow<Boolean> =
        combine(devMode, spoofs) { master, flags ->
            master && flags[tool.prefKey] == true
        }

    suspend fun isActive(tool: DevToolId): Boolean =
        repository.isSandboxNow(tool.prefKey)
}

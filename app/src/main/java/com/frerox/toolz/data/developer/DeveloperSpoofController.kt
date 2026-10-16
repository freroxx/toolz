/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Controlled-spoof gate. Single choke point: tool ViewModels ask this
 * controller instead of reading DataStore directly.
 *
 * Master OFF => every tool reports real data, even if individual
 * spoof flags are still persisted (so re-enabling restores selection).
 */

package com.frerox.toolz.data.developer

import com.frerox.toolz.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeveloperSpoofController @Inject constructor(
    private val settings: SettingsRepository,
) {
    /** Master switch. */
    val devModeEnabled: Flow<Boolean> = settings.developerModeEnabled

    /** True only when master ON *and* this tool's spoof ON. */
    fun isSandbox(tool: DevToolId): Flow<Boolean> =
        combine(settings.developerModeEnabled, settings.spoofFlag(tool.prefKey)) { master, flag ->
            master && flag
        }

    /** Number of spoof flags currently ON (regardless of master). */
    val spoofOnCount: Flow<Int> = settings.allSpoofFlags.map { flags ->
        flags.values.count { it }
    }

    /** Number of *effective* sandboxes (master ON + flag ON). */
    val activeSandboxCount: Flow<Int> =
        combine(settings.developerModeEnabled, settings.allSpoofFlags) { master, flags ->
            if (!master) 0 else flags.values.count { it }
        }

    /**
     * Guard for repository entry points. Returns true when the caller must
     * use local fixtures and skip network/sensors/jobs.
     * Suspend version for one-shot checks inside suspend functions.
     */
    suspend fun shouldSpoof(tool: DevToolId): Boolean =
        settings.isSandboxNow(tool.prefKey)

    /** Throw if sandboxed — for code paths that must never run with fakes. */
    suspend fun blockOrThrow(tool: DevToolId) {
        if (shouldSpoof(tool)) {
            android.util.Log.w(
                "DevSpoof",
                "Blocked real ${tool.name} call while sandbox active (policy=${tool.networkPolicy})",
            )
            throw SpoofSandboxException(tool)
        }
    }
}

class SpoofSandboxException(val tool: DevToolId) :
    IllegalStateException("Real ${tool.title} call blocked: spoof sandbox active (local-only)")

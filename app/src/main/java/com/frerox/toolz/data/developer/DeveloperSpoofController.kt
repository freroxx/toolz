/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Controlled-spoof facade. Tool ViewModels and repositories ask THIS class
 * instead of reading DataStore directly. All state is delegated to
 * [SpoofGate] — the single hot cache of the master flag, per-tool flags and
 * structured params.
 *
 * Master OFF => every tool reports real data, even if individual
 * spoof flags are still persisted (so re-enabling restores selection).
 */

package com.frerox.toolz.data.developer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeveloperSpoofController @Inject constructor(
    private val gate: SpoofGate,
) {
    /** Master switch (hot). */
    val devModeEnabled: Flow<Boolean> get() = gate.devMode

    /** True only when master ON *and* this tool's spoof ON. */
    fun isSandbox(tool: DevToolId): Flow<Boolean> =
        combineGate(tool)

    private fun combineGate(tool: DevToolId): Flow<Boolean> = gate.active(tool)

    /** Number of spoof flags currently ON (regardless of master). */
    val spoofOnCount: Flow<Int> = gate.spoofs.map { flags -> flags.values.count { it } }

    /** Number of *effective* sandboxes (master ON + flag ON), fully reactive. */
    val activeSandboxCount: Flow<Int> = combine(gate.devMode, gate.spoofs) { master, flags ->
        if (!master) 0 else flags.values.count { it }
    }

    /**
     * Guard for repository entry points. Returns true when the caller must
     * use local fixtures and skip network/sensors/jobs.
     */
    fun shouldSpoof(tool: DevToolId): Boolean = gate.sandboxed(tool)

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

/** Maps {"spoof_battery": true, …} + master onto {"BATTERY" -> true, …} per tool. */
fun SpoofGrid(master: Boolean, flags: Map<String, Boolean>): Map<DevToolId, Boolean> {
    if (!master) return DevToolId.entries.associateWith { false }
    return DevToolId.entries.associateWith { flags[it.prefKey] == true }
}

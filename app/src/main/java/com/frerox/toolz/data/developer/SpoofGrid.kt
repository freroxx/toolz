/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Pure mapping from persisted sandbox flags to effective per-tool state.
 */

package com.frerox.toolz.data.developer

/**
 * Maps `{"spoof_battery": true, ...}` plus the master switch onto an effective
 * per-tool state. The master switch wins: when it is off every tool reports
 * live data, even if individual selections are still persisted.
 */
fun SpoofGrid(master: Boolean, flags: Map<String, Boolean>): Map<DevToolId, Boolean> {
    if (!master) return DevToolId.entries.associateWith { false }
    return DevToolId.entries.associateWith { flags[it.prefKey] == true }
}

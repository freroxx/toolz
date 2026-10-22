/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Composable-side access to the spoof sandbox, for screens that need to
 * relax UI affordances (permission prompts, onboarding, setup nags) while
 * a tool's sandbox is active. ViewModels are preferred for data — this is
 * only for tiny screen-level decisions.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.SpoofGate
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SpoofGateEntryPoint {
    fun spoofGate(): SpoofGate
}

/**
 * DEV-SANDBOX: true while [tool]'s sandbox is active (master ON + tool flag ON).
 * Defaults to false for the first frame, then updates reactively — screens use
 * it to skip permission placeholders and setup flows the sandbox renders moot.
 */
@Composable
fun rememberSandboxActive(tool: DevToolId): Boolean {
    val context = LocalContext.current
    val gate = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SpoofGateEntryPoint::class.java,
        ).spoofGate()
    }
    return gate.active(tool)
        .collectAsStateWithLifecycle(initialValue = false)
        .value
}

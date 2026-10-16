/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Tiny shared ViewModel that answers one question: "is this tool currently
 * sandboxed?" Used by [SpoofWatermarkHost] and the global route overlay so
 * individual tool screens never touch DataStore directly.
 */

package com.frerox.toolz.ui.screens.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.DeveloperSpoofController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SpoofStateViewModel @Inject constructor(
    private val spoofController: DeveloperSpoofController,
) : ViewModel() {
    private val cache = mutableMapOf<DevToolId, StateFlow<Boolean>>()

    fun isSandbox(tool: DevToolId): StateFlow<Boolean> =
        cache.getOrPut(tool) {
            spoofController.isSandbox(tool)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
        }
}

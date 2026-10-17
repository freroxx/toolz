/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Dashboard quick-fix for sandboxed tools: a small red star on any spoofed
 * tool's card, plus a long-press dialog that explains the data is fake and
 * offers to disable the sandbox or jump to the developer console.
 * Local-only — the write path is the same pref the console toggles.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.SpoofGate
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * DEV-SPOOF: live snapshot of which tools are actively sandboxed
 * (master ON + per-tool flag ON) and the disable action for the
 * dashboard long-press dialog.
 */
@HiltViewModel
class SpoofQuickFixViewModel @Inject constructor(
    private val gate: SpoofGate,
    private val repository: SettingsRepository,
) : ViewModel() {

    val activeSandboxes: StateFlow<Set<DevToolId>> =
        combine(gate.devMode, gate.spoofs) { master, flags ->
            if (!master) emptySet()
            else DevToolId.entries.filterTo(mutableSetOf()) { flags[it.prefKey] == true }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Same pref write the console performs — nothing extra, no telemetry. */
    fun disableSandbox(tool: DevToolId) {
        viewModelScope.launch {
            repository.setSpoofFlag(tool.prefKey, false)
            android.util.Log.w("DevSpoof", "${tool.title} sandbox disabled from dashboard quick-fix")
        }
    }
}

/** Small red star marking a spoofed tool's card (top-right on grid, right on list). */
@Composable
fun SpoofSandboxStar(modifier: Modifier = Modifier) {
    Icon(
        Icons.Rounded.Star,
        contentDescription = "Spoofed: fake local data",
        tint = MaterialTheme.colorScheme.error,
        modifier = modifier
            .size(18.dp)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                androidx.compose.foundation.shape.CircleShape,
            ),
    )
}

/** DEV-SPOOF: "this tool's data is fake" — disable the sandbox or open the console. */
@Composable
fun SpoofQuickFixDialog(
    tool: DevToolId,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.Science,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("${tool.title} is fake and spoofed") },
        text = {
            Text(
                "This tool's info is fake and spoofed for testing — nothing was " +
                    "saved, deleted, or sent anywhere. Press here to disable it and " +
                    "real data returns instantly. Full sliders live in developer options.",
            )
        },
        confirmButton = {
            TextButton(onClick = { onDisable(); onDismiss() }) {
                Text("Disable spoof", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { onOpenDeveloperOptions(); onDismiss() }) {
                Text("Developer options")
            }
        },
    )
}

/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Dashboard quick-fix for sandboxed tools: a small star on any sandboxed
 * tool's card, plus a long-press dialog that explains the data is a local
 * fixture and offers to turn off the sandbox or open the developer console.
 * Local-only — the write path is the same pref the console toggles.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import com.frerox.toolz.data.developer.DevLogHub
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.SpoofGate
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Live snapshot of which tools are actively sandboxed (master ON + per-tool
 * flag ON) and the disable action for the dashboard long-press dialog.
 */
@HiltViewModel
class SpoofQuickFixViewModel @Inject constructor(
    private val gate: SpoofGate,
    private val repository: SettingsRepository,
) : ViewModel() {

    data class QuickFixState(
        val busy: Boolean = false,
        val error: String? = null,
    )

    val activeSandboxes: StateFlow<Set<DevToolId>> =
        combine(gate.devMode, gate.spoofs) { master, flags ->
            if (!master) emptySet()
            else DevToolId.entries.filterTo(mutableSetOf()) { flags[it.prefKey] == true }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _quickFixState = MutableStateFlow(QuickFixState())
    val quickFixState: StateFlow<QuickFixState> = _quickFixState.asStateFlow()

    fun clearQuickFixState() {
        _quickFixState.value = QuickFixState()
    }

    /** Same pref write the console performs — nothing extra, no analytics. */
    fun disableSandbox(tool: DevToolId) {
        if (_quickFixState.value.busy) return
        _quickFixState.value = QuickFixState(busy = true)
        viewModelScope.launch {
            try {
                repository.setSpoofFlag(tool.prefKey, false)
                _quickFixState.value = QuickFixState()
                DevLogHub.i(DevLogHub.TAG, "${tool.title} sandbox disabled from dashboard quick-fix")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _quickFixState.value = QuickFixState(
                    error = "Could not turn off ${tool.title} · try again",
                )
                DevLogHub.e(DevLogHub.TAG, "Could not disable ${tool.title} from quick-fix", e)
            }
        }
    }
}

/** Small accent star marking a sandboxed tool's card (top-right on grid, right on list). */
@Composable
fun SpoofSandboxStar(modifier: Modifier = Modifier) {
    Icon(
        Icons.Rounded.Star,
        contentDescription = "Sandbox active: local fixture data",
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = modifier
            .size(18.dp)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                androidx.compose.foundation.shape.CircleShape,
            ),
    )
}

/** "This tool's data is a local fixture" — exit the sandbox or open the console. */
@Composable
fun SpoofQuickFixDialog(
    tool: DevToolId,
    busy: Boolean,
    error: String?,
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
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("Sandbox data is active for ${tool.title}") },
        text = {
            Column {
                Text(
                    "This screen is using a local fixture from Developer Mode. " +
                        "Nothing is saved, deleted or sent anywhere. Turn off this " +
                        "sandbox to return to live data immediately.",
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDisable,
                enabled = !busy,
            ) {
                Text(
                    text = if (busy) "Turning off…" else "Turn off sandbox",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onOpenDeveloperOptions(); onDismiss() },
                enabled = !busy,
            ) {
                Text("Open console")
            }
        },
    )
}

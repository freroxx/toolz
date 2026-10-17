/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Generic per-tool configurator rendered from [DevToolId.fieldSpecs].
 * Every control debounces its write (300ms) so dragging a slider doesn't
 * hammer DataStore — and each fixture stays RAM-only per the sandbox
 * contract. When spoof_whisper is configured here, the offline thread
 * preview renders as message bubbles.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.FakeWhisperSandbox
import com.frerox.toolz.data.developer.SpoofParams
import com.frerox.toolz.ui.screens.developer.fieldSpecs
import com.frerox.toolz.ui.screens.developer.icon
import com.frerox.toolz.ui.screens.developer.openRoute
import com.frerox.toolz.ui.screens.developer.spoofFixtureSummary
import kotlinx.coroutines.delay

/**
 * Debounce helper: shows `pending` while set, then commits it once after
 * 300ms of no further changes. `current` is the latest persisted value;
 * any new value from the store resets the pending local override.
 */
@Composable
private fun rememberDebounced(
    current: String,
    onCommit: (String) -> Unit,
): Pair<String, (String) -> Unit> {
    var pending by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pending, current) {
        val value = pending ?: return@LaunchedEffect
        delay(300)
        onCommit(value)
        pending = null
    }
    return (pending ?: current) to { pending = it }
}

@Composable
private fun DebouncedTextField(
    label: String,
    current: String,
    maxLength: Int,
    enabled: Boolean,
    onCommit: (String) -> Unit,
) {
    val (shown, setSh) = rememberDebounced(current, onCommit)
    OutlinedTextField(
        value = shown.take(maxLength),
        onValueChange = setSh,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpoofConfiguratorSheet(
    tool: DevToolId?,
    params: SpoofParams,
    devOn: Boolean,
    sandboxActive: Boolean,
    onUpdateSpoofParams: ((SpoofParams) -> SpoofParams) -> Unit,
    onSetSpoof: (DevToolId, Boolean) -> Unit,
    onOpenRoute: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (tool == null) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (sandboxActive) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            tool.icon,
                            contentDescription = null,
                            tint = if (sandboxActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tool.title, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(
                        tool.networkPolicy.name + " • local only",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("SANDBOX", fontWeight = FontWeight.Black, fontSize = 11.sp)
                    Switch(
                        checked = sandboxActive,
                        enabled = devOn,
                        onCheckedChange = { onSetSpoof(tool, it) },
                    )
                }
            }

            if (sandboxActive) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        "FIXTURE → ${spoofFixtureSummary(tool, params)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val specs = tool.fieldSpecs
            if (specs.isEmpty()) {
                if (tool == DevToolId.WHISPER) {
                    WhisperPreviewCard()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "The private sandbox uses this fixed thread — no tunable values. Toggle the sandbox and open Whisper to see it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "This tool has a fixed smart fixture — no tunable values. Toggle the sandbox and open the tool to see it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                specs.forEach { spec ->
                    when (spec) {
                        is com.frerox.toolz.ui.screens.developer.SpoofFieldSpec.FixtureSlider ->
                            DebouncedSlider(
                                label = spec.label,
                                current = spec.get(params),
                                min = spec.min,
                                max = spec.max,
                                format = spec.format,
                                enabled = devOn,
                                onCommit = { v -> onUpdateSpoofParams { spec.set(it, v) } },
                            )
                        is com.frerox.toolz.ui.screens.developer.SpoofFieldSpec.FixtureSwitch ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(spec.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Switch(
                                    checked = spec.get(params),
                                    enabled = devOn,
                                    onCheckedChange = { v -> onUpdateSpoofParams { spec.set(it, v) } },
                                )
                            }
                        is com.frerox.toolz.ui.screens.developer.SpoofFieldSpec.FixtureText ->
                            DebouncedTextField(
                                label = spec.label,
                                current = spec.get(params),
                                maxLength = spec.maxLength,
                                enabled = devOn,
                                onCommit = { v ->
                                    if (v.isNotBlank()) onUpdateSpoofParams { spec.set(it, v) }
                                },
                            )
                    }
                }
            }

            tool.openRoute?.let { route ->
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilledTonalButton(onClick = { onDismiss(); onOpenRoute(route) }) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("OPEN TOOL SCREEN", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Offline Whisper thread preview as bubbles — shown in the Whisper configurator. */
@Composable
fun WhisperPreviewCard(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Text(
            FakeWhisperSandbox.BANNER,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(10.dp))
        FakeWhisperSandbox.thread.forEach { msg ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = if (msg.fromMe) Arrangement.End else Arrangement.Start,
            ) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (msg.fromMe) 16.dp else 4.dp,
                        bottomEnd = if (msg.fromMe) 4.dp else 16.dp,
                    ),
                    color = if (msg.fromMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (msg.fromMe) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                ) {
                    Text(
                        msg.text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(max = 260.dp),
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Send is disabled here — fixtures only. No Supabase, no realtime.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

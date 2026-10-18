/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Generic per-tool configurator rendered from [DevToolId.fieldSpecs].
 * Every control debounces its write (300ms) so dragging a slider doesn't
 * hammer DataStore — and each fixture stays RAM-only per the sandbox
 * contract. When spoof_whisper is configured here, the offline thread
 * preview renders as message bubbles.
 *
 * Revamp changes:
 *  - Active state uses an animated green dot badge on the icon (not red/error
 *    — sandbox-on is an intentional state, not an error)
 *  - The "FIXTURE →" active banner uses primary tint instead of errorContainer
 *  - Sliders now show min/max labels below the track
 *  - Switches show a subtitle line (spec.subtitle) if provided
 *  - Text fields show a live char-count "12/32" label next to the field title
 *  - "OPEN TOOL SCREEN" button renders regardless of sandbox state (was gated)
 *    and uses Icons.Rounded.ArrowOutward (more modern than OpenInNew)
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.FakeWhisperSandbox
import com.frerox.toolz.data.developer.SpoofParams
import com.frerox.toolz.ui.screens.developer.SpoofFieldSpec
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
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "${shown.length}/$maxLength",
                fontSize = 11.sp,
                color = if (shown.length >= maxLength) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = shown.take(maxLength),
            onValueChange = setSh,
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Clean green dot beacon — shown on the tool icon when the sandbox is active. */
@Composable
private fun ActiveDot() {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(Color(0xFF66BB6A))
            .border(1.5.dp, Color.White, CircleShape),
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
            // ── Header ────────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon with green-dot badge when active
                Box {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.size(46.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                tool.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (sandboxActive) {
                        ActiveDot()
                            .let { _ ->
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 2.dp, y = (-2).dp),
                                ) { ActiveDot() }
                            }
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
                    Text(
                        if (sandboxActive) "ACTIVE" else "SANDBOX",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = if (sandboxActive) Color(0xFF66BB6A) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = sandboxActive,
                        enabled = devOn,
                        onCheckedChange = { onSetSpoof(tool, it) },
                    )
                }
            }

            // Active fixture banner — primary tint (not error; sandbox-on is intentional)
            if (sandboxActive) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f),
                            RoundedCornerShape(14.dp),
                        )
                        .padding(12.dp),
                ) {
                    Text(
                        "FIXTURE → ${spoofFixtureSummary(tool, params)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Field specs ───────────────────────────────────────────────────
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
                        is SpoofFieldSpec.FixtureSlider ->
                            DebouncedSlider(
                                label = spec.label,
                                current = spec.get(params),
                                min = spec.min,
                                max = spec.max,
                                format = spec.format,
                                enabled = devOn,
                                onCommit = { v -> onUpdateSpoofParams { spec.set(it, v) } },
                                minLabel = spec.format(spec.min),
                                maxLabel = spec.format(spec.max),
                            )
                        is SpoofFieldSpec.FixtureSwitch ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(spec.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        if (spec.subtitle.isNotBlank()) {
                                            Text(
                                                spec.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = spec.get(params),
                                        enabled = devOn,
                                        onCheckedChange = { v -> onUpdateSpoofParams { spec.set(it, v) } },
                                    )
                                }
                            }
                        is SpoofFieldSpec.FixtureText ->
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

            // ── Open tool screen — shown regardless of sandbox state ──────────
            tool.openRoute?.let { route ->
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilledTonalButton(onClick = { onDismiss(); onOpenRoute(route) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
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

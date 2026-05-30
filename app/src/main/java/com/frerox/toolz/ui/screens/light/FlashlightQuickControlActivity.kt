package com.frerox.toolz.ui.screens.light

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frerox.toolz.MainActivity
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.ToolzTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToLong

// ─────────────────────────────────────────────────────────────────────────────
// FlashlightQuickControlActivity
//
//  Launched from the QS tile long-press. Hosts the compact bottom sheet
//  with a large toggle button + all settings from the main flashlight screen.
// ─────────────────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class FlashlightQuickControlActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ToolzTheme {
                val vm: FlashlightViewModel = viewModel()
                val state by vm.uiState.collectAsState()

                ModalBottomSheet(
                    onDismissRequest = { finish() },
                    sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    shape            = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    containerColor   = MaterialTheme.colorScheme.surface,
                    dragHandle       = {
                        Box(
                            Modifier
                                .padding(top = 12.dp, bottom = 6.dp)
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        )
                    },
                ) {
                    QuickSheetContent(
                        state           = state,
                        onToggle        = vm::toggleFlashlight,
                        onSetMode       = vm::setMode,
                        onSetBrightness = vm::setBrightness,
                        onSetStrobe     = vm::setStrobeInterval,
                        onSetTimer      = vm::setTimer,
                        onSetDiscoRange = vm::setDiscoRange,
                        onOpenFull      = {
                            startActivity(
                                Intent(this@FlashlightQuickControlActivity, MainActivity::class.java).apply {
                                    putExtra("navigate_to", "flashlight")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                            )
                            finish()
                        },
                        onDismiss       = { finish() },
                    )
                }
            }
        }
    }
}

private val BeamYellow = Color(0xFFFFEE58)
private val BeamAmber  = Color(0xFFFBC02D)

@Composable
private fun QuickSheetContent(
    state: FlashlightState,
    onToggle: () -> Unit,
    onSetMode: (FlashlightMode) -> Unit,
    onSetBrightness: (Float) -> Unit,
    onSetStrobe: (Long) -> Unit,
    onSetTimer: (Int) -> Unit,
    onSetDiscoRange: (Long, Long) -> Unit,
    onOpenFull: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── Header: icon + status ─────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text          = "FLASHLIGHT",
                    style         = MaterialTheme.typography.titleLarge,
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                )
                AnimatedContent(
                    targetState = state.isOn to state.mode,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    label = "subtitle",
                ) { (on, mode) ->
                    Text(
                        text  = if (on) when (mode) {
                            FlashlightMode.STEADY -> "Steady beam active"
                            FlashlightMode.STROBE -> "Strobe mode active"
                            FlashlightMode.SOS    -> "SOS signal active"
                            FlashlightMode.DISCO  -> "Disco mode active"
                        } else "Ready to shine",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) BeamAmber
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            // Full-screen shortcut
            FilledTonalIconButton(
                onClick  = onOpenFull,
                modifier = Modifier.size(44.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Icon(Icons.Rounded.OpenInFull, "Full screen", modifier = Modifier.size(18.dp))
            }
        }

        // ── Main Toggle & Mode Selector ───────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Large toggle button
            ToolzExpressiveButton(
                onClick  = onToggle,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                shape    = RoundedCornerShape(20.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (state.isOn) BeamYellow
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor   = if (state.isOn) Color.Black
                    else MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(
                    if (state.isOn) Icons.Rounded.FlashlightOff else Icons.Rounded.FlashlightOn,
                    null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (state.isOn) "STOP" else "START",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        // ── Mode selector ─────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlLabel(icon = Icons.Rounded.SettingsSuggest, label = "SIGNAL MODE")
            ToolzConnectedButtonGroup(
                selectedIndex = state.mode.ordinal,
                options = FlashlightMode.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                onOptionSelected = { index -> onSetMode(FlashlightMode.entries[index]) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Contextual Controls Panel ─────────────────────────────────────────
        ExpressiveCard(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            elevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Brightness Slider
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlLabel(icon = Icons.Rounded.Tune, label = "INTENSITY")
                        Text(
                            if (state.isBrightnessSupported) "${(state.brightness * 100).toInt()}%" else "HW LIMIT",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = if (state.isBrightnessSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    ExpressiveSlider(
                        value = state.brightness,
                        onValueChange = onSetBrightness,
                        enabled = state.isBrightnessSupported,
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Strobe Speed
                AnimatedVisibility(visible = state.mode == FlashlightMode.STROBE) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val freqHz = (1000f / (2f * state.strobeIntervalMs)).let {
                            if (it >= 10f) "${it.toInt()} Hz" else "%.1f Hz".format(it)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            ControlLabel(icon = Icons.Rounded.FlashOn, label = "STROBE SPEED")
                            Text(freqHz, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                        ExpressiveSlider(
                            value = 1f - ((state.strobeIntervalMs - 40f) / (500f - 40f)),
                            onValueChange = { v ->
                                onSetStrobe((500f - v * (500f - 40f)).roundToLong().coerceIn(40L, 500L))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.tertiary, activeTrackColor = MaterialTheme.colorScheme.tertiary)
                        )
                    }
                }

                // Disco Range
                AnimatedVisibility(visible = state.mode == FlashlightMode.DISCO) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            ControlLabel(icon = Icons.Rounded.MusicNote, label = "PACE RANGE")
                            Text("${state.discoIntervalRange.first}ms - ${state.discoIntervalRange.second}ms", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                        RangeSlider(
                            value = state.discoIntervalRange.first.toFloat()..state.discoIntervalRange.second.toFloat(),
                            onValueChange = { range -> onSetDiscoRange(range.start.toLong(), range.endInclusive.toLong()) },
                            valueRange = 30f..1000f,
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.tertiary, activeTrackColor = MaterialTheme.colorScheme.tertiary)
                        )
                    }
                }

                // Timer
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ControlLabel(icon = Icons.Rounded.Timer, label = "AUTO-OFF")
                    val timerOptions = listOf(0, 1, 5, 10, 30)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(timerOptions) { mins ->
                            ExpressiveFilterChip(
                                selected = state.timerMinutes == mins,
                                onClick = { onSetTimer(mins) },
                                label = { Text(if (mins == 0) "OFF" else "${mins}m") }
                            )
                        }
                    }
                }
            }
        }

        // ── Actions ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("DISMISS", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun ControlLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        Text(
            label,
            style         = MaterialTheme.typography.labelSmall,
            fontWeight    = FontWeight.Black,
            color         = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
        )
    }
}

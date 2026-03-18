package com.frerox.toolz.ui.screens.light

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlin.math.roundToLong

// ─────────────────────────────────────────────────────────────
//  Design tokens
// ─────────────────────────────────────────────────────────────

private val BeamYellow = Color(0xFFFFEE58)
private val BeamAmber  = Color(0xFFFBC02D)
private val BeamWhite  = Color(0xFFFFFDE7)

// ─────────────────────────────────────────────────────────────
//  Main screen
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun FlashlightScreen(
    viewModel: FlashlightViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "FLASHLIGHT",
                        fontWeight    = FontWeight.Black,
                        letterSpacing = 3.sp,
                        style         = MaterialTheme.typography.labelMedium,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick  = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (cameraPermission.status.isGranted) {
            FlashlightContent(
                state    = state,
                onToggle = viewModel::toggleFlashlight,
                onSetMode       = viewModel::setMode,
                onSetBrightness = viewModel::setBrightness,
                onSetStrobe     = viewModel::setStrobeInterval,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            )
        } else {
            PermissionContent(
                onRequest = { cameraPermission.launchPermissionRequest() },
                modifier  = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Flashlight content
// ─────────────────────────────────────────────────────────────

@Composable
private fun FlashlightContent(
    state: FlashlightState,
    onToggle: () -> Unit,
    onSetMode: (FlashlightMode) -> Unit,
    onSetBrightness: (Float) -> Unit,
    onSetStrobe: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ── Glow animations — always declared unconditionally ──────────────────
    // The glow should animate only when the torch is on, but we MUST call
    // animateFloat unconditionally to obey Compose composition rules.
    // We derive the effective alpha from the raw animated value afterwards.

    val glowInf = rememberInfiniteTransition(label = "glow")

    // Outer halo pulse
    val outerGlowRaw by glowInf.animateFloat(
        initialValue  = 0.7f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "outerGlow",
    )
    // Mid ring pulse (offset phase)
    val midGlowRaw by glowInf.animateFloat(
        initialValue  = 1.0f,
        targetValue   = 0.7f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "midGlow",
    )

    // Effective values — zero when torch is off (no rendering cost)
    val effectiveBrightness = if (state.isOn) state.brightness else 0f
    val outerGlow           = if (state.isOn) outerGlowRaw else 0f
    val midGlow             = if (state.isOn) midGlowRaw   else 0f

    // SOS dot blink animation
    val sosInf = rememberInfiniteTransition(label = "sos")
    val sosDotAlpha by sosInf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(400), RepeatMode.Reverse),
        label = "sosDot",
    )

    // Button scale feedback
    val buttonScale by animateFloatAsState(
        if (state.isOn) 1.05f else 1.0f,
        spring(Spring.DampingRatioMediumBouncy),
        label = "buttonScale",
    )

    Column(
        modifier              = modifier,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.5f))

        // ── Glow rings + main button ───────────────────────────────────────
        Box(
            modifier         = Modifier.size(320.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Outermost diffuse halo
            GlowRing(
                size   = 310.dp,
                scale  = outerGlow,
                alpha  = 0.18f * effectiveBrightness,
                color  = BeamYellow,
                solid  = false,
            )
            // Mid ring
            GlowRing(
                size   = 240.dp,
                scale  = midGlow,
                alpha  = 0.28f * effectiveBrightness,
                color  = BeamYellow,
                solid  = false,
            )
            // Inner solid ring
            GlowRing(
                size   = 195.dp,
                scale  = 1f,
                alpha  = 0.55f * effectiveBrightness,
                color  = BeamAmber,
                solid  = true,
            )

            // Main toggle button
            Surface(
                modifier = Modifier
                    .size(170.dp)
                    .scale(buttonScale)
                    .shadow(
                        elevation  = if (state.isOn) (28 * effectiveBrightness).dp else 4.dp,
                        shape      = CircleShape,
                        spotColor  = if (state.isOn) BeamAmber else Color.Transparent,
                    )
                    .bouncyClick { onToggle() },
                shape  = CircleShape,
                color  = if (state.isOn) BeamYellow else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    width = if (state.isOn) 3.dp else 1.dp,
                    color = if (state.isOn) BeamWhite.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector        = if (state.isOn) Icons.Rounded.FlashlightOn
                        else Icons.Rounded.FlashlightOff,
                        contentDescription = if (state.isOn) "Turn off" else "Turn on",
                        modifier           = Modifier.size(68.dp),
                        tint               = if (state.isOn) Color.Black
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Status chip ────────────────────────────────────────────────────
        AnimatedContent(
            targetState = state.isOn,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            label = "statusChip",
        ) { isOn ->
            Surface(
                color  = if (isOn) BeamYellow.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape  = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (isOn) BeamAmber.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                ),
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    // Status dot — blinks in SOS mode
                    val dotAlpha = when {
                        !isOn                          -> 0.4f
                        state.mode == FlashlightMode.SOS -> sosDotAlpha
                        else                           -> 1f
                    }
                    Box(
                        Modifier
                            .size(7.dp)
                            .alpha(dotAlpha)
                            .background(
                                if (isOn) BeamAmber else MaterialTheme.colorScheme.outline,
                                CircleShape,
                            )
                    )
                    Text(
                        text = when {
                            !isOn                            -> "STANDBY"
                            state.mode == FlashlightMode.SOS    -> "SOS SIGNAL"
                            state.mode == FlashlightMode.STROBE -> "STROBE"
                            else                             -> "BEAM ACTIVE"
                        },
                        style         = MaterialTheme.typography.labelSmall,
                        fontWeight    = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        color         = if (isOn) BeamAmber
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Controls panel ─────────────────────────────────────────────────
        ControlsPanel(
            state           = state,
            onSetMode       = onSetMode,
            onSetBrightness = onSetBrightness,
            onSetStrobe     = onSetStrobe,
            modifier        = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Controls panel — brightness, mode, strobe speed
// ─────────────────────────────────────────────────────────────

@Composable
private fun ControlsPanel(
    state: FlashlightState,
    onSetMode: (FlashlightMode) -> Unit,
    onSetBrightness: (Float) -> Unit,
    onSetStrobe: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(28.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier            = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // ── Mode selector ─────────────────────────────────────────────
            ControlLabel(
                icon  = Icons.Rounded.SettingsSuggest,
                label = "SIGNAL MODE",
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                FlashlightMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick  = { onSetMode(mode) },
                        shape    = SegmentedButtonDefaults.itemShape(i, FlashlightMode.entries.size),
                        colors   = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor   = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            mode.name,
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }

            // ── Brightness slider ─────────────────────────────────────────
            AnimatedVisibility(visible = state.mode == FlashlightMode.STEADY) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        ControlLabel(
                            icon  = Icons.Rounded.Tune,
                            label = "INTENSITY",
                        )
                        if (!state.isBrightnessSupported) {
                            Text(
                                "HW LIMITED",
                                style      = MaterialTheme.typography.labelSmall,
                                color      = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            Text(
                                "${(state.brightness * 100).roundToInt()}%",
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color      = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Slider(
                        value          = state.brightness,
                        onValueChange  = onSetBrightness,
                        enabled        = state.isBrightnessSupported,
                        valueRange     = 0.1f..1.0f,
                        modifier       = Modifier.fillMaxWidth(),
                        colors         = SliderDefaults.colors(
                            thumbColor         = MaterialTheme.colorScheme.primary,
                            activeTrackColor   = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            disabledThumbColor          = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
                            disabledActiveTrackColor    = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f),
                            disabledInactiveTrackColor  = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.1f),
                        ),
                    )
                }
            }

            // ── Strobe speed slider ───────────────────────────────────────
            AnimatedVisibility(visible = state.mode == FlashlightMode.STROBE) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Slider value = 1 / interval, so higher = faster.
                    // Map: slider 0..1 → interval 500ms..40ms (inverted).
                    val sliderValue = remember(state.strobeIntervalMs) {
                        1f - ((state.strobeIntervalMs - 40f) / (500f - 40f))
                    }
                    val freqHz = remember(state.strobeIntervalMs) {
                        (1000f / (2f * state.strobeIntervalMs)).let {
                            if (it >= 10f) "${it.roundToInt()} Hz" else "%.1f Hz".format(it)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        ControlLabel(
                            icon  = Icons.Rounded.FlashOn,
                            label = "STROBE SPEED",
                        )
                        Text(
                            freqHz,
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color      = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value         = sliderValue,
                        onValueChange = { v ->
                            // Invert back to milliseconds
                            val ms = (500f - v * (500f - 40f)).roundToLong().coerceIn(40L, 500L)
                            onSetStrobe(ms)
                        },
                        valueRange    = 0f..1f,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = SliderDefaults.colors(
                            thumbColor         = MaterialTheme.colorScheme.tertiary,
                            activeTrackColor   = MaterialTheme.colorScheme.tertiary,
                            inactiveTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                        ),
                    )
                    // Speed hint labels
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Slow", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                        Text("Fast", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                    }
                }
            }

            // ── SOS info row ──────────────────────────────────────────────
            AnimatedVisibility(visible = state.mode == FlashlightMode.SOS) {
                Surface(
                    color  = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f),
                    shape  = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Warning, null,
                            modifier = Modifier.size(17.dp),
                            tint     = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "International SOS · · ·  — — —  · · ·",
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Glow ring helper
// ─────────────────────────────────────────────────────────────

@Composable
private fun GlowRing(
    size: Dp,
    scale: Float,
    alpha: Float,
    color: Color,
    solid: Boolean,
) {
    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .alpha(alpha)
            .background(
                if (solid) Brush.radialGradient(
                    listOf(color.copy(alpha = 0.5f), color.copy(alpha = 0.0f))
                ) else Brush.radialGradient(
                    listOf(Color.Transparent, color.copy(alpha = 0.6f), Color.Transparent),
                    radius = size.value * 1.5f,
                ),
                CircleShape,
            )
    )
}

// ─────────────────────────────────────────────────────────────
//  Control label helper
// ─────────────────────────────────────────────────────────────

@Composable
private fun ControlLabel(icon: ImageVector, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon, null,
            tint     = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            style         = MaterialTheme.typography.labelSmall,
            fontWeight    = FontWeight.Black,
            color         = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Permission request screen
// ─────────────────────────────────────────────────────────────

@Composable
private fun PermissionContent(
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(40.dp),
        ) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape    = RoundedCornerShape(36.dp),
                color    = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                border   = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.FlashlightOn, null,
                        modifier = Modifier.size(56.dp),
                        tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Camera Permission",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Camera access is required to control the device flashlight. Your photos and videos are not accessed.",
                textAlign = TextAlign.Center,
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(36.dp))
            Button(
                onClick  = onRequest,
                shape    = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(58.dp),
            ) {
                Text("Grant Permission", fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Utility
// ─────────────────────────────────────────────────────────────

private fun Float.roundToInt(): Int = (this + 0.5f).toInt()
private fun Float.roundToLong(): Long = (this + 0.5f).toLong()
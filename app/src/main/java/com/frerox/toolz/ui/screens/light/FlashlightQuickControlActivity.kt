package com.frerox.toolz.ui.screens.light

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.ToolzTheme
import dagger.hilt.android.AndroidEntryPoint

// ─────────────────────────────────────────────────────────────────────────────
// FlashlightQuickControlActivity
//
//  Launched from the QS tile long-press. Hosts the compact bottom sheet
//  with a large toggle button + mode / brightness quick controls.
// ─────────────────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class FlashlightQuickControlActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
                        state      = state,
                        onToggle   = vm::toggleFlashlight,
                        onSetMode  = vm::setMode,
                        onSetBright= vm::setBrightness,
                        onOpenFull = {
                            startActivity(
                                Intent(this@FlashlightQuickControlActivity, FlashlightQuickControlActivity::class.java)
                                    .putExtra("open_full", true)
                            )
                            finish()
                        },
                        onDismiss  = { finish() },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sheet content — polished M3 Expressive layout
// ─────────────────────────────────────────────────────────────────────────────

private val BeamYellow = Color(0xFFFFEE58)
private val BeamAmber  = Color(0xFFFBC02D)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickSheetContent(
    state: FlashlightState,
    onToggle: () -> Unit,
    onSetMode: (FlashlightMode) -> Unit,
    onSetBright: (Float) -> Unit,
    onOpenFull: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Scale spring on ON state
    val headerScale by animateFloatAsState(
        targetValue   = if (state.isOn) 1.04f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label         = "quickScale",
    )

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
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
                        } else "Tap to activate",
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
                shape    = SmallExpressiveShape,
                colors   = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Icon(Icons.Rounded.OpenInFull, "Full screen", modifier = Modifier.size(18.dp))
            }
        }

        // ── Large toggle button ───────────────────────────────────────────────
        ToolzExpressiveButton(
            onClick  = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape    = SquircleShape,
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (state.isOn) BeamYellow
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor   = if (state.isOn) Color.Black
                else MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            AnimatedContent(
                targetState = state.isOn,
                transitionSpec = {
                    (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn())
                        .togetherWith(scaleOut() + fadeOut())
                },
                label = "toggleIcon",
            ) { on ->
                Icon(
                    if (on) Icons.Rounded.FlashlightOff else Icons.Rounded.FlashlightOn,
                    null,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                if (state.isOn) "TURN OFF" else "TURN ON",
                style         = MaterialTheme.typography.titleMedium,
                fontWeight    = FontWeight.Black,
                letterSpacing = 1.sp,
            )
        }

        // ── Mode selector chips ───────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlashlightMode.entries.forEach { mode ->
                val selected = state.mode == mode
                val (modeIcon, modeLabel) = when (mode) {
                    FlashlightMode.STEADY -> Icons.Rounded.WbSunny   to "Steady"
                    FlashlightMode.STROBE -> Icons.Rounded.FlashOn    to "Strobe"
                    FlashlightMode.SOS    -> Icons.Rounded.Warning    to "SOS"
                    FlashlightMode.DISCO  -> Icons.Rounded.MusicNote  to "Disco"
                }
                val chipColor by animateColorAsState(
                    targetValue   = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                    animationSpec = tween(200),
                    label         = "chipColor_$mode",
                )
                Surface(
                    onClick = { onSetMode(mode) },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    shape    = SquircleShape,
                    color    = chipColor,
                    border   = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) else null,
                ) {
                    Column(
                        modifier            = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            modeIcon, null,
                            modifier = Modifier.size(20.dp),
                            tint     = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            modeLabel,
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                            color      = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }

        // ── Brightness slider (only if hw supports it) ────────────────────────
        AnimatedVisibility(
            visible = state.isBrightnessSupported && state.mode == FlashlightMode.STEADY,
            enter   = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit    = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.Tune, null,
                            modifier = Modifier.size(15.dp),
                            tint     = MaterialTheme.colorScheme.primary)
                        Text("INTENSITY",
                            style         = MaterialTheme.typography.labelSmall,
                            fontWeight    = FontWeight.Black,
                            color         = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp)
                    }
                    Text(
                        "${(state.brightness * 100).toInt()}%",
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color      = MaterialTheme.colorScheme.primary,
                    )
                }
                ExpressiveSlider(
                    value         = state.brightness,
                    onValueChange = onSetBright,
                    valueRange    = 0.1f..1.0f,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
        }

        // ── Dismiss ───────────────────────────────────────────────────────────
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("CLOSE",
                fontWeight = FontWeight.Black,
                color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                letterSpacing = 1.sp)
        }
    }
}
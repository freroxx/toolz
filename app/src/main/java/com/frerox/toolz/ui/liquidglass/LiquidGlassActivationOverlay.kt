/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Uses backdrop library © Kyant, Apache License 2.0
 * https://github.com/Kyant0/AndroidLiquidGlass
 */

package com.frerox.toolz.ui.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.frerox.toolz.R
import com.frerox.toolz.data.skin.IconCatalog
import com.frerox.toolz.data.skin.IconShape
import com.frerox.toolz.ui.components.MarkPalettes
import com.frerox.toolz.ui.components.ToolzMarkCanvas
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidToggle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  Timeline (milliseconds) — every beat of the sequence lives here.
//  Re-time the whole animation by editing this block only.
// ─────────────────────────────────────────────────────────────────────────────
private object Beat {
    // Ambient world
    const val SCRIM_IN = 700f
    const val AMBIENT_START = 200f
    const val AMBIENT_END = 1600f

    // 2D mark arrives (spring)
    const val MARK_IN_START = 250f
    const val MARK_IN_END = 1450f

    // Anticipation: the mark compresses, then releases into the morph
    const val DIP_START = 1550f
    const val DIP_END = 1850f
    const val RELEASE_END = 2650f

    // Flat mark → glass jewel
    const val MORPH_START = 1850f
    const val MORPH_END = 2450f
    const val FLARE_START = 1950f
    const val FLARE_END = 2800f
    const val RIM_START = 1950f
    const val RIM_END = 3300f

    // Refraction burst
    const val BURST = 2350f

    // Frosted panes drift in after the burst — the first hint of the new UI language
    const val PANES_START = 2900f

    // Typography
    const val TITLE_START = 2950f
    const val TITLE_STAGGER = 120f
    const val TITLE_WORD = 700f
    const val RULE_START = 3550f
    const val RULE_END = 4200f
    const val SUB_START = 3700f
    const val SUB_END = 4350f

    // Exit: text leaves, mark lifts away, a lens iris opens onto the new UI
    const val EXIT_START = 5500f
    const val TOTAL = 6900f
}

private val GROUP_SHIFT = 56.dp      // lifts the whole composition slightly above centre
private val JEWEL_BOX = 164.dp
private val JEWEL_SIZE = 156.dp
private val TEXT_OFFSET = 190.dp     // distance from screen centre to the text block's centre

private val TitleBrush = Brush.verticalGradient(listOf(Color.White, Color(0xFFE2DAFF)))
private val Lilac = Color(0xFFC4B5FD)

/**
 * Premium cinematic transition played when Liquid Glass mode is toggled.
 *
 * Supports both enabling (Material 3 Expressive → Liquid Glass) and
 * disabling (Liquid Glass → Material 3 Expressive).
 */
@Composable
fun LiquidGlassActivationOverlay(
    visible: Boolean,
    enabling: Boolean = true,
    onFinished: () -> Unit,
) {
    if (!visible) return
    if (enabling) {
        EnableTransition(onFinished)
    } else {
        DisableTransition(onFinished)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Enable Transition: Material 3 Expressive → Liquid Glass
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EnableTransition(onFinished: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    val currentOnFinished by rememberUpdatedState(onFinished)

    val clock = remember { Animatable(0f) }
    val time = remember(clock) { { clock.value } }

    val defaultSpec = remember { IconCatalog.find(IconCatalog.DEFAULT_ID) }
    val defaultPalette = remember(defaultSpec) { MarkPalettes.of(defaultSpec) }
    val sparks = remember { buildSparks() }
    val panes = remember { buildPanes() }
    val glassPainter = painterResource(R.drawable.ic_liquid_glass_mark)
    val activationBackdrop = rememberLayerBackdrop()

    LaunchedEffect(Unit) {
        // Haptics are tied to the same beats the visuals use.
        launch {
            delay(Beat.DIP_END.toLong())
            vibrationManager?.vibrateClick()
        }
        launch {
            delay(Beat.BURST.toLong())
            vibrationManager?.vibrateSuccess()
        }
        launch {
            delay((Beat.EXIT_START + 100f).toLong())
            vibrationManager?.vibrateClick()
        }
        clock.animateTo(Beat.TOTAL, tween(Beat.TOTAL.toInt(), easing = LinearEasing))
        currentOnFinished()
    }

    Dialog(
        onDismissRequest = { currentOnFinished() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Remove the platform dim so the iris reveal is clean, with no pop when the dialog closes.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (time() > 600f) currentOnFinished()
                }
        ) {
            // ── World layer: scrim, aurora, vignette, bloom, shockwaves, shards, iris ──
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(activationBackdrop)
                    // Offscreen so the iris can erase everything with DstOut.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                val t = time()
                val c = Offset(size.width / 2f, size.height / 2f - GROUP_SHIFT.toPx())
                val reach = hypot(max(c.x, size.width - c.x), max(c.y, size.height - c.y)) * 1.1f

                drawBackdrop(t, c)
                drawPanes(panes, t)
                // Glow + caustic pool are drawn here, in the full-screen layer, so they are never
                // clipped by the jewel's own (fading) layer.
                drawJewelGlow(t, c)
                drawStreak(t, c)

                val b = Beat.BURST
                drawShockwave(seg(t, b, b + 1500f), c, reach, power = 2.8f, strength = 0.90f)
                drawShockwave(seg(t, b + 150f, b + 1500f), c, reach, power = 2.5f, strength = 0.55f)
                drawShockwave(seg(t, b + 380f, b + 1600f), c, reach, power = 2.2f, strength = 0.28f)
                drawSparks(sparks, t, c)

                drawIris(t, c)
            }

            // ── Jewel ──────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = -GROUP_SHIFT)
                    .size(JEWEL_BOX)
                    .graphicsLayer {
                        val t = time()
                        val s = markScale(t)
                        scaleX = s
                        scaleY = s
                        rotationZ = -11f * (1f - springOut(seg(t, Beat.MARK_IN_START, Beat.MARK_IN_END)))
                        translationY = sin(t / 1000f * 1.9f) * 3.dp.toPx() *
                                seg(t, Beat.MARK_IN_END, Beat.MARK_IN_END + 600f)
                        alpha = markAlpha(t)
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Layer A: flat Material mark — compresses and dissolves as the glass inflates
                ToolzMarkCanvas(
                    palette = defaultPalette,
                    frame = IconShape.OCTAGON,
                    modifier = Modifier
                        .size(148.dp)
                        .graphicsLayer {
                            val t = time()
                            alpha = 1f - easeIn(seg(t, Beat.MORPH_START, Beat.MORPH_START + 420f))
                            val s = 1f - 0.10f * morph(t)
                            scaleX = s
                            scaleY = s
                        },
                )

                // Layer B: glass jewel. The image and its light live in ONE offscreen layer and the
                // light is blended with SrcAtop, so it can only ever land on the jewel's own opaque
                // pixels — whatever the shape — and can never form a box around it.
                Canvas(
                    modifier = Modifier
                        .size(JEWEL_SIZE)
                        .graphicsLayer {
                            val t = time()
                            alpha = easeOut(seg(t, Beat.MORPH_START + 120f, Beat.MORPH_END))
                            val s = 0.88f + 0.12f * springOut(seg(t, Beat.MORPH_START, Beat.MORPH_END + 500f))
                            scaleX = s
                            scaleY = s
                            // Slow 3D tilt so the jewel feels like a physical object catching light
                            val tilt = morph(t)
                            rotationX = 7f * sin(t / 1000f * 1.3f) * tilt
                            rotationY = 9f * cos(t / 1000f * 1.1f) * tilt
                            cameraDistance = 14f * density
                            compositingStrategy = CompositingStrategy.Offscreen
                        },
                ) {
                    with(glassPainter) { draw(size = size) }
                    drawJewelLight(time())
                }
            }

            // ── Typography ─────────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = TEXT_OFFSET - GROUP_SHIFT)
                    .padding(horizontal = 28.dp)
                    .graphicsLayer {
                        alpha = 1f - easeIn(seg(time(), Beat.EXIT_START, Beat.EXIT_START + 500f))
                    },
            ) {
                TitleLine(listOf("Toolz", "got"), firstIndex = 0, time = time)
                TitleLine(listOf("a", "new", "look"), firstIndex = 2, time = time)

                Spacer(modifier = Modifier.height(18.dp))

                // Hairline rule that draws outward from the centre
                Box(
                    modifier = Modifier
                        .width(88.dp)
                        .height(2.dp)
                        .graphicsLayer {
                            val p = easeOut(seg(time(), Beat.RULE_START, Beat.RULE_END))
                            scaleX = p
                            alpha = p
                        }
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Lilac, Color.Transparent),
                            ),
                        ),
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Liquid Glass Mode Activated",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Lilac,
                    ),
                    modifier = Modifier.graphicsLayer {
                        val p = easeOut(seg(time(), Beat.SUB_START, Beat.SUB_END))
                        alpha = p
                        translationY = (1f - p) * 14.dp.toPx()
                    },
                )
            }

            // ── Real Liquid Glass Showcase Card ────────────────────────────────
            GlassShowcaseCard(
                backdrop = activationBackdrop,
                time = time,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 44.dp)
            )

            // ── Skip Button ────────────────────────────────────────────────────
            Surface(
                onClick = { currentOnFinished() },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.50f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Skip",
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Disable Transition: Liquid Glass → Material 3 Expressive
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DisableTransition(onFinished: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    val currentOnFinished by rememberUpdatedState(onFinished)
    val clock = remember { Animatable(0f) }
    val time = remember(clock) { { clock.value } }

    val defaultSpec = remember { IconCatalog.find(IconCatalog.DEFAULT_ID) }
    val defaultPalette = remember(defaultSpec) { MarkPalettes.of(defaultSpec) }
    val glassPainter = painterResource(R.drawable.ic_liquid_glass_mark)

    LaunchedEffect(Unit) {
        launch {
            delay(150L)
            vibrationManager?.vibrateClick()
        }
        launch {
            delay(600L)
            vibrationManager?.vibrateSuccess()
        }
        clock.animateTo(1800f, tween(1800, easing = LinearEasing))
        currentOnFinished()
    }

    Dialog(
        onDismissRequest = { currentOnFinished() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (time() > 300f) currentOnFinished()
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                val t = time()
                val c = Offset(size.width / 2f, size.height / 2f - GROUP_SHIFT.toPx())
                val reach = hypot(max(c.x, size.width - c.x), max(c.y, size.height - c.y)) * 1.1f

                // Tonal wash
                val scrim = (t / 400f).coerceIn(0f, 1f)
                val exitScrim = ((t - 1400f) / 400f).coerceIn(0f, 1f)
                drawRect(Color(0xFF0F0E17).copy(alpha = 0.95f * scrim * (1f - exitScrim)))

                // Iris exit reveal
                val exitP = ((t - 1300f) / 500f).coerceIn(0f, 1f)
                if (exitP > 0f) {
                    val r = reach * easeInOut(exitP)
                    drawCircle(
                        color = Color.Transparent,
                        radius = r,
                        center = c,
                        blendMode = BlendMode.Clear,
                    )
                }
            }

            // Jewel condensing back to flat mark
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = -GROUP_SHIFT)
                    .size(JEWEL_BOX)
                    .graphicsLayer {
                        val t = time()
                        val exit = ((t - 1300f) / 500f).coerceIn(0f, 1f)
                        alpha = 1f - exit
                    },
                contentAlignment = Alignment.Center,
            ) {
                val t = time()
                val morphProgress = (t / 800f).coerceIn(0f, 1f)

                // Glass jewel fades out
                Canvas(
                    modifier = Modifier
                        .size(JEWEL_SIZE)
                        .graphicsLayer {
                            alpha = (1f - morphProgress).coerceIn(0f, 1f)
                            val s = 1f - 0.12f * morphProgress
                            scaleX = s
                            scaleY = s
                        },
                ) {
                    with(glassPainter) { draw(size = size) }
                }

                // Flat mark springs in
                ToolzMarkCanvas(
                    palette = defaultPalette,
                    frame = IconShape.OCTAGON,
                    modifier = Modifier
                        .size(148.dp)
                        .graphicsLayer {
                            alpha = morphProgress
                            val s = 0.85f + 0.15f * springOut(morphProgress)
                            scaleX = s
                            scaleY = s
                        },
                )
            }

            // Typography
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = TEXT_OFFSET - GROUP_SHIFT)
                    .padding(horizontal = 28.dp)
                    .graphicsLayer {
                        val t = time()
                        val p = (t / 500f).coerceIn(0f, 1f)
                        val exit = ((t - 1300f) / 500f).coerceIn(0f, 1f)
                        alpha = p * (1f - exit)
                        translationY = (1f - p) * 12.dp.toPx()
                    },
            ) {
                Text(
                    text = "Material 3 Expressive",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    ),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Standard Mode Restored",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color.White.copy(alpha = 0.75f),
                        letterSpacing = 1.2.sp,
                    ),
                )
            }

            // Skip button
            Surface(
                onClick = { currentOnFinished() },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.50f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Skip",
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Real Liquid Glass Showcase Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GlassShowcaseCard(
    backdrop: com.kyant.backdrop.Backdrop,
    time: () -> Float,
    modifier: Modifier = Modifier,
) {
    val t = time()
    val enter = springOut(seg(t, Beat.PANES_START, Beat.PANES_START + 800f))
    val exit = easeIn(seg(t, Beat.EXIT_START, Beat.TOTAL - 200f))
    val alpha = (enter * (1f - exit)).coerceIn(0f, 1f)
    if (alpha <= 0.01f) return

    val slideY = 32.dp * (1f - enter)

    GlassCard(
        backdrop = backdrop,
        shape = RoundedCornerShape(24.dp),
        tint = Color(0xFF9D5FFF).copy(alpha = 0.22f),
        modifier = modifier
            .offset(y = slideY)
            .graphicsLayer { this.alpha = alpha }
            .fillMaxWidth(0.90f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF9D5FFF).copy(alpha = 0.35f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Column {
                    Text(
                        text = "Physical Refraction",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "Specular highlight & chromatic dispersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                    )
                }
            }
            com.kyant.backdrop.catalog.components.LiquidToggle(
                selected = { true },
                onSelect = {},
                backdrop = backdrop,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LiquidGlassChip(
                selected = true,
                onClick = {},
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Fluid Optics",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            LiquidGlassChip(
                selected = false,
                onClick = {},
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Live Shaders",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Typography
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TitleLine(words: List<String>, firstIndex: Int, time: () -> Float) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        words.forEachIndexed { i, word ->
            RevealWord(
                text = word,
                start = Beat.TITLE_START + (firstIndex + i) * Beat.TITLE_STAGGER,
                time = time,
            )
        }
    }
}

@Composable
private fun RevealWord(text: String, start: Float, time: () -> Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall.copy(
            brush = TitleBrush,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            letterSpacing = (-1).sp,
            fontWeight = FontWeight.Black,
            shadow = Shadow(
                color = Color(0xFF7C3AED).copy(alpha = 0.55f),
                offset = Offset.Zero,
                blurRadius = 28f,
            ),
        ),
        modifier = Modifier.graphicsLayer {
            val p = easeOut(seg(time(), start, start + Beat.TITLE_WORD))
            alpha = p
            translationY = (1f - p) * 26.dp.toPx()
            val s = 0.94f + 0.06f * p
            scaleX = s
            scaleY = s
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Timeline math
// ─────────────────────────────────────────────────────────────────────────────

/** Normalised progress of [t] inside [from]..[to], clamped to 0..1. */
private fun seg(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun easeOut(p: Float): Float = 1f - (1f - p).pow(3)
private fun easeIn(p: Float): Float = p * p * p
private fun easeInOut(p: Float): Float =
    if (p < 0.5f) 4f * p * p * p else 1f - (-2f * p + 2f).pow(3) / 2f

/** Damped spring, ~8 % overshoot, settles at 1. */
private fun springOut(p: Float): Float {
    if (p <= 0f) return 0f
    if (p >= 1f) return 1f
    return 1f - exp(-6.5f * p) * cos(8f * p)
}

private fun morph(t: Float): Float = easeInOut(seg(t, Beat.MORPH_START, Beat.MORPH_END))

/** Short white-hot spike at the moment of the burst. */
private fun burstFlash(t: Float): Float {
    val rise = easeOut(seg(t, Beat.BURST - 80f, Beat.BURST + 60f))
    val fall = easeOut(seg(t, Beat.BURST + 60f, Beat.BURST + 800f))
    return rise * (1f - fall)
}

private fun markScale(t: Float): Float {
    val enter = 0.55f + 0.45f * springOut(seg(t, Beat.MARK_IN_START, Beat.MARK_IN_END))
    // Compress into the morph, then release with overshoot (punch goes slightly negative → a pop).
    val dip = easeInOut(seg(t, Beat.DIP_START, Beat.DIP_END))
    val release = springOut(seg(t, Beat.DIP_END, Beat.RELEASE_END))
    val punch = dip * (1f - release)
    val exit = easeIn(seg(t, Beat.EXIT_START, Beat.EXIT_START + 800f))
    return enter * (1f - 0.09f * punch) * (1f + 0.22f * exit)
}

private fun markAlpha(t: Float): Float {
    val enter = easeOut(seg(t, Beat.MARK_IN_START, Beat.MARK_IN_START + 450f))
    val exit = easeIn(seg(t, Beat.EXIT_START + 100f, Beat.EXIT_START + 800f))
    return enter * (1f - exit)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Drawing
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawBackdrop(t: Float, center: Offset) {
    val w = size.width
    val h = size.height
    val big = max(w, h)

    val scrim = easeOut(seg(t, 0f, Beat.SCRIM_IN))
    drawRect(Color(0xFF07040E).copy(alpha = 0.93f * scrim))

    // Slow-drifting aurora that wakes up with the mark and flares on the burst
    val ambient = easeOut(seg(t, Beat.AMBIENT_START, Beat.AMBIENT_END))
    val flash = burstFlash(t)
    if (ambient > 0.01f) {
        val ph = t / 1000f
        fun orb(color: Color, fx: Float, fy: Float, radiusFrac: Float, a: Float) {
            val o = Offset(fx * w, fy * h)
            val r = big * radiusFrac
            val alpha = (a * ambient * (1f + 0.8f * flash)).coerceIn(0f, 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = alpha),
                    1f to Color.Transparent,
                    center = o,
                    radius = r,
                ),
                radius = r,
                center = o,
            )
        }
        orb(Color(0xFF7C3AED), 0.22f + 0.05f * sin(ph * 0.70f), 0.28f + 0.04f * cos(ph * 0.50f), 0.62f, 0.34f)
        orb(Color(0xFF2563EB), 0.80f + 0.05f * cos(ph * 0.60f), 0.74f + 0.05f * sin(ph * 0.45f), 0.66f, 0.30f)
        orb(Color(0xFFDB2777), 0.72f + 0.04f * sin(ph * 0.50f), 0.20f + 0.03f * cos(ph * 0.80f), 0.40f, 0.14f)
    }

    // Vignette
    drawRect(
        brush = Brush.radialGradient(
            0.5f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.55f * scrim),
            center = Offset(w / 2f, h / 2f),
            radius = hypot(w, h) / 2f,
        ),
    )

    // Lens bloom at the burst
    if (flash > 0.01f) {
        val r = big * 0.55f
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.22f * flash),
                1f to Color.Transparent,
                center = center,
                radius = r,
            ),
            radius = r,
            center = center,
        )
    }
}

/**
 * One refraction wavefront: a soft lensing band plus three additive rings with
 * slightly different radii (magenta / white / cyan) to fake chromatic dispersion.
 */
private fun DrawScope.drawShockwave(
    p: Float,
    center: Offset,
    reach: Float,
    power: Float,
    strength: Float,
) {
    if (p <= 0f || p >= 1f) return
    val e = 1f - (1f - p).pow(power)
    val r = reach * e
    val a = (1f - e).pow(1.4f) * strength
    if (r < 2f || a < 0.01f) return

    val u = 1.dp.toPx()
    val width = u * (1.5f + 14f * (1f - e))

    drawCircle(
        brush = Brush.radialGradient(
            0.80f to Color.Transparent,
            0.95f to Color(0xFFB4A0FF).copy(alpha = a * 0.35f),
            1.00f to Color.Transparent,
            center = center,
            radius = r,
        ),
        radius = r,
        center = center,
    )

    fun ring(radius: Float, color: Color) {
        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = width),
            blendMode = BlendMode.Plus,
        )
    }
    ring(r * 0.992f, Color(0xFFFF5FA2).copy(alpha = a * 0.55f))
    ring(r, Color.White.copy(alpha = a * 0.90f))
    ring(r * 1.008f, Color(0xFF38BDF8).copy(alpha = a * 0.60f))
}

private class Spark(
    val angle: Float,
    val reach: Float,
    val radiusDp: Float,
    val delay: Float,
    val color: Color,
)

private fun fract(x: Float): Float = x - floor(x)

/** Deterministic scatter — golden-angle distribution, so it never looks random-clumpy. */
private fun buildSparks(): List<Spark> {
    val palette = listOf(Color.White, Color(0xFFC4B5FD), Color(0xFF93C5FD), Color(0xFFF0ABFC))
    return List(28) { i ->
        Spark(
            angle = i * 2.3999631f,
            reach = 0.16f + 0.40f * fract(i * 0.6180339f + 0.13f),
            radiusDp = 1.2f + 2.4f * fract(i * 0.4142136f + 0.37f),
            delay = 0.22f * fract(i * 0.7320508f),
            color = palette[i % palette.size],
        )
    }
}

private fun DrawScope.drawSparks(sparks: List<Spark>, t: Float, center: Offset) {
    val base = seg(t, Beat.BURST, Beat.BURST + 1500f)
    if (base <= 0f || base >= 1f) return
    val startR = 70.dp.toPx()
    val span = minOf(size.width, size.height)
    sparks.forEach { s ->
        val p = ((base - s.delay) / (1f - s.delay)).coerceIn(0f, 1f)
        if (p > 0f && p < 1f) {
            val e = easeOut(p)
            val d = startR + s.reach * span * e
            val pos = Offset(center.x + cos(s.angle) * d, center.y + sin(s.angle) * d)
            val a = (1f - p).pow(1.2f) * 0.9f
            drawCircle(
                color = s.color.copy(alpha = a),
                radius = s.radiusDp.dp.toPx() * (1f - 0.6f * p),
                center = pos,
            )
        }
    }
}

/**
 * Light that moves across the glass. Every draw uses SrcAtop, so it is composited only onto
 * pixels the jewel image already painted — the light follows the real silhouette.
 * Must be called after the jewel has been drawn, inside the same offscreen layer.
 */
private fun DrawScope.drawJewelLight(t: Float) {
    val glass = easeOut(seg(t, Beat.MORPH_START + 120f, Beat.MORPH_END))
    if (glass <= 0.01f) return

    // Diagonal specular sweep
    val f = seg(t, Beat.FLARE_START, Beat.FLARE_END)
    if (f > 0.001f && f < 0.999f) {
        val x = size.width * (easeInOut(f) * 1.8f - 0.4f)
        val band = size.width * 0.55f
        drawRect(
            brush = Brush.linearGradient(
                0.00f to Color.Transparent,
                0.42f to Color.White.copy(alpha = 0.18f),
                0.50f to Color.White.copy(alpha = 0.70f),
                0.58f to Color(0xFF93C5FD).copy(alpha = 0.35f),
                1.00f to Color.Transparent,
                start = Offset(x - band, 0f),
                end = Offset(x + band, size.height),
            ),
            blendMode = BlendMode.SrcAtop,
        )
    }

    // Rotating conic sheen: a lobe of light that orbits the jewel one and a half times, then fades
    val sheen = glass *
            seg(t, Beat.RIM_START, Beat.RIM_START + 300f) *
            (1f - easeIn(seg(t, Beat.RIM_END - 600f, Beat.RIM_END)))
    if (sheen > 0.01f) {
        val angle = 540f * easeInOut(seg(t, Beat.RIM_START, Beat.RIM_END))
        val c = Offset(size.width / 2f, size.height / 2f)
        rotate(degrees = angle, pivot = c) {
            drawRect(
                brush = Brush.sweepGradient(
                    0.00f to Color.Transparent,
                    0.62f to Color.Transparent,
                    0.84f to Color(0xFF93C5FD).copy(alpha = 0.22f * sheen),
                    0.94f to Color.White.copy(alpha = 0.50f * sheen),
                    1.00f to Color.Transparent,
                    center = c,
                ),
                // Oversized so rotation never leaves the corners uncovered
                topLeft = Offset(-size.width * 0.25f, -size.height * 0.25f),
                size = Size(size.width * 1.5f, size.height * 1.5f),
                blendMode = BlendMode.SrcAtop,
            )
        }
    }
}

/** Violet aura behind the jewel plus a faint caustic light-pool on the "floor" beneath it. */
private fun DrawScope.drawJewelGlow(t: Float, c: Offset) {
    val vis = markAlpha(t)
    if (vis <= 0.01f) return
    val s = markScale(t)
    val breathe = 1f + 0.04f * sin(t / 1000f * 2.6f)

    val glow = (0.42f * morph(t) + 0.35f * burstFlash(t)).coerceIn(0f, 1f) * vis
    if (glow > 0.01f) {
        val r = 150.dp.toPx() * s * breathe
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to Color(0xFFB066FE).copy(alpha = glow),
                0.5f to Color(0xFF5865F2).copy(alpha = glow * 0.5f),
                1.0f to Color.Transparent,
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
        )
    }

    val pool = 0.38f * morph(t) * vis
    if (pool > 0.01f) {
        val pivot = Offset(c.x, c.y + 104.dp.toPx() * s)
        val r = 96.dp.toPx() * s
        scale(scaleX = 1f, scaleY = 0.16f, pivot = pivot) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFFA78BFA).copy(alpha = pool),
                    1f to Color.Transparent,
                    center = pivot,
                    radius = r,
                ),
                radius = r,
                center = pivot,
            )
        }
    }
}

/** Anamorphic lens streak: a thin cinematic horizontal line that shoots out on the burst. */
private fun DrawScope.drawStreak(t: Float, c: Offset) {
    val p = seg(t, Beat.BURST - 60f, Beat.BURST + 900f)
    if (p <= 0f || p >= 1f) return
    val rise = easeOut(seg(t, Beat.BURST - 60f, Beat.BURST + 60f))
    val a = (1f - p).pow(1.6f) * rise
    if (a < 0.01f) return

    val half = size.width * (0.15f + 0.85f * easeOut(p))
    val left = c.x - half
    val thin = 2.dp.toPx()
    val thick = 12.dp.toPx()

    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.5f to Color(0xFF7DD3FC).copy(alpha = a * 0.22f),
            1f to Color.Transparent,
            startX = left,
            endX = c.x + half,
        ),
        topLeft = Offset(left, c.y - thick / 2f),
        size = Size(half * 2f, thick),
        blendMode = BlendMode.Plus,
    )
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.5f to Color.White.copy(alpha = a * 0.90f),
            1f to Color.Transparent,
            startX = left,
            endX = c.x + half,
        ),
        topLeft = Offset(left, c.y - thin / 2f),
        size = Size(half * 2f, thin),
        blendMode = BlendMode.Plus,
    )
}

/** A frosted pane: a translucent, softly lit rounded slab that drifts in the background. */
private class Pane(
    val fx: Float,
    val fy: Float,
    val widthDp: Float,
    val heightDp: Float,
    val rotation: Float,
    val depth: Float,   // parallax strength
    val phase: Float,
    val delay: Float,   // ms after PANES_START
)

private fun buildPanes(): List<Pane> = listOf(
    Pane(0.16f, 0.17f, 132f, 92f, -14f, 1.0f, 0.0f, 0f),
    Pane(0.86f, 0.23f, 104f, 148f, 11f, 0.7f, 1.7f, 140f),
    Pane(0.09f, 0.60f, 92f, 120f, 9f, 0.6f, 3.1f, 260f),
    Pane(0.90f, 0.68f, 138f, 96f, -9f, 1.1f, 4.4f, 90f),
    Pane(0.30f, 0.90f, 118f, 80f, 7f, 0.8f, 2.3f, 340f),
    Pane(0.72f, 0.09f, 84f, 84f, 18f, 0.5f, 5.2f, 200f),
)

private fun DrawScope.drawPanes(panes: List<Pane>, t: Float) {
    val out = 1f - easeIn(seg(t, Beat.EXIT_START, Beat.EXIT_START + 900f))
    if (t < Beat.PANES_START || out <= 0.01f) return
    val ph = t / 1000f
    panes.forEach { pane ->
        val p = easeOut(seg(t, Beat.PANES_START + pane.delay, Beat.PANES_START + pane.delay + 1000f))
        val a = p * out
        if (a > 0.01f) {
            val w = pane.widthDp.dp.toPx()
            val h = pane.heightDp.dp.toPx()
            val cx = pane.fx * size.width + sin(ph * 0.6f + pane.phase) * 14.dp.toPx() * pane.depth
            val cy = pane.fy * size.height + cos(ph * 0.5f + pane.phase) * 10.dp.toPx() * pane.depth +
                    (1f - p) * 36.dp.toPx()
            val topLeft = Offset(cx - w / 2f, cy - h / 2f)
            val corner = CornerRadius(22.dp.toPx())
            rotate(degrees = pane.rotation + sin(ph * 0.4f + pane.phase) * 3f, pivot = Offset(cx, cy)) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.10f * a),
                        1f to Color(0xFFA78BFA).copy(alpha = 0.03f * a),
                        start = topLeft,
                        end = Offset(topLeft.x + w, topLeft.y + h),
                    ),
                    topLeft = topLeft,
                    size = Size(w, h),
                    cornerRadius = corner,
                )
                // Edge light: bright on the top-left, gone by the bottom-right
                drawRoundRect(
                    brush = Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.38f * a),
                        0.55f to Color.White.copy(alpha = 0.04f * a),
                        1f to Color(0xFF93C5FD).copy(alpha = 0.16f * a),
                        start = topLeft,
                        end = Offset(topLeft.x + w, topLeft.y + h),
                    ),
                    topLeft = topLeft,
                    size = Size(w, h),
                    cornerRadius = corner,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
    }
}

/**
 * Exit: instead of a flat fade, a soft lens iris opens from the jewel and
 * erases the scrim, so the new Liquid Glass UI is revealed *through* the glass.
 */
private fun DrawScope.drawIris(t: Float, center: Offset) {
    val p = seg(t, Beat.EXIT_START + 100f, Beat.TOTAL - 100f)
    if (p <= 0f) return
    val e = easeInOut(p)

    val reach = hypot(max(center.x, size.width - center.x), max(center.y, size.height - center.y))
    val feather = reach * (0.08f + 0.22f * e)
    val inner = reach * e
    val outer = inner + feather
    if (outer < 2f) return

    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Black,
            (inner / outer).coerceIn(0.002f, 0.999f) to Color.Black,
            1f to Color.Transparent,
            center = center,
            radius = outer,
        ),
        radius = outer,
        center = center,
        blendMode = BlendMode.DstOut,
    )

    // Glass edge on the opening
    val fade = (1f - e).pow(0.8f) * seg(p, 0f, 0.12f)
    if (fade > 0.01f) {
        val u = 1.dp.toPx()
        val rimR = inner + feather * 0.5f
        fun ring(radius: Float, color: Color, w: Float) {
            drawCircle(color = color, radius = radius, center = center, style = Stroke(width = w))
        }
        ring(rimR - 2.5f * u, Color(0xFFFF5FA2).copy(alpha = fade * 0.30f), 2f * u)
        ring(rimR, Color.White.copy(alpha = fade * 0.55f), 1.5f * u)
        ring(rimR + 2.5f * u, Color(0xFF38BDF8).copy(alpha = fade * 0.35f), 2f * u)
    }
}
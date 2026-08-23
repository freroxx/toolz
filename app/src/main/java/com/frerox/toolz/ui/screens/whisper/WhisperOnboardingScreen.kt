/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch

/**
 * Onboarding flow for Whisper, built around M3 Expressive shape morphing.
 * The icon container physically morphs from one MaterialShapes shape to the
 * next as the user swipes. The background carries a small constellation of
 * softly floating, independently morphing shapes that recolor with the
 * active step, giving the screen continuous, layered motion rather than a
 * single static glow.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhisperOnboardingScreen(
    onComplete: () -> Unit,
    viewModel: WhisperViewModel = hiltViewModel(),
) {
    val screenshotBypassEnabled by viewModel.screenshotBypassEnabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Local guard against double-taps: once completing starts, both buttons disable until
    // the VM callback fires (the VM-side markOnboardingAsShown guard is handled separately).
    var isCompleting by remember { mutableStateOf(false) }

    // Onboarding contains key-generation details — never capture this screen.
    SecureWindow(bypassEnabled = screenshotBypassEnabled)

    // V2-FIX O-H1: the four kickers are prominent headings and were hardcoded English.
    // The step data is built inside composition, so kickers resolve via stringResource
    // like the titles/descriptions.
    val onboardingSteps = listOf(
        OnboardingStep(
            kicker = stringResource(R.string.st_Whisper_Onboarding_Kicker1),
            title = stringResource(R.string.st_Whisper_Onboarding_Title1),
            description = stringResource(R.string.st_Whisper_Onboarding_Desc1),
            icon = Icons.AutoMirrored.Rounded.Chat,
            color = MaterialTheme.colorScheme.primary,
            shape = MaterialShapes.Cookie9Sided,
        ),
        OnboardingStep(
            kicker = stringResource(R.string.st_Whisper_Onboarding_Kicker2),
            title = stringResource(R.string.st_Whisper_Onboarding_Title2),
            description = stringResource(R.string.st_Whisper_Onboarding_Desc2),
            icon = Icons.Rounded.Explore,
            color = MaterialTheme.colorScheme.secondary,
            shape = MaterialShapes.Pill,
        ),
        OnboardingStep(
            kicker = stringResource(R.string.st_Whisper_Onboarding_Kicker3),
            title = stringResource(R.string.st_Whisper_Onboarding_Title3),
            description = stringResource(R.string.st_Whisper_Onboarding_Desc3),
            icon = Icons.Rounded.EnhancedEncryption,
            color = MaterialTheme.colorScheme.tertiary,
            shape = MaterialShapes.Clover4Leaf,
        ),
        OnboardingStep(
            kicker = stringResource(R.string.st_Whisper_Onboarding_Kicker4),
            title = stringResource(R.string.st_Whisper_Onboarding_Title4),
            description = stringResource(R.string.st_Whisper_Onboarding_Desc4),
            icon = Icons.Rounded.VerifiedUser,
            color = MaterialTheme.colorScheme.primary,
            shape = MaterialShapes.Sunny,
        ),
    )

    // Page count is derived from the steps list so adding/removing a page can never
    // desync the pager from its content.
    val pagerState = rememberPagerState(initialPage = 0) { onboardingSteps.size }
    val haptic = rememberToolzHapticFeedback()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
    ) {
        OnboardingExpressiveBackground(
            pagerState = pagerState,
            steps = onboardingSteps,
        )

        Column(
            // V2-FIX O-M1: the root column ignored status-bar insets; content could draw
            // under the status bar on edge-to-edge devices.
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                OnboardingPage(
                    step = onboardingSteps[page],
                    isVisible = pagerState.currentPage == page,
                    pagerState = pagerState,
                    steps = onboardingSteps,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(onboardingSteps.size) { index ->
                        val active = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (active) 28.dp else 8.dp,
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            label = "indicatorWidth",
                        )
                        val indicatorColor by animateColorAsState(
                            targetValue = if (active) {
                                onboardingSteps[index].color
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                            },
                            label = "indicatorColor",
                        )
                        // Resolved in composition — the semantics block below is not
                        // composable, so it consumes the pre-built string.
                        val pageOfCd = stringResource(
                            R.string.st_Whisper_Onboarding_PageOf,
                            index + 1,
                            onboardingSteps.size,
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(indicatorColor)
                                // V2-FIX O-M2: the dots were purely decorative to
                                // accessibility services — announce the selected state and
                                // page position.
                                .semantics {
                                    selected = active
                                    contentDescription = pageOfCd
                                }
                        )
                    }
                }

                val isLastPage = pagerState.currentPage == onboardingSteps.lastIndex
                ToolzExpressiveButton(
                    onClick = {
                        haptic.click()
                        if (isCompleting) return@ToolzExpressiveButton
                        if (isLastPage) {
                            isCompleting = true
                            viewModel.markOnboardingAsShown { onComplete() }
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    enabled = !isCompleting,
                ) {
                    AnimatedContent(
                        targetState = isLastPage,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "btnContent",
                    ) { last ->
                        Text(
                            text = if (last) {
                                stringResource(R.string.st_Whisper_Onboarding_GetStarted)
                            } else {
                                stringResource(R.string.st_Whisper_Onboarding_Next)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Box(
                    modifier = Modifier.height(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isLastPage,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        TextButton(
                            onClick = {
                                haptic.click()
                                if (isCompleting) return@TextButton
                                isCompleting = true
                                viewModel.markOnboardingAsShown { onComplete() }
                            },
                            enabled = !isCompleting,
                        ) {
                            Text(
                                stringResource(R.string.st_Whisper_OnboardingSkip),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A single onboarding page. The icon container's shape is derived from the
 * pager's live scroll position, so it continuously morphs between the
 * current and next step's shape as the user drags — rather than jump-cutting.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnboardingPage(
    step: OnboardingStep,
    isVisible: Boolean,
    pagerState: PagerState,
    steps: List<OnboardingStep>,
) {
    val pageIndex = steps.indexOf(step)

    val morphProgress by remember {
        derivedStateOf {
            val diff = pagerState.currentPage - pageIndex
            (diff + pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
        }
    }

    val nextShape = when {
        morphProgress > 0f && pageIndex < steps.lastIndex -> steps[pageIndex + 1].shape
        morphProgress < 0f && pageIndex > 0 -> steps[pageIndex - 1].shape
        else -> step.shape
    }

    val morph = remember(step.shape, nextShape) { Morph(step.shape, nextShape) }
    val morphShape = remember(morph) {
        MorphPolygonShape(morph, progress = { kotlin.math.abs(morphProgress) })
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(500),
        label = "contentAlpha",
    )
    val contentTranslation by animateFloatAsState(
        targetValue = if (isVisible) 0f else 24f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "contentTranslation",
    )

    // Gentle continuous bob so the hero shape never sits perfectly still,
    // even when the pager is idle.
    // L-18 FIX (reviewwhisper.md): performance mode renders a static hero — every other
    // Whisper screen already gates its animations behind LocalPerformanceMode.
    val performanceMode = com.frerox.toolz.ui.theme.LocalPerformanceMode.current
    val bob = if (performanceMode) {
        remember { 0f }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "iconBob")
        val animated by infiniteTransition.animateFloat(
            initialValue = -6f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bobOffset",
        )
        animated
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .graphicsLayer {
                alpha = contentAlpha
                translationY = contentTranslation
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp),
        ) {
            // Soft halo directly behind the icon shape — separate from the
            // page background so the icon always reads with clear contrast
            // no matter what's floating behind it.
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .graphicsLayer {
                        translationY = bob * 0.6f
                        alpha = 0.35f
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(step.color.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(196.dp)
                    .graphicsLayer { translationY = bob }
                    .clip(morphShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                step.color,
                                step.color.copy(alpha = 0.82f),
                            )
                        )
                    ),
            ) {
                Icon(
                    imageVector = step.icon,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.surface,
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = step.kicker,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = step.color,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = step.title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp,
            lineHeight = 38.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = step.description,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/**
 * A small constellation of decorative shapes drifting behind the content.
 * Each shape morphs independently between two MaterialShapes, floats along
 * a slow orbit, and recolors toward the active step — so the background
 * feels alive at every point in the flow instead of only reacting to swipes.
 * Kept low-alpha and out of the content's horizontal center band so it never
 * competes with the title, description, or icon for attention.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnboardingExpressiveBackground(
    pagerState: PagerState,
    steps: List<OnboardingStep>,
) {
    val density = LocalDensity.current
    val activeColor by animateColorAsState(
        targetValue = steps[pagerState.currentPage].color,
        animationSpec = tween(900),
        label = "activeColor",
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // Broad, very soft wash that recolors with the active step, giving
        // the whole screen a tint shift instead of one hard-edged glow.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.16f }
                .background(
                    Brush.radialGradient(
                        colors = listOf(activeColor, Color.Transparent),
                        center = Offset(widthPx * 0.5f, heightPx * 0.28f),
                        radius = maxOf(widthPx, heightPx) * 0.75f,
                    )
                )
        )

        val orbitSpecs = remember {
            listOf(
                FloatingShapeSpec(
                    baseShapeIndex = 1,
                    anchor = Alignment.TopStart,
                    offsetX = (-64).dp,
                    offsetY = 96.dp,
                    size = 150.dp,
                    alpha = 0.14f,
                    period = 5200,
                    driftAmplitude = 26f,
                    rotationRange = 20f,
                ),
                FloatingShapeSpec(
                    baseShapeIndex = 2,
                    anchor = Alignment.TopEnd,
                    offsetX = 56.dp,
                    offsetY = 180.dp,
                    size = 110.dp,
                    alpha = 0.16f,
                    period = 4300,
                    driftAmplitude = 18f,
                    rotationRange = -28f,
                ),
                FloatingShapeSpec(
                    baseShapeIndex = 3,
                    anchor = Alignment.BottomStart,
                    offsetX = (-40).dp,
                    offsetY = (-220).dp,
                    size = 96.dp,
                    alpha = 0.15f,
                    period = 3700,
                    driftAmplitude = 22f,
                    rotationRange = 24f,
                ),
                FloatingShapeSpec(
                    baseShapeIndex = 0,
                    anchor = Alignment.BottomEnd,
                    offsetX = 40.dp,
                    offsetY = (-140).dp,
                    size = 168.dp,
                    alpha = 0.13f,
                    period = 6000,
                    driftAmplitude = 30f,
                    rotationRange = -18f,
                ),
            )
        }

        orbitSpecs.forEach { spec ->
            FloatingMorphShape(
                spec = spec,
                steps = steps,
                currentPage = pagerState.currentPage,
            )
        }
    }
}

private data class FloatingShapeSpec(
    val baseShapeIndex: Int,
    val anchor: Alignment,
    val offsetX: androidx.compose.ui.unit.Dp,
    val offsetY: androidx.compose.ui.unit.Dp,
    val size: androidx.compose.ui.unit.Dp,
    val alpha: Float,
    val period: Int,
    val driftAmplitude: Float,
    val rotationRange: Float,
)

/**
 * One decorative background shape. It morphs endlessly between its own base
 * shape and the shape belonging to the currently active step, so the whole
 * constellation visually "agrees" with whichever page is showing, while each
 * shape drifts and rotates on its own independent, offset timing so they
 * never move in lockstep.
 */
@Composable
private fun BoxScope.FloatingMorphShape(
    spec: FloatingShapeSpec,
    steps: List<OnboardingStep>,
    currentPage: Int,
) {
    // L-18 FIX: performance mode renders one static shape per spec — no infinite
    // transitions, no morph allocation.
    val performanceMode = com.frerox.toolz.ui.theme.LocalPerformanceMode.current
    if (performanceMode) {
        val staticStep = steps[spec.baseShapeIndex % steps.size]
        Box(
            modifier = Modifier
                .align(spec.anchor)
                .offset(x = spec.offsetX, y = spec.offsetY)
                .size(spec.size)
                .graphicsLayer { alpha = spec.alpha }
                .clip(MorphPolygonShape(Morph(staticStep.shape, staticStep.shape), progress = { 0f }))
                .background(staticStep.color)
        )
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "floatingShape")

    val drift by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(spec.period, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )
    val morphCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(spec.period * 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "morphCycle",
    )

    val baseStep = steps[spec.baseShapeIndex % steps.size]
    val targetStep = steps[currentPage]

    val morph = remember(baseStep.shape, targetStep.shape) {
        Morph(baseStep.shape, targetStep.shape)
    }
    val morphShape = remember(morph) {
        MorphPolygonShape(morph, progress = { morphCycle })
    }

    val color by animateColorAsState(
        targetValue = targetStep.color,
        animationSpec = tween(900),
        label = "floatingShapeColor",
    )

    Box(
        modifier = Modifier
            .align(spec.anchor)
            .offset(x = spec.offsetX, y = spec.offsetY)
            .size(spec.size)
            .graphicsLayer {
                translationX = drift * spec.driftAmplitude
                translationY = -drift * spec.driftAmplitude * 0.6f
                rotationZ = drift * spec.rotationRange
                alpha = spec.alpha
            }
            .clip(morphShape)
            .background(color)
    )
}

data class OnboardingStep(
    val kicker: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val shape: RoundedPolygon,
)
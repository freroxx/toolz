/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhisperOnboardingScreen(
    onComplete: () -> Unit,
    viewModel: WhisperViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val haptic = rememberToolzHapticFeedback()

    val onboardingSteps = listOf(
        OnboardingStep(
            title = stringResource(R.string.st_Whisper_Onboarding_Title1),
            description = stringResource(R.string.st_Whisper_Onboarding_Desc1),
            icon = Icons.AutoMirrored.Rounded.Chat,
            color = MaterialTheme.colorScheme.primary,
            shape = StarExpressiveShape
        ),
        OnboardingStep(
            title = stringResource(R.string.st_Whisper_Onboarding_Title2),
            description = stringResource(R.string.st_Whisper_Onboarding_Desc2),
            icon = Icons.Rounded.Explore,
            color = MaterialTheme.colorScheme.secondary,
            shape = PebbleExpressiveShape
        ),
        OnboardingStep(
            title = stringResource(R.string.st_Whisper_Onboarding_Title3),
            description = stringResource(R.string.st_Whisper_Onboarding_Desc3),
            icon = Icons.Rounded.EnhancedEncryption,
            color = MaterialTheme.colorScheme.tertiary,
            shape = DiamondExpressiveShape
        ),
        OnboardingStep(
            title = stringResource(R.string.st_Whisper_Onboarding_Title4),
            description = stringResource(R.string.st_Whisper_Onboarding_Desc4),
            icon = Icons.Rounded.VerifiedUser,
            color = MaterialTheme.colorScheme.primary,
            shape = OvalExpressiveShape
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
    ) {
        // Background Decorations
        OnboardingDecorations(
            page = pagerState.currentPage,
            offset = { pagerState.currentPageOffsetFraction },
            steps = onboardingSteps
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) { page ->
                OnboardingPage(onboardingSteps[page], isVisible = pagerState.currentPage == page)
            }

            // Bottom Navigation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val active = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (active) 24.dp else 8.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                        )
                    }
                }

                // Buttons
                val isLastPage = pagerState.currentPage == 3
                ToolzExpressiveButton(
                    onClick = {
                        haptic.click()
                        if (isLastPage) {
                            viewModel.markOnboardingAsShown { onComplete() }
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    AnimatedContent(
                        targetState = isLastPage,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "btnContent"
                    ) { last ->
                        Text(
                            text = if (last) stringResource(R.string.st_Whisper_Onboarding_GetStarted) 
                                   else stringResource(R.string.st_Whisper_Onboarding_Next),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (!isLastPage) {
                    TextButton(
                        onClick = {
                            haptic.click()
                            viewModel.markOnboardingAsShown { onComplete() }
                        }
                    ) {
                        Text(
                            "Skip",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(step: OnboardingStep, isVisible: Boolean) {
    val contentScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "contentScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(600),
        label = "contentAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .graphicsLayer {
                scaleX = contentScale
                scaleY = contentScale
                alpha = contentAlpha
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon with expressive shape background
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer {
                    shadowElevation = 32f
                    shape = step.shape
                    clip = true
                }
                .background(
                    Brush.linearGradient(
                        listOf(
                            step.color.copy(alpha = 0.85f),
                            step.color
                        )
                    )
                )
        ) {
            Icon(
                step.icon,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = Color.White
            )
        }

        Spacer(Modifier.height(64.dp))

        Text(
            text = step.title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = (-1).sp
        )

        Spacer(Modifier.height(24.dp))

        TypewriterText(
            text = step.description,
            isVisible = isVisible,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TypewriterText(
    text: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    var displayedText by remember { mutableStateOf("") }
    
    LaunchedEffect(isVisible, text) {
        if (isVisible) {
            displayedText = ""
            val words = text.split(" ")
            for (i in words.indices) {
                displayedText = words.take(i + 1).joinToString(" ")
                delay(30.milliseconds) // Fast smooth typewriter
            }
        } else {
            displayedText = ""
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (displayedText.isNotEmpty()) 1f else 0f,
        animationSpec = tween(600),
        label = "typewriterAlpha"
    )

    Text(
        text = displayedText,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 30.sp,
        modifier = modifier.alpha(alpha)
    )
}

@Composable
private fun OnboardingDecorations(
    page: Int, 
    offset: () -> Float,
    steps: List<OnboardingStep>
) {
    val infiniteTransition = rememberInfiniteTransition(label = "decor")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    val currentStep = steps[page]
    val color by animateColorAsState(
        targetValue = currentStep.color,
        animationSpec = tween(1000),
        label = "color"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Large background shape
        Box(
            modifier = Modifier
                .size(500.dp)
                .offset(x = (-150).dp, y = (-150).dp)
                .graphicsLayer {
                    rotationZ = rotation
                    val s = 1.2f + (offset() * 0.3f)
                    scaleX = s
                    scaleY = s
                    alpha = 0.08f
                }
                .clip(currentStep.shape)
                .background(color)
        )

        // Floating shapes with varied M3 geometry
        ExpressiveFloatingDecoration(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = 120.dp)
                .graphicsLayer {
                    rotationZ = -rotation * 0.4f
                    val s = 1f + (offset() * 0.15f)
                    scaleX = s
                    scaleY = s
                },
            color = color,
            alpha = 0.12f,
            shape = steps[(page + 1) % steps.size].shape
        )

        ExpressiveFloatingDecoration(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = (-200).dp)
                .graphicsLayer {
                    rotationZ = rotation * 0.6f
                    val s = 1f - (offset() * 0.15f)
                    scaleX = s
                    scaleY = s
                },
            color = color,
            alpha = 0.12f,
            shape = steps[(page + 2) % steps.size].shape
        )
        
        ExpressiveFloatingDecoration(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 80.dp, y = 0.dp)
                .rotate(rotation * 0.3f),
            color = color,
            alpha = 0.08f,
            shape = steps[(page + 3) % steps.size].shape
        )
    }
}

@Composable
private fun ExpressiveFloatingDecoration(
    modifier: Modifier,
    color: Color,
    alpha: Float,
    shape: androidx.compose.ui.graphics.Shape
) {
    Box(
        modifier = modifier
            .size(140.dp)
            .graphicsLayer { 
                this.shape = shape
                this.clip = true
            }
            .background(color.copy(alpha = alpha))
    )
}

data class OnboardingStep(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val shape: androidx.compose.ui.graphics.Shape
)

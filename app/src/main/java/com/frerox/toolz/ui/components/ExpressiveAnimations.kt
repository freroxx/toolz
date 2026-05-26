package com.frerox.toolz.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.frerox.toolz.ui.theme.LocalPerformanceMode

@Composable
fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn(
        animationSpec = tween(durationMillis = 400, delayMillis = index * 60),
    ) + slideInVertically(
        initialOffsetY = { 32 },
        animationSpec = tween(durationMillis = 450, delayMillis = index * 60),
    ) + scaleIn(
        initialScale = 0.92f,
        animationSpec = tween(durationMillis = 350, delayMillis = index * 60),
    ),
    exit: ExitTransition = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.96f),
    content: @Composable () -> Unit,
) {
    val performanceMode = LocalPerformanceMode.current
    if (performanceMode) {
        Box(modifier = modifier) { content() }
        return
    }

    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        visibleState.targetState = true
    }

    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = enter,
        exit = exit,
    ) {
        content()
    }
}

package com.frerox.toolz.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.unit.IntOffset
import com.frerox.toolz.ui.theme.LocalPerformanceMode

@Composable
fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    spatialOffset: IntOffset = IntOffset(0, 32),
    enter: EnterTransition = fadeIn(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = 0.01f
        ),
    ) + slideInVertically(
        initialOffsetY = { spatialOffset.y },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
    ) + scaleIn(
        initialScale = 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
    ),
    exit: ExitTransition = fadeOut(
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    ) + scaleOut(
        targetScale = 0.96f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    ),
    content: @Composable () -> Unit,
) {
    val performanceMode = LocalPerformanceMode.current
    if (performanceMode) {
        Box(modifier = modifier) { content() }
        return
    }

    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        // Add a small initial delay for the first item to let the layout settle
        if (index == 0) kotlinx.coroutines.delay(50)
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

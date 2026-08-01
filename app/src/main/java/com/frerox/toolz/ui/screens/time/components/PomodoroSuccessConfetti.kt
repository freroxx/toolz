/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.ui.screens.time.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun PomodoroSuccessConfetti(
    onFinished: () -> Unit
) {
    val duration = 3000L
    val particleCount = 100
    
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration.toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    val particles = remember {
        List(particleCount) {
            ConfettiParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat() * -0.5f, // Start above the screen
                color = listOf(
                    Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0),
                    Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3),
                    Color(0xFF03A9F4), Color(0xFF00BCD4), Color(0xFF009688),
                    Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39),
                    Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800),
                    Color(0xFFFF5722)
                ).random(),
                size = Random.nextFloat() * 10f + 10f,
                speed = Random.nextFloat() * 2f + 1f,
                rotationSpeed = Random.nextFloat() * 360f,
                drift = (Random.nextFloat() - 0.5f) * 0.2f
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(duration)
        onFinished()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val globalAlpha = when {
            progress < 0.1f -> progress / 0.1f
            progress > 0.8f -> (1f - progress) / 0.2f
            else -> 1f
        }.coerceIn(0f, 1f)

        particles.forEach { particle ->
            val y = (particle.y + progress * particle.speed) % 1.5f
            if (y < 1.1f) {
                val x = (particle.x + progress * particle.drift) % 1.0f
                val particleAlpha = when {
                    y < 0f -> 0f
                    y < 0.1f -> y / 0.1f
                    y > 0.9f -> (1.1f - y) / 0.2f
                    else -> 1f
                }.coerceIn(0f, 1f) * globalAlpha

                if (particleAlpha > 0f) {
                    rotate(progress * particle.rotationSpeed, Offset(x * width, y * height)) {
                        drawRect(
                            color = particle.color.copy(alpha = particleAlpha),
                            topLeft = Offset(x * width, y * height),
                            size = androidx.compose.ui.geometry.Size(particle.size.dp.toPx(), (particle.size * 0.6f).dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

private data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val color: Color,
    val size: Float,
    val speed: Float,
    val rotationSpeed: Float,
    val drift: Float
)

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

package com.frerox.toolz.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.ui.screens.focus.CaffeinateViewModel
import kotlinx.coroutines.delay

@Composable
fun CaffeinatePopup(
    onDismiss: () -> Unit,
    onActivated: () -> Unit,
    viewModel: CaffeinateViewModel = hiltViewModel()
) {
    var isActivating by remember { mutableStateOf(false) }
    var activationProgress by remember { mutableFloatStateOf(0f) }

    val coffeeFillProgress by animateFloatAsState(
        targetValue = activationProgress,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "coffeeFill"
    )

    LaunchedEffect(isActivating) {
        if (isActivating) {
            activationProgress = 1f
            delay(1600)
            onActivated()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(enabled = !isActivating) { onDismiss() }
        )

        AnimatedContent(
            targetState = isActivating,
            transitionSpec = {
                (fadeIn(tween(400)) + scaleIn(spring(dampingRatio = Spring.DampingRatioLowBouncy))).togetherWith(
                    fadeOut(tween(300)) + scaleOut(targetScale = 1.1f)
                )
            },
            label = "popupContent"
        ) { activating ->
            if (activating) {
                ActivatingCoffeeCard(coffeeFillProgress)
            } else {
                CaffeinateOptionsCard(
                    onAuto = {
                        viewModel.setInfinite(true)
                        viewModel.toggleService()
                        isActivating = true
                    },
                    onManual = {
                        viewModel.setInfinite(false)
                        viewModel.setReminderInterval(60) // Default 1 hour for manual
                        viewModel.toggleService()
                        isActivating = true
                    }
                )
            }
        }
    }
}

@Composable
fun CaffeinateOptionsCard(
    onAuto: () -> Unit,
    onManual: () -> Unit
) {
    ExpressiveCard(
        onClick = {},
        modifier = Modifier
            .width(320.dp)
            .padding(16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Coffee,
                        null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Caffeinate",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            
            Text(
                text = "Keep your screen awake, with a cup of coffee",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ToolzExpressiveButton(
                    modifier = Modifier.weight(1f),
                    onClick = onAuto,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AllInclusive, null)
                        Text("Auto", fontWeight = FontWeight.Bold)
                        Text("Infinite", style = MaterialTheme.typography.labelSmall)
                    }
                }
                
                ToolzExpressiveButton(
                    modifier = Modifier.weight(1f),
                    onClick = onManual,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Timer, null)
                        Text("Manual", fontWeight = FontWeight.Bold)
                        Text("1 Hour", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun ActivatingCoffeeCard(progress: Float) {
    ExpressiveCard(
        onClick = {},
        modifier = Modifier
            .width(280.dp)
            .height(220.dp),
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.BottomCenter) {
                    // Empty cup / Background
                    Icon(
                        Icons.Rounded.Coffee,
                        null,
                        modifier = Modifier.fillMaxSize(),
                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                    )
                    
                    // Filling cup - using a clip and another icon
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(progress)
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    ) {
                        Icon(
                            Icons.Rounded.Coffee,
                            null,
                            modifier = Modifier.size(100.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "Brewing...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                ToolzWavyLinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(120.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                )
            }
        }
    }
}

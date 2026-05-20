package com.frerox.toolz.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.ui.screens.focus.CaffeinateViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaffeinatePopup(
    onDismiss: () -> Unit,
    onActivated: () -> Unit,
    viewModel: CaffeinateViewModel = hiltViewModel()
) {
    var isActivating by remember { mutableStateOf(false) }
    var activationProgress by remember { mutableStateOf(0f) }

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
                .clickableEnabled(enabled = !isActivating) { onDismiss() }
        )

        AnimatedContent(
            targetState = isActivating,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.9f)).togetherWith(fadeOut() + scaleOut(targetScale = 1.1f))
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
    ElevatedCard(
        modifier = Modifier
            .width(320.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                CaffeinateButton(
                    modifier = Modifier.weight(1f),
                    title = "Auto",
                    subtitle = "Infinite",
                    icon = Icons.Rounded.AllInclusive,
                    onClick = onAuto,
                    color = MaterialTheme.colorScheme.primary
                )
                
                CaffeinateButton(
                    modifier = Modifier.weight(1f),
                    title = "Manual",
                    subtitle = "Set Time",
                    icon = Icons.Rounded.Timer,
                    onClick = onManual,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun ActivatingCoffeeCard(progress: Float) {
    ElevatedCard(
        modifier = Modifier
            .width(280.dp)
            .height(200.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.BottomCenter) {
                    // Empty cup
                    Icon(
                        Icons.Rounded.Coffee,
                        null,
                        modifier = Modifier.fillMaxSize(),
                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                    )
                    
                    // Filling cup
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(progress)
                            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)) // Approximate cup bottom
                    ) {
                        Icon(
                            Icons.Rounded.Coffee,
                            null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Activating...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun CaffeinateButton(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    color: Color
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(24.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Extension to avoid error in the caller
private fun Modifier.clickableEnabled(enabled: Boolean, onClick: () -> Unit): Modifier = this.then(
    if (enabled) Modifier.clickable(onClick = onClick) else Modifier
)

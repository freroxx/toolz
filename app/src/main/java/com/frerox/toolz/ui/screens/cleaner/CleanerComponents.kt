package com.frerox.toolz.ui.screens.cleaner

import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.cleaner.StorageInfo
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager

// ═══════════════════════════════════════════════════════════════════════════════
// Storage Arc Indicator
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun StorageArcIndicator(
    storageInfo: StorageInfo,
    cleanableBytes: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val performanceMode = LocalPerformanceMode.current
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val tertiary = MaterialTheme.colorScheme.tertiary

    val total = storageInfo.totalBytes.coerceAtLeast(1L).toFloat()
    val usedFraction = (storageInfo.usedBytes.toFloat() / total).coerceIn(0f, 1f)
    val cleanableFraction = (cleanableBytes.toFloat() / total).coerceIn(0f, 1f)
    val usedNonCleanableFraction = (usedFraction - cleanableFraction).coerceAtLeast(0f)

    val animatedUsed by animateFloatAsState(
        targetValue = usedNonCleanableFraction,
        animationSpec = if (performanceMode) tween(300) else tween(1200, easing = FastOutSlowInEasing),
        label = "usedArc"
    )
    val animatedCleanable by animateFloatAsState(
        targetValue = cleanableFraction,
        animationSpec = if (performanceMode) tween(300) else tween(1200, 200, FastOutSlowInEasing),
        label = "cleanableArc"
    )

    Box(
        modifier = modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val strokeWidth = 28f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val startAngle = 135f
            val totalSweep = 270f

            // Background arc
            drawArc(
                color = surfaceVariant,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Used space arc
            drawArc(
                color = primary,
                startAngle = startAngle,
                sweepAngle = totalSweep * animatedUsed,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Cleanable space arc (red/warning)
            if (animatedCleanable > 0f) {
                drawArc(
                    color = error.copy(alpha = 0.8f),
                    startAngle = startAngle + totalSweep * animatedUsed,
                    sweepAngle = totalSweep * animatedCleanable,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Formatter.formatFileSize(context, storageInfo.usedBytes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "USED",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp
            )
            if (cleanableBytes > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${Formatter.formatFileSize(context, cleanableBytes)} cleanable",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Scanning Radar Animation
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ScanningRadar(
    modifier: Modifier = Modifier
) {
    val performanceMode = LocalPerformanceMode.current
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface

    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (performanceMode) 2000 else 1500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarSweep"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier.size(160.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        // Concentric circles
        for (i in 1..3) {
            drawCircle(
                color = primary.copy(alpha = 0.08f),
                radius = radius * i / 3f,
                center = center,
                style = Stroke(width = 1.5f)
            )
        }

        // Pulse ring
        drawCircle(
            color = primary.copy(alpha = pulseAlpha),
            radius = radius * 0.95f,
            center = center,
            style = Stroke(width = 3f)
        )

        // Sweep line
        rotate(rotation, pivot = center) {
            // Gradient sweep
            drawArc(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.7f to Color.Transparent,
                    0.85f to primary.copy(alpha = 0.15f),
                    1f to primary.copy(alpha = 0.4f),
                    center = center
                ),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )

            // Leading line
            drawLine(
                color = primary,
                start = center,
                end = Offset(center.x + radius * 0.9f, center.y),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
        }

        // Center dot
        drawCircle(
            color = primary,
            radius = 5f,
            center = center
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// File Stream Ticker — rapid-fire path display
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun FileStreamTicker(
    currentPath: String,
    filesScanned: Int,
    modifier: Modifier = Modifier
) {
    val displayPath = remember(currentPath) {
        if (currentPath.length > 60) "…" + currentPath.takeLast(58) else currentPath
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "SCANNING",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
            Text(
                "$filesScanned files",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            displayPath,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Slide to Clean Button
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SlideToCleanButton(
    cleanableBytes: Long,
    onClean: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val density = LocalDensity.current
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var trackWidth by remember { mutableFloatStateOf(1f) }
    val thumbSize = with(density) { 56.dp.toPx() }
    val maxDrag = (trackWidth - thumbSize).coerceAtLeast(0f)
    val progress = if (maxDrag > 0) (dragOffset / maxDrag).coerceIn(0f, 1f) else 0f
    val isComplete = progress >= 0.95f

    val animatedTrackColor by animateColorAsState(
        targetValue = if (isComplete) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        animationSpec = tween(300),
        label = "trackColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(animatedTrackColor)
            .pointerInput(Unit) {
                trackWidth = size.width.toFloat()
                detectHorizontalDragGestures(
                    onDragStart = {
                        trackWidth = size.width.toFloat()
                    },
                    onDragEnd = {
                        if (isComplete) {
                            vibrationManager?.vibrateLongClick()
                            onClean()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffset = (dragOffset + dragAmount).coerceIn(0f, maxDrag)
                        if (progress > 0.3f && progress < 0.31f) {
                            vibrationManager?.vibrateClick()
                        }
                        if (progress > 0.6f && progress < 0.61f) {
                            vibrationManager?.vibrateClick()
                        }
                        if (isComplete) {
                            vibrationManager?.vibrateClick()
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Label
        Text(
            if (isComplete) "RELEASE TO CLEAN" else "SLIDE TO CLEAN · ${Formatter.formatFileSize(context, cleanableBytes)}",
            modifier = Modifier.fillMaxWidth().alpha(1f - progress * 0.7f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = if (isComplete) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )

        // Thumb
        Box(
            modifier = Modifier
                .offset(x = with(density) { dragOffset.toDp() })
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (isComplete) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isComplete) Icons.Rounded.Check else Icons.Rounded.ChevronRight,
                contentDescription = "Clean",
                tint = if (isComplete) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Permission Education Dialog
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PermissionEducationDialog(
    onGrantClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        title = {
            Text(
                "Full Storage Access",
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "To find duplicates and detect leftover files across your entire device, File Cleaner needs access to all files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow(Icons.Rounded.Shield, "Your files are never uploaded")
                        Spacer(Modifier.height(8.dp))
                        InfoRow(Icons.Rounded.Speed, "All scanning happens locally")
                        Spacer(Modifier.height(8.dp))
                        InfoRow(Icons.Rounded.Delete, "Only files YOU select are deleted")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onGrantClick,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("GRANT ACCESS", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("NOT NOW", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
            }
        }
    )
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Scan Stats Row
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ScanStatsRow(
    filesScanned: Int,
    duplicatesFound: Int,
    corpsesFound: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatChip(label = "SCANNED", value = "$filesScanned", MaterialTheme.colorScheme.primary)
        StatChip(label = "DUPLICATES", value = "$duplicatesFound", MaterialTheme.colorScheme.error)
        StatChip(label = "CORPSES", value = "$corpsesFound", MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            fontSize = 9.sp
        )
    }
}

package com.frerox.toolz.ui.screens.cleaner

import android.text.format.Formatter
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.frerox.toolz.data.cleaner.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager

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
    val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    val total = storageInfo.totalBytes.coerceAtLeast(1L).toFloat()
    val usedFraction = (storageInfo.usedBytes.toFloat() / total).coerceIn(0f, 1f)
    val cleanableFraction = (cleanableBytes.toFloat() / total).coerceIn(0f, 1f)
    val usedNonCleanableFraction = (usedFraction - cleanableFraction).coerceAtLeast(0f)

    val animatedUsed by animateFloatAsState(
        targetValue = usedNonCleanableFraction,
        animationSpec = if (performanceMode) tween(300) else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "usedArc"
    )
    val animatedCleanable by animateFloatAsState(
        targetValue = cleanableFraction,
        animationSpec = if (performanceMode) tween(300) else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "cleanableArc"
    )

    Box(
        modifier = modifier.size(300.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!performanceMode) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(primary.copy(alpha = 0.12f), Color.Transparent)
                        ),
                        CircleShape
                    )
            )
        }

        Canvas(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            val strokeWidth = 36f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val startAngle = 135f
            val totalSweep = 270f

            drawArc(
                color = outline,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = primary,
                startAngle = startAngle,
                sweepAngle = totalSweep * animatedUsed,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            if (animatedCleanable > 0f) {
                drawArc(
                    color = error,
                    startAngle = startAngle + totalSweep * animatedUsed,
                    sweepAngle = totalSweep * animatedCleanable,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Formatter.formatFileSize(context, storageInfo.usedBytes),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "STORAGE OCCUPIED",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp
            )
            
            AnimatedVisibility(
                visible = cleanableBytes > 0,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.AutoDelete, 
                            null, 
                            modifier = Modifier.size(14.dp), 
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${Formatter.formatFileSize(context, cleanableBytes)} CLEANABLE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveScanningIndicator(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val performanceMode = LocalPerformanceMode.current
    val infiniteTransition = rememberInfiniteTransition(label = "expressive_scan")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val strokeWidth by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "strokeWidth"
    )

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 30f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweepAngle"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(260.dp)) {
        if (!performanceMode) {
            for (i in 0..2) {
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.4f + (i * 0.2f),
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000 + (i * 500), easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "pulse_$i"
                )
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.15f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000 + (i * 500), easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "alpha_$i"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(pulseScale)
                        .alpha(pulseAlpha)
                        .background(primary, CircleShape)
                )
            }
        }

        Canvas(modifier = Modifier.size(200.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            
            drawCircle(
                color = primary.copy(alpha = 0.05f),
                radius = radius,
                center = center,
                style = Stroke(width = 8.dp.toPx())
            )

            rotate(rotation) {
                drawArc(
                    color = primary,
                    startAngle = 0f,
                    sweepAngle = if (performanceMode) 270f else sweepAngle,
                    useCenter = false,
                    style = Stroke(
                        width = if (performanceMode) 8.dp.toPx() else strokeWidth.dp.toPx(), 
                        cap = StrokeCap.Round
                    ),
                    size = Size(radius * 2, radius * 2),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )
            }
        }

        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryCard(
    category: CleanCategory,
    onToggleItem: (String) -> Unit,
    onToggleDuplicate: (String, String) -> Unit,
    onOpenFile: (String) -> Unit,
    onLongPress: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            expanded = !expanded 
                        },
                        onLongClick = {
                            vibrationManager?.vibrateLongClick()
                            onLongPress()
                        }
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            if (category.totalSize > 0) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getIconForName(category.icon),
                        contentDescription = null,
                        tint = if (category.totalSize > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
                
                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        category.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (category.totalSize > 0) Formatter.formatFileSize(context, category.totalSize) else "Optimized",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (category.totalSize > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (category.isSafeToClean && category.totalSize > 0) {
                    Icon(
                        Icons.Rounded.Verified, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary, 
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                }

                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = if (performanceMode) fadeIn() else expandVertically() + fadeIn(),
                exit = if (performanceMode) fadeOut() else shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, start = 12.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val displayItems = remember(category.items, performanceMode) {
                        category.items.take(if (performanceMode) 5 else 8)
                    }
                    
                    displayItems.forEach { item ->
                        key(item) {
                            when (item) {
                                is CleanItem.GenericFile -> GenericFileItem(item.file, onToggleItem, onOpenFile)
                                is CleanItem.Corpse -> CorpseItem(item.entry, onToggleItem, onOpenFile)
                                is CleanItem.Duplicate -> DuplicateGroupItem(item.group, onToggleDuplicate, onOpenFile)
                                is CleanItem.UnusedApp -> UnusedAppItem(item.entry, onToggleItem)
                            }
                        }
                    }
                    
                    if (category.items.size > displayItems.size) {
                        Button(
                            onClick = onLongPress,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("VIEW ALL ${category.items.size} ITEMS", fontWeight = FontWeight.Black, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileThumbnail(file: FileEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isVideo = remember(file.extension) {
        file.extension.lowercase() in listOf("mp4", "mkv", "avi", "mov", "webm", "flv")
    }
    val icon = getIconForExtension(file.extension)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file.thumbnailUri ?: file.path)
                    .crossfade(true)
                    .apply {
                        if (isVideo) decoderFactory(VideoFrameDecoder.Factory())
                    }
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                }
            )
            
            if (isVideo) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun GenericFileItem(file: FileEntry, onToggle: (String) -> Unit, onOpenFile: (String) -> Unit) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { 
                vibrationManager?.vibrateClick()
                onOpenFile(file.path) 
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = file.isSelected,
            onCheckedChange = { 
                vibrationManager?.vibrateClick()
                onToggle(file.path) 
            }
        )
        
        FileThumbnail(file, modifier = Modifier.size(48.dp))

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${Formatter.formatFileSize(context, file.sizeBytes)} • ${file.extension.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }

        IconButton(onClick = { 
            vibrationManager?.vibrateClick()
            onOpenFile(file.path) 
        }) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CorpseItem(corpse: CorpseEntry, onToggle: (String) -> Unit, onOpenFile: (String) -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { 
                vibrationManager?.vibrateClick()
                onOpenFile(corpse.path) 
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = corpse.isSelected, 
            onCheckedChange = { 
                vibrationManager?.vibrateClick()
                onToggle(corpse.path) 
            }
        )
        
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.FolderDelete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                corpse.packageName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${Formatter.formatFileSize(LocalContext.current, corpse.sizeBytes)} • ${corpse.type.name} Folder",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun UnusedAppItem(entry: UnusedAppEntry, onToggle: (String) -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    val context = LocalContext.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { 
                vibrationManager?.vibrateClick()
                onToggle(entry.packageName) 
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = entry.isSelected, 
            onCheckedChange = { 
                vibrationManager?.vibrateClick()
                onToggle(entry.packageName) 
            }
        )
        
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = entry.icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.appName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${Formatter.formatFileSize(context, entry.sizeBytes)} • Last used ${formatLastUsed(entry.lastUsed)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatLastUsed(time: Long): String {
    if (time == 0L) return "Long ago"
    val diff = System.currentTimeMillis() - time
    val days = diff / (24 * 60 * 60 * 1000)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        days < 30 -> "$days days ago"
        else -> "${days / 30} months ago"
    }
}

@Composable
private fun DuplicateGroupItem(group: DuplicateGroup, onToggle: (String, String) -> Unit, onOpenFile: (String) -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    val firstFile = group.files.firstOrNull()
    val fileName = firstFile?.path?.substringAfterLast('/') ?: "Unknown"
    val extension = fileName.substringAfterLast('.', "").lowercase()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            firstFile?.let {
                FileThumbnail(FileEntry(fileName, it.path, group.sizeBytes, it.lastModified, extension), modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${group.files.size} Duplicates found", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        group.files.forEachIndexed { index, file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { 
                        vibrationManager?.vibrateClick()
                        onToggle(group.hash, file.path) 
                    }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = file.isSelected,
                    onCheckedChange = { 
                        vibrationManager?.vibrateClick()
                        onToggle(group.hash, file.path) 
                    },
                    modifier = Modifier.scale(0.85f)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        file.path.substringAfterLast('/'),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (index == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        if (index == 0) "Original Version" else "Extra Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
                IconButton(onClick = { 
                    vibrationManager?.vibrateClick()
                    onOpenFile(file.path) 
                }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Visibility, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun Modifier.scale(scale: Float) = this.then(Modifier.size((24 * scale).dp))

@Composable
fun CleaningProgressIndicator(progress: Float) {
    val performanceMode = LocalPerformanceMode.current
    val primary = MaterialTheme.colorScheme.primary
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (performanceMode) tween(300) else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "cleaningProgress"
    )
    
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
        if (!performanceMode) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = primary.copy(alpha = 0.05f),
                    radius = (size.minDimension / 2f) * (0.8f + (animatedProgress * 0.2f)),
                    center = center
                )
            }
        }

        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxSize().padding(12.dp),
            strokeWidth = 14.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize().padding(12.dp),
            strokeWidth = 14.dp,
            color = primary,
            strokeCap = StrokeCap.Round
        )
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${(animatedProgress * 100).toInt()}%",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black),
                color = primary
            )
            Text(
                "DELETING JUNK",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
    var trackWidth by remember { mutableFloatStateOf(0f) }
    
    val thumbSizeDp = 64.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val horizontalPaddingPx = with(density) { 6.dp.toPx() }
    
    val maxDrag = (trackWidth - thumbSizePx - (horizontalPaddingPx * 2)).coerceAtLeast(0f)
    val progress = if (maxDrag > 0) (dragOffset / maxDrag).coerceIn(0f, 1f) else 0f
    val isComplete = progress >= 0.98f

    LaunchedEffect(isComplete) {
        if (isComplete) {
            vibrationManager?.vibrateLongClick()
            onClean()
            dragOffset = 0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .onSizeChanged { trackWidth = it.width.toFloat() }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), CircleShape)
            .pointerInput(trackWidth, maxDrag) {
                if (trackWidth <= 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (progress < 0.98f) dragOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffset = (dragOffset + dragAmount).coerceIn(0f, maxDrag)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            "SLIDE TO FREE ${Formatter.formatFileSize(context, cleanableBytes)}".uppercase(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 80.dp)
                .alpha(1f - progress),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Surface(
            modifier = Modifier
                .offset(x = with(density) { (dragOffset + horizontalPaddingPx).toDp() })
                .size(thumbSizeDp)
                .padding(4.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun SectionGridView(
    category: CleanCategory,
    onToggleItem: (String) -> Unit,
    onToggleDuplicate: (String, String) -> Unit,
    onOpenFile: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(category.items, key = { item ->
                when (item) {
                    is CleanItem.GenericFile -> "file_${item.file.path}"
                    is CleanItem.Corpse -> "corpse_${item.entry.path}"
                    is CleanItem.Duplicate -> "dupe_${item.group.hash}"
                    is CleanItem.UnusedApp -> "app_${item.entry.packageName}"
                }
            }) { item ->
                GridItem(
                    item = item,
                    onToggleItem = onToggleItem,
                    onToggleDuplicate = onToggleDuplicate,
                    onOpenFile = onOpenFile
                )
            }
        }
    }
}

@Composable
private fun GridItem(
    item: CleanItem,
    onToggleItem: (String) -> Unit,
    onToggleDuplicate: (String, String) -> Unit,
    onOpenFile: (String) -> Unit
) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    
    val isSelected = when (item) {
        is CleanItem.GenericFile -> item.file.isSelected
        is CleanItem.Corpse -> item.entry.isSelected
        is CleanItem.Duplicate -> item.group.files.any { it.isSelected }
        is CleanItem.UnusedApp -> item.entry.isSelected
    }

    val path = when (item) {
        is CleanItem.GenericFile -> item.file.path
        is CleanItem.Corpse -> item.entry.path
        is CleanItem.Duplicate -> item.group.files.firstOrNull { it.isSelected }?.path ?: item.group.files.firstOrNull()?.path ?: ""
        is CleanItem.UnusedApp -> item.entry.packageName
    }
    
    val ext = if (item is CleanItem.UnusedApp) "apk" else path.substringAfterLast('.', "").lowercase()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        onClick = {
            vibrationManager?.vibrateClick()
            when (item) {
                is CleanItem.GenericFile -> onToggleItem(item.file.path)
                is CleanItem.Corpse -> onToggleItem(item.entry.path)
                is CleanItem.UnusedApp -> onToggleItem(item.entry.packageName)
                is CleanItem.Duplicate -> {
                     val toToggle = item.group.files.find { it.isSelected } ?: item.group.files.lastOrNull()
                     toToggle?.let { onToggleDuplicate(item.group.hash, it.path) }
                }
            }
        },
        border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item is CleanItem.UnusedApp) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                     AsyncImage(model = item.entry.icon, contentDescription = null, modifier = Modifier.size(56.dp))
                }
            } else {
                val fileEntry = when(item) {
                    is CleanItem.GenericFile -> item.file
                    is CleanItem.Duplicate -> {
                        val f = item.group.files.first()
                        FileEntry(f.path.substringAfterLast('/'), f.path, item.group.sizeBytes, f.lastModified, ext)
                    }
                    else -> FileEntry("", path, 0, 0, ext)
                }
                FileThumbnail(fileEntry, modifier = Modifier.fillMaxSize())
            }

            if (isSelected) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)))
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                    .padding(10.dp)
            ) {
                Text(
                    when (item) {
                        is CleanItem.GenericFile -> item.file.name
                        is CleanItem.Corpse -> item.entry.packageName
                        is CleanItem.UnusedApp -> item.entry.appName
                        is CleanItem.Duplicate -> item.group.files.firstOrNull()?.path?.substringAfterLast('/') ?: "Duplicate"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when (item) {
                        is CleanItem.GenericFile -> Formatter.formatFileSize(context, item.file.sizeBytes)
                        is CleanItem.Corpse -> Formatter.formatFileSize(context, item.entry.sizeBytes)
                        is CleanItem.UnusedApp -> Formatter.formatFileSize(context, item.entry.sizeBytes)
                        is CleanItem.Duplicate -> Formatter.formatFileSize(context, item.group.sizeBytes)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }

            if (item !is CleanItem.UnusedApp) {
                IconButton(
                    onClick = { 
                        vibrationManager?.vibrateClick()
                        onOpenFile(path) 
                    },
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(36.dp)
                ) {
                    Icon(Icons.Rounded.Visibility, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun PermissionEducationDialog(onGrantClick: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.FolderSpecial, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) },
        title = { Text("Storage Access Required", fontWeight = FontWeight.Bold) },
        text = { 
            Text(
                "To perform a deep scan and find hidden junk, Toolz requires permission to access all files. Your privacy is guaranteed.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ) 
        },
        confirmButton = { 
            Button(
                onClick = onGrantClick,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("GRANT PERMISSION", fontWeight = FontWeight.Bold) } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { 
                Text("NOT NOW", color = MaterialTheme.colorScheme.secondary)
            } 
        },
        shape = RoundedCornerShape(28.dp)
    )
}

private fun getIconForName(name: String): ImageVector {
    return when (name) {
        "DeleteSweep" -> Icons.Rounded.DeleteSweep
        "FileCopy" -> Icons.Rounded.FileCopy
        "AutoDelete" -> Icons.Rounded.AutoDelete
        "Straighten" -> Icons.Rounded.Straighten
        "FolderOff" -> Icons.Rounded.FolderOff
        "AppSettingsAlt" -> Icons.Rounded.AppSettingsAlt
        "Description" -> Icons.Rounded.Description
        else -> Icons.Rounded.Folder
    }
}

private fun getIconForExtension(ext: String): ImageVector {
    return when (ext.lowercase()) {
        "pdf" -> Icons.Rounded.PictureAsPdf
        "mp3", "wav", "m4a", "ogg", "flac" -> Icons.Rounded.Audiotrack
        "jpg", "jpeg", "png", "gif", "webp", "bmp" -> Icons.Rounded.Image
        "mp4", "mkv", "avi", "mov", "webm" -> Icons.Rounded.Movie
        "zip", "rar", "7z", "tar" -> Icons.Rounded.FolderZip
        "apk" -> Icons.Rounded.Android
        "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> Icons.Rounded.Description
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }
}

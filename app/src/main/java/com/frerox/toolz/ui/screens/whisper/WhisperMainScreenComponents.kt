package com.frerox.toolz.ui.screens.whisper

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.ui.components.bouncyClick

/**
 * P0-6b FIX: Extracted from WhisperMainScreen.kt (2925 → ~2600) — first real split.
 * These helpers were all private in MainScreen and are now shared to reduce god-file size.
 * Next PR moves DiscoverTab/ProfileTab bodies here.
 */
@Composable
fun WhisperAvatar(
    profile: WhisperProfile,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    var isImageError by remember(profile.avatarUrl) { mutableStateOf(false) }
    val baseModifier = modifier
        .size(size)
        .clip(CircleShape)
        .then(
            when {
                onClick != null && onLongClick != null -> Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                onClick != null -> Modifier.bouncyClick(onClick = onClick)
                onLongClick != null -> Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
                else -> Modifier
            }
        )
    if (!profile.avatarUrl.isNullOrBlank() && !isImageError) {
        AsyncImage(model = profile.avatarUrl, contentDescription = profile.effectiveName, contentScale = ContentScale.Crop, onError = { isImageError = true }, modifier = baseModifier)
    } else {
        Box(modifier = baseModifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))), contentAlignment = Alignment.Center) {
            Text(profile.avatarInitial, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Black, fontSize = (size.value * 0.42f).sp)
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp))
}

@Composable
fun WhisperEmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun ConversationSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 0.7f, animationSpec = infiniteRepeatable(animation = tween(900, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "shimmerAlpha")
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = shimmerAlpha)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = shimmerAlpha * 0.5f)))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(modifier = Modifier.width(100.dp).height(14.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.outline.copy(alpha = shimmerAlpha * 0.5f)))
                    Box(modifier = Modifier.width(40.dp).height(10.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.outline.copy(alpha = shimmerAlpha * 0.3f)))
                }
                Box(modifier = Modifier.fillMaxWidth(0.8f).height(10.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.outline.copy(alpha = shimmerAlpha * 0.3f)))
            }
        }
    }
}

@Composable
fun FriendSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 0.7f, animationSpec = infiniteRepeatable(animation = tween(900, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "shimmerAlpha")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp).padding(vertical = 4.dp).alpha(shimmerAlpha)) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.width(40.dp).height(8.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))
    }
}

@Composable
fun DiscoverSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 0.7f, animationSpec = infiniteRepeatable(animation = tween(900, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "shimmerAlpha")
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = shimmerAlpha)) {
        Column {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = shimmerAlpha * 0.5f)))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.width(120.dp).height(14.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.outline.copy(alpha = shimmerAlpha * 0.5f)))
                    Box(modifier = Modifier.width(80.dp).height(10.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.outline.copy(alpha = shimmerAlpha * 0.3f)))
                }
            }
            Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f).height(36.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))
                Box(modifier = Modifier.weight(1f).height(36.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))
            }
        }
    }
}

@Composable
fun String.formatTimestamp(): String {
    val yesterday = stringResource(R.string.st_Whisper_Chat_Yesterday)
    return try {
        val dt = java.time.OffsetDateTime.parse(this)
        val local = dt.atZoneSameInstant(java.time.ZoneId.systemDefault())
        val now = java.time.ZonedDateTime.now()
        val days = java.time.temporal.ChronoUnit.DAYS.between(local.toLocalDate(), now.toLocalDate())
        when {
            days == 0L -> "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
            days == 1L -> yesterday
            days < 7L -> local.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            else -> "${local.dayOfMonth}/${local.monthValue}"
        }
    } catch (_: Exception) { "" }
}

internal fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = this.read(buffer)
        if (read < 0) break
        total += read
        require(total <= maxBytes) { "Avatar file is too large." }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

internal const val MAX_AVATAR_READ_BYTES = 10 * 1024 * 1024

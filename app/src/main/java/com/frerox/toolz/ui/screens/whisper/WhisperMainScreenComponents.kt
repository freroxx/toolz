package com.frerox.toolz.ui.screens.whisper

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperPresence
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.ui.components.bouncyClick

/**
 * P0-6b FIX: Extracted from WhisperMainScreen.kt (2925 → ~2600) — first real split.
 * These helpers were all private in MainScreen and are now shared to reduce god-file size.
 * Next PR moves DiscoverTab/ProfileTab bodies here.
 */

/** L-15 FIX (reviewwhisper.md): single online-indicator color instead of three literals. */
internal val WhisperOnlineGreen = Color(0xFF4CAF50)

/**
 * V3-FIX (item 8a): localized presence labels replacing the hardcoded English
 * "Online" string compares. Reuses the existing st_Whisper_Online resource;
 * RECENT / OFFLINE / UNKNOWN have dedicated keys added to all four locales.
 */
@Composable
internal fun whisperPresenceLabel(presence: WhisperPresence): String = when (presence) {
    WhisperPresence.ONLINE -> stringResource(R.string.st_Whisper_Online)
    WhisperPresence.RECENT -> stringResource(R.string.st_Whisper_Presence_Recent)
    WhisperPresence.OFFLINE -> stringResource(R.string.st_Whisper_Presence_Offline)
    WhisperPresence.UNKNOWN -> stringResource(R.string.st_Whisper_Presence_Unknown)
}

/**
 * L-16 FIX (reviewwhisper.md): cache-buster derived from the profile's server-side
 * updatedAt — avatars are persisted WITHOUT a ?t= buster anymore (a persisted buster
 * defeats every other viewer's cache). Pass bust=true only where staleness matters
 * right after an edit (own hero avatar / own full-screen view).
 */
internal fun whisperAvatarModel(profile: WhisperProfile, bust: Boolean = false): String? {
    val url = profile.avatarUrl ?: return null
    if (!bust || profile.updatedAt.isBlank()) return url
    return "$url?t=${profile.updatedAt.hashCode()}"
}

/**
 * M-16 FIX (reviewwhisper.md): shared row composable for ALL Whisper option sheets.
 * MainScreen's ChatOptionsSheet and ChatScreen's ConversationOptionsSheet had drifted
 * apart (divergent labels/tints); both now render through this single component.
 */
@Composable
fun WhisperOptionsListItem(
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    // V2-FIX M-H4: async-resolved rows (e.g. block state) render disabled/indeterminate
    // until their state lands instead of flashing a wrong default.
    enabled: Boolean = true,
) {
    ListItem(
        leadingContent = { Icon(leadingIcon, null, tint = iconTint) },
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(label, fontWeight = FontWeight.Medium, color = labelColor)
    }
}
@Composable
fun WhisperAvatar(
    profile: WhisperProfile,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    bustCache: Boolean = false,
) {
    val resolvedUrl = whisperAvatarModel(profile, bust = bustCache)
    var isImageError by remember(resolvedUrl) { mutableStateOf(false) }
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
    if (!resolvedUrl.isNullOrBlank() && !isImageError) {
        AsyncImage(model = resolvedUrl, contentDescription = profile.effectiveName, contentScale = ContentScale.Crop, onError = { isImageError = true }, modifier = baseModifier)
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
    // V2-FIX M-M7: timestamps are now localized (device locale via SHORT date/time styles)
    // and parsed once per timestamp value instead of on every recomposition.
    return remember(this, yesterday) {
        try {
            val dt = java.time.OffsetDateTime.parse(this)
            val local = dt.atZoneSameInstant(java.time.ZoneId.systemDefault())
            val now = java.time.ZonedDateTime.now()
            val days = java.time.temporal.ChronoUnit.DAYS.between(local.toLocalDate(), now.toLocalDate())
            val locale = java.util.Locale.getDefault()
            when {
                days == 0L ->
                    local.format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(locale))
                days == 1L -> yesterday
                days < 7L -> local.format(java.time.format.DateTimeFormatter.ofPattern("EEE", locale))
                else ->
                    local.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT).withLocale(locale))
            }
        } catch (_: Exception) { "" }
    }
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

/** Decodes a bitmap downsampled so its pixel count stays within [maxWidth]x[maxHeight]. */
internal fun decodeBoundedBitmap(bytes: ByteArray, maxWidth: Int, maxHeight: Int): android.graphics.Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxWidth || bounds.outHeight / (sample * 2) >= maxHeight) {
        sample *= 2
    }
    return android.graphics.BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    )
}

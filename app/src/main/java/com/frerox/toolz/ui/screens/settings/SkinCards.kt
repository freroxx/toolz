/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import com.frerox.toolz.data.skin.SkinDto
import com.frerox.toolz.ui.components.ToolzMarkThemed

/** Localized theme-mode word for skin summaries ("system", "light", "dark"). */
@Composable
internal fun themeModeLabel(mode: String): String = when (mode.uppercase()) {
    "LIGHT" -> stringResource(R.string.st_AppSkin_theme_light)
    "DARK" -> stringResource(R.string.st_AppSkin_theme_dark)
    else -> stringResource(R.string.st_AppSkin_theme_system)
}

/** Localized "dynamic" / "custom" word for skin summaries. */
@Composable
internal fun dynamicWord(dynamic: Boolean): String = stringResource(
    if (dynamic) R.string.st_AppSkin_mode_dynamic else R.string.st_AppSkin_mode_custom
)

/** Localized icon-frame name ("Octagon" / "Circle"). */
@Composable
internal fun iconShapeLabel(shape: String): String = when (
    com.frerox.toolz.data.skin.IconShape.parse(shape)
) {
    com.frerox.toolz.data.skin.IconShape.CIRCLE -> stringResource(R.string.st_AppSkin_shape_circle)
    else -> stringResource(R.string.st_AppSkin_shape_octagon)
}

/** Localized icon-catalog tag ("Vivid", "Dark", …). Unknown tags render as-is. */
@Composable
internal fun iconTagLabel(tag: String): String = when (tag) {
    "Adaptive" -> stringResource(R.string.st_AppSkin_tag_adaptive)
    "Vivid" -> stringResource(R.string.st_AppSkin_tag_vivid)
    "Dark" -> stringResource(R.string.st_AppSkin_tag_dark)
    "Light" -> stringResource(R.string.st_AppSkin_tag_light)
    "Mono" -> stringResource(R.string.st_AppSkin_tag_mono)
    "Legacy" -> stringResource(R.string.st_AppSkin_tag_legacy)
    "All" -> stringResource(R.string.st_AppSkin_tag_all)
    else -> tag
}

/**
 * Shared skin rows/cards: community banner ("made by …") + optional review status.
 * Used by App Skin, Skins Catalog and onboarding so they never drift apart.
 */

@Composable
fun SubmissionStatusChip(status: String?) {
    val labelRes = when (status) {
        "published" -> R.string.st_AppSkin_status_live
        "archived", "removed" -> R.string.st_AppSkin_status_rejected
        else -> R.string.st_AppSkin_status_pending
    }
    val (container, content) = when (status) {
        "published" -> Color(0xFF0C7E78).copy(alpha = 0.15f) to Color(0xFF0C7E78)
        "archived", "removed" -> Color.Gray.copy(alpha = 0.15f) to Color.Gray
        else -> Color(0xFFFF8A3D).copy(alpha = 0.15f) to Color(0xFFCC5A17)
    }
    Surface(shape = RoundedCornerShape(10.dp), color = container) {
        Text(
            stringResource(labelRes),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = content,
        )
    }
}

@Composable
fun SkinRowCard(
    skin: SkinDto,
    selected: Boolean,
    isDark: Boolean,
    dynamicColor: Boolean,
    schemePrimary: Color,
    schemeSecondary: Color,
    reviewStatus: String? = null,
    showStatus: Boolean = false,
    iconGradient: Boolean = true,
    onApply: () -> Unit,
) {
    Surface(
        onClick = onApply,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolzMarkThemed(
                    presetId = skin.iconPresetId,
                    isDark = isDark,
                    dynamicColor = skin.dynamicColor,
                    scheme = MaterialTheme.colorScheme,
                    shape = skin.iconShape,
                    adaptToTheme = skin.iconAdaptTheme,
                    followDynamic = skin.iconFollowDynamic,
                    iconGradient = iconGradient,
                    modifier = Modifier.size(54.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(skin.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                        if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        "${themeModeLabel(skin.themeMode)} · ${dynamicWord(skin.dynamicColor)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    // Community banner.
                    Text(
                        stringResource(R.string.st_AppSkin_made_by, skin.author?.takeIf { it.isNotBlank() } ?: "Toolz"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val dotPrimary = skin.primary?.let { Color(it.toInt()) } ?: schemePrimary
                        val dotSecondary = skin.secondary?.let { Color(it.toInt()) } ?: schemeSecondary
                        Box(Modifier.size(18.dp).clip(CircleShape).background(dotPrimary))
                        Box(Modifier.size(18.dp).clip(CircleShape).background(dotSecondary))
                    }
                    if (showStatus) SubmissionStatusChip(reviewStatus)
                }
            }
        }
    }
}

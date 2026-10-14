/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.skin.IconCatalog
import com.frerox.toolz.data.skin.LegacySkins
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExtraLargeExpressiveShape
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzMarkThemed
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.horizontalFadingEdges

/**
 * Onboarding App Skin step: pick an icon preset + optional skin.
 * Skippable, polished, offline-first (legacy skins bundled).
 */
@Composable
fun StyleStep(
    state: OnboardingViewModel.OnboardingState,
    onIconPresetChange: (String) -> Unit,
    onIconShapeChange: (String) -> Unit,
    onIconAdaptChange: (Boolean) -> Unit,
    onIconFollowChange: (Boolean) -> Unit,
    onSkinApply: (com.frerox.toolz.data.skin.SkinDto) -> Unit,
    onNext: () -> Unit,
) {
    val scroll = rememberScrollState()
    val scheme = MaterialTheme.colorScheme
    val isDark = when (state.themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(scroll).fadingEdges(top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        StaggeredEntrance(index = 0) {
            SectionHeader(eyebrow = "Style", title = "Make Toolz yours")
        }
        Spacer(Modifier.height(8.dp))
        StaggeredEntrance(index = 1) {
            Text(
                "Pick an icon + skin. You can change everything later in Settings → App Skin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(20.dp))
        StaggeredEntrance(index = 2) {
            ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CardEyebrow("App icon")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.fillMaxWidth().horizontalFadingEdges(left = 10.dp, right = 10.dp),
                    ) {
                        items(IconCatalog.BUILT_IN) { spec ->
                            val sel = spec.id == state.iconPresetId
                            Surface(
                                onClick = { onIconPresetChange(spec.id) },
                                shape = RoundedCornerShape(18.dp),
                                color = if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = if (sel) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.size(72.dp),
                            ) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                                    ToolzMarkThemed(
                                        presetId = spec.id,
                                        isDark = isDark,
                                        dynamicColor = state.dynamicColor,
                                        scheme = scheme,
                                        shape = state.iconShape,
                                        adaptToTheme = state.iconAdaptTheme,
                                        followDynamic = state.iconFollowDynamic,
                                        modifier = Modifier.size(52.dp),
                                    )
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("OCTAGON", "CIRCLE").forEach { s ->
                            val sel = com.frerox.toolz.data.skin.IconShape.parse(state.iconShape) == com.frerox.toolz.data.skin.IconShape.parse(s)
                            Surface(
                                onClick = { onIconShapeChange(s) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.weight(1f).height(40.dp),
                            ) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Text(s, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    OnboardingToggleExpressive(
                        title = "Adapt to theme",
                        desc = "Dark / light variants",
                        icon = Icons.Rounded.Palette,
                        checked = state.iconAdaptTheme,
                        onCheckedChange = onIconAdaptChange,
                    )
                    OnboardingToggleExpressive(
                        title = "Follow dynamic colors",
                        desc = "Match wallpaper when Dynamic color is on",
                        icon = Icons.Rounded.Palette,
                        checked = state.iconFollowDynamic,
                        onCheckedChange = onIconFollowChange,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        StaggeredEntrance(index = 3) {
            ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardEyebrow("Toolz Skins · ${LegacySkins.ALL.size} included")
                    LegacySkins.ALL.take(4).forEach { skin ->
                        val sel = skin.id == state.activeSkinId
                        Surface(
                            onClick = { onSkinApply(skin) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = if (sel) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ToolzMarkThemed(
                                    presetId = skin.iconPresetId,
                                    isDark = isDark,
                                    dynamicColor = skin.dynamicColor,
                                    scheme = scheme,
                                    shape = skin.iconShape,
                                    adaptToTheme = skin.iconAdaptTheme,
                                    followDynamic = skin.iconFollowDynamic,
                                    modifier = Modifier.size(44.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(skin.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "made by ${skin.author?.takeIf { it.isNotBlank() } ?: "Toolz"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                    )
                                    Text(skin.description ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        StaggeredEntrance(index = 4) {
            ToolzExpressiveButton(onClick = onNext, modifier = Modifier.fillMaxWidth().height(60.dp), shape = ExtraLargeExpressiveShape) {
                Text("Continue", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

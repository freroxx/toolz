/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.skin.IconCatalog
import com.frerox.toolz.data.skin.SkinDto
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzMarkThemed
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.theme.LocalVibrationManager
import kotlinx.coroutines.launch

/**
 * APP SKIN section: icon presets + appearance tweaks + community skins.
 * Polished, searchable, works offline (legacy presets always present).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSkinSection(
    viewModel: SettingsViewModel,
    searchQuery: String,
    themeMode: String,
    dynamicColor: Boolean,
) {
    val vibrationManager = LocalVibrationManager.current
    val scope = rememberCoroutineScope()

    val presetId by viewModel.appIconPresetId.collectAsState(initial = "grape-haze")
    val shape by viewModel.appIconShape.collectAsState(initial = "HEXAGON")
    val adaptTheme by viewModel.appIconAdaptTheme.collectAsState(initial = true)
    val followDynamic by viewModel.appIconFollowDynamic.collectAsState(initial = true)
    val bgOv by viewModel.appIconBgOverride.collectAsState(initial = null)
    val fgOv by viewModel.appIconFgOverride.collectAsState(initial = null)
    val strokeOv by viewModel.appIconStrokeOverride.collectAsState(initial = null)
    val iconGradient by viewModel.appIconGradientEnabled.collectAsState(initial = true)
    val activeSkinId by viewModel.activeSkinId.collectAsState(initial = null)
    val skins by viewModel.skins.collectAsState(initial = emptyList())
    val syncing by viewModel.skinsSyncing.collectAsState(initial = false)

    var showCatalog by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var catalogTag by remember { mutableStateOf("All") }
    var catalogQuery by remember { mutableStateOf("") }

    val isDark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { viewModel.refreshSkins(false) }

    if (matches(searchQuery, "skin", "app skin", "icon", "icons", "catalog", "appearance", "theme pack", "preset")) {
        SettingsItem(
            title = "Current look",
            subtitle = "${IconCatalog.find(presetId).name} · ${shape.lowercase()} · ${activeSkinId ?: "custom"}",
            icon = Icons.Rounded.Palette,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ToolzMarkThemed(
                    presetId = presetId,
                    isDark = isDark,
                    dynamicColor = dynamicColor,
                    schemePrimary = scheme.primary,
                    schemeSecondary = scheme.secondary,
                    shape = shape,
                    adaptToTheme = adaptTheme,
                    followDynamic = followDynamic,
                    bgOverride = bgOv,
                    fgOverride = fgOv,
                    strokeOverride = strokeOv,
                    modifier = Modifier.size(84.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolzExpressiveButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            showCatalog = true
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Rounded.GridView, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Icon Catalog", fontWeight = FontWeight.Black)
                    }
                    Text(
                        "${IconCatalog.BUILT_IN.size} presets · launcher uses new mark, in-app follows these settings",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            // Quick strip: first 6 presets
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(IconCatalog.BUILT_IN.take(8)) { spec ->
                    val selected = spec.id == presetId
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                        Surface(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                viewModel.setAppIconPresetId(spec.id)
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            ),
                            modifier = Modifier.size(60.dp).bouncyClick {},
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                ToolzMarkThemed(
                                    presetId = spec.id,
                                    isDark = isDark,
                                    dynamicColor = dynamicColor,
                                    schemePrimary = scheme.primary,
                                    schemeSecondary = scheme.secondary,
                                    shape = shape,
                                    adaptToTheme = adaptTheme,
                                    followDynamic = followDynamic,
                                    modifier = Modifier.size(44.dp),
                                )
                            }
                        }
                        Text(
                            spec.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
                            maxLines = 1,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (matches(searchQuery, "icon", "adapt", "theme", "dynamic", "material", "wallpaper", "shape", "hexagon", "circle", "pill")) {
        SettingsToggleItem(
            title = "Adapt to theme",
            subtitle = "Icon shifts for dark / light mode (uses theme + preset variants)",
            icon = Icons.Rounded.Palette,
            checked = adaptTheme,
            onCheckedChange = { viewModel.setAppIconAdaptTheme(it) },
        )
        SettingsToggleItem(
            title = "Follow dynamic colors",
            subtitle = if (dynamicColor) "Icon accents follow wallpaper (Material You)" else "Turn on Dynamic color above to enable — otherwise icon uses theme + primary / secondary",
            icon = Icons.Rounded.Palette,
            checked = followDynamic,
            onCheckedChange = { viewModel.setAppIconFollowDynamic(it) },
            enabled = dynamicColor,
        )
        SettingsItem(
            title = "Icon shape",
            subtitle = "Current: ${shape.lowercase()}",
            icon = Icons.Rounded.GridView,
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("HEXAGON", "CIRCLE", "PILL").forEach { s ->
                    val sel = shape == s
                    Surface(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.setAppIconShape(s)
                        },
                        modifier = Modifier.weight(1f).height(46.dp).bouncyClick {},
                        shape = RoundedCornerShape(14.dp),
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(s, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        SettingsToggleItem(
            title = "Icon gradient",
            subtitle = "Soft gradient on presets that support it",
            icon = Icons.Rounded.Palette,
            checked = iconGradient,
            onCheckedChange = { viewModel.setAppIconGradientEnabled(it) },
        )
        if (bgOv != null || fgOv != null || strokeOv != null) {
            SettingsItem(
                title = "Custom icon colors",
                subtitle = "Tap reset to return to preset",
                icon = Icons.Rounded.RestartAlt,
                onClick = {
                    vibrationManager?.vibrateClick()
                    viewModel.clearIconOverrides()
                },
            )
        }
    }

    if (matches(searchQuery, "skin", "skins", "community", "share", "upload", "theme pack")) {
        SettingsItem(
            title = "Toolz Skins",
            subtitle = "${skins.size} available ${if (syncing) "· syncing…" else ""}",
            icon = Icons.Rounded.Palette,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                skins.take(6).forEach { skin ->
                    SkinRow(
                        skin = skin,
                        selected = skin.id == activeSkinId,
                        isDark = isDark,
                        dynamicColor = dynamicColor,
                        schemePrimary = scheme.primary,
                        schemeSecondary = scheme.secondary,
                        onApply = {
                            vibrationManager?.vibrateClick()
                            viewModel.applySkin(skin)
                        },
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolzExpressiveButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            scope.launch { viewModel.refreshSkins(true) }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Text("Refresh", fontWeight = FontWeight.Black)
                    }
                    ToolzExpressiveButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            showShare = true
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("Share my look", fontWeight = FontWeight.Black)
                    }
                }
                Text(
                    "Skins change theme + colors + icon together. Shared skins go to review, then appear for everyone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }

    if (showCatalog) {
        IconCatalogSheet(
            currentId = presetId,
            shape = shape,
            isDark = isDark,
            dynamicColor = dynamicColor,
            schemePrimary = scheme.primary,
            schemeSecondary = scheme.secondary,
            adaptToTheme = adaptTheme,
            followDynamic = followDynamic,
            tag = catalogTag,
            onTagChange = { catalogTag = it },
            query = catalogQuery,
            onQueryChange = { catalogQuery = it },
            onPick = {
                viewModel.setAppIconPresetId(it)
                showCatalog = false
            },
            onDismiss = { showCatalog = false },
        )
    }

    if (showShare) {
        ShareSkinDialog(
            onDismiss = { showShare = false },
            onSubmit = { name ->
                viewModel.submitCurrentAsSkin(name) { }
                showShare = false
            },
        )
    }
}

@Composable
private fun SkinRow(
    skin: SkinDto,
    selected: Boolean,
    isDark: Boolean,
    dynamicColor: Boolean,
    schemePrimary: Color,
    schemeSecondary: Color,
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
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolzMarkThemed(
                presetId = skin.iconPresetId,
                isDark = isDark,
                dynamicColor = skin.dynamicColor,
                schemePrimary = schemePrimary,
                schemeSecondary = schemeSecondary,
                shape = skin.iconShape,
                adaptToTheme = skin.iconAdaptTheme,
                followDynamic = skin.iconFollowDynamic,
                modifier = Modifier.size(52.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(skin.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                    if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
                Text(
                    "${skin.themeMode.lowercase()} · ${if (skin.dynamicColor) "dynamic" else "custom"} · ${skin.author ?: "community"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            // Theme dots
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val dotPrimary = skin.primary?.let { Color(it.toInt()) } ?: schemePrimary
                val dotSecondary = skin.secondary?.let { Color(it.toInt()) } ?: schemeSecondary
                Box(Modifier.size(18.dp).clip(CircleShape).background(dotPrimary))
                Box(Modifier.size(18.dp).clip(CircleShape).background(dotSecondary))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconCatalogSheet(
    currentId: String,
    shape: String,
    isDark: Boolean,
    dynamicColor: Boolean,
    schemePrimary: Color,
    schemeSecondary: Color,
    adaptToTheme: Boolean,
    followDynamic: Boolean,
    tag: String,
    onTagChange: (String) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Icon Catalog", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                "${IconCatalog.BUILT_IN.size} presets · tap to apply in-app instantly",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search icons…") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                ),
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IconCatalog.tags) { t ->
                    val sel = t == tag
                    Surface(
                        onClick = { onTagChange(t) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.bouncyClick {},
                    ) {
                        Text(
                            t,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val filtered = IconCatalog.BUILT_IN.filter {
                (tag == "All" || it.tag == tag) && (query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().height(420.dp),
            ) {
                items(filtered, key = { it.id }) { spec ->
                    val selected = spec.id == currentId
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = { onPick(spec.id) },
                            shape = RoundedCornerShape(22.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            ),
                            modifier = Modifier.size(96.dp).bouncyClick {},
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(12.dp)) {
                                ToolzMarkThemed(
                                    presetId = spec.id,
                                    isDark = isDark,
                                    dynamicColor = dynamicColor,
                                    schemePrimary = schemePrimary,
                                    schemeSecondary = schemeSecondary,
                                    shape = shape,
                                    adaptToTheme = adaptToTheme,
                                    followDynamic = followDynamic,
                                    modifier = Modifier.size(72.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            spec.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Text(spec.tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareSkinDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Share your look", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    "We'll package your theme + colors + icon as a community skin. Our team reviews it before it goes live.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    placeholder = { Text("e.g. Midnight Grape") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
                    androidx.compose.material3.Button(
                        onClick = { if (name.trim().length >= 3) onSubmit(name.trim()) },
                        enabled = name.trim().length >= 3,
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Submit", fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

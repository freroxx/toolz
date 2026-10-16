/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.R
import com.frerox.toolz.data.skin.IconCatalog
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzMarkThemed
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.horizontalFadingEdges
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch

/**
 * Full-screen Skins Catalog: search, filter, preview, apply, share.
 * Mirrors IconCatalogScreen so the two catalogs feel like one family.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinsCatalogScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    val scope = rememberCoroutineScope()

    val themeMode by viewModel.themeMode.collectAsState(initial = "SYSTEM")
    val dynamicColor by viewModel.dynamicColor.collectAsState(initial = true)
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
    val userName by viewModel.userName.collectAsState(initial = "")
    val mySubmissions by viewModel.mySubmissions.collectAsState(initial = emptyList())
    val submissionStatuses by viewModel.submissionStatuses.collectAsState(initial = emptyMap())

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var showShare by remember { mutableStateOf(false) }
    var shareResult by remember { mutableStateOf<Result<String>?>(null) }

    val isDark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { viewModel.refreshSkins(false) }

    val filters = listOf("All", "Toolz", "Community", "Light", "Dark", "Dynamic")
    val filterLabels = mapOf(
        "All" to stringResource(R.string.st_AppSkin_tag_all),
        "Toolz" to stringResource(R.string.st_AppSkin_filter_toolz),
        "Community" to stringResource(R.string.st_AppSkin_filter_community),
        "Light" to stringResource(R.string.st_AppSkin_tag_light),
        "Dark" to stringResource(R.string.st_AppSkin_tag_dark),
        "Dynamic" to stringResource(R.string.st_AppSkin_mode_dynamic_cap),
    )
    val filtered = skins.filter { skin ->
        val matchesQuery = query.isBlank() ||
            skin.name.contains(query, true) ||
            (skin.author ?: "").contains(query, true)
        if (!matchesQuery) return@filter false
        when (filter) {
            "Toolz" -> skin.author == "Toolz"
            "Community" -> skin.author != "Toolz"
            "Light" -> skin.themeMode == "LIGHT"
            "Dark" -> skin.themeMode == "DARK"
            "Dynamic" -> skin.dynamicColor
            else -> true
        }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_AppSkin_catalog_skins_title),
                subtitle = stringResource(R.string.st_AppSkin_skins_sub, skins.size) +
                    if (syncing) stringResource(R.string.st_AppSkin_syncing) else "",
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_SettingsScreen_u1v2))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                largeFlexible = true,
                modifier = Modifier.statusBarsPadding(),
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.st_AppSkin_search_skins)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                ),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                modifier = Modifier.fillMaxWidth().horizontalFadingEdges(left = 12.dp, right = 12.dp),
            ) {
                items(filters) { f ->
                    val sel = f == filter
                    Surface(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            filter = f
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.bouncyClick {},
                    ) {
                        Text(
                            filterLabels[f] ?: f,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().fadingEdges(top = 12.dp, bottom = 12.dp).padding(bottom = 24.dp),
            ) {
                if (mySubmissions.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.st_AppSkin_header_mine),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                        )
                    }
                    items(mySubmissions, key = { "mine-${it.id}" }) { sub ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sub.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        stringResource(
                                            R.string.st_AppSkin_made_by,
                                            userName.ifBlank {
                                                stringResource(R.string.st_AppSkin_made_by_you)
                                            },
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                    )
                                }
                                SubmissionStatusChip(submissionStatuses[sub.id])
                            }
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.st_AppSkin_header_community),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                        )
                    }
                }
                items(filtered, key = { it.id }) { skin ->
                    SkinRowCard(
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
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolzExpressiveButton(
                            onClick = { scope.launch { viewModel.refreshSkins(true) } },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Text(stringResource(R.string.st_FocusFlowScreen_7c4d), fontWeight = FontWeight.Black)
                        }
                        ToolzExpressiveButton(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                showShare = true
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text(stringResource(R.string.st_AppSkin_share), fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showShare) {
        var name by remember { mutableStateOf("") }
        var author by remember(userName) { mutableStateOf(userName) }
        var submitting by remember { mutableStateOf(false) }
        androidx.compose.ui.window.Dialog(onDismissRequest = { if (!submitting) showShare = false }) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.st_AppSkin_share_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    // Live preview of exactly what gets packaged.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        val current = IconCatalog.find(presetId)
                        ToolzMarkThemed(
                            presetId = presetId,
                            isDark = isDark,
                            dynamicColor = dynamicColor,
                            scheme = scheme,
                            shape = shape,
                            adaptToTheme = adaptTheme,
                            followDynamic = followDynamic,
                            bgOverride = bgOv,
                            fgOverride = fgOv,
                            strokeOverride = strokeOv,
                            iconGradient = iconGradient,
                            modifier = Modifier.size(64.dp),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${themeModeLabel(themeMode)} · ${
                                    if (dynamicColor) stringResource(R.string.st_AppSkin_mode_dynamic)
                                    else stringResource(R.string.st_AppSkin_mode_custom_colors)
                                }",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${current.name} · ${iconShapeLabel(shape)}" +
                                    if (iconGradient) " · ${stringResource(R.string.st_AppSkin_gradient_word)}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.st_AppSkin_share_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(32) },
                        placeholder = { Text(stringResource(R.string.st_AppSkin_share_name_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it.take(32) },
                        placeholder = { Text(stringResource(R.string.st_AppSkin_share_author_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(onClick = { showShare = false }, enabled = !submitting) { Text(stringResource(R.string.st_Common_Cancel)) }
                        androidx.compose.material3.Button(
                            onClick = {
                                if (name.trim().length >= 3 && !submitting) {
                                    submitting = true
                                    viewModel.submitCurrentAsSkin(name.trim(), author.trim()) { r ->
                                        shareResult = r
                                        submitting = false
                                        showShare = false
                                    }
                                }
                            },
                            enabled = name.trim().length >= 3 && !submitting,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (submitting) R.string.st_AppSkin_sharing
                                    else R.string.st_AppSkin_submit
                                ),
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }
        }
    }

    shareResult?.let { r ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { shareResult = null }) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val dupe = r.exceptionOrNull() as? com.frerox.toolz.data.skin.DuplicateSkinException
                    Text(
                        stringResource(
                            if (r.isSuccess) R.string.st_AppSkin_shared_ok
                            else if (dupe != null) R.string.st_AppSkin_shared_dup
                            else R.string.st_AppSkin_shared_fail
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (r.isSuccess) stringResource(R.string.st_AppSkin_shared_pending_short)
                        else if (dupe != null) stringResource(R.string.st_AppSkin_shared_dup_msg, dupe.existingName)
                        else (r.exceptionOrNull()?.message ?: stringResource(R.string.st_AppSkin_shared_err)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.Button(
                        onClick = { shareResult = null },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.st_AiAssistantScreen_u7v9), fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

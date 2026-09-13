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

package com.frerox.toolz.ui.screens.ai

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.frerox.toolz.R
import com.frerox.toolz.data.ai.*
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiSettingsScreen(
    viewModel: AiAssistantViewModel = hiltViewModel(),
    onNavigateToBrowser: (String) -> Unit = {},
    onBack: () -> Unit,
) {
    val settingsState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val performanceMode = LocalPerformanceMode.current
    val vibration = LocalVibrationManager.current

    val configIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.updateCustomIcon(it) }
    }

    val handleBack = {
        vibration?.vibrateClick()
        viewModel.cancelConfigSwitch()
        viewModel.discardSettingsDraft()
        onBack()
    }

    BackHandler(onBack = handleBack)

    AiSettingsContent(
        state = settingsState,
        savedConfigs = uiState.savedConfigs,
        pendingConfig = uiState.pendingConfig,
        onBack = handleBack,
        onProviderChange = viewModel::updateProvider,
        onApiKeyChange = viewModel::updateApiKey,
        onModelChange = viewModel::updateModel,
        onIconChange = viewModel::updateIcon,
        onCustomIconClick = { configIconPicker.launch("image/*") },
        onSave = {
            viewModel.onSettingsSaveRequest()
            if (viewModel.uiState.value.pendingConfig == null &&
                viewModel.settingsUiState.value.showNoKeyWarningFor == null
            ) {
                onBack()
            }
        },
        onSaveConfig = viewModel::saveConfig,
        onDeleteConfig = viewModel::deleteConfig,
        onEditConfig = viewModel::editConfig,
        onMoveConfig = viewModel::moveConfig,
        onTest = viewModel::testConnection,
        performanceMode = performanceMode,
        onToggleDynamicPrompts = viewModel::toggleDynamicPrompts,
        onPromptFormatChange = viewModel::updatePromptFormat,
        aiSearchIconVisible = uiState.aiSearchIconVisible,
        onSetAiSearchIconVisible = viewModel::setAiSearchIconVisible,
        catalogVersion = settingsState.catalogVersion,
        catalogUpdatedAt = settingsState.catalogUpdatedAt,
        isRefreshingCatalog = settingsState.isRefreshingCatalog,
        catalogRefreshResult = settingsState.catalogRefreshResult,
        onRefreshCatalog = viewModel::refreshCatalog,
        // Identity callbacks
        onSelectIdentity = viewModel::selectIdentity,
        onShowCustomIdentityEditor = viewModel::showCustomIdentityEditor,
        onDismissCustomIdentityEditor = viewModel::dismissCustomIdentityEditor,
        onSaveCustomIdentity = viewModel::saveCustomIdentity,
        onDeleteCustomIdentity = viewModel::deleteCustomIdentity,
        // Switch / Key dialogs
        onConfirmConfigSwitch = { viewModel.confirmConfigSwitch(); onBack() },
        onCancelConfigSwitch = viewModel::cancelConfigSwitch,
        onConfirmSaveWithoutKey = { viewModel.confirmSaveWithoutKey(); if (viewModel.uiState.value.pendingConfig == null) onBack() },
        onDismissNoKeyWarning = viewModel::dismissNoKeyWarning,
        onNavigateToBrowser = onNavigateToBrowser,
        showGroqKeyMissingDialog = settingsState.showGroqKeyMissingDialog,
        onDismissGroqDialog = viewModel::dismissGroqDialog,
        onSaveGroqKey = viewModel::saveGroqKey,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiSettingsContent(
    state: AiSettingsUiState,
    savedConfigs: List<AiConfig>,
    pendingConfig: AiConfig?,
    onBack: () -> Unit,
    onProviderChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onCustomIconClick: () -> Unit,
    onSave: () -> Unit,
    onSaveConfig: (String) -> Unit,
    onDeleteConfig: (AiConfig) -> Unit,
    onEditConfig: (AiConfig) -> Unit,
    @Suppress("UNUSED_PARAMETER") onMoveConfig: (Int, Int) -> Unit,
    onTest: () -> Unit,
    performanceMode: Boolean,
    onToggleDynamicPrompts: (Boolean) -> Unit,
    onPromptFormatChange: (String) -> Unit,
    aiSearchIconVisible: Boolean,
    onSetAiSearchIconVisible: (Boolean) -> Unit,
    catalogVersion: Int?,
    catalogUpdatedAt: String?,
    isRefreshingCatalog: Boolean,
    catalogRefreshResult: String?,
    onRefreshCatalog: () -> Unit,
    // Identity
    onSelectIdentity: (String) -> Unit,
    onShowCustomIdentityEditor: (AiIdentity?) -> Unit,
    onDismissCustomIdentityEditor: () -> Unit,
    onSaveCustomIdentity: (String, String) -> Unit,
    onDeleteCustomIdentity: (String) -> Unit,
    // Dialogs
    onConfirmConfigSwitch: () -> Unit,
    onCancelConfigSwitch: () -> Unit,
    onConfirmSaveWithoutKey: () -> Unit,
    onDismissNoKeyWarning: () -> Unit,
    onNavigateToBrowser: (String) -> Unit,
    showGroqKeyMissingDialog: Boolean,
    onDismissGroqDialog: () -> Unit,
    onSaveGroqKey: (String) -> Unit,
) {
    var configName by remember(state.editingConfig) { mutableStateOf(state.editingConfig?.name ?: "") }
    var showConfigSave by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var activeTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.st_AiSettings_title),
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    ToolzExpressiveButton(
                        onClick = onSave,
                        shape = MediumExpressiveShape,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.st_AiSettings_save), fontWeight = FontWeight.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AiDesign.surfaceColor()
                )
            )
        },
        containerColor = AiDesign.surfaceColor()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab switch: Configuration vs Presets
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ToolzConnectedButtonGroup(
                    selectedIndex = activeTab,
                    options = listOf(
                        stringResource(R.string.st_AiAssistantScreen_k7l8),
                        stringResource(R.string.st_AiAssistantScreen_m9n0)
                    ),
                    onOptionSelected = { activeTab = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    if (targetState > initialState)
                        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                    else
                        (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                },
                label = "settingsTab",
                modifier = Modifier.weight(1f)
            ) { tab ->
                if (tab == 0) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        item(key = "provider") {
                            SettingsProviderRow(state.provider, onProviderChange)
                        }

                        item(key = "model") {
                            SettingsModelSection(
                                selectedModel = state.selectedModel,
                                provider = state.provider,
                                modelAvailability = state.modelAvailability,
                                showModelMenu = showModelMenu,
                                onModelChange = onModelChange,
                                onShowModelMenuChange = { showModelMenu = it }
                            )
                        }

                        item(key = "apiKey") {
                            SettingsApiKeySection(
                                apiKey = state.apiKey,
                                provider = state.provider,
                                onApiKeyChange = onApiKeyChange,
                                onShowTutorial = { showTutorial = true }
                            )
                        }

                        item(key = "identity") {
                            SettingsIdentitySection(
                                selectedIdentityId = state.selectedIdentityId,
                                customIdentities = state.customIdentities,
                                onSelectIdentity = onSelectIdentity,
                                onAddCustomIdentity = { onShowCustomIdentityEditor(null) },
                                onEditCustomIdentity = onShowCustomIdentityEditor,
                                onDeleteCustomIdentity = onDeleteCustomIdentity,
                            )
                        }

                        item(key = "prompts") {
                            SettingsPromptsSection(
                                dynamicPromptsEnabled = state.dynamicPromptsEnabled,
                                promptFormat = state.promptFormat,
                                onToggleDynamicPrompts = onToggleDynamicPrompts,
                                onPromptFormatChange = onPromptFormatChange
                            )
                        }

                        item(key = "advanced") {
                            SettingsAdvancedSection(
                                aiSearchIconVisible = aiSearchIconVisible,
                                onSetAiSearchIconVisible = onSetAiSearchIconVisible,
                                catalogVersion = catalogVersion,
                                catalogUpdatedAt = catalogUpdatedAt,
                                isRefreshingCatalog = isRefreshingCatalog,
                                catalogRefreshResult = catalogRefreshResult,
                                onRefreshCatalog = onRefreshCatalog,
                            )
                        }

                        item(key = "test_and_save") {
                            SettingsTestSaveSection(
                                isTesting = state.isTesting,
                                testResult = state.testResult,
                                onTest = onTest,
                                onShowConfigSave = { showConfigSave = true },
                                editingConfig = state.editingConfig
                            )
                        }

                        item(key = "preset_edit") {
                            AnimatedVisibility(visible = showConfigSave || state.editingConfig != null) {
                                SettingsPresetEditSection(
                                    configName = configName,
                                    onConfigNameChange = { configName = it },
                                    selectedIcon = state.selectedIcon,
                                    onIconChange = onIconChange,
                                    customIconUri = state.customIconUri,
                                    onCustomIconClick = onCustomIconClick,
                                    provider = state.provider,
                                    onSaveConfig = {
                                        onSaveConfig(it)
                                        showConfigSave = false
                                    }
                                )
                            }
                        }

                        item(key = "bottom_spacer") {
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                } else {
                    SettingsPresetsList(
                        savedConfigs = savedConfigs,
                        onEditConfig = onEditConfig,
                        onDeleteConfig = onDeleteConfig,
                        onActiveTabChange = { activeTab = it }
                    )
                }
            }
        }
    }

    // Dialogs & Sheets
    if (showTutorial) {
        GuideDialog(onDismiss = { showTutorial = false })
    }

    if (state.showCustomIdentityEditor) {
        CustomIdentityDialog(
            initialIdentity = state.editingIdentity,
            onDismiss = onDismissCustomIdentityEditor,
            onSave = onSaveCustomIdentity,
        )
    }

    if (showGroqKeyMissingDialog) {
        GroqKeyRequiredDialog(
            onDismiss = onDismissGroqDialog,
            onSave = onSaveGroqKey,
            onGetLink = { onNavigateToBrowser("https://console.groq.com/keys") }
        )
    }

    if (pendingConfig != null) {
        ModernAiDialog(
            title = "Apply new settings?",
            icon = Icons.Rounded.Tune,
            iconColor = MaterialTheme.colorScheme.primary,
            description = "Switch to ${pendingConfig.provider} • ${pendingConfig.model}?",
            supportingText = "The current chat keeps its original settings. A fresh chat starts with the new ones — stored keys are never deleted.",
            primaryButtonText = "APPLY & NEW CHAT",
            onPrimaryClick = onConfirmConfigSwitch,
            secondaryButtonText = "KEEP EDITING",
            onSecondaryClick = onCancelConfigSwitch,
            onDismiss = onCancelConfigSwitch,
        )
    }

    if (state.showNoKeyWarningFor != null) {
        ModernAiDialog(
            title = "No API key",
            icon = Icons.Rounded.VpnKey,
            iconColor = MaterialTheme.colorScheme.error,
            description = "No API key set for ${state.showNoKeyWarningFor}.",
            supportingText = "Chats on this provider will fail until you add one. Paste a key in the field above, or save anyway and add it later.",
            primaryButtonText = "SAVE ANYWAY",
            onPrimaryClick = onConfirmSaveWithoutKey,
            secondaryButtonText = "GO BACK",
            onSecondaryClick = onDismissNoKeyWarning,
            onDismiss = onDismissNoKeyWarning,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsProviderRow(
    currentProvider: String,
    onProviderChange: (String) -> Unit
) {
    SettingsSection(stringResource(R.string.st_AiAssistantScreen_s5t6)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AiSettingsHelper.providers) { p ->
                val isSelected = currentProvider == p
                val pColor = AiDesign.providerColor(p) ?: MaterialTheme.colorScheme.primary
                ExpressiveFilterChip(
                    selected = isSelected, onClick = { onProviderChange(p) },
                    label = { Text(p, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium) },
                    leadingIcon = { Icon(getIconForConfig("AUTO", p), null, Modifier.size(14.dp)) },
                    shape = MediumExpressiveShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = pColor.copy(alpha = 0.15f),
                        selectedLabelColor = pColor,
                        selectedLeadingIconColor = pColor,
                    ),
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected, selectedBorderColor = pColor.copy(alpha = 0.3f)),
                )
            }
        }
    }
}

@Composable
private fun SettingsModelSection(
    selectedModel: String,
    provider: String,
    modelAvailability: ModelAvailability,
    showModelMenu: Boolean,
    onModelChange: (String) -> Unit,
    onShowModelMenuChange: (Boolean) -> Unit
) {
    SettingsSection(stringResource(R.string.st_AiAssistantScreen_u7v8)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                Surface(
                    onClick = { onShowModelMenuChange(true) },
                    shape = MediumExpressiveShape,
                    color = AiDesign.glassColor(),
                    border = BorderStroke(1.dp, AiDesign.glassBorder())
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Text(selectedModel, fontWeight = FontWeight.Bold, color = AiDesign.textColor())
                        Icon(Icons.Rounded.UnfoldMore, null, tint = AiDesign.textColor(0.6f))
                    }
                }
                DropdownMenu(
                    expanded = showModelMenu,
                    onDismissRequest = { onShowModelMenuChange(false) },
                    containerColor = AiDesign.cardColor(),
                    shape = LargeExpressiveShape
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                AiSettingsHelper.getProviderDescription(provider),
                                style = MaterialTheme.typography.labelSmall,
                                color = AiDesign.textColor(0.55f),
                            )
                        },
                        onClick = {},
                        enabled = false,
                    )
                    HorizontalDivider(color = AiDesign.glassBorder())
                    AiSettingsHelper.getModels(provider).forEach { m ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(m, modifier = Modifier.weight(1f, fill = false))
                                    if (AiSettingsHelper.isFreeModel(provider, m)) {
                                        Surface(shape = CircleShape, color = Color(0xFF4CAF50).copy(alpha = 0.15f)) {
                                            Text("FREE", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32), fontSize = 9.sp)
                                        }
                                    }
                                    if (m == AiSettingsHelper.getRecommendedModel(provider)) {
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                                            Text("FAST", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp)
                                        }
                                    }
                                }
                            },
                            onClick = { onModelChange(m); onShowModelMenuChange(false) },
                            trailingIcon = if (m == selectedModel) {
                                { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                            } else null,
                        )
                    }
                }
            }

            // Custom Model Input
            OutlinedTextField(
                value = selectedModel,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.st_AiAssistantScreen_w9x0)) },
                shape = MediumExpressiveShape,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AiDesign.glassColor(),
                    unfocusedContainerColor = AiDesign.glassColor(),
                    unfocusedBorderColor = AiDesign.glassBorder(),
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                trailingIcon = {
                    AnimatedContent(targetState = modelAvailability, label = "modelAvailability") { availability ->
                        when (availability) {
                            ModelAvailability.AVAILABLE -> Icon(Icons.Rounded.CheckCircle, stringResource(R.string.st_AiAssistantScreen_a1b3), tint = Color(0xFF4CAF50))
                            ModelAvailability.UNAVAILABLE -> Icon(Icons.Rounded.Error, stringResource(R.string.st_AiAssistantScreen_c3d5), tint = MaterialTheme.colorScheme.error)
                            ModelAvailability.CHECKING -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            ModelAvailability.UNKNOWN -> {}
                        }
                    }
                },
                supportingText = {
                    Text(
                        when (modelAvailability) {
                            ModelAvailability.AVAILABLE -> stringResource(R.string.st_AiAssistantScreen_a1b3)
                            ModelAvailability.UNAVAILABLE -> stringResource(R.string.st_AiAssistantScreen_c3d5)
                            ModelAvailability.CHECKING -> stringResource(R.string.st_AiAssistantScreen_e5f7)
                            ModelAvailability.UNKNOWN -> ""
                        },
                        color = when (modelAvailability) {
                            ModelAvailability.AVAILABLE -> Color(0xFF4CAF50)
                            ModelAvailability.UNAVAILABLE -> MaterialTheme.colorScheme.error
                            else -> AiDesign.textColor(0.5f)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun SettingsApiKeySection(
    apiKey: String,
    provider: String,
    onApiKeyChange: (String) -> Unit,
    onShowTutorial: () -> Unit
) {
    val context = LocalContext.current
    var isKeyMasked by remember { mutableStateOf(true) }

    SettingsSection(stringResource(R.string.st_AiAssistantScreen_g7h9)) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            shape = MediumExpressiveShape,
            visualTransformation = if (isKeyMasked) PasswordVisualTransformation() else VisualTransformation.None,
            placeholder = { Text(AiSettingsHelper.getApiKeyPlaceholder(provider), color = AiDesign.textColor(0.3f)) },
            trailingIcon = {
                if (apiKey.isNotEmpty()) {
                    IconButton(onClick = { onApiKeyChange("") }) {
                        Icon(Icons.Rounded.Close, null, tint = AiDesign.textColor(0.5f))
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AiDesign.glassColor(),
                unfocusedContainerColor = AiDesign.glassColor(),
                unfocusedBorderColor = AiDesign.glassBorder(),
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Button to hide/show API key next to Guide
            TextButton(
                onClick = { isKeyMasked = !isKeyMasked },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = if (isKeyMasked) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = if (isKeyMasked) stringResource(R.string.st_AiSettings_show_key) else stringResource(R.string.st_AiSettings_hide_key),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isKeyMasked) stringResource(R.string.st_AiSettings_show_key) else stringResource(R.string.st_AiSettings_hide_key),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            TextButton(onShowTutorial) {
                Text(
                    stringResource(R.string.st_AiAssistantScreen_i9j1),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AiSettingsHelper.getApiKeyUrl(provider))))
            }) {
                Text(
                    stringResource(R.string.st_AiAssistantScreen_e1f2),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Identity Section (System Prompt Persona)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsIdentitySection(
    selectedIdentityId: String,
    customIdentities: List<AiIdentity>,
    onSelectIdentity: (String) -> Unit,
    onAddCustomIdentity: () -> Unit,
    onEditCustomIdentity: (AiIdentity) -> Unit,
    onDeleteCustomIdentity: (String) -> Unit,
) {
    val allIdentities = remember(customIdentities) {
        AiSettingsHelper.builtInIdentities + customIdentities
    }

    var expandedIdentityId by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(width = 18.dp, height = 3.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape))
            Text(
                stringResource(R.string.st_AiSettings_identity_title),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )
        }
        Text(
            stringResource(R.string.st_AiSettings_identity_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = AiDesign.textColor(0.6f)
        )

        Spacer(Modifier.height(4.dp))

        // Identity cards list
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            allIdentities.forEach { identity ->
                val isSelected = identity.id == selectedIdentityId
                val isExpanded = expandedIdentityId == identity.id
                val hasPrompt = identity.prompt.isNotBlank()

                Surface(
                    onClick = { onSelectIdentity(identity.id) },
                    shape = MediumExpressiveShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else AiDesign.glassColor(),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else AiDesign.glassBorder()
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectIdentity(identity.id) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = AiDesign.textColor(0.4f)
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = identity.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else AiDesign.textColor()
                                )
                                if (hasPrompt && !isExpanded) {
                                    Text(
                                        text = identity.prompt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AiDesign.textColor(0.55f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Expand/collapse chevron
                            if (hasPrompt) {
                                IconButton(
                                    onClick = {
                                        expandedIdentityId = if (isExpanded) null else identity.id
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                                        contentDescription = "Toggle prompt details",
                                        tint = AiDesign.textColor(0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Edit/delete for custom identities
                            if (!identity.isBuiltIn) {
                                IconButton(
                                    onClick = { onEditCustomIdentity(identity) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        contentDescription = "Edit identity",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteCustomIdentity(identity.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete identity",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Expanded full prompt view
                        AnimatedVisibility(
                            visible = isExpanded && hasPrompt,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Surface(
                                shape = SmallExpressiveShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, start = 36.dp)
                            ) {
                                Text(
                                    text = identity.prompt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AiDesign.textColor(0.8f),
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add custom identity button
        TextButton(
            onClick = onAddCustomIdentity,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.st_AiSettings_add_identity),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom Identity Create / Edit Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CustomIdentityDialog(
    initialIdentity: AiIdentity?,
    onDismiss: () -> Unit,
    onSave: (name: String, prompt: String) -> Unit
) {
    var name by remember(initialIdentity) { mutableStateOf(initialIdentity?.name.orEmpty()) }
    var prompt by remember(initialIdentity) { mutableStateOf(initialIdentity?.prompt.orEmpty()) }
    val isEditing = initialIdentity != null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = SquircleShape,
            color = AiDesign.surfaceColor(),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isEditing) stringResource(R.string.st_AiSettings_edit_identity) else stringResource(R.string.st_AiSettings_add_identity),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.st_AiSettings_identity_name)) },
                    placeholder = { Text("e.g. Socratic Questioner") },
                    singleLine = true,
                    shape = MediumExpressiveShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AiDesign.glassColor(),
                        unfocusedContainerColor = AiDesign.glassColor()
                    )
                )

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.st_AiSettings_identity_prompt)) },
                    placeholder = { Text("Instructions that define how the AI thinks, speaks, and responds...") },
                    minLines = 3,
                    maxLines = 6,
                    shape = MediumExpressiveShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AiDesign.glassColor(),
                        unfocusedContainerColor = AiDesign.glassColor()
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.st_AiAssistantScreen_q3r4), color = AiDesign.textColor(0.5f), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    ToolzExpressiveButton(
                        onClick = {
                            if (name.isNotBlank() && prompt.isNotBlank()) {
                                onSave(name.trim(), prompt.trim())
                            }
                        },
                        enabled = name.isNotBlank() && prompt.isNotBlank(),
                        shape = MediumExpressiveShape
                    ) {
                        Text(stringResource(R.string.st_AiSettings_save_identity), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Other Settings Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsPromptsSection(
    dynamicPromptsEnabled: Boolean,
    promptFormat: String,
    onToggleDynamicPrompts: (Boolean) -> Unit,
    onPromptFormatChange: (String) -> Unit
) {
    SettingsSection(stringResource(R.string.st_AiAssistantScreen_k1l3)) {
        Surface(Modifier.fillMaxWidth(), MediumExpressiveShape, AiDesign.glassColor(), border = BorderStroke(1.dp, AiDesign.glassBorder())) {
            Row(Modifier.padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.st_AiAssistantScreen_m3n5), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.st_AiAssistantScreen_o5p7), style = MaterialTheme.typography.labelSmall, color = AiDesign.textColor(0.55f))
                }
                ExpressiveSwitch(checked = dynamicPromptsEnabled, onCheckedChange = onToggleDynamicPrompts)
            }
        }
        AnimatedVisibility(visible = dynamicPromptsEnabled) {
            Column(Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.st_AiAssistantScreen_q7r9), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
                ToolzConnectedButtonGroup(
                    selectedIndex = listOf("short", "medium", "long").indexOf(promptFormat).coerceAtLeast(0),
                    options = listOf(stringResource(R.string.st_AiAssistantScreen_s9t1), stringResource(R.string.st_AiAssistantScreen_u1v3), stringResource(R.string.st_AiAssistantScreen_w3x5)),
                    onOptionSelected = { onPromptFormatChange(listOf("short", "medium", "long")[it]) },
                )
            }
        }
    }
}

@Composable
private fun SettingsAdvancedSection(
    aiSearchIconVisible: Boolean,
    onSetAiSearchIconVisible: (Boolean) -> Unit,
    catalogVersion: Int?,
    catalogUpdatedAt: String?,
    isRefreshingCatalog: Boolean,
    catalogRefreshResult: String?,
    onRefreshCatalog: () -> Unit,
) {
    SettingsSection(stringResource(R.string.st_AiAssistantScreen_y5z7)) {
        Surface(Modifier.fillMaxWidth(), MediumExpressiveShape, AiDesign.glassColor(), border = BorderStroke(1.dp, AiDesign.glassBorder())) {
            Row(Modifier.padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.st_AiAssistantScreen_a7b9), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.st_AiAssistantScreen_c9d1), style = MaterialTheme.typography.labelSmall, color = AiDesign.textColor(0.55f))
                }
                ExpressiveSwitch(checked = aiSearchIconVisible, onCheckedChange = onSetAiSearchIconVisible)
            }
        }
        // Server model catalog
        Surface(Modifier.fillMaxWidth(), MediumExpressiveShape, AiDesign.glassColor(), border = BorderStroke(1.dp, AiDesign.glassBorder())) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Model catalog", fontWeight = FontWeight.Bold)
                        Text(
                            if (catalogVersion != null)
                                "Server list" + (catalogUpdatedAt?.let { " • updated $it" } ?: "") + " (v$catalogVersion)"
                            else "Not loaded — tap refresh to fetch",
                            style = MaterialTheme.typography.labelSmall,
                            color = AiDesign.textColor(0.55f),
                        )
                    }
                    if (isRefreshingCatalog) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefreshCatalog, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.Refresh, "Refresh model catalog", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (catalogRefreshResult != null) {
                    Text(
                        catalogRefreshResult,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (catalogRefreshResult.startsWith("✓")) Color(0xFF4CAF50)
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTestSaveSection(
    isTesting: Boolean,
    testResult: String?,
    onTest: () -> Unit,
    onShowConfigSave: () -> Unit,
    editingConfig: AiConfig?
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolzExpressiveButton(
                onClick = onTest,
                modifier = Modifier.weight(1f).height(48.dp),
                enabled = !isTesting,
                shape = MediumExpressiveShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                if (isTesting) ToolzWavyCircularProgressIndicator(Modifier.size(16.dp), MaterialTheme.colorScheme.secondary, Color.Transparent)
                else Text(stringResource(R.string.st_AiAssistantScreen_e1f3), fontWeight = FontWeight.Bold)
            }
        }
        if (testResult != null) {
            Surface(
                color = if (testResult.startsWith("✓")) Color(0xFF4CAF50).copy(alpha = 0.14f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                shape = MediumExpressiveShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(testResult, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        ToolzExpressiveButton(
            onClick = onShowConfigSave,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MediumExpressiveShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        ) {
            Text(
                if (editingConfig != null) stringResource(R.string.st_AiAssistantScreen_g3h5) else stringResource(R.string.st_AiAssistantScreen_i5j7),
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun SettingsPresetEditSection(
    configName: String,
    onConfigNameChange: (String) -> Unit,
    selectedIcon: String,
    onIconChange: (String) -> Unit,
    customIconUri: String?,
    onCustomIconClick: () -> Unit,
    provider: String,
    onSaveConfig: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = configName,
            onValueChange = onConfigNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.st_AiAssistantScreen_k7l9)) },
            shape = MediumExpressiveShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AiDesign.glassColor(),
                unfocusedContainerColor = AiDesign.glassColor(),
                unfocusedBorderColor = AiDesign.glassBorder()
            )
        )
        // Icon picker row
        Text(
            stringResource(R.string.st_AiAssistantScreen_m9n1),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Surface(
                    onClick = onCustomIconClick,
                    modifier = Modifier.size(48.dp),
                    shape = MediumExpressiveShape,
                    color = if (selectedIcon == "CUSTOM") MaterialTheme.colorScheme.primaryContainer else AiDesign.glassColor(),
                    border = BorderStroke(if (selectedIcon == "CUSTOM") 2.dp else 1.dp, if (selectedIcon == "CUSTOM") MaterialTheme.colorScheme.primary else AiDesign.glassBorder())
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (customIconUri != null) AsyncImage(customIconUri, null, Modifier.fillMaxSize().clip(MediumExpressiveShape), contentScale = ContentScale.Crop)
                        else Icon(Icons.Rounded.AddAPhoto, null, Modifier.size(20.dp), tint = AiDesign.textColor(0.6f))
                    }
                }
            }
            items(listOf("AUTO","GEMINI","CHATGPT","GROQ","CLAUDE","DEEPSEEK","OPENCODE_ZEN","OPENCODE_GO","BOT","SPARKLE")) { ik ->
                val isSelected = selectedIcon == ik
                Surface(
                    onClick = { onIconChange(ik) },
                    modifier = Modifier.size(48.dp),
                    shape = MediumExpressiveShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else AiDesign.glassColor(),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else AiDesign.glassBorder())
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(getIconForConfig(ik, provider), null, Modifier.size(24.dp), tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else AiDesign.textColor(0.7f))
                    }
                }
            }
        }
        ToolzExpressiveButton(
            onClick = { onSaveConfig(configName) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = configName.isNotBlank(),
            shape = MediumExpressiveShape
        ) {
            Text(stringResource(R.string.st_AiAssistantScreen_o1p3), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SettingsPresetsList(
    savedConfigs: List<AiConfig>,
    onEditConfig: (AiConfig) -> Unit,
    onDeleteConfig: (AiConfig) -> Unit,
    onActiveTabChange: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (savedConfigs.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(modifier = Modifier.size(64.dp), shape = SquircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bookmarks, null, Modifier.size(28.dp).alpha(0.3f), tint = AiDesign.textColor()) }
                    }
                    Text(stringResource(R.string.st_AiAssistantScreen_q3r5), color = AiDesign.textColor(0.4f), modifier = Modifier.padding(top = 12.dp), fontWeight = FontWeight.Medium)
                }
            }
        }
        items(savedConfigs, key = { it.name }) { config ->
            Surface(Modifier.fillMaxWidth(), MediumExpressiveShape, AiDesign.glassColor(), border = BorderStroke(1.dp, AiDesign.glassBorder())) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AiAvatar(config, 42.dp, performanceMode = true)
                    Column(Modifier.weight(1f)) {
                        Text(config.name, fontWeight = FontWeight.Black, color = AiDesign.textColor())
                        Text("${config.provider} · ${config.model}", style = MaterialTheme.typography.labelSmall, color = AiDesign.textColor(0.55f))
                    }
                    ToolzExpressiveIconButton(
                        onClick = { onEditConfig(config); onActiveTabChange(0) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        shape = SmallExpressiveShape
                    ) {
                        Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    ToolzExpressiveIconButton(
                        onClick = { onDeleteConfig(config) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        shape = SmallExpressiveShape
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(width = 18.dp, height = 3.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp)
        }
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup Guide Dialog (with OpenCode fix)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val providers = AiSettingsHelper.providers
    val pagerState = rememberPagerState { providers.size }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = SquircleShape, color = AiDesign.surfaceColor()) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.st_AiAssistantScreen_s5t7), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp))
                HorizontalPager(state = pagerState, modifier = Modifier.height(360.dp)) { page ->
                    val provider = providers[page]
                    val pColor = AiDesign.providerColor(provider) ?: MaterialTheme.colorScheme.primary
                    val steps = AiSettingsHelper.tutorials[provider].orEmpty()
                    val keyUrl = AiSettingsHelper.getApiKeyUrl(provider)
                    val isOpencode = provider.contains("OpenCode", ignoreCase = true) ||
                            provider.contains("zen", ignoreCase = true) ||
                            provider.contains("go", ignoreCase = true)

                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(modifier = Modifier.size(36.dp), shape = SmallExpressiveShape, color = pColor.copy(alpha = 0.12f)) {
                                Box(contentAlignment = Alignment.Center) { Icon(getIconForConfig("AUTO", provider), null, Modifier.size(18.dp), pColor) }
                            }
                            Text(provider, fontWeight = FontWeight.Black, color = pColor, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            AiSettingsHelper.getProviderDescription(provider),
                            color = AiDesign.textColor(0.65f),
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 19.sp,
                        )
                        if (steps.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                steps.forEachIndexed { i, step ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                        Surface(
                                            modifier = Modifier.size(22.dp),
                                            shape = CircleShape,
                                            color = pColor.copy(alpha = 0.14f),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    "${i + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Black,
                                                    color = pColor,
                                                )
                                            }
                                        }
                                        Text(
                                            step,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AiDesign.textColor(0.8f),
                                            lineHeight = 19.sp,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ToolzOutlinedExpressiveButton(
                                onClick = {
                                    if (keyUrl.isNotBlank()) {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(keyUrl)))
                                        }
                                    }
                                },
                                shape = MediumExpressiveShape,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.VpnKey, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Get key", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                            // Don't show redundant/useless placeholder chip for OpenCode Zen / Go
                            val placeholder = AiSettingsHelper.getApiKeyPlaceholder(provider)
                            if (!isOpencode && placeholder.isNotBlank() && !placeholder.contains("Paste key", ignoreCase = true)) {
                                Surface(shape = SmallExpressiveShape, color = AiDesign.glassColor(), border = BorderStroke(1.dp, AiDesign.glassBorder())) {
                                    Text(
                                        placeholder,
                                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AiDesign.textColor(0.55f),
                                    )
                                }
                            }
                        }
                    }
                }
                // Pager indicators
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(providers.size) { i ->
                        val active = pagerState.currentPage == i
                        val w by animateDpAsState(if (active) 20.dp else 6.dp, spring(Spring.DampingRatioMediumBouncy), label = "dot")
                        Box(Modifier.padding(2.dp).clip(CircleShape).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest).size(width = w, height = 5.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
                ToolzExpressiveButton(onDismiss, Modifier.fillMaxWidth()) { Text(stringResource(R.string.st_AiAssistantScreen_u7v9), fontWeight = FontWeight.Black) }
            }
        }
    }
}

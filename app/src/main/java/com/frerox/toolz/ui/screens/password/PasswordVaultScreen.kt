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

package com.frerox.toolz.ui.screens.password

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.saveable.rememberSaveable
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.withContext
import com.frerox.toolz.R
import com.frerox.toolz.data.password.PasswordEntity
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.screens.password.components.GeneratorCard
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground
import com.frerox.toolz.util.password.PasswordGenerator
import com.frerox.toolz.util.password.PasswordUtils
import com.frerox.toolz.util.password.VaultClipboard
import com.frerox.toolz.util.password.VaultPasswordEngine
import com.frerox.toolz.util.security.BiometricPromptUtils

// ═══════════════════════════════════════════════════════════════════════════════
// ROOT SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PasswordVaultScreen(
    viewModel: PasswordVaultViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current

    val categorizedPasswords by viewModel.categorizedPasswords.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val vaultStats by viewModel.vaultStats.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()

    var isUnlocked by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPassword by remember { mutableStateOf<PasswordEntity?>(null) }
    var passwordToDelete by remember { mutableStateOf<PasswordEntity?>(null) }
    var showGenerator by remember { mutableStateOf(false) }
    var prefillPassword by remember { mutableStateOf("") }

    // Auto-lock when the app goes to background so recent-apps/trailing
    // composition never exposes secrets.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) isUnlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(importMessage) {
        importMessage?.let {
            Toast.makeText(
                context,
                context.getString(
                    R.string.st_PasswordVaultScreen_import_result,
                    it.imported, it.skipped
                ),
                Toast.LENGTH_LONG
            ).show()
            viewModel.consumeImportMessage()
        }
    }

    val csvPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importCsv(it, context) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground(),
    ) {
        AnimatedContent(
            targetState = isUnlocked,
            transitionSpec = {
                val enter = fadeIn(tween(700, delayMillis = 120)) + scaleIn(
                    initialScale = 0.94f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
                val exit = fadeOut(tween(350)) + scaleOut(
                    targetScale = 0.96f,
                    animationSpec = tween(350),
                )
                enter togetherWith exit
            },
            label = "vault_unlock_transition",
        ) { unlocked ->
            if (unlocked) {
                VaultMainContent(
                    viewModel = viewModel,
                    categorizedPasswords = categorizedPasswords,
                    searchQuery = searchQuery,
                    isScanning = isScanning,
                    vaultStats = vaultStats,
                    onAddClick = { showAddDialog = true },
                    onGeneratorClick = { showGenerator = true },
                    onEditPassword = { editingPassword = it },
                    onDeletePassword = { passwordToDelete = it },
                    onBackClick = onBackClick,
                    onCsvImport = { csvPicker.launch("*/*") },
                )
            } else {
                BiometricGate(onSuccess = { isUnlocked = true })
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    if (showAddDialog) {
        AddPasswordDialog(
            prefillPassword = prefillPassword,
            onDismiss = {
                showAddDialog = false
                prefillPassword = ""
            },
            onConfirm = { name, url, user, pass ->
                viewModel.addPassword(name, url, user, pass)
                showAddDialog = false
                prefillPassword = ""
            },
            onGeneratorClick = { showGenerator = true }
        )
    }

    editingPassword?.let { password ->
        AddPasswordDialog(
            initialEntity = password,
            onDismiss = { editingPassword = null },
            onConfirm = { name, url, user, pass ->
                viewModel.updatePassword(
                    password.copy(name = name, url = url, username = user, password = pass),
                )
                editingPassword = null
            },
            onGeneratorClick = { showGenerator = true }
        )
    }

    passwordToDelete?.let { password ->
        DeleteConfirmDialog(
            name = password.name,
            onConfirm = {
                viewModel.deletePassword(password)
                passwordToDelete = null
            },
            onDismiss = { passwordToDelete = null },
        )
    }

    if (showGenerator) {
        GeneratorBottomSheet(
            viewModel = viewModel,
            onDismiss = { showGenerator = false },
            onUsePassword = { generated ->
                showGenerator = false
                if (editingPassword == null && !showAddDialog) {
                    prefillPassword = generated
                    showAddDialog = true
                } else {
                    prefillPassword = generated
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// VAULT MAIN CONTENT (unlocked scaffold)
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VaultMainContent(
    viewModel: PasswordVaultViewModel,
    categorizedPasswords: Map<String, List<PasswordEntity>>,
    searchQuery: String,
    isScanning: Boolean,
    vaultStats: PasswordVaultViewModel.VaultStats,
    onAddClick: () -> Unit,
    onGeneratorClick: () -> Unit,
    onEditPassword: (PasswordEntity) -> Unit,
    onDeletePassword: (PasswordEntity) -> Unit,
    onBackClick: () -> Unit,
    onCsvImport: () -> Unit,
) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            ExpressiveTopAppBar(
                title = "Vault",
                subtitle = stringResource(R.string.st_PasswordVaultScreen_e1a2),
                largeFlexible = true,
                titleHorizontalAlignment = Alignment.Start,
                navigationIcon = {
                    ToolzExpressiveIconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBackClick()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        shape = MediumExpressiveShape,
                    ) {
                        Icon(
                            Icons.Rounded.ArrowBackIosNew,
                            contentDescription = stringResource(R.string.st_PasswordVaultScreen_b3c4),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                actions = {
                    ToolzExpressiveIconButton(
                        onClick = {
                            vibrationManager?.vibrateTick()
                            val autofillManager = context.getSystemService(
                                android.view.autofill.AutofillManager::class.java,
                            )
                            if (autofillManager != null && !autofillManager.hasEnabledAutofillServices()) {
                                try {
                                    val intent = Intent("android.settings.REQUEST_SET_AUTOFILL_SERVICE")
                                    intent.data = "package:${context.packageName}".toUri()
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        context.startActivity(Intent("android.settings.AUTOFILL_SETTINGS"))
                                    } catch (e2: Exception) {
                                        Toast.makeText(context, context.getString(R.string.st_PasswordVaultScreen_a1b2), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, context.getString(R.string.st_PasswordVaultScreen_c3d4), Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        shape = MediumExpressiveShape,
                    ) {
                        Icon(Icons.Rounded.SettingsSuggest, contentDescription = stringResource(R.string.st_PasswordVaultScreen_e5f6))
                    }
                    Spacer(Modifier.width(8.dp))
                    ToolzExpressiveIconButton(
                        onClick = {
                            vibrationManager?.vibrateTick()
                            onCsvImport()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        shape = MediumExpressiveShape,
                    ) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = stringResource(R.string.st_PasswordVaultScreen_g7h8))
                    }
                    Spacer(Modifier.width(4.dp))
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExpressiveFabMenu(
                contentDescription = stringResource(R.string.st_PasswordVaultScreen_i9j0),
                items = listOf(
                    Triple(stringResource(R.string.st_PasswordVaultScreen_k1l2), Icons.Rounded.AutoAwesome, onGeneratorClick),
                    Triple(stringResource(R.string.st_PasswordVaultScreen_m3n4), Icons.Rounded.Add, onAddClick),
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
        ) {
            // ── Stats row (collapses on scroll) ──────────────────────────────
            AnimatedVisibility(
                visible = scrollBehavior.state.collapsedFraction < 0.2f,
                enter = expandVertically(
                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                ) + fadeIn(tween(300)),
                exit = shrinkVertically(
                    spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
                ) + fadeOut(tween(200)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StaggeredEntrance(index = 0, modifier = Modifier.weight(1f)) {
                        VaultStatCard(
                            label = stringResource(R.string.st_PasswordVaultScreen_o5p6),
                            value = vaultStats.total.toString(),
                            icon = Icons.Rounded.Inventory2,
                            accentColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                    StaggeredEntrance(index = 1, modifier = Modifier.weight(1f)) {
                        VaultStatCard(
                            label = stringResource(R.string.st_PasswordVaultScreen_q7r8),
                            value = vaultStats.breached.toString(),
                            icon = Icons.Rounded.GppBad,
                            accentColor = if (vaultStats.breached > 0)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.outline,
                        )
                    }
                    StaggeredEntrance(index = 2, modifier = Modifier.weight(1f)) {
                        VaultStatCard(
                            label = stringResource(R.string.st_PasswordVaultScreen_s9t0),
                            value = vaultStats.weak.toString(),
                            icon = Icons.Rounded.Password,
                            accentColor = if (vaultStats.weak > 0)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                if (vaultStats.total > 0) {
                    TierDistributionBar(
                        tierCounts = vaultStats.tierCounts,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                }
            }

            // ── Search + Scan row ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = ExtraLargeExpressiveShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 0.dp,
                ) {
                    ExpressiveSearchField(
                        query = searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = { Text(stringResource(R.string.st_PasswordVaultScreen_u1v2)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingIcon = {
                            AnimatedVisibility(
                                visible = searchQuery.isNotEmpty(),
                                enter = scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(),
                                exit = scaleOut() + fadeOut(),
                            ) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.st_PasswordVaultScreen_w3x4))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        onSearch = {},
                    )
                }
                ScanButton(isScanning = isScanning, onClick = { viewModel.scanVault() })
            }

            // ── Password list ─────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdges(top = 8.dp, bottom = 48.dp),
                    contentPadding = PaddingValues(
                        start = 20.dp, end = 20.dp, top = 4.dp, bottom = 128.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    categorizedPasswords.forEach { (category, list) ->
                        item(key = "header_$category", contentType = "header") {
                            CategoryHeader(name = category)
                        }
                        items(list, key = { it.id }, contentType = { "credential" }) { password ->
                            CredentialCard(
                                    password = password,
                                    onDelete = { onDeletePassword(password) },
                                    onCheckPwned = { viewModel.checkPwned(password) },
                                    onEdit = onEditPassword,
                                )
                        }
                    }

                    if (categorizedPasswords.isEmpty() && searchQuery.isNotEmpty()) {
                        item { EmptySearchResult() }
                    }
                    if (categorizedPasswords.isEmpty() && searchQuery.isEmpty()) {
                        item { EmptyVaultState() }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BIOMETRIC GATE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun BiometricGate(onSuccess: () -> Unit) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Pulsing biometric button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(220.dp),
            ) {
                // Pulse rings from ExpressiveProgress
                ExpressivePulseIndicator(
                    modifier = Modifier.size(200.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                // Fingerprint button
                Surface(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        (context as? FragmentActivity)?.let {
                            BiometricPromptUtils.showBiometricPrompt(
                                activity = it,
                                onSuccess = { onSuccess() },
                            )
                        }
                    },
                    modifier = Modifier.size(116.dp),
                    shape = ExtraLargeExpressiveShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 24.dp,
                    tonalElevation = 8.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Fingerprint,
                            contentDescription = stringResource(R.string.st_PasswordVaultScreen_a7b8),
                            modifier = Modifier.size(62.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(44.dp))

            Text(
                stringResource(R.string.st_PasswordVaultScreen_c9d0),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.st_PasswordVaultScreen_e1f2),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(44.dp))

            ToolzExpressiveButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    (context as? FragmentActivity)?.let {
                        BiometricPromptUtils.showBiometricPrompt(
                            activity = it,
                            onSuccess = { onSuccess() },
                        )
                    }
                },
                shape = LargeExpressiveShape,
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 18.dp),
            ) {
                Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.st_PasswordVaultScreen_g3h4),
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// STAT CARD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun VaultStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    ExpressiveCard(
        onClick = {},
        modifier = modifier,
        shape = LargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        elevation = 0.dp,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Surface(
                shape = SmallExpressiveShape,
                color = accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TIER DISTRIBUTION BAR (critical/weak/mid/strong/elite)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun TierDistributionBar(tierCounts: List<Int>, modifier: Modifier = Modifier) {
    val colors = listOf(
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.primary
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(SmallExpressiveShape),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tierCounts.forEachIndexed { index, count ->
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .weight(count.toFloat())
                        .fillMaxHeight()
                        .background(colors[index.coerceIn(colors.indices)])
                )
            }
        }
        if (tierCounts.sum() == 0) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCAN BUTTON
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ScanButton(isScanning: Boolean, onClick: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(56.dp),
    ) {
        // Pulse rings when scanning (from ExpressiveProgress)
        if (isScanning) {
            ExpressivePulseIndicator(
                modifier = Modifier.size(56.dp),
                color = primaryColor,
            )
        }
        ToolzExpressiveIconButton(
            onClick = {
                if (!isScanning) {
                    vibrationManager?.vibrateClick()
                    onClick()
                }
            },
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isScanning) primaryColor
                else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isScanning) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            shape = MediumExpressiveShape,
        ) {
            if (isScanning) {
                ToolzLoadingIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    Icons.Rounded.Security,
                    contentDescription = stringResource(R.string.st_PasswordVaultScreen_y5z6),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CATEGORY HEADER
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun CategoryHeader(name: String) {
    val categoryColor = when (name) {
        "CRITICAL"   -> MaterialTheme.colorScheme.error
        "WEAK"       -> MaterialTheme.colorScheme.tertiary
        "MID"        -> MaterialTheme.colorScheme.secondary
        "INCOMPLETE" -> MaterialTheme.colorScheme.tertiary
        else         -> MaterialTheme.colorScheme.primary
    }
    val labelRes = when (name) {
        "CRITICAL"   -> R.string.st_PasswordVaultScreen_strength_critical
        "WEAK"       -> R.string.st_PasswordVaultScreen_strength_weak
        "MID"        -> R.string.st_PasswordVaultScreen_strength_mid
        "STRONG"     -> R.string.st_PasswordVaultScreen_strength_strong
        "ELITE"      -> R.string.st_PasswordVaultScreen_strength_elite
        else         -> null
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    ) {
        Surface(
            color = categoryColor.copy(alpha = 0.10f),
            shape = SmallExpressiveShape,
            border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.18f)),
        ) {
            Text(
                text = labelRes?.let { stringResource(it) } ?: name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = categoryColor,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .alpha(0.12f),
            color = categoryColor,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// EMPTY STATES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun EmptySearchResult() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = ExtraLargeExpressiveShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                )
            }
        }
        Text(
            stringResource(R.string.st_PasswordVaultScreen_i5j6),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp,
        )
        Text(
            stringResource(R.string.st_PasswordVaultScreen_k7l8),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun EmptyVaultState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = ExtraLargeExpressiveShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            stringResource(R.string.st_PasswordVaultScreen_m9n0),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.st_PasswordVaultScreen_o1p2),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CREDENTIAL CARD
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CredentialCard(
    password: PasswordEntity,
    onDelete: () -> Unit,
    onCheckPwned: () -> Unit,
    onEdit: (PasswordEntity) -> Unit,
) {
    var expanded by rememberSaveable(password.id) { mutableStateOf(false) }
    var revealed by rememberSaveable(password.id) { mutableStateOf(false) }
    val vibrationManager = LocalVibrationManager.current
    val context = LocalContext.current
    val copyMessage = stringResource(R.string.st_PasswordVaultScreen_q3r4)

    val smartName = remember(password.name, password.url) {
        PasswordUtils.getSmartName(password.url, password.name)
    }
    val isIncomplete = password.password.isEmpty()
    val pawnedCount = password.pwnedCount ?: 0
    val isBreached = pawnedCount > 0
    val isWhisper = remember(password.name, password.url) {
        password.url?.contains("whisper.toolz.app", ignoreCase = true) == true ||
        password.name.startsWith("Whisper", ignoreCase = true)
    }
    val isTokenAccount = remember(password.name, password.password) {
        password.name.contains("Anon", ignoreCase = true) || (password.password.length == 64 && password.password.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
    }

    // Swipe-to-dismiss: left = edit, right = delete
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    vibrationManager?.vibrateClick()
                    onEdit(password)
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    vibrationManager?.vibrateClick()
                    onDelete()
                    false
                }
                else -> false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val revealColor by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                },
                animationSpec = spring(Spring.DampingRatioMediumBouncy),
                label = "swipe_bg",
            )
            val iconScale by animateFloatAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.65f else 1.25f,
                animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
                label = "swipe_scale",
            )
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val swipeIcon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Rounded.Edit
                SwipeToDismissBoxValue.EndToStart -> Icons.Rounded.Delete
                else -> null
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(SquircleShape)
                    .background(revealColor)
                    .padding(horizontal = 28.dp),
                contentAlignment = alignment,
            ) {
                if (swipeIcon != null) {
                    Icon(
                        swipeIcon,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                        tint = if (direction == SwipeToDismissBoxValue.StartToEnd)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
    ) {
        // ── Card background animated by state ─────────────────────────────
        val whisperTint = MaterialTheme.colorScheme.secondary
        val cardBg by animateColorAsState(
            targetValue = when {
                isBreached  -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f)
                isWhisper   -> if (expanded) whisperTint.copy(alpha = 0.18f) else whisperTint.copy(alpha = 0.08f)
                expanded    -> MaterialTheme.colorScheme.surfaceContainerHighest
                else        -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
            label = "card_bg",
        )
        val cardBorder by animateColorAsState(
            targetValue = when {
                isBreached -> MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
                isWhisper  -> if (expanded) whisperTint.copy(alpha = 0.55f) else whisperTint.copy(alpha = 0.30f)
                expanded   -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else       -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f)
            },
            animationSpec = tween(300),
            label = "card_border",
        )

        Surface(
            onClick = {
                vibrationManager?.vibrateTick()
                expanded = !expanded
                if (!expanded) revealed = false
            },
            modifier = Modifier.fillMaxWidth(),
            shape = SquircleShape,
            color = cardBg,
            border = BorderStroke(if (isWhisper) 1.5.dp else 1.dp, cardBorder),
            tonalElevation = if (expanded) 2.dp else 0.dp,
            shadowElevation = if (expanded) 3.dp else 0.dp,
        ) {
            Column(modifier = Modifier.padding(18.dp)) {

                // ── Header ────────────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconAvatar(
                        password = password,
                        smartName = smartName,
                        isIncomplete = isIncomplete,
                        context = context,
                    )

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                smartName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (isWhisper) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(10.dp),
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            if (isTokenAccount) "Whisper Token" else "Whisper E2EE",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = password.username.ifBlank { "No username set" },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                isIncomplete -> MaterialTheme.colorScheme.tertiary
                                else         -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Quick-copy icon button
                    if (!isIncomplete) {
                        ToolzExpressiveIconButton(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                VaultClipboard.copySecret(context, password.password)
                                Toast.makeText(context, copyMessage, Toast.LENGTH_SHORT).show()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                            shape = SmallExpressiveShape,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.st_PasswordVaultScreen_s5t6),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        CompactStrengthBadge(strength = password.strength)
                    } else {
                        Surface(
                            shape = SmallExpressiveShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.WarningAmber,
                                    contentDescription = stringResource(R.string.st_PasswordVaultScreen_u7v8),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                // ── Expanded detail panel ──────────────────────────────────────
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) + fadeIn(tween(220)),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    ) + fadeOut(tween(160)),
                ) {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Password reveal / set-password CTA
                        if (!isIncomplete) {
                            PasswordRevealSurface(
                                password = password.password,
                                revealed = revealed,
                                onToggleReveal = {
                                    vibrationManager?.vibrateTick()
                                    revealed = !revealed
                                },
                            )
                        } else {
                            ToolzExpressiveButton(
                                onClick = { onEdit(password) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = MediumExpressiveShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                ),
                            ) {
                                Icon(Icons.Rounded.LockOpen, contentDescription = null)
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.st_PasswordVaultScreen_a3b5), fontWeight = FontWeight.Black)
                            }
                        }

                        // Wavy strength bar
                        if (!isIncomplete) {
                            WavyStrengthIndicator(
                                strength = password.strength,
                                passwordForDetails = password.password
                            )
                        }

                        // Password history (only after reveal, capped at 5)
                        if (password.passwordHistory.isNotEmpty() && revealed) {
                            PasswordHistorySection(
                                history = password.passwordHistory,
                                onCopy = { old ->
                                    vibrationManager?.vibrateClick()
                                    VaultClipboard.copySecret(context, old)
                                    Toast.makeText(context, context.getString(R.string.st_PasswordVaultScreen_g9h1), Toast.LENGTH_SHORT).show()
                                },
                            )
                        }

                        // Breach banner
                        if (password.pwnedCount != null && !isIncomplete) {
                            BreachStatusBanner(pwnedCount = password.pwnedCount ?: 0)
                        }

                        // Action buttons
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ToolzOutlinedExpressiveButton(
                                onClick = { onEdit(password) },
                                modifier = Modifier.weight(1f),
                                shape = SmallExpressiveShape,
                                contentPadding = PaddingValues(vertical = 14.dp),
                            ) {
                                Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.st_PasswordVaultScreen_k3l5), fontWeight = FontWeight.Bold)
                            }

                            if (!isIncomplete) {
                                ToolzExpressiveButton(
                                    onClick = {
                                        vibrationManager?.vibrateTick()
                                        onCheckPwned()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = SmallExpressiveShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isBreached)
                                            MaterialTheme.colorScheme.errorContainer
                                        else
                                            MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = if (isBreached)
                                            MaterialTheme.colorScheme.onErrorContainer
                                        else
                                            MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                    contentPadding = PaddingValues(vertical = 14.dp),
                                ) {
                                    Icon(Icons.Rounded.Security, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.st_PasswordVaultScreen_m5n7), fontWeight = FontWeight.Bold)
                                }
                            }

                            ToolzExpressiveButton(
                                onClick = { onDelete() },
                                modifier = Modifier.weight(1f),
                                shape = SmallExpressiveShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                                contentPadding = PaddingValues(vertical = 14.dp),
                            ) {
                                Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.st_PasswordVaultScreen_o7p9), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── CredentialCard sub-composables ───────────────────────────────────────────

@Composable
private fun AppIconAvatar(
    password: PasswordEntity,
    smartName: String,
    isIncomplete: Boolean,
    context: android.content.Context,
) {
    var loadFailed by remember { mutableStateOf(false) }
    val isWhisper = remember(password.name, password.url) {
        password.url?.contains("whisper.toolz.app", ignoreCase = true) == true ||
        password.name.startsWith("Whisper", ignoreCase = true)
    }
    val isApp = password.url?.startsWith("android://") == true
    val packageName = if (isApp) password.url?.removePrefix("android://") else null

    val secondary = MaterialTheme.colorScheme.secondary
    val onSecondary = MaterialTheme.colorScheme.onSecondary
    val bgColor = when {
        isWhisper -> secondary
        isIncomplete -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
    }

    val fallbackTextColor = if (isIncomplete)
        MaterialTheme.colorScheme.onTertiaryContainer
    else
        MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        shape = MediumExpressiveShape,
        color = bgColor,
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isWhisper) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = smartName,
                        tint = onSecondary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else if (isApp && packageName != null) {
                val appIcon = remember(packageName) { PasswordUtils.getAppIcon(context, packageName) }
                if (appIcon != null) {
                    AsyncImage(
                        model = appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                    )
                } else {
                    loadFailed = true
                }
            } else if (!password.url.isNullOrBlank()) {
                val domain = remember(password.url) {
                    try {
                        val uri = if (!password.url!!.startsWith("http"))
                            java.net.URI("https://${password.url}")
                        else
                            java.net.URI(password.url!!)
                        uri.host?.removePrefix("www.")
                    } catch (e: Exception) {
                        null
                    }
                }
                if (domain != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("https://www.google.com/s2/favicons?domain=$domain&sz=128")
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        onError = { loadFailed = true },
                    )
                } else {
                    loadFailed = true
                }
            } else {
                loadFailed = true
            }

            if (loadFailed) {
                Text(
                    text = smartName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = fallbackTextColor,
                )
            }
        }
    }
}

@Composable
private fun CompactStrengthBadge(strength: Int) {
    val color = rememberStrengthColor(strength)
    Surface(
        shape = SmallExpressiveShape,
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text = stringResource(strengthLabel(strength)),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = color,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PasswordRevealSurface(
    password: String,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MediumExpressiveShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = revealed,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(initialScale = 0.95f)).togetherWith(
                        fadeOut(tween(150)) + scaleOut(targetScale = 0.95f),
                    )
                },
                modifier = Modifier.weight(1f),
                label = "pw_reveal",
            ) { show ->
                Text(
                    text = if (show) password
                    else "••••••••••••",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = if (show) 0.sp else 3.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onToggleReveal) {
                Icon(
                    if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (revealed) stringResource(R.string.st_PasswordVaultScreen_w9x0) else stringResource(R.string.st_PasswordVaultScreen_y1z2),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PasswordHistorySection(history: List<String>, onCopy: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.st_PasswordVaultScreen_e7f9),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
        )
        history.take(PasswordEntity.MAX_HISTORY).forEach { oldPass ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                shape = SmallExpressiveShape,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        oldPass,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = { onCopy(oldPass) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.st_PasswordVaultScreen_i1j3),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreachStatusBanner(pwnedCount: Int) {
    val isBreached = pwnedCount > 0
    val bannerColor = if (isBreached)
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
    else
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    val contentColor = if (isBreached)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.secondary
    val icon = if (isBreached) Icons.Rounded.GppBad else Icons.Rounded.Verified
    val text = if (isBreached)
        stringResource(R.string.st_PasswordVaultScreen_breach_count, pwnedCount)
    else
        stringResource(R.string.st_PasswordVaultScreen_breach_safe)

    Surface(
        color = bannerColor,
        shape = SmallExpressiveShape,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.15f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// WAVY STRENGTH INDICATOR (uses LinearWavyProgressIndicator from ExpressiveProgress)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun WavyStrengthIndicator(
    strength: Int,
    modifier: Modifier = Modifier,
    passwordForDetails: String? = null
) {
    val safeStrength = strength.coerceIn(0, 4)
    val targetColor = rememberStrengthColor(safeStrength)
    val targetProgress = (safeStrength + 1) / 5f
    var detailsExpanded by rememberSaveable(passwordForDetails) { mutableStateOf(false) }
    val report = remember(passwordForDetails, safeStrength) {
        passwordForDetails?.let { VaultPasswordEngine.assess(it) }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "strength_progress",
    )
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(450),
        label = "strength_color",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.st_PasswordVaultScreen_c5d7),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            AnimatedContent(
                targetState = strengthLabel(safeStrength),
                transitionSpec = {
                    slideInVertically { -it } + fadeIn() togetherWith
                            slideOutVertically { it } + fadeOut()
                },
                label = "strength_label",
            ) { label ->
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = animatedColor,
                    letterSpacing = 1.sp,
                )
            }
        }
        // Official M3 Expressive wavy progress bar
        ExpressiveWavyLinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = animatedColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        // Collapsible details: bits, crack time, reasons.
        if (report != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        R.string.st_PasswordVaultScreen_bits,
                        report.bits.toInt()
                    ) + " · " + stringResource(
                        R.string.st_PasswordVaultScreen_crack_time,
                        report.crackTimeLabel
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { detailsExpanded = !detailsExpanded },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (detailsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(
                            if (detailsExpanded) R.string.st_PasswordVaultScreen_hide_details
                            else R.string.st_PasswordVaultScreen_show_details
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            AnimatedVisibility(
                visible = detailsExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    report.reasons.forEach { reason ->
                        Text(
                            "• ${stringResource(reasonString(reason))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    report.suggestions.forEach { suggestion ->
                        Text(
                            "→ $suggestion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@StringRes
private fun reasonString(reason: VaultPasswordEngine.Reason): Int = when (reason) {
    VaultPasswordEngine.Reason.TOO_SHORT -> R.string.st_PasswordVaultScreen_reason_short
    VaultPasswordEngine.Reason.REPEATS -> R.string.st_PasswordVaultScreen_reason_repeats
    VaultPasswordEngine.Reason.SEQUENCE -> R.string.st_PasswordVaultScreen_reason_sequence
    VaultPasswordEngine.Reason.COMMON -> R.string.st_PasswordVaultScreen_reason_common
    VaultPasswordEngine.Reason.GOOD_LENGTH -> R.string.st_PasswordVaultScreen_reason_good_length
    VaultPasswordEngine.Reason.GOOD_MIX -> R.string.st_PasswordVaultScreen_reason_good_mix
}

// ═══════════════════════════════════════════════════════════════════════════════
// GENERATOR BOTTOM SHEET
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GeneratorBottomSheet(
    viewModel: PasswordVaultViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onUsePassword: ((String) -> Unit)? = null
) {
    val vibrationManager = LocalVibrationManager.current
    val context = LocalContext.current
    val settings by viewModel.generatorSettings.collectAsState()
    val spec = remember(settings) { viewModel.specFromSettings(settings) }

    var generatedPassword by remember { mutableStateOf("") }
    var specError by remember { mutableStateOf(false) }

    fun regenerate(current: VaultPasswordEngine.PasswordSpec = spec) {
        val result = VaultPasswordEngine.generate(current)
        val pwd = result.getOrNull()
        specError = pwd == null
        if (pwd != null) generatedPassword = pwd
    }

    // Initial password only — spec edits regen on release / button, never on
    // every slider tick (that discarded manual refreshes).
    LaunchedEffect(Unit) {
        regenerate(viewModel.specFromSettings())
    }

    val report = remember(generatedPassword) {
        VaultPasswordEngine.assess(generatedPassword.ifEmpty { "x" })
    }
    val emptyPool = specError || VaultPasswordEngine.buildPool(spec).isEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                width = 56.dp,
                height = 4.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Title
            Text(
                stringResource(R.string.st_PasswordVaultScreen_q9r1),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
                color = MaterialTheme.colorScheme.onSurface,
            )

            GeneratorCard(
                spec = spec,
                password = generatedPassword,
                report = report,
                emptyPool = emptyPool,
                onSpecChange = { newSpec ->
                    viewModel.updateGeneratorSettings(
                        settings.copy(
                            length = newSpec.length.toFloat(),
                            includeLowercase = newSpec.includeLowercase,
                            includeUppercase = newSpec.includeUppercase,
                            includeNumbers = newSpec.includeNumbers,
                            includeSymbols = newSpec.includeSymbols,
                            excludeAmbiguous = newSpec.excludeAmbiguous,
                            customSymbols = newSpec.customSymbols,
                            pinMode = newSpec.pinMode
                        )
                    )
                    regenerate(newSpec)
                },
                onRegenerate = { regenerate() },
                onCopy = {
                    vibrationManager?.vibrateClick()
                    VaultClipboard.copySecret(context, generatedPassword)
                    Toast.makeText(context, context.getString(R.string.st_PasswordVaultScreen_w5x7), Toast.LENGTH_SHORT).show()
                },
                onPreset = { preset ->
                    viewModel.updateGeneratorSettings(
                        settings.copy(
                            length = preset.length.toFloat(),
                            includeLowercase = preset.includeLowercase,
                            includeUppercase = preset.includeUppercase,
                            includeNumbers = preset.includeNumbers,
                            includeSymbols = preset.includeSymbols,
                            excludeAmbiguous = preset.excludeAmbiguous,
                            customSymbols = preset.customSymbols,
                            pinMode = preset.pinMode
                        )
                    )
                    regenerate(preset)
                },
                onMemorable = {
                    vibrationManager?.vibrateClick()
                    generatedPassword = VaultPasswordEngine.generateMemorable()
                }
            )

            // Use password CTA
            ToolzExpressiveButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    if (onUsePassword != null) {
                        onUsePassword(generatedPassword)
                    } else {
                        VaultClipboard.copySecret(context, generatedPassword)
                        Toast.makeText(context, context.getString(R.string.st_PasswordVaultScreen_w5x7), Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = LargeExpressiveShape,
                enabled = generatedPassword.isNotEmpty() && !emptyPool
            ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(
                        if (onUsePassword != null) R.string.st_PasswordVaultScreen_gen_use_password
                        else R.string.st_PasswordVaultScreen_y7z9
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DELETE CONFIRM DIALOG
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun DeleteConfirmDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ExtraLargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        },
        title = {
            Text(
                stringResource(R.string.st_PasswordVaultScreen_a9b1),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                stringResource(R.string.st_PasswordVaultScreen_delete_message, name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            ToolzExpressiveButton(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                shape = LargeExpressiveShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.st_PasswordVaultScreen_c1d3), fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            ToolzOutlinedExpressiveButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = LargeExpressiveShape,
            ) {
                Text(stringResource(R.string.st_PasswordVaultScreen_e3f5), fontWeight = FontWeight.Bold)
            }
        },
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// ADD / EDIT PASSWORD DIALOG
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AddPasswordDialog(
    initialEntity: PasswordEntity? = null,
    prefillPassword: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String, String) -> Unit,
    onGeneratorClick: (() -> Unit)? = null
) {
    val vibrationManager = LocalVibrationManager.current
    var name by remember(initialEntity?.id) { mutableStateOf(initialEntity?.name ?: "") }
    var url by remember(initialEntity?.id) { mutableStateOf(initialEntity?.url ?: "") }
    var username by remember(initialEntity?.id) { mutableStateOf(initialEntity?.username ?: "") }
    var password by remember(initialEntity?.id) { mutableStateOf(initialEntity?.password ?: prefillPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    // Generator handoff: sheet produces a password while this dialog stays open.
    LaunchedEffect(prefillPassword) {
        if (prefillPassword.isNotEmpty() && prefillPassword != password) {
            password = prefillPassword
        }
    }

    val liveStrength = remember(password) {
        if (password.isNotEmpty()) PasswordGenerator.calculateStrength(password) else -1
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onAppSelected = { appName, packageName ->
                name = appName
                url = "android://$packageName"
                showAppPicker = false
            },
        )
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ExtraLargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (initialEntity == null) stringResource(R.string.st_PasswordVaultScreen_g5h7) else stringResource(R.string.st_PasswordVaultScreen_i7j9),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                )
                ToolzExpressiveIconButton(
                    onClick = {
                        vibrationManager?.vibrateTick()
                        showAppPicker = true
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    shape = SmallExpressiveShape,
                ) {
                    Icon(Icons.Rounded.Apps, contentDescription = stringResource(R.string.st_PasswordVaultScreen_k9l1))
                }
                if (onGeneratorClick != null) {
                    Spacer(Modifier.width(8.dp))
                    ToolzExpressiveIconButton(
                        onClick = {
                            vibrationManager?.vibrateTick()
                            onGeneratorClick()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        shape = SmallExpressiveShape,
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = stringResource(R.string.st_PasswordVaultScreen_k1l2))
                    }
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.st_PasswordVaultScreen_m1n3)) },
                    shape = MediumExpressiveShape,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Rounded.Label,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    colors = textFieldColors,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.st_PasswordVaultScreen_o3p5)) },
                    shape = MediumExpressiveShape,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    colors = textFieldColors,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.st_PasswordVaultScreen_q5r7)) },
                    shape = MediumExpressiveShape,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    colors = textFieldColors,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.st_PasswordVaultScreen_s7t9)) },
                    shape = MediumExpressiveShape,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            vibrationManager?.vibrateTick()
                            passwordVisible = !passwordVisible
                        }) {
                            Icon(
                                if (passwordVisible) Icons.Rounded.VisibilityOff
                                else Icons.Rounded.Visibility,
                                contentDescription = if (passwordVisible) stringResource(R.string.st_PasswordVaultScreen_u9v1) else stringResource(R.string.st_PasswordVaultScreen_w1x3),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    colors = textFieldColors,
                )

                // Live strength indicator as user types
                AnimatedVisibility(
                    visible = password.isNotEmpty() && liveStrength >= 0,
                    enter = expandVertically(spring(Spring.DampingRatioLowBouncy)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    if (liveStrength >= 0) {
                        WavyStrengthIndicator(
                            strength = liveStrength,
                            modifier = Modifier.fillMaxWidth(),
                            passwordForDetails = password
                        )
                    }
                }
            }
        },
        confirmButton = {
            ToolzExpressiveButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    if (name.isNotBlank() && username.isNotBlank()) {
                        onConfirm(name, url.ifBlank { null }, username, password)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = LargeExpressiveShape,
                enabled = name.isNotBlank() && username.isNotBlank(),
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (initialEntity == null) stringResource(R.string.st_PasswordVaultScreen_y3z5) else stringResource(R.string.st_PasswordVaultScreen_a5b7),
                    fontWeight = FontWeight.Black,
                )
            }
        },
        dismissButton = {
            ToolzOutlinedExpressiveButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = LargeExpressiveShape,
            ) {
                Text(stringResource(R.string.st_PasswordVaultScreen_e3f5), fontWeight = FontWeight.Bold)
            }
        },
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// APP PICKER DIALOG
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AppPickerDialog(
    onDismiss: () -> Unit,
    onAppSelected: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        apps = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                    .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
            }.getOrDefault(emptyList())
        }
        loading = false
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            pm.getApplicationLabel(it).toString().contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ExtraLargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.st_PasswordVaultScreen_c7d9),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.st_PasswordVaultScreen_u1v2)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        text = {
            if (loading) {
                Box(
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                    val appName = pm.getApplicationLabel(app).toString()
                    val packageName = app.packageName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MediumExpressiveShape)
                            .clickable { onAppSelected(appName, packageName) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = SmallExpressiveShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        ) {
                            AsyncImage(
                                model = pm.getApplicationIcon(app),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(44.dp)
                                    .padding(6.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                appName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.st_PasswordVaultScreen_m7n9), fontWeight = FontWeight.Bold)
            }
        },
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun rememberStrengthColor(strength: Int): Color = when (strength.coerceIn(0, 4)) {
    0    -> MaterialTheme.colorScheme.error
    1    -> MaterialTheme.colorScheme.tertiary
    2    -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

@StringRes
private fun strengthLabel(strength: Int): Int = when (strength.coerceIn(0, 4)) {
    0    -> R.string.st_PasswordVaultScreen_strength_critical
    1    -> R.string.st_PasswordVaultScreen_strength_weak
    2    -> R.string.st_PasswordVaultScreen_strength_mid
    3    -> R.string.st_PasswordVaultScreen_strength_strong
    else -> R.string.st_PasswordVaultScreen_strength_elite
}

// ═══════════════════════════════════════════════════════════════════════════════
// PREVIEWS  (Light + Dark)
// ═══════════════════════════════════════════════════════════════════════════════

@Preview(name = "Biometric Gate — Light", showBackground = true, showSystemUi = true)
@Composable
private fun BiometricGateLightPreview() {
    ToolzTheme(darkTheme = false) { BiometricGate(onSuccess = {}) }
}

@Preview(name = "Biometric Gate — Dark", showBackground = true, showSystemUi = true)
@Composable
private fun BiometricGateDarkPreview() {
    ToolzTheme(darkTheme = true) { BiometricGate(onSuccess = {}) }
}

@Preview(name = "Credential Card — Light", showBackground = true)
@Composable
private fun CredentialCardLightPreview() {
    ToolzTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CredentialCard(
                password = PasswordEntity(
                    id = 1, name = "GitHub", url = "https://github.com",
                    username = "dev@example.com", password = "Str0ng#Passw0rd!",
                    strength = 4, pwnedCount = 0,
                ),
                onDelete = {}, onCheckPwned = {}, onEdit = {},
            )
            CredentialCard(
                password = PasswordEntity(
                    id = 2, name = "Gmail", url = "https://gmail.com",
                    username = "user@gmail.com", password = "weak",
                    strength = 1, pwnedCount = 3,
                ),
                onDelete = {}, onCheckPwned = {}, onEdit = {},
            )
            CredentialCard(
                password = PasswordEntity(
                    id = 3, name = "Old Account", url = null,
                    username = "user", password = "",
                    strength = 0, pwnedCount = null,
                ),
                onDelete = {}, onCheckPwned = {}, onEdit = {},
            )
        }
    }
}

@Preview(name = "Credential Card — Dark", showBackground = true)
@Composable
private fun CredentialCardDarkPreview() {
    ToolzTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CredentialCard(
                password = PasswordEntity(
                    id = 1, name = "GitHub", url = "https://github.com",
                    username = "dev@example.com", password = "Str0ng#Passw0rd!",
                    strength = 4, pwnedCount = 0,
                ),
                onDelete = {}, onCheckPwned = {}, onEdit = {},
            )
            CredentialCard(
                password = PasswordEntity(
                    id = 2, name = "Spotify", url = "https://spotify.com",
                    username = "music@example.com", password = "moderate123",
                    strength = 2, pwnedCount = 1,
                ),
                onDelete = {}, onCheckPwned = {}, onEdit = {},
            )
        }
    }
}

@Preview(name = "Stat Cards — Light", showBackground = true)
@Composable
private fun StatCardsLightPreview() {
    ToolzTheme(darkTheme = false) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VaultStatCard("Total", "24", Icons.Rounded.Inventory2, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            VaultStatCard("Breached", "2", Icons.Rounded.GppBad, MaterialTheme.colorScheme.error, Modifier.weight(1f))
            VaultStatCard("Weak", "5", Icons.Rounded.Password, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        }
    }
}

@Preview(name = "Stat Cards — Dark", showBackground = true)
@Composable
private fun StatCardsDarkPreview() {
    ToolzTheme(darkTheme = true) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VaultStatCard("Total", "24", Icons.Rounded.Inventory2, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            VaultStatCard("Breached", "0", Icons.Rounded.GppBad, MaterialTheme.colorScheme.outline, Modifier.weight(1f))
            VaultStatCard("Weak", "3", Icons.Rounded.Password, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        }
    }
}

@Preview(name = "Strength Indicator — All levels", showBackground = true)
@Composable
private fun StrengthIndicatorAllLevelsPreview() {
    ToolzTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            listOf(0, 1, 2, 3, 4).forEach { s ->
                WavyStrengthIndicator(strength = s, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview(name = "Category Headers", showBackground = true)
@Composable
private fun CategoryHeadersPreview() {
    ToolzTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(20.dp),
        ) {
            listOf("CRITICAL", "WEAK", "MID", "STRONG", "ELITE", "INCOMPLETE").forEach { name ->
                CategoryHeader(name = name)
            }
        }
    }
}

@Preview(name = "Delete Dialog — Light", showBackground = true)
@Composable
private fun DeleteDialogLightPreview() {
    ToolzTheme(darkTheme = false) {
        DeleteConfirmDialog(
            name = "GitHub",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Delete Dialog — Dark", showBackground = true)
@Composable
private fun DeleteDialogDarkPreview() {
    ToolzTheme(darkTheme = true) {
        DeleteConfirmDialog(
            name = "Gmail",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Add Entry Dialog — Light", showBackground = true)
@Composable
private fun AddDialogLightPreview() {
    ToolzTheme(darkTheme = false) {
        AddPasswordDialog(onDismiss = {}, onConfirm = { _, _, _, _ -> })
    }
}

@Preview(name = "Add Entry Dialog — Dark", showBackground = true)
@Composable
private fun AddDialogDarkPreview() {
    ToolzTheme(darkTheme = true) {
        AddPasswordDialog(onDismiss = {}, onConfirm = { _, _, _, _ -> })
    }
}
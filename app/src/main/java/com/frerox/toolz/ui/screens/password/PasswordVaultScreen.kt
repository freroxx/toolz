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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.frerox.toolz.data.password.PasswordEntity
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground
import com.frerox.toolz.util.password.PasswordGenerator
import com.frerox.toolz.util.password.PasswordUtils
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

    var isUnlocked by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPassword by remember { mutableStateOf<PasswordEntity?>(null) }
    var passwordToDelete by remember { mutableStateOf<PasswordEntity?>(null) }
    var showGenerator by remember { mutableStateOf(false) }

    val csvPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importCsv(context, it) } }

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
                    onCsvImport = { csvPicker.launch("text/*") },
                )
            } else {
                BiometricGate(onSuccess = { isUnlocked = true })
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    if (showAddDialog) {
        AddPasswordDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url, user, pass ->
                viewModel.addPassword(name, url, user, pass)
                showAddDialog = false
            },
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
                subtitle = "Encrypted & Secure",
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
                            contentDescription = "Back",
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
                                        Toast.makeText(context, "Autofill settings not found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Autofill is active ✓", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        shape = MediumExpressiveShape,
                    ) {
                        Icon(Icons.Rounded.SettingsSuggest, contentDescription = "Autofill settings")
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
                        Icon(Icons.Rounded.FileUpload, contentDescription = "Import CSV")
                    }
                    Spacer(Modifier.width(4.dp))
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExpressiveFabMenu(
                contentDescription = "Vault actions",
                items = listOf(
                    Triple("Password Generator", Icons.Rounded.AutoAwesome, onGeneratorClick),
                    Triple("Add Credential", Icons.Rounded.Add, onAddClick),
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StaggeredEntrance(index = 0, modifier = Modifier.weight(1f)) {
                        VaultStatCard(
                            label = "Total",
                            value = vaultStats.total.toString(),
                            icon = Icons.Rounded.Inventory2,
                            accentColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                    StaggeredEntrance(index = 1, modifier = Modifier.weight(1f)) {
                        VaultStatCard(
                            label = "Breached",
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
                            label = "Weak",
                            value = vaultStats.weak.toString(),
                            icon = Icons.Rounded.Password,
                            accentColor = if (vaultStats.weak > 0)
                                Color(0xFFFF9800)
                            else
                                MaterialTheme.colorScheme.outline,
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
                        onQueryChange = {
                            vibrationManager?.vibrateTick()
                            viewModel.onSearchQueryChange(it)
                        },
                        placeholder = { Text("Search vault…") },
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
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
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
                        item(key = "header_$category") {
                            CategoryHeader(name = category)
                        }
                        items(list, key = { it.id }) { password ->
                            val itemIndex = list.indexOf(password)
                            StaggeredEntrance(index = itemIndex) {
                                CredentialCard(
                                    password = password,
                                    onDelete = { onDeletePassword(password) },
                                    onCheckPwned = { viewModel.checkPwned(password) },
                                    onEdit = onEditPassword,
                                )
                            }
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
                        BiometricPromptUtils.showBiometricPrompt(
                            activity = context as FragmentActivity,
                            onSuccess = { onSuccess() },
                        )
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
                            contentDescription = "Unlock Vault",
                            modifier = Modifier.size(62.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(44.dp))

            Text(
                "Vault Encrypted",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Verify your identity to reveal secrets",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(44.dp))

            ToolzExpressiveButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    BiometricPromptUtils.showBiometricPrompt(
                        activity = context as FragmentActivity,
                        onSuccess = { onSuccess() },
                    )
                },
                shape = LargeExpressiveShape,
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 18.dp),
            ) {
                Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Authenticate",
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
                    contentDescription = "Scan vault",
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
        "MUST CHANGE" -> MaterialTheme.colorScheme.error
        "WEAK"        -> Color(0xFFFF9800)
        "INCOMPLETE"  -> MaterialTheme.colorScheme.tertiary
        else          -> MaterialTheme.colorScheme.primary
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
                text = name,
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
            "NO MATCHES FOUND",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp,
        )
        Text(
            "Try adjusting your search query.",
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
            "Vault is Empty",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Tap the + button to add your first credential.",
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
    var expanded by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    val vibrationManager = LocalVibrationManager.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val smartName = remember(password.name, password.url) {
        PasswordUtils.getSmartName(password.url, password.name)
    }
    val isIncomplete = password.password.isEmpty()
    val pawnedCount = password.pwnedCount ?: 0
    val isBreached = pawnedCount > 0

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
        val cardBg by animateColorAsState(
            targetValue = when {
                isBreached  -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f)
                expanded    -> MaterialTheme.colorScheme.surfaceContainerHighest
                else        -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
            label = "card_bg",
        )
        val cardBorder by animateColorAsState(
            targetValue = when {
                isBreached -> MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
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
            border = BorderStroke(1.dp, cardBorder),
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
                        Text(
                            smartName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
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
                                clipboardManager.setText(AnnotatedString(password.password))
                                Toast.makeText(context, "Password copied", Toast.LENGTH_SHORT).show()
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
                                contentDescription = "Copy password",
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
                                    contentDescription = "No password set",
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
                                Text("Set Password", fontWeight = FontWeight.Black)
                            }
                        }

                        // Wavy strength bar
                        if (!isIncomplete) {
                            WavyStrengthIndicator(strength = password.strength)
                        }

                        // Password history
                        if (password.passwordHistory.isNotEmpty()) {
                            PasswordHistorySection(
                                history = password.passwordHistory,
                                onCopy = { old ->
                                    vibrationManager?.vibrateClick()
                                    clipboardManager.setText(AnnotatedString(old))
                                    Toast.makeText(context, "Old password copied", Toast.LENGTH_SHORT).show()
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
                                Text("Edit", fontWeight = FontWeight.Bold)
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
                                    Text("Scan", fontWeight = FontWeight.Bold)
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
                                Text("Delete", fontWeight = FontWeight.Bold)
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
    val isApp = password.url?.startsWith("android://") == true
    val packageName = if (isApp) password.url?.removePrefix("android://") else null

    val bgColor = if (isIncomplete)
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
    else
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)

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
            if (isApp && packageName != null) {
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
            text = strengthLabel(strength),
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
                    else "•".repeat(password.length.coerceIn(8, 20)),
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
                    contentDescription = if (revealed) "Hide password" else "Show password",
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
            "PASSWORD HISTORY",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
        )
        history.take(10).forEach { oldPass ->
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
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = "Copy old password",
                            modifier = Modifier.size(14.dp),
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
        "Breached in $pwnedCount leak${if (pwnedCount > 1) "s" else ""}!"
    else
        "Identity safe from known leaks"

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
fun WavyStrengthIndicator(strength: Int, modifier: Modifier = Modifier) {
    val targetColor = rememberStrengthColor(strength)
    val targetProgress = (strength + 1) / 5f

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
                "STRENGTH",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            AnimatedContent(
                targetState = strengthLabel(strength),
                transitionSpec = {
                    slideInVertically { -it } + fadeIn() togetherWith
                            slideOutVertically { it } + fadeOut()
                },
                label = "strength_label",
            ) { label ->
                Text(
                    label,
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
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GENERATOR BOTTOM SHEET
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GeneratorBottomSheet(
    viewModel: PasswordVaultViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val settings by viewModel.generatorSettings.collectAsState()

    var generatedPassword by remember {
        mutableStateOf(
            PasswordGenerator.generate(
                length = settings.length.toInt(),
                includeSymbols = settings.includeSymbols,
                includeNumbers = settings.includeNumbers,
                includeUppercase = settings.includeUppercase,
            ),
        )
    }

    // Regenerate when settings change
    LaunchedEffect(settings) {
        generatedPassword = PasswordGenerator.generate(
            length = settings.length.toInt(),
            includeSymbols = settings.includeSymbols,
            includeNumbers = settings.includeNumbers,
            includeUppercase = settings.includeUppercase,
        )
    }

    val generatedStrength = remember(generatedPassword) {
        if (generatedPassword.isNotEmpty()) PasswordGenerator.calculateStrength(generatedPassword) else 0
    }
    val isStrong = settings.length >= 14 && settings.includeSymbols && settings.includeNumbers

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
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Title
            Text(
                "Password Engine",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Generated password card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = LargeExpressiveShape,
                color = if (isStrong)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    width = if (isStrong) 2.dp else 1.dp,
                    color = if (isStrong)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
                    else
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AnimatedContent(
                            targetState = generatedPassword,
                            transitionSpec = {
                                (scaleIn(
                                    initialScale = 0.82f,
                                    animationSpec = spring(
                                        Spring.DampingRatioLowBouncy,
                                        Spring.StiffnessMediumLow,
                                    ),
                                ) + fadeIn()).togetherWith(
                                    scaleOut(targetScale = 0.82f) + fadeOut(),
                                )
                            },
                            modifier = Modifier.weight(1f),
                            label = "gen_password_anim",
                        ) { pw ->
                            Text(
                                pw,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = if (isStrong) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        ToolzExpressiveIconButton(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                generatedPassword = PasswordGenerator.generate(
                                    length = settings.length.toInt(),
                                    includeSymbols = settings.includeSymbols,
                                    includeNumbers = settings.includeNumbers,
                                    includeUppercase = settings.includeUppercase,
                                )
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            shape = MediumExpressiveShape,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Regenerate",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // Inline wavy strength indicator
                    WavyStrengthIndicator(strength = generatedStrength)
                }
            }

            // Length slider section
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Length",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    AnimatedContent(
                        targetState = settings.length.toInt(),
                        transitionSpec = {
                            slideInVertically { -it } + fadeIn() togetherWith
                                    slideOutVertically { it } + fadeOut()
                        },
                        label = "length_display",
                    ) { len ->
                        Text(
                            len.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                // ExpressiveSlider with physics-based thumb from ExpressiveInputs.kt
                ExpressiveSlider(
                    value = settings.length,
                    onValueChange = { newVal ->
                        if (newVal.toInt() != settings.length.toInt()) {
                            vibrationManager?.vibrateTick()
                        }
                        viewModel.updateGeneratorSettings(settings.copy(length = newVal))
                    },
                    valueRange = 8f..64f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                )
            }

            // Options row — ExpressiveFilterChips from ExpressiveButtons.kt
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ExpressiveFilterChip(
                    selected = settings.includeUppercase,
                    onClick = {
                        viewModel.updateGeneratorSettings(
                            settings.copy(includeUppercase = !settings.includeUppercase),
                        )
                    },
                    label = { Text("A-Z", fontWeight = FontWeight.Black) },
                    leadingIcon = {
                        Icon(Icons.Rounded.TextFields, null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f),
                    shape = MediumExpressiveShape,
                )
                ExpressiveFilterChip(
                    selected = settings.includeNumbers,
                    onClick = {
                        viewModel.updateGeneratorSettings(
                            settings.copy(includeNumbers = !settings.includeNumbers),
                        )
                    },
                    label = { Text("1-9", fontWeight = FontWeight.Black) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Numbers, null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f),
                    shape = MediumExpressiveShape,
                )
                ExpressiveFilterChip(
                    selected = settings.includeSymbols,
                    onClick = {
                        viewModel.updateGeneratorSettings(
                            settings.copy(includeSymbols = !settings.includeSymbols),
                        )
                    },
                    label = { Text("@#!", fontWeight = FontWeight.Black) },
                    leadingIcon = {
                        Icon(Icons.Rounded.AlternateEmail, null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f),
                    shape = MediumExpressiveShape,
                )
            }

            // Copy password CTA
            ToolzExpressiveButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    clipboardManager.setText(AnnotatedString(generatedPassword))
                    Toast.makeText(context, "Password copied to clipboard", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = LargeExpressiveShape,
            ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Copy Password",
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
                "Delete Credential?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                "Permanently delete credentials for \"$name\"? This cannot be undone.",
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
                Text("Delete Forever", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            ToolzOutlinedExpressiveButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = LargeExpressiveShape,
            ) {
                Text("Cancel", fontWeight = FontWeight.Bold)
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
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String, String) -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    var name by remember { mutableStateOf(initialEntity?.name ?: "") }
    var url by remember { mutableStateOf(initialEntity?.url ?: "") }
    var username by remember { mutableStateOf(initialEntity?.username ?: "") }
    var password by remember { mutableStateOf(initialEntity?.password ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

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
                    if (initialEntity == null) "New Entry" else "Edit Entry",
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
                    Icon(Icons.Rounded.Apps, contentDescription = "Import from App")
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
                    label = { Text("Service Name") },
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
                    label = { Text("URL / App Package") },
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
                    label = { Text("Username / Email") },
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
                    label = { Text("Password") },
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
                                contentDescription = if (passwordVisible) "Hide" else "Show",
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
                    if (initialEntity == null) "Secure Credentials" else "Update Credentials",
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
                Text("Cancel", fontWeight = FontWeight.Bold)
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
    val apps = remember {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .sortedBy { pm.getApplicationLabel(it).toString() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ExtraLargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                "Select Source App",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            LazyColumn(modifier = Modifier.height(420.dp)) {
                items(apps) { app ->
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        },
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun rememberStrengthColor(strength: Int): Color = when (strength) {
    0    -> MaterialTheme.colorScheme.error
    1    -> Color(0xFFFB8C00)
    2    -> Color(0xFFFDD835)
    3    -> Color(0xFF7CB342)
    else -> MaterialTheme.colorScheme.primary
}

private fun strengthLabel(strength: Int): String = when (strength) {
    0    -> "CRITICAL"
    1    -> "WEAK"
    2    -> "MEDIUM"
    3    -> "STRONG"
    else -> "ELITE"
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
            VaultStatCard("Total", "24", Icons.Rounded.Inventory2, Color(0xFF6750A4), Modifier.weight(1f))
            VaultStatCard("Breached", "2", Icons.Rounded.GppBad, Color(0xFFB00020), Modifier.weight(1f))
            VaultStatCard("Weak", "5", Icons.Rounded.Password, Color(0xFFFF9800), Modifier.weight(1f))
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
            VaultStatCard("Total", "24", Icons.Rounded.Inventory2, Color(0xFFD0BCFF), Modifier.weight(1f))
            VaultStatCard("Breached", "0", Icons.Rounded.GppBad, Color(0xFFCCC2DC), Modifier.weight(1f))
            VaultStatCard("Weak", "3", Icons.Rounded.Password, Color(0xFFFF9800), Modifier.weight(1f))
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
            listOf("MUST CHANGE", "WEAK", "INCOMPLETE", "SAFE").forEach { name ->
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
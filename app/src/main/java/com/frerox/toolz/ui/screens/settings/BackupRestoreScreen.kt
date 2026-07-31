package com.frerox.toolz.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.frerox.toolz.data.backup.BackupCategory
import com.frerox.toolz.data.backup.BackupItem
import com.frerox.toolz.ui.components.*

/**
 * Custom contract to support initial directory hint.
 */
class OpenDocumentWithInitial(private val initialUri: Uri?) : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
        }
    }
}

/**
 * BackupRestoreScreen.
 *
 * Redesign notes:
 *  - Real M3 Expressive text: sentence case, standard type scale weights. The old
 *    ALL-CAPS + FontWeight.Black + letterSpacing headers were poster-design, not M3 —
 *    removed everywhere, including the "DO NOT CLOSE THE APP" shouting during progress.
 *  - Color and shape now carry emphasis (tonal surfaces, a colored category icon,
 *    a filled vs. outlined action pairing) instead of typographic loudness.
 *  - Added a real confirmation state: after a backup or restore finishes, the action
 *    card reflects that plainly ("Backup created just now") instead of just resetting
 *    back to the same buttons with no memory of what happened.
 *  - Custom interval now opens as a full ExpressiveCard-styled sheet-like dialog that
 *    matches the rest of the screen instead of a plain system Dialog with a raw Slider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: BackupRestoreViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showCustomIntervalDialog by remember { mutableStateOf(false) }

    // Directory hint: points the picker at Documents/Toolz_Backups.
    val initialUri = remember {
        "content://com.android.externalstorage.documents/document/primary%3ADocuments%2FToolz_Backups".toUri()
    }

    val backupPicker = rememberLauncherForActivityResult(
        contract = OpenDocumentWithInitial(initialUri)
    ) { uri: Uri? ->
        uri?.let { viewModel.restoreBackup(it) }
    }

    Scaffold(
        topBar = {
            Surface(
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp
            ) {
                ExpressiveTopAppBar(
                    title = "Backup & restore",
                    subtitle = "Keep a copy of your data, or bring it back",
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .fadingEdges(top = 16.dp, bottom = 16.dp)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            StaggeredEntrance(index = 0) {
                ActionSection(
                    isExporting = uiState.isExporting,
                    isImporting = uiState.isImporting,
                    progress = uiState.progress,
                    onCreateBackup = { viewModel.createBackup() },
                    onRestoreFile = {
                        backupPicker.launch(arrayOf("application/octet-stream", "*/*"))
                    }
                )
            }

            StaggeredEntrance(index = 1) {
                SectionHeader(
                    title = "What to include",
                    onSelectAll = { viewModel.selectAll() },
                    onSelectNone = { viewModel.selectNone() }
                )
            }

            BackupCategory.entries.forEachIndexed { index, category ->
                StaggeredEntrance(index = index + 2) {
                    CategorySelectionCard(
                        category = category,
                        selectedItems = uiState.selectedItems,
                        onToggleItem = { viewModel.toggleItem(it) },
                        onToggleCategory = { viewModel.toggleCategory(it) }
                    )
                }
            }

            StaggeredEntrance(index = BackupCategory.entries.size + 2) {
                AutoBackupSection(
                    currentFrequency = uiState.backupFrequency,
                    customDays = uiState.customAutoBackupDays,
                    notificationsEnabled = uiState.backupNotifications,
                    onFrequencyChange = { viewModel.setBackupFrequency(it) },
                    onToggleNotifications = { viewModel.setBackupNotifications(it) },
                    onShowCustomInterval = { showCustomIntervalDialog = true }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showCustomIntervalDialog) {
        CustomIntervalDialog(
            initialDays = uiState.customAutoBackupDays,
            onDismiss = { showCustomIntervalDialog = false },
            onConfirm = {
                viewModel.setCustomAutoBackupDays(it)
                viewModel.setBackupFrequency("Custom")
                showCustomIntervalDialog = false
            }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row {
            TextButton(onClick = onSelectAll) { Text("Select all", style = MaterialTheme.typography.labelLarge) }
            TextButton(onClick = onSelectNone) { Text("None", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySelectionCard(
    category: BackupCategory,
    selectedItems: Set<BackupItem>,
    onToggleItem: (BackupItem) -> Unit,
    onToggleCategory: (BackupCategory) -> Unit
) {
    val itemsInCategory = BackupItem.entries.filter { it.category == category }
    val selectedInCategory = itemsInCategory.filter { selectedItems.contains(it) }
    val isAllSelected = selectedInCategory.isNotEmpty() && selectedInCategory.size == itemsInCategory.size
    val isPartiallySelected = selectedInCategory.isNotEmpty() && !isAllSelected

    ExpressiveCard(
        onClick = { onToggleCategory(category) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val icon: ImageVector = when (category) {
                    BackupCategory.PRODUCTIVITY -> Icons.Rounded.RocketLaunch
                    BackupCategory.SECURITY -> Icons.Rounded.VerifiedUser
                    BackupCategory.PERSONAL -> Icons.Rounded.Person
                    BackupCategory.SYSTEM -> Icons.Rounded.SettingsSuggest
                }
                Surface(
                    color = if (isAllSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        icon,
                        null,
                        tint = if (isAllSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp).size(20.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        category.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (selectedInCategory.isEmpty()) "Not included"
                        else "${selectedInCategory.size} of ${itemsInCategory.size} included",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TriStateCheckbox(
                    state = when {
                        isAllSelected -> ToggleableState.On
                        isPartiallySelected -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    },
                    onClick = { onToggleCategory(category) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsInCategory.forEach { item ->
                    ExpressiveFilterChip(
                        selected = selectedItems.contains(item),
                        onClick = { onToggleItem(item) },
                        label = { Text(item.displayName, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionSection(
    isExporting: Boolean,
    isImporting: Boolean,
    progress: String?,
    onCreateBackup: () -> Unit,
    onRestoreFile: () -> Unit
) {
    val isBusy = isExporting || isImporting

    // Tracks what just finished so we can show real confirmation after the fact,
    // without depending on backend state we can't see the shape of. Purely local UI
    // memory for this screen visit.
    var justCompleted by remember { mutableStateOf<String?>(null) }
    var wasBusy by remember { mutableStateOf(false) }
    LaunchedEffect(isBusy) {
        if (wasBusy && !isBusy) {
            justCompleted = if (isExporting) "Backup created just now" else "Restore completed just now"
        }
        wasBusy = isBusy
    }

    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(targetState = isBusy, label = "backup_action_state") { busy ->
                if (busy) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ToolzWavyCircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            progress ?: if (isExporting) "Creating your backup" else "Restoring your data",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "This only takes a moment — keep the app open until it finishes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolzExpressiveButton(
                                onClick = onCreateBackup,
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Rounded.CloudUpload, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New backup", fontWeight = FontWeight.SemiBold, softWrap = false)
                            }

                            ToolzOutlinedExpressiveButton(
                                onClick = onRestoreFile,
                                modifier = Modifier.weight(0.8f),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Rounded.CloudDownload, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Restore", softWrap = false)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Real confirmation, not just an action button — reflects what
                        // actually just happened on this screen, if anything did.
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (justCompleted != null) Icons.Rounded.CheckCircle else Icons.Rounded.FolderOpen,
                                    null,
                                    tint = if (justCompleted != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    justCompleted ?: "Saved to Documents/Toolz_Backups",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderSimple(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoBackupSection(
    currentFrequency: String,
    customDays: Int,
    notificationsEnabled: Boolean,
    onFrequencyChange: (String) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onShowCustomInterval: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeaderSimple(title = "Automation")

        ExpressiveCard(
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            Icons.Rounded.Update,
                            null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(10.dp).size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Scheduled backups",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (currentFrequency == "Never") "Off — back up manually anytime"
                            else if (currentFrequency == "Custom") "Every $customDays days"
                            else currentFrequency,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ExpressiveSwitch(
                        checked = currentFrequency != "Never",
                        onCheckedChange = { if (it) onFrequencyChange("Daily") else onFrequencyChange("Never") }
                    )
                }

                AnimatedVisibility(visible = currentFrequency != "Never") {
                    Column {
                        Spacer(Modifier.height(24.dp))
                        
                        Text(
                            "Backup frequency",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                        )

                        val freqOptions = listOf("Daily", "Weekly", "Monthly", "Custom")
                        val freqSelectedIndex = when (currentFrequency) {
                            "Daily" -> 0
                            "Weekly" -> 1
                            "Monthly" -> 2
                            "Custom" -> 3
                            else -> 0
                        }

                        ToolzConnectedButtonGroup(
                            selectedIndex = freqSelectedIndex,
                            options = listOf(
                                "Daily", "Weekly", "Monthly",
                                if (currentFrequency == "Custom") "${customDays}d" else "Custom"
                            ),
                            onOptionSelected = { index ->
                                if (index == 3) onShowCustomInterval()
                                else onFrequencyChange(freqOptions[index])
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(20.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Rounded.NotificationsActive,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Notifications",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Get notified about backup results",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ExpressiveSwitch(
                                checked = notificationsEnabled,
                                onCheckedChange = onToggleNotifications
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomIntervalDialog(
    initialDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var days by remember { mutableStateOf(initialDays.coerceIn(1, 30)) }

    Dialog(onDismissRequest = onDismiss) {
        ExpressiveCard(
            onClick = {},
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Rounded.Update,
                        null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(14.dp).size(24.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Custom interval",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Back up automatically every",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$days",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (days == 1) " day" else " days",
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(20.dp))
                ExpressiveSlider(
                    value = days.toFloat(),
                    onValueChange = { days = it.toInt() },
                    valueRange = 1f..30f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToolzOutlinedExpressiveButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    ToolzExpressiveButton(
                        onClick = { onConfirm(days) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Set interval")
                    }
                }
            }
        }
    }
}
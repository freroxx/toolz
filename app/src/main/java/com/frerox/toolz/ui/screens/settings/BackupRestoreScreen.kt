package com.frerox.toolz.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.frerox.toolz.data.backup.BackupCategory
import com.frerox.toolz.data.backup.BackupItem
import com.frerox.toolz.ui.components.*

/**
 * Custom contract to support initial directory hint
 */
class OpenDocumentWithInitial(private val initialUri: Uri?) : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: BackupRestoreViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showCustomIntervalDialog by remember { mutableStateOf(false) }

    // Directory Redirection Hint (Points precisely to Documents/Toolz_Backups)
    val initialUri = remember {
        "content://com.android.externalstorage.documents/document/primary%3ADocuments%2FToolz_Backups".toUri()
    }
    
    // Improved Picker with custom contract for initial URI and broad MIME support
    val backupPicker = rememberLauncherForActivityResult(
        contract = OpenDocumentWithInitial(initialUri)
    ) { uri: Uri? ->
        uri?.let { viewModel.restoreBackup(it) }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Backup & Restore",
                subtitle = "Manage your data snapshots",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Action Section (Primary backup/restore area)
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

            // 2. Data Selection Area
            StaggeredEntrance(index = 1) {
                GroupedSelectionHeader(
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

            // 3. Automation Area
            StaggeredEntrance(index = BackupCategory.entries.size + 2) {
                AutoBackupSection(
                    currentFrequency = uiState.backupFrequency,
                    customDays = uiState.customAutoBackupDays,
                    onFrequencyChange = { viewModel.setBackupFrequency(it) },
                    onShowCustomInterval = { showCustomIntervalDialog = true }
                )
            }

            Spacer(Modifier.height(48.dp))
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
private fun GroupedSelectionHeader(
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "DATA SELECTION",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp
        )
        Row {
            TextButton(onClick = onSelectAll) { Text("SELECT ALL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onSelectNone) { Text("NONE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
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
    val isAllSelected = selectedInCategory.size == itemsInCategory.size

    ExpressiveCard(
        onClick = { onToggleCategory(category) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val icon = when(category) {
                    BackupCategory.PRODUCTIVITY -> Icons.Rounded.RocketLaunch
                    BackupCategory.SECURITY -> Icons.Rounded.VerifiedUser
                    BackupCategory.PERSONAL -> Icons.Rounded.Person
                    BackupCategory.SYSTEM -> Icons.Rounded.SettingsSuggest
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        icon, 
                        null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    category.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Checkbox(
                    checked = isAllSelected,
                    onCheckedChange = { onToggleCategory(category) },
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

    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    progress ?: "Processing...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Please do not close the app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ToolzExpressiveButton(
                        onClick = onCreateBackup,
                        modifier = Modifier.weight(1.5f),
                    ) {
                        Icon(Icons.Rounded.CloudUpload, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("BACKUP")
                    }
                    
                    ToolzOutlinedExpressiveButton(
                        onClick = onRestoreFile,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CloudDownload, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("RESTORE")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Info, 
                        null, 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Stored in Documents/Toolz_Backups",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoBackupSection(
    currentFrequency: String,
    customDays: Int,
    onFrequencyChange: (String) -> Unit,
    onShowCustomInterval: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "AUTOMATION",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
            letterSpacing = 1.2.sp
        )

        ExpressiveCard(
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Update, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Scheduled Backups",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(20.dp))
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Never", "Daily", "Weekly", "Monthly").forEach { option ->
                        ExpressiveFilterChip(
                            selected = currentFrequency == option,
                            onClick = { onFrequencyChange(option) },
                            label = { Text(option) }
                        )
                    }
                    
                    ExpressiveFilterChip(
                        selected = currentFrequency == "Custom",
                        onClick = onShowCustomInterval,
                        label = { 
                            Text(if (currentFrequency == "Custom") "Every $customDays days" else "Custom Interval") 
                        }
                    )
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
    var days by remember { mutableStateOf(initialDays) }

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
                Text(
                    "Custom Interval",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "Backup every $days days",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(32.dp))
                Slider(
                    value = days.toFloat(),
                    onValueChange = { days = it.toInt() },
                    valueRange = 1f..30f,
                    steps = 29,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToolzOutlinedExpressiveButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CANCEL")
                    }
                    ToolzExpressiveButton(
                        onClick = { onConfirm(days) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("SET INTERVAL")
                    }
                }
            }
        }
    }
}

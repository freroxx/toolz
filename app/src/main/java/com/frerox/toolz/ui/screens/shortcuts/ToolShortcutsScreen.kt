package com.frerox.toolz.ui.screens.shortcuts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.R
import com.frerox.toolz.shortcuts.ToolShortcutDefinitions
import com.frerox.toolz.shortcuts.ToolShortcutManager
import com.frerox.toolz.ui.theme.LocalVibrationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolShortcutsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    var searchQuery by remember { mutableStateOf("") }

    val allShortcuts = remember { ToolShortcutDefinitions.all }

    val filtered = remember(searchQuery, allShortcuts) {
        if (searchQuery.isBlank()) allShortcuts
        else {
            val q = searchQuery.trim().lowercase()
            allShortcuts.filter {
                try {
                    val label = context.getString(it.labelRes).lowercase()
                    val desc = context.getString(it.descriptionRes).lowercase()
                    label.contains(q) || desc.contains(q)
                } catch (_: Exception) {
                    it.id.contains(q)
                }
            }
        }
    }

    val isSupported = remember { ToolShortcutManager.isPinnedSupported(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.st_Shortcut_Manage_Title),
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            stringResource(R.string.st_Shortcut_Manage_Desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.st_Shortcut_Manage_Search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            if (!isSupported) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                ) {
                    Text(
                        stringResource(R.string.st_Shortcut_NotSupported),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Text(
                "${filtered.size} tools",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filtered, key = { it.id }) { def ->
                    ToolShortcutRow(
                        def = def,
                        onPin = {
                            vibrationManager?.vibrateClick()
                            ToolShortcutManager.requestPinShortcut(context, def)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolShortcutRow(
    def: com.frerox.toolz.shortcuts.ToolShortcutDef,
    onPin: () -> Unit
) {
    val context = LocalContext.current
    val label = try { context.getString(def.labelRes) } catch (_: Exception) { def.id }
    val desc = try { context.getString(def.descriptionRes) } catch (_: Exception) { "" }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = def.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 1)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f), maxLines = 1)
            }
            FilledTonalButton(
                onClick = onPin,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(Icons.Rounded.AddToHomeScreen, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.st_Shortcut_Pin), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

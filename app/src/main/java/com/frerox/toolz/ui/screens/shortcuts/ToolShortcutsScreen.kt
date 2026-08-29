package com.frerox.toolz.ui.screens.shortcuts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.R
import com.frerox.toolz.shortcuts.ToolShortcutDefinitions
import com.frerox.toolz.shortcuts.ToolShortcutManager
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_Shortcut_Manage_Title),
                subtitle = stringResource(R.string.st_Shortcut_Manage_Desc),
                titleHorizontalAlignment = Alignment.Start,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                largeFlexible = true,
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                // Expressive search field
                StaggeredEntrance(index = 0) {
                    ExpressiveSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.st_Shortcut_Manage_Search)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.AddToHomeScreen, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else null
                    )
                }

                if (!isSupported) {
                    StaggeredEntrance(index = 1) {
                        ExpressiveCard(
                            onClick = {},
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Text(
                                stringResource(R.string.st_Shortcut_NotSupported),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                StaggeredEntrance(index = 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${filtered.size} tools",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        ExpressiveStatePill(
                            text = if (searchQuery.isBlank()) "All 48" else "Filtered",
                            icon = Icons.Rounded.Search,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdges(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)
                ) {
                    itemsIndexed(filtered, key = { _, d -> d.id }) { idx, def ->
                        StaggeredEntrance(index = idx + 3) {
                            ToolShortcutRowExpressive(
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
    }
}

@Composable
private fun ToolShortcutRowExpressive(
    def: com.frerox.toolz.shortcuts.ToolShortcutDef,
    onPin: () -> Unit
) {
    val context = LocalContext.current
    val label = try { context.getString(def.labelRes) } catch (_: Exception) { def.id }
    val desc = try { context.getString(def.descriptionRes) } catch (_: Exception) { "" }

    ExpressiveCard(
        onClick = onPin,
        shape = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = def.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                onClick = onPin,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AddToHomeScreen, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

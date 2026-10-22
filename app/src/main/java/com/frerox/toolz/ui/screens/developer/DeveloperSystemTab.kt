/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * System tab: read-only app/device context, appearance summary, storage and
 * Room inspectors, preferences, the screen launcher, links and maintenance.
 */

package com.frerox.toolz.ui.screens.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.developer.DevAppInfo
import com.frerox.toolz.data.developer.DevPrefsState
import com.frerox.toolz.data.developer.DevStorageState
import com.frerox.toolz.data.developer.DevTableState
import com.frerox.toolz.data.developer.formatBytes
import com.frerox.toolz.data.developer.formatDuration
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.screens.developer.components.DevActionRow
import com.frerox.toolz.ui.screens.developer.components.DevCard
import com.frerox.toolz.ui.screens.developer.components.DevDangerCard
import com.frerox.toolz.ui.screens.developer.components.DevInlineNotice
import com.frerox.toolz.ui.screens.developer.components.DevKeyValue
import com.frerox.toolz.ui.screens.developer.components.DevPill
import com.frerox.toolz.ui.screens.developer.components.DevSectionTitle
import com.frerox.toolz.ui.screens.developer.components.DevStatTile
import com.frerox.toolz.ui.screens.developer.components.DevToggleRow
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DeveloperSystemTab(
    appInfo: DevAppInfo,
    themeMode: String,
    appLanguage: String,
    shakeToOpen: Boolean,
    storageState: DevStorageState,
    tableState: DevTableState,
    prefsState: DevPrefsState,
    onSetShakeToOpen: (Boolean) -> Unit,
    onRefreshStorage: () -> Unit,
    onClearCache: () -> Unit,
    onRefreshTables: () -> Unit,
    onClearDatabase: () -> Unit,
    onRefreshPrefs: () -> Unit,
    onCopyPrefs: (Map<String, String>) -> Unit,
    onOpenRoute: (String) -> Unit,
    onOpenRouteLauncher: () -> Unit,
    onResetFixtures: () -> Unit,
    onResetOnboarding: () -> Unit,
    onResetDeveloperMode: () -> Unit,
    onRestart: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenSystemDeveloperOptions: () -> Unit,
    onOpenDiscord: () -> Unit,
) {
    var showAllTables by rememberSaveable { mutableStateOf(false) }
    var showAllPrefs by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val localeTag = configuration.locales[0]?.toLanguageTag().orEmpty()
    val tableRows = tableState.tables.sumOf { it.rows.coerceAtLeast(0) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "about") {
            DevCard {
                DevSectionTitle(
                    title = "Application",
                    supporting = "Read-only runtime snapshot",
                    action = { DevPill(if (appInfo.debuggable) "Debug" else "Release") },
                )
                DevKeyValue("Version", "${appInfo.versionName} (${appInfo.versionCode})")
                DevKeyValue("Package", appInfo.packageName, monospace = true)
                DevKeyValue("Device", appInfo.deviceLabel)
                DevKeyValue("Android", appInfo.androidLabel)
                DevKeyValue("ABI", appInfo.abi, monospace = true)
                DevKeyValue("PID", appInfo.pid.toString(), monospace = true)
                DevKeyValue("Uptime", formatDuration(appInfo.processUptimeMs), monospace = true)
                DevKeyValue("Density", "${appInfo.densityDpi} dpi", monospace = true)
            }
        }

        item(key = "appearance") {
            DevCard {
                DevSectionTitle(
                    title = "Appearance context",
                    supporting = "Managed in Settings to keep one source of truth",
                )
                DevKeyValue("Theme", themeModeLabel(themeMode))
                DevKeyValue(
                    "Language",
                    if (appLanguage.isBlank()) localeTag.ifBlank { "System" }
                    else "$appLanguage · $localeTag",
                )
                DevKeyValue("Font scale", "${(density.fontScale * 100).roundToInt()}%")
                DevKeyValue(
                    "Layout",
                    if (layoutDirection == LayoutDirection.Rtl) "Right to left" else "Left to right",
                )
                DevActionRow(
                    icon = Icons.Rounded.Settings,
                    title = "Open appearance settings",
                    supporting = "Theme, language, font size and layout",
                    onClick = { onOpenRoute(Screen.Settings.route) },
                )
            }
        }

        item(key = "behavior") {
            DevCard {
                DevSectionTitle(
                    title = "Behavior",
                    supporting = "Input and launch shortcuts",
                )
                DevToggleRow(
                    icon = Icons.Rounded.Vibration,
                    title = "Shake to open Developer Mode",
                    supporting = "Works only while Toolz is in the foreground.",
                    checked = shakeToOpen,
                    onCheckedChange = onSetShakeToOpen,
                )
            }
        }

        item(key = "session") {
            DevCard {
                DevSectionTitle(
                    title = "Session",
                    supporting = "Apply changes or replay setup",
                )
                DevActionRow(
                    icon = Icons.Rounded.RestartAlt,
                    title = "Restart app",
                    supporting = "Apply the current sandbox selection to every screen now.",
                    onClick = onRestart,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                DevActionRow(
                    icon = Icons.Rounded.Refresh,
                    title = "Reset onboarding",
                    supporting = "Show the welcome flow on the next launch.",
                    onClick = onResetOnboarding,
                )
            }
        }

        item(key = "storage") {
            DevCard {
                DevSectionTitle(
                    title = "Storage",
                    supporting = "Private app directories",
                    action = if (storageState.loading) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    } else {
                        null
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DevStatTile(
                        label = "Cache",
                        value = formatBytes(storageState.info.cacheBytes),
                        modifier = Modifier.weight(1f),
                    )
                    DevStatTile(
                        label = "Files",
                        value = formatBytes(storageState.info.filesBytes),
                        modifier = Modifier.weight(1f),
                    )
                    DevStatTile(
                        label = "DB",
                        value = formatBytes(storageState.info.databaseBytes),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (storageState.error != null) {
                    DevInlineNotice(
                        icon = Icons.Rounded.Warning,
                        text = storageState.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onRefreshStorage,
                        enabled = !storageState.loading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(17.dp),
                        )
                        Text("Refresh", maxLines = 1)
                    }
                    Button(
                        onClick = onClearCache,
                        enabled = !storageState.loading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Clear cache", maxLines = 1)
                    }
                }
                Text(
                    text = "Clearing cache is safe. Files, databases and preferences are not touched.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "database") {
            DevCard {
                DevSectionTitle(
                    title = "Room database",
                    supporting = if (tableState.tables.isEmpty()) {
                        "No tables inspected yet"
                    } else {
                        "${tableState.tables.size} tables · $tableRows rows"
                    },
                    action = if (tableState.loading) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    } else {
                        null
                    },
                )
                if (tableState.error != null) {
                    DevInlineNotice(
                        icon = Icons.Rounded.Warning,
                        text = tableState.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                if (tableState.loading && tableState.tables.isEmpty()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                val visibleTables = if (showAllTables) {
                    tableState.tables
                } else {
                    tableState.tables.take(8)
                }
                visibleTables.forEach { table ->
                    DevKeyValue(
                        label = table.name,
                        value = if (table.rows < 0) "unreadable" else table.rows.toString(),
                        monospace = true,
                    )
                }
                if (tableState.tables.size > 8) {
                    TextButton(onClick = { showAllTables = !showAllTables }) {
                        Text(
                            if (showAllTables) {
                                "Show fewer tables"
                            } else {
                                "Show all ${tableState.tables.size} tables"
                            },
                        )
                    }
                }
                if (tableState.tables.isEmpty() && !tableState.loading) {
                    Text(
                        text = "No Room tables were returned.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onRefreshTables,
                    enabled = !tableState.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(17.dp),
                    )
                    Text("Refresh table counts", maxLines = 1)
                }
            }
        }

        item(key = "prefs") {
            DevCard {
                DevSectionTitle(
                    title = "Preferences",
                    supporting = "${prefsState.values.size} keys · sensitive values redacted",
                    action = if (prefsState.loading) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    } else {
                        null
                    },
                )
                if (prefsState.error != null) {
                    DevInlineNotice(
                        icon = Icons.Rounded.Warning,
                        text = prefsState.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                if (prefsState.values.isEmpty()) {
                    Text(
                        text = if (prefsState.loading) "Reading preferences…" else "No preferences found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val entries = prefsState.values.entries.toList()
                    val visible = if (showAllPrefs) entries else entries.take(12)
                    visible.forEach { (key, value) ->
                        PrefRow(key = key, value = value)
                    }
                    if (entries.size > 12) {
                        TextButton(onClick = { showAllPrefs = !showAllPrefs }) {
                            Text(
                                if (showAllPrefs) {
                                    "Show fewer preferences"
                                } else {
                                    "Show all ${entries.size} preferences"
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onRefreshPrefs,
                        enabled = !prefsState.loading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(17.dp),
                        )
                        Text("Refresh", maxLines = 1)
                    }
                    Button(
                        onClick = { onCopyPrefs(prefsState.values) },
                        enabled = prefsState.values.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Copy snapshot", maxLines = 1)
                    }
                }
                Text(
                    text = "Snapshot stays local. Copy only when you need to inspect it outside the app.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "routes") {
            DevCard {
                DevSectionTitle(
                    title = "Screen launcher",
                    supporting = "${devRoutes.size} routes that need no extra arguments",
                )
                DevActionRow(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    title = "Open any screen",
                    supporting = "Search every real route in the app",
                    onClick = onOpenRouteLauncher,
                )
            }
        }

        item(key = "links") {
            DevCard {
                DevSectionTitle(
                    title = "System & community",
                    supporting = "Links leave Developer Mode",
                )
                DevActionRow(
                    icon = Icons.Rounded.Settings,
                    title = "Android developer options",
                    supporting = "Animation scale, USB debugging and system logs",
                    onClick = onOpenSystemDeveloperOptions,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                DevActionRow(
                    icon = Icons.Rounded.Notifications,
                    title = "App notification settings",
                    supporting = "Permission and channel controls for Toolz",
                    onClick = onOpenNotificationSettings,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                DevActionRow(
                    icon = Icons.Rounded.Forum,
                    title = "Toolz Discord",
                    supporting = "Community support and release notes",
                    onClick = onOpenDiscord,
                )
            }
        }

        item(key = "danger") {
            DevDangerCard {
                DevSectionTitle(
                    title = "Danger zone",
                    supporting = "Destructive local actions. Each one asks for confirmation.",
                )
                DevActionRow(
                    icon = Icons.Rounded.DeleteForever,
                    title = "Clear all Room tables",
                    supporting = "Delete every row in every app database. Preferences and fixtures are kept.",
                    onClick = onClearDatabase,
                    enabled = !tableState.loading,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.22f))
                DevActionRow(
                    icon = Icons.Rounded.Restore,
                    title = "Reset fixture values",
                    supporting = "Restore default sandbox values. The current selection is kept.",
                    onClick = onResetFixtures,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.22f))
                DevActionRow(
                    icon = Icons.Rounded.Warning,
                    title = "Reset Developer Mode",
                    supporting = "Turn every sandbox off and clear crash history. Shake-to-open and fixture values are kept.",
                    onClick = onResetDeveloperMode,
                )
            }
        }

        item(key = "privacy") {
            DevInlineNotice(
                icon = Icons.Rounded.Lock,
                text = "No analytics or tracking. Diagnostics are generated on this device and leave only through the clipboard or system share sheet when you choose.",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrefRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}

private fun themeModeLabel(value: String): String = when (value.uppercase(Locale.ROOT)) {
    "LIGHT" -> "Light"
    "DARK" -> "Dark"
    "SYSTEM" -> "System default"
    else -> value.ifBlank { "System default" }
}

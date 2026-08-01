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

package com.frerox.toolz.data.backup

import android.net.Uri
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ToolzBackupManifest(
    val payloadFormatVersion: Int,
    val appPackageName: String,
    val appVersionCode: Long,
    val appVersionName: String,
    val createdAtMillis: Long,
    val androidSdk: Int,
    val databaseName: String,
    val databaseSchemaVersion: Int,
    val entryHashes: Map<String, String>,
    val notes: List<String> = emptyList(),
    val includedItems: Set<BackupItem> = BackupItem.values().toSet()
)

enum class BackupCategory(val displayName: String) {
    PRODUCTIVITY("Productivity"),
    SECURITY("Security & Privacy"),
    PERSONAL("Personal Data"),
    SYSTEM("System & Settings")
}

enum class BackupItem(val displayName: String, val category: BackupCategory) {
    NOTES("Notes & Attachments", BackupCategory.PRODUCTIVITY),
    TASKS("Tasks & To-Dos", BackupCategory.PRODUCTIVITY),
    CALENDAR("Calendar Events", BackupCategory.PRODUCTIVITY),
    
    AI_KEYS("AI Provider Keys", BackupCategory.SECURITY),
    PASSWORDS("Password Vault", BackupCategory.SECURITY),
    
    AI_HISTORY("AI Chat History", BackupCategory.PERSONAL),
    SEARCH_HISTORY("Search & Bookmarks", BackupCategory.PERSONAL),
    NOTIFICATIONS("Notification Vault", BackupCategory.PERSONAL),
    CLIPBOARD("Clipboard History", BackupCategory.PERSONAL),
    
    SETTINGS("App Preferences", BackupCategory.SYSTEM),
    STEPS("Step Counter Data", BackupCategory.SYSTEM),
    MATH_HISTORY("Math & Calculations", BackupCategory.SYSTEM),
    PDF_METADATA("PDF Tools Data", BackupCategory.SYSTEM),
    OTHERS("Miscellaneous Data", BackupCategory.SYSTEM)
}

data class BackupExportResult(
    val uri: Uri,
    val fileName: String,
    val sha256: String,
    val byteCount: Long,
    val entryCount: Int
)

data class BackupImportResult(
    val manifest: ToolzBackupManifest,
    val restoredEntries: Int,
    val requiresAppRestart: Boolean = true
)

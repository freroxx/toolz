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
import com.frerox.toolz.util.secure.SecureContainer
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
    WHISPER("Whisper Messenger"),
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
    
    WHISPER_ACCESS("Whisper Access Files", BackupCategory.WHISPER),
    WHISPER_STATE("Whisper Local State", BackupCategory.WHISPER),
    WHISPER_HISTORY("Whisper Message History", BackupCategory.WHISPER),
    
    SETTINGS("App Preferences", BackupCategory.SYSTEM),
    STEPS("Step Counter Data", BackupCategory.SYSTEM),
    MATH_HISTORY("Math & Calculations", BackupCategory.SYSTEM),
    PDF_METADATA("PDF Tools Data", BackupCategory.SYSTEM),
    CATALOG_DATA("Music Catalog Taste Profile", BackupCategory.PERSONAL),
    OTHERS("Miscellaneous Data", BackupCategory.SYSTEM)
}

/**
 * How the outer backup archive is protected.
 *
 * [None] keeps the historical plain `.tzbk` zip (the UI warns that it contains
 * plaintext secrets). [Secret] wraps the whole zip in an authenticated
 * [com.frerox.toolz.util.secure.SecureContainer] so the archive is unreadable
 * without the user's PIN/password.
 */
sealed interface BackupProtection {
    data object None : BackupProtection
    data class Secret(
        val value: String,
        val mode: SecureContainer.SecretMode,
    ) : BackupProtection
}

/** The picked archive is encrypted and the caller did not supply a secret. */
class BackupPasswordRequiredException :
    IllegalArgumentException("This backup is password protected.")

/** The supplied secret did not open the encrypted archive. */
class BackupWrongPasswordException :
    IllegalArgumentException("Incorrect backup password.")

/**
 * Whisper history contains readable message text, so it is only exported inside
 * a password-protected container. Plain `.tzbk` archives keep their historical
 * behaviour and simply omit it.
 */
class BackupHistoryRequiresPasswordException :
    IllegalArgumentException("Whisper history requires a backup password.")

data class BackupExportResult(
    val uri: Uri,
    val fileName: String,
    val sha256: String,
    val byteCount: Long,
    val entryCount: Int,
    val encrypted: Boolean = false,
)

data class BackupImportResult(
    val manifest: ToolzBackupManifest,
    val restoredEntries: Int,
    val requiresAppRestart: Boolean = true,
    // CAL-P3 (additive): future calendar alarms re-scheduled after CALENDAR restore.
    val rescheduledCalendarAlarms: Int = 0,
    // T-P0-03 (additive): future task alarms re-scheduled after TASKS restore.
    val rescheduledTaskAlarms: Int = 0,
    // True when the archive was opened from a password-protected container.
    val wasEncrypted: Boolean = false,
    // Whisper history was staged for replay after the next sign-in.
    val historyImportDeferred: Boolean = false,
)

// ── Clipboard backup v2 ─────────────────────────────────────────────────────

/**
 * Portable clipboard payload. The archive is intentionally plaintext for now
 * (`encryption = "none"`) so users can recover their own data; a future code
 * change can switch this to an encrypted container without changing the DTO.
 *
 * Attachments are stored as decrypted bytes under
 * `data/clipboard_blobs/<sha256>.bin`, then re-encrypted with the local
 * Keystore-backed key on restore.
 */
@JsonClass(generateAdapter = true)
data class ClipboardBackupV2(
    val version: Int = 2,
    val encryption: String = "none",
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val clips: List<ClipboardBackupClipV2> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ClipboardBackupClipV2(
    val content: String,
    val timestamp: Long,
    val type: String,
    val isPinned: Boolean = false,
    val previewUrl: String? = null,
    val sourceApp: String? = null,
    val summary: String? = null,
    val isAiProcessed: Boolean = false,
    val contentHash: Int = 0,
    val source: String? = null,
    val contentHashSha256: String? = null,
    val htmlContent: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val attachmentCount: Int = 0,
    val isSensitive: Boolean = false,
    val sensitivityReason: String? = null,
    val isLocked: Boolean = false,
    val passwordVaultMatch: Boolean = false,
    val metadataJson: String? = null,
    val deletedAt: Long? = null,
    val expiresAt: Long? = null,
    val aiState: String? = null,
    val aiError: String? = null,
    val attachments: List<ClipboardBackupAttachmentV2> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ClipboardBackupAttachmentV2(
    val blobId: String,
    /** Zip entry with decrypted bytes, or null when the blob was unreadable. */
    val blobPath: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val position: Int = 0,
    val width: Int? = null,
    val height: Int? = null,
)

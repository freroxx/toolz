/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.frerox.toolz.data.password.PasswordDao
import com.frerox.toolz.data.password.PasswordEntity
import com.frerox.toolz.util.CryptoManager
import com.frerox.toolz.util.password.PasswordGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WhisperAccessPayload(
    val version: Int = 1,
    val app: String = "toolz_whisper",
    val username: String,
    @SerialName("auth_type") val authType: String, // "TOKEN" or "PASSWORD"
    val credential: String, // Raw token or password
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Singleton
class WhisperAubupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val passwordDao: PasswordDao,
) {
    companion object {
        fun isValidWhisperCode(code: String): Boolean =
            code.length == 6 && code.all { it in '0'..'9' }

        // V2-FIX B3: on pre-Android-Q devices the access file is written to the legacy
        // PUBLIC Downloads dir, where other apps may read it. Callers append this to the
        // success toast when Build.VERSION.SDK_INT < 29. English-only here (non-UI data
        // layer) — MainScreen now appends the localized
        // R.string.st_Whisper_Aubup_LegacyStorageWarning instead of this constant.
        const val WARNING_LEGACY_STORAGE =
            "Warning: On this Android version the file was saved to shared storage (Downloads/Toolz), where other apps may be able to read it."

        // V3-FIX (multi-account): stable filename prefix — kept identical to the old
        // scheme so previously-written access files still match the folder scanner.
        const val ACCESS_FILE_PREFIX = "whisper_access_"

        /**
         * V6-R7 (#auto-detect): the Downloads scan reads PUBLIC storage via the File
         * API — on API ≥30 that requires "All files access" (MANAGE_EXTERNAL_STORAGE
         * granted in Settings); on ≤29 the runtime READ/WRITE pair is enough.
         */
        fun hasFileAccessForScan(context: android.content.Context): Boolean =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.READ_EXTERNAL_STORAGE,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }

        /** Settings deep-link for the All-files-access toggle (API ≥30). */
        fun allFilesAccessIntent(context: android.content.Context): android.content.Intent =
            android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                android.net.Uri.fromParts("package", context.packageName, null),
            )
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Intelligently saves or updates Whisper credentials in the Toolz Password Vault.
     * Prevents duplicate entries on repeated logins.
     */
    suspend fun upsertWhisperVaultEntry(
        name: String,
        username: String,
        credential: String,
        // V3-FIX: persisted into PasswordEntity.isToken so type detection no longer
        // depends on vault-name sniffing. null leaves the flag unknown/legacy.
        isToken: Boolean? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUser = username.trim().lowercase()
            val cleanCred = credential.trim()
            val all = passwordDao.getAllPasswordsSync()
            // P1 FIX (reviewwhisper.md): Matching on password==cleanCred collided across
            // users (any vault entry with same password would be overwritten). Match only
            // on url + username (canonical key), consistent with createAccessFileForUser.
            val existing = all.find { entity ->
                entity.url == "whisper.toolz.app" &&
                    entity.username.equals(cleanUser, ignoreCase = true)
            }

            if (existing != null) {
                val updated = existing.copy(
                    name = name,
                    username = cleanUser,
                    password = cleanCred,
                    strength = PasswordGenerator.calculateStrength(cleanCred),
                    // V3-FIX: write the type flag explicitly when known (null keeps it).
                    isToken = isToken,
                    lastUsedAt = System.currentTimeMillis()
                )
                passwordDao.updatePassword(updated)
            } else {
                val newEntity = PasswordEntity(
                    name = name,
                    url = "whisper.toolz.app",
                    username = cleanUser,
                    password = cleanCred,
                    strength = PasswordGenerator.calculateStrength(cleanCred),
                    // V3-FIX: write the type flag explicitly when known.
                    isToken = isToken,
                    createdAt = System.currentTimeMillis(),
                    lastUsedAt = System.currentTimeMillis()
                )
                passwordDao.insertPassword(newEntity)
            }
        }
    }

    // P0-3 FIX: Q+ scoped storage — public Downloads via MediaStore is the only
    // uninstall-persistent location. App-specific external files (scoped) is deleted
    // on uninstall, so it defeats the backup purpose. Writes now try MediaStore
    // public Downloads/Toolz first; scoped is fallback only if MediaStore fails.
    private fun resolveToolzDir(): File {
        return try {
            val legacy = File(
                @Suppress("DEPRECATION") Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Toolz"
            )
            val scoped = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "Toolz")
            // On pre-Q, legacy public dir is freely writable via File API
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (!legacy.exists()) legacy.mkdirs()
                if (legacy.exists() && legacy.canWrite()) return legacy
            }
            // On Q+, legacy File API may still work if MANAGE_EXTERNAL_STORAGE granted, try it first for persistence
            if (legacy.exists() && legacy.canWrite()) return legacy
            // Try to ensure legacy via MediaStore probe (best-effort) — actual write will use MediaStore path
            // Fallback to scoped which is always writable but NOT persistent
            if (!scoped.exists()) scoped.mkdirs()
            scoped
        } catch (_: Exception) {
            File(context.filesDir, "Toolz_access").apply { if (!exists()) mkdirs() }
        }
    }

    private fun writeViaMediaStore(fileName: String, content: String): File? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Toolz")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            // Return a File pointing to legacy path for caller convenience (even though MediaStore holds the truth)
            // On Q+ the File API path may not be directly readable without permission, but we return it for UI toast
            @Suppress("DEPRECATION")
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Toolz/$fileName")
        } catch (_: Exception) { null }
    }

    private fun queryMediaStoreForEncFiles(): List<File> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val result = mutableListOf<File>()
        try {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.RELATIVE_PATH)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
            val args = arrayOf("whisper%.enc", "%Download/Toolz%")
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    if (!name.lowercase().endsWith(".enc")) continue
                    if (!name.lowercase().contains("whisper") && !name.lowercase().startsWith(ACCESS_FILE_PREFIX)) continue
                    @Suppress("DEPRECATION")
                    val legacyFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Toolz/$name")
                    // Even if File API can't list, the MediaStore entry proves it exists — add legacyFile handle
                    result += legacyFile
                    // Also check if file actually exists via File API (when permission granted) to get lastModified
                    if (!legacyFile.exists()) {
                        // Keep synthetic entry with lastModified 0 — will be sorted last
                    }
                }
            }
            // Also sweep Downloads root via MediaStore (user moved file out of Toolz)
            val rootArgs = arrayOf("whisper%.enc", "%Download%")
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, rootArgs, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val relPath = cursor.getString(pathIdx) ?: ""
                    if (!name.lowercase().contains("whisper")) continue
                    if (relPath.contains("Toolz")) continue // already added
                    @Suppress("DEPRECATION")
                    val rootFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name)
                    if (rootFile !in result) result += rootFile
                }
            }
        } catch (_: Exception) {}
        return result
    }

    /**
     * V3-FIX (multi-account): access files are now named after the human-readable
     * display name so a user owning several Whisper accounts can tell the files apart
     * in Downloads/Toolz. SanitizedDisplayName keeps only [A-Za-z0-9_-] and is capped
     * at 24 chars; fallback chain is display name -> username -> literal "account"
     * (empty after sanitization falls through to the next candidate). The
     * whisper_access_ prefix is preserved so old files still scan.
     */
    private fun accessFileStem(displayName: String?, username: String): String {
        val candidates = listOfNotNull(
            displayName?.trim()?.takeIf { it.isNotEmpty() },
            username.trim().takeIf { it.isNotEmpty() },
        )
        for (candidate in candidates) {
            val sanitized = candidate.filter { c ->
                c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '-'
            }.take(24)
            if (sanitized.isNotEmpty()) return sanitized
        }
        return "account"
    }

    suspend fun createAccessFile(
        username: String,
        authType: String,
        tokenOrPassword: String,
        displayName: String?,
        whisperCode: String,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidWhisperCode(whisperCode)) { "Whisper Code must be exactly 6 digits." }
            val payload = WhisperAccessPayload(
                username = username.trim().lowercase(),
                authType = authType,
                credential = tokenOrPassword.trim(),
                displayName = displayName?.trim(),
            )
            val jsonString = json.encodeToString(payload)

            // P0-2 NOTE: 6-digit code = 1M entropy. CryptoManager now hardens this file
            // with PBKDF2 310000 iterations + random 16-byte salt (V2-FIX B1; legacy
            // .enc files at 65536 still decrypt via the fallback path).
            // Offline brute-force remains trivial if .enc is exfiltrated; mitigation is user
            // choosing long random password OR future 8+ alphanum requirement. For 1.0 we
            // keep 6-digit for compat but document KDF params and add rate-limit on decrypt
            // (decryptAccessFile is throttled at UI 300ms + caller delay).
            val (encryptedText, success) = CryptoManager.encryptWithPassphrase(
                plaintext = jsonString,
                passphrase = whisperCode.toCharArray(),
            )
            if (!success || encryptedText.isBlank()) {
                error("Encryption failed with the provided Whisper Code.")
            }

            val fileName = "${ACCESS_FILE_PREFIX}${accessFileStem(displayName, username)}.enc"
            // Try MediaStore public Downloads/Toolz first on Q+ (persistent across uninstall)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mediaFile = writeViaMediaStore(fileName, encryptedText)
                if (mediaFile != null) {
                    // Also keep a copy in scoped for immediate scan fallback (best-effort, ignore failures)
                    try {
                        val scopedDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "Toolz")
                        if (!scopedDir.exists()) scopedDir.mkdirs()
                        val scopedFile = File(scopedDir, fileName)
                        FileOutputStream(scopedFile).use { it.write(encryptedText.toByteArray(Charsets.UTF_8)) }
                    } catch (_: Exception) {}
                    return@runCatching mediaFile
                }
            }
            val toolzDir = resolveToolzDir()
            // P1 privacy: filenames no longer embed a hash of the raw username.
            // V3-FIX (multi-account): the visible stem is now the sanitized display name
            val targetFile = File(toolzDir, fileName)
            FileOutputStream(targetFile).use { output ->
                output.write(encryptedText.toByteArray(Charsets.UTF_8))
            }
            targetFile
        }
    }

    suspend fun createAccessFileForUser(
        username: String,
        displayName: String?,
        whisperCode: String,
        fallbackCredential: String? = null,
        // V2-FIX B4: explicit token classification from the caller when known (e.g. the
        // live session). null keeps the backward-compatible vault-name heuristic.
        isToken: Boolean? = null,
    ): Result<File> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim().lowercase()
        // V2-FIX B5: loads the whole vault; a DAO-scoped query would be preferable, but
        // PasswordDao has no whisper-specific filtered query today (getPasswordsByDomain
        // matches name LIKE '%domain%' and would miss "Whisper: user" entries whose url
        // is null) — deferred to avoid touching the shared Room schema in this pass.
        val allPasswords = passwordDao.getAllPasswordsSync()
        val matched = allPasswords.find {
            (it.url?.contains("whisper.toolz.app", ignoreCase = true) == true || it.name.startsWith("Whisper", ignoreCase = true)) &&
            it.username.equals(cleanUser, ignoreCase = true)
        }

        val credential = matched?.password?.ifBlank { null } ?: fallbackCredential?.ifBlank { null }
        if (credential.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("No credentials found for @$cleanUser in Password Vault. Please re-enter credentials."))
        }

        // P2-14 FIX: Don't misclassify hex passwords as tokens. Only trust vault name "Anon".
        // V2-FIX B4: an explicit caller hint wins; heuristic only when metadata can't tell.
        // V3-FIX: read precedence is caller hint -> persisted entity flag -> legacy
        // name-substring heuristic (only for rows predating the isToken column).
        val resolvedIsToken = isToken
            ?: matched?.isToken
            ?: (matched?.name?.contains("Anon", ignoreCase = true) == true)
        val authType = if (resolvedIsToken) "TOKEN" else "PASSWORD"

        createAccessFile(
            username = cleanUser,
            authType = authType,
            tokenOrPassword = credential,
            displayName = displayName,
            whisperCode = whisperCode,
        )
    }

    suspend fun scanVaultForWhisperAccounts(): List<PasswordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            // V2-FIX B5: full-vault load kept — no existing PasswordDao query matches the
            // url-domain OR name-prefix predicate (see note in createAccessFileForUser);
            // adding a scoped DAO query is deferred.
            // V3-FIX: returned entities carry the persisted isToken flag — consumers
            // should prefer it over the name-substring heuristic.
            val all = passwordDao.getAllPasswordsSync()
            val matched = all.filter { entity ->
                entity.url?.contains("whisper.toolz.app", ignoreCase = true) == true ||
                entity.name.startsWith("Whisper", ignoreCase = true)
            }
            // V3-FIX (multi-account): most-recent-first everywhere. PasswordEntity carries
            // a date field (lastUsedAt, bumped on every upsert/login) — primary sort key,
            // descending. Tie-breaks: newer createdAt first, then case-insensitive
            // username ascending so ordering stays deterministic across scans when
            // timestamps are equal.
            matched.sortedWith(
                compareByDescending<PasswordEntity> { it.lastUsedAt }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.username.lowercase() }
            )
        }.getOrDefault(emptyList())
    }

    /**
     * V3-FIX (multi-account): [vaultAccounts] enables conservative filename-level
     * dedupe against known vault rows; defaults to none for legacy callers.
     */
    suspend fun scanToolzFolderForAccessFiles(
        vaultAccounts: List<PasswordEntity> = emptyList(),
    ): List<File> = withContext(Dispatchers.IO) {
        runCatching {
            // V3-FIX (multi-account): skip an .enc file whose embedded stem clearly
            // equals a vault account's username (old naming scheme wrote the sanitized
            // username) so restoring from the vault doesn't offer a redundant duplicate.
            // LIMITATION: this is filename-only heuristics. Payload-level dedupe (two
            // differently-named files holding the SAME credential) requires decrypting
            // every .enc with a Whisper Code we don't have at scan time and is
            // deliberately deferred — hiding a genuine second account behind a false
            // "duplicate" would be worse than showing a rare duplicate row.
            val knownStems = buildSet {
                vaultAccounts.forEach { acct ->
                    acct.username.trim().lowercase().takeIf { it.isNotEmpty() }?.let { add(it) }
                    // Vault names look like "Whisper: user" / "Whisper Anon: user".
                    acct.name.substringAfter(':', "").trim().lowercase()
                        .takeIf { it.isNotEmpty() }?.let { add(it) }
                }
            }
            // Scan both scoped (Q+ writes) and legacy public dir for backward compat, plus MediaStore on Q+
            val mediaFiles = queryMediaStoreForEncFiles()
            val scopedDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "Toolz")
            @Suppress("DEPRECATION")
            val legacyDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Toolz")
            val files = mutableListOf<File>()
            files += mediaFiles
            for (dir in listOf(scopedDir, legacyDir)) {
                if (!dir.exists() || !dir.isDirectory) continue
                val listed = dir.listFiles() ?: continue
                files += listed.filter { file ->
                    file.isFile && file.name.lowercase().endsWith(".enc") &&
                    (file.name.lowercase().contains("whisper") || file.name.lowercase().startsWith(ACCESS_FILE_PREFIX))
                }
            }
            // Also scan app-internal fallback (used if external unavailable)
            val internalDir = File(context.filesDir, "Toolz_access")
            if (internalDir.exists() && internalDir.isDirectory) {
                internalDir.listFiles()?.let { listed ->
                    files += listed.filter { it.isFile && it.name.lowercase().endsWith(".enc") }
                }
            }
            // V6-R7 FIX (auto-detect): also sweep the PUBLIC Downloads ROOT — users
            // frequently move access files out of /Toolz. File-API listing here works
            // on ≤API29 or when legacy storage is granted; harmless otherwise.
            // On Q+ MediaStore already covered root, but keep File API for <Q or granted permission.
            @Suppress("DEPRECATION")
            val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsRoot.listFiles()?.let { listed ->
                files += listed.filter { file ->
                    file.isFile && file.name.lowercase().endsWith(".enc") &&
                        file.name.lowercase().contains("whisper")
                }
            }
            // Most-recent-first kept (spec b); dedupe (path) applied after sorting.
            val seen = mutableSetOf<String>()
            files.sortedByDescending { it.lastModified() }
                .filter { seen.add(it.canonicalFile.absolutePath) }
                .filterNot { file ->
                val stem = file.name.lowercase()
                    .removePrefix(ACCESS_FILE_PREFIX)
                    .removeSuffix(".enc")
                stem.isNotBlank() && stem in knownStems
            }
        }.getOrDefault(emptyList())
    }

    suspend fun decryptAccessFile(file: File, whisperCode: String): Result<WhisperAccessPayload> = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidWhisperCode(whisperCode)) { "Whisper Code must be exactly 6 digits." }
            // On Q+ the File API may report !exists even though MediaStore has the file (no MANAGE_EXTERNAL_STORAGE).
            // Try File API first, then MediaStore fallback.
            var cipherText: String? = null
            if (file.exists() && file.length() > 0 && file.length() <= 1_200_000L) {
                cipherText = runCatching { FileInputStream(file).use { it.bufferedReader().readText().trim() } }.getOrNull()
            }
            if (cipherText.isNullOrBlank()) {
                // MediaStore fallback on Q+
                cipherText = readViaMediaStore(file.name)
            }
            if (cipherText.isNullOrBlank()) {
                if (!file.exists()) error("Could not read the selected file")
                if (file.length() == 0L) error("Could not read the selected file")
                if (file.length() > 1_200_000L) error("Access file exceeds the 1 MB limit")
                // Last attempt via FileInputStream without length check
                cipherText = FileInputStream(file).use { it.bufferedReader().readText().trim() }
            }
            if (cipherText.isNullOrBlank()) error("Could not read the selected file")
            decryptCiphertext(cipherText, whisperCode)
        }
    }

    private fun readViaMediaStore(fileName: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val args = arrayOf(fileName)
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val id = cursor.getLong(idIdx)
                    val uri = android.net.Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                    resolver.openInputStream(uri)?.use { it.bufferedReader().readText().trim() }
                } else null
            }
        } catch (_: Exception) { null }
    }

    suspend fun decryptAccessBytes(bytes: ByteArray, whisperCode: String): Result<WhisperAccessPayload> = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidWhisperCode(whisperCode)) { "Whisper Code must be exactly 6 digits." }
            if (bytes.isEmpty()) error("Could not read the selected file")
            if (bytes.size > 1_200_000) error("Access file exceeds the 1 MB limit")
            val cipherText = String(bytes, Charsets.UTF_8).trim()
            if (cipherText.isBlank()) error("Could not read the selected file")
            decryptCiphertext(cipherText, whisperCode)
        }
    }

    private fun decryptCiphertext(cipherText: String, whisperCode: String): WhisperAccessPayload {
        // V2-FIX B1: hardened 310000-iteration format first, legacy 65536 fallback so
        // pre-B1 .enc access files keep decrypting. Also tolerate whitespace/line-wrapped Base64 from email/cloud.
        val cleaned = cipherText.trim().replace(Regex("\\s"), "")
        if (cleaned.isEmpty()) error("Could not read the selected file")
        val (decryptedJson, success) = CryptoManager.decryptWithPassphrase(
            combinedBase64 = cleaned,
            passphrase = whisperCode.toCharArray(),
        )
        if (!success) {
            // Preserve specific malformed signal so mapper can show AubupInvalidFile instead of generic Incorrect Code
            if (decryptedJson.contains("Malformed", ignoreCase = true)) {
                error("Malformed encrypted data")
            }
            error("Incorrect Whisper Code or corrupted access file.")
        }
        if (decryptedJson.isBlank()) error("Incorrect Whisper Code or corrupted access file.")
        val payload = json.decodeFromString<WhisperAccessPayload>(decryptedJson)
        require(payload.app == "toolz_whisper") { "This file is not a valid Whisper Access File." }
        return payload
    }
}

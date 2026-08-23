/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import android.os.Environment
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
        isToken: Boolean = false,
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
                    createdAt = System.currentTimeMillis(),
                    lastUsedAt = System.currentTimeMillis()
                )
                passwordDao.insertPassword(newEntity)
            }
        }
    }

    // P0-3 FIX: Deprecated getExternalStoragePublicDirectory fails on Android Q+ scoped storage.
    // Provide modern resolver: Q+ uses MediaStore/Downloads via app-specific fallback + legacy dir for reading.
    private fun resolveToolzDir(): File {
        return try {
            // On Q+ the public Downloads/Toolz is restricted; prefer app's external files dir
            // plus also keep legacy for reading old files. Writes go to scoped location that
            // does not require STORAGE permission.
            val scoped = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "Toolz")
            // Also ensure legacy dir exists for backward-compat scanning (best-effort)
            val legacy = File(
                @Suppress("DEPRECATION") Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Toolz"
            )
            // Prefer scoped if we are on Q+, otherwise legacy still works for write.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                if (!scoped.exists()) scoped.mkdirs()
                scoped
            } else {
                if (!legacy.exists()) legacy.mkdirs()
                if (legacy.exists() && legacy.canWrite()) legacy else scoped.apply { if (!exists()) mkdirs() }
            }
        } catch (_: Exception) {
            File(context.filesDir, "Toolz_access").apply { if (!exists()) mkdirs() }
        }
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

            val toolzDir = resolveToolzDir()
            // P1 privacy: filename no longer leaks hash of raw username; use only sanitized 8-char prefix.
            val rawUser = username.trim().lowercase()
            val cleanUser = rawUser.replace(Regex("[^a-z0-9_]"), "_").take(16)
            val targetFile = File(toolzDir, "whisper_access_${cleanUser}.enc")

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
        val resolvedIsToken = isToken ?: (matched?.name?.contains("Anon", ignoreCase = true) == true)
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
            val all = passwordDao.getAllPasswordsSync()
            all.filter { entity ->
                entity.url?.contains("whisper.toolz.app", ignoreCase = true) == true ||
                entity.name.startsWith("Whisper", ignoreCase = true)
            }
        }.getOrDefault(emptyList())
    }

    suspend fun scanToolzFolderForAccessFiles(): List<File> = withContext(Dispatchers.IO) {
        runCatching {
            // Scan both scoped (Q+ writes) and legacy public dir for backward compat.
            val scopedDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "Toolz")
            @Suppress("DEPRECATION")
            val legacyDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Toolz")
            val files = mutableListOf<File>()
            for (dir in listOf(scopedDir, legacyDir)) {
                if (!dir.exists() || !dir.isDirectory) continue
                val listed = dir.listFiles() ?: continue
                files += listed.filter { file ->
                    file.isFile && file.name.lowercase().endsWith(".enc") &&
                    (file.name.lowercase().contains("whisper") || file.name.lowercase().startsWith("whisper_access"))
                }
            }
            // Also scan app-internal fallback (used if external unavailable)
            val internalDir = File(context.filesDir, "Toolz_access")
            if (internalDir.exists() && internalDir.isDirectory) {
                internalDir.listFiles()?.let { listed ->
                    files += listed.filter { it.isFile && it.name.lowercase().endsWith(".enc") }
                }
            }
            files.sortedByDescending { it.lastModified() }
        }.getOrDefault(emptyList())
    }

    suspend fun decryptAccessFile(file: File, whisperCode: String): Result<WhisperAccessPayload> = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidWhisperCode(whisperCode)) { "Whisper Code must be exactly 6 digits." }
            val cipherText = FileInputStream(file).use { it.bufferedReader().readText() }
            decryptCiphertext(cipherText, whisperCode)
        }
    }

    suspend fun decryptAccessBytes(bytes: ByteArray, whisperCode: String): Result<WhisperAccessPayload> = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidWhisperCode(whisperCode)) { "Whisper Code must be exactly 6 digits." }
            val cipherText = String(bytes, Charsets.UTF_8)
            decryptCiphertext(cipherText, whisperCode)
        }
    }

    private fun decryptCiphertext(cipherText: String, whisperCode: String): WhisperAccessPayload {
        // V2-FIX B1: hardened 310000-iteration format first, legacy 65536 fallback so
        // pre-B1 .enc access files keep decrypting.
        val (decryptedJson, success) = CryptoManager.decryptWithPassphrase(
            combinedBase64 = cipherText,
            passphrase = whisperCode.toCharArray(),
        )
        if (!success || decryptedJson.isBlank()) {
            error("Incorrect Whisper Code or corrupted access file.")
        }
        val payload = json.decodeFromString<WhisperAccessPayload>(decryptedJson)
        require(payload.app == "toolz_whisper") { "This file is not a valid Whisper Access File." }
        return payload
    }
}

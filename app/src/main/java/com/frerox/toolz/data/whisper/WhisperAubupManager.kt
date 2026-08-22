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
            code.length == 4 && code.all { it in '0'..'9' }
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createAccessFile(
        username: String,
        authType: String,
        tokenOrPassword: String,
        displayName: String?,
        whisperCode: String,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidWhisperCode(whisperCode)) { "Whisper Code must be exactly 4 digits." }
            val payload = WhisperAccessPayload(
                username = username.trim().lowercase(),
                authType = authType,
                credential = tokenOrPassword.trim(),
                displayName = displayName?.trim(),
            )
            val jsonString = json.encodeToString(payload)

            val (encryptedText, success) = CryptoManager.encryptAes(
                plaintext = jsonString,
                password = whisperCode.toCharArray(),
            )
            if (!success || encryptedText.isBlank()) {
                error("Encryption failed with the provided Whisper Code.")
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val toolzDir = File(downloadsDir, "Toolz").apply { if (!exists()) mkdirs() }
            val cleanUser = username.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
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
    ): Result<File> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim().lowercase()
        val allPasswords = passwordDao.getAllPasswordsSync()
        val matched = allPasswords.find {
            (it.url?.contains("whisper.toolz.app", ignoreCase = true) == true || it.name.startsWith("Whisper", ignoreCase = true)) &&
            it.username.equals(cleanUser, ignoreCase = true)
        }

        val credential = matched?.password?.ifBlank { null } ?: fallbackCredential?.ifBlank { null }
        if (credential.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("No credentials found for @$cleanUser in Password Vault. Please re-enter credentials."))
        }

        val isToken = matched?.name?.contains("Anon", ignoreCase = true) == true || (credential.length == 64 && credential.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
        val authType = if (isToken) "TOKEN" else "PASSWORD"

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
            val all = passwordDao.getAllPasswordsSync()
            all.filter { entity ->
                entity.url?.contains("whisper.toolz.app", ignoreCase = true) == true ||
                entity.name.startsWith("Whisper", ignoreCase = true)
            }
        }.getOrDefault(emptyList())
    }

    suspend fun scanToolzFolderForAccessFiles(): List<File> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val toolzDir = File(downloadsDir, "Toolz")
            if (!toolzDir.exists() || !toolzDir.isDirectory) return@runCatching emptyList()

            val files = toolzDir.listFiles() ?: return@runCatching emptyList()
            files.filter { file ->
                file.isFile && file.name.lowercase().endsWith(".enc") &&
                (file.name.lowercase().contains("whisper") || file.name.lowercase().startsWith("whisper_access"))
            }.sortedByDescending { it.lastModified() }
        }.getOrDefault(emptyList())
    }

    suspend fun decryptAccessFile(file: File, whisperCode: String): Result<WhisperAccessPayload> = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidWhisperCode(whisperCode)) { "Whisper Code must be exactly 4 digits." }
            val cipherText = FileInputStream(file).use { it.bufferedReader().readText() }
            decryptCiphertext(cipherText, whisperCode)
        }
    }

    suspend fun decryptAccessBytes(bytes: ByteArray, whisperCode: String): Result<WhisperAccessPayload> = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidWhisperCode(whisperCode)) { "Whisper Code must be exactly 4 digits." }
            val cipherText = String(bytes, Charsets.UTF_8)
            decryptCiphertext(cipherText, whisperCode)
        }
    }

    private fun decryptCiphertext(cipherText: String, whisperCode: String): WhisperAccessPayload {
        val (decryptedJson, success) = CryptoManager.decryptAes(
            combinedBase64 = cipherText,
            password = whisperCode.toCharArray(),
        )
        if (!success || decryptedJson.isBlank()) {
            error("Incorrect Whisper Code or corrupted access file.")
        }
        val payload = json.decodeFromString<WhisperAccessPayload>(decryptedJson)
        require(payload.app == "toolz_whisper") { "This file is not a valid Whisper Access File." }
        return payload
    }
}

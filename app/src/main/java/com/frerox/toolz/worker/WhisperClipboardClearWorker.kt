/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.worker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager-backed clipboard clearing that survives process death.
 * Replaces VM-scoped temporary delays so sensitive tokens cannot leak on process restart.
 */
@HiltWorker
class WhisperClipboardClearWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_TOKEN = "key_token"
        const val KEY_RESTORE_TO = "key_restore_to"
        const val UNIQUE_WORK_NAME = "whisper_clipboard_clear_work"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "whisper_clipboard_key"
        private const val GCM_TAG_BITS = 128
        private const val IV_SIZE = 12

        /**
         * Encrypt for WorkManager storage (VM side). Mirrors decrypt below. P0-5 FIX.
         * Returns null when Keystore is unavailable — callers must NOT schedule the
         * worker in that case (the old base64 fallback persisted a reversible
         * credential in WorkManager's SQLite, defeating the point of encrypting).
         */
        fun encryptForStorage(plain: String, ctx: Context): String? {
            try {
                if (plain.isEmpty()) return ""
                val key = getOrCreateKey(ctx)
                val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
                val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
                // Prepend IV (12) then ciphertext+tag — store as base64
                val combined = cipher.iv + encrypted
                return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
            } catch (_: Exception) {
                return null
            }
        }

        private fun decryptFromStorage(b64: String?, ctx: Context): String? {
            if (b64.isNullOrEmpty()) return ""
            return try {
                val combined = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                if (combined.size <= IV_SIZE) return null
                val iv = combined.sliceArray(0 until IV_SIZE)
                val ciphertext = combined.sliceArray(IV_SIZE until combined.size)
                val key = getOrCreateKey(ctx)
                val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(GCM_TAG_BITS, iv))
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (_: Exception) {
                // Fallback: maybe it was stored plaintext before fix, or as plain base64
                try { String(android.util.Base64.decode(b64, android.util.Base64.NO_WRAP), Charsets.UTF_8) } catch (_: Exception) { b64 }
            }
        }

        private fun getOrCreateKey(ctx: Context): javax.crypto.SecretKey {
            val keyStore = java.security.KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) return keyStore.getKey(KEY_ALIAS, null) as javax.crypto.SecretKey
            val generator = javax.crypto.KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            generator.init(
                android.security.keystore.KeyGenParameterSpec.Builder(KEY_ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            return generator.generateKey()
        }
    }

    override suspend fun doWork(): ListenableWorker.Result {
        // P0-5: Values areEncrypted in WorkManager DB — decrypt before comparison.
        val encryptedToken = inputData.getString(KEY_TOKEN) ?: return ListenableWorker.Result.success()
        val token = decryptFromStorage(encryptedToken, context) ?: return ListenableWorker.Result.success()
        val encryptedRestore = inputData.getString(KEY_RESTORE_TO)
        val restoreTo = decryptFromStorage(encryptedRestore, context)

        withContext(Dispatchers.Main) {
            val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@withContext
            val currentClip = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            if (currentClip == token) {
                clipboard.setPrimaryClip(ClipData.newPlainText(null, restoreTo ?: ""))
            }
        }
        return ListenableWorker.Result.success()
    }
}

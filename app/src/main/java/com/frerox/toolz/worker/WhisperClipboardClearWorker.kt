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
        private const val TAG = "WhisperClipClear"
        const val KEY_TOKEN = "key_token"
        const val KEY_RESTORE_TO = "key_restore_to"
        const val UNIQUE_WORK_NAME = "whisper_clipboard_clear_work"
        // V2-FIX H-?: version tag embedded in the stored envelope JSON so future format
        // changes are detected at decrypt time instead of guessed by fallback shims.
        private const val ENVELOPE_VERSION = 1
        private const val KEY_ENVELOPE_VERSION = "v"
        private const val KEY_ENVELOPE_PAYLOAD = "payload"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "whisper_clipboard_key"
        private const val GCM_TAG_BITS = 128
        private const val IV_SIZE = 12

        /**
         * Encrypt for WorkManager storage (VM side). Mirrors decrypt below. P0-5 FIX.
         * Returns null when Keystore is unavailable — callers must NOT schedule the
         * worker in that case (the old base64 fallback persisted a reversible
         * credential in WorkManager's SQLite, defeating the point of encrypting).
         *
         * V2-FIX H-?: the envelope is now a JSON object `{"v":1,"payload":"<b64(iv+ct)>"}`,
         * so the format is self-describing and forward-evolvable.
         *
         * NOTE: [android.content.ClipDescription.EXTRA_IS_SENSITIVE] cannot be applied in
         * this worker — it must be set where the sensitive COPY happens (the token copy site,
         * Compose LocalClipboardManager.setText in WhisperAuthScreen), and that API offers no
         * ClipDescription access. Migrating that copy site to the system ClipboardManager is
         * non-trivial, so it stays unchanged; this worker only clears afterwards.
         */
        fun encryptForStorage(plain: String, ctx: Context): String? {
            try {
                if (plain.isEmpty()) return ""
                val key = getOrCreateKey(ctx)
                val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
                val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
                // Prepend IV (12) then ciphertext+tag — store as base64 inside a versioned envelope
                val combined = cipher.iv + encrypted
                return org.json.JSONObject()
                    .put(KEY_ENVELOPE_VERSION, ENVELOPE_VERSION)
                    .put(KEY_ENVELOPE_PAYLOAD, android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP))
                    .toString()
            } catch (_: Exception) {
                return null
            }
        }

        /**
         * Decrypts a stored envelope. Returns:
         *  - "" for an empty input (nothing was ever stored),
         *  - the plaintext for a valid v1 envelope,
         *  - null when decryption fails or the envelope is unknown/malformed.
         *
         * V2-FIX H-?: the old fail-open shim (try plaintext → try raw base64 → return the
         * ciphertext string itself) is REMOVED. A broken envelope now logs loudly and yields
         * null; the worker then treats it as a no-op and never writes to the clipboard.
         */
        private fun decryptFromStorage(b64: String?, ctx: Context): String? {
            if (b64.isNullOrEmpty()) return ""
            return try {
                val envelope = org.json.JSONObject(b64)
                val version = envelope.optInt(KEY_ENVELOPE_VERSION, -1)
                if (version != ENVELOPE_VERSION) {
                    android.util.Log.e(TAG, "Unsupported clipboard envelope version $version — ignoring stored value")
                    return null
                }
                val combined = android.util.Base64.decode(
                    envelope.getString(KEY_ENVELOPE_PAYLOAD),
                    android.util.Base64.NO_WRAP,
                )
                if (combined.size <= IV_SIZE) return null
                val iv = combined.sliceArray(0 until IV_SIZE)
                val ciphertext = combined.sliceArray(IV_SIZE until combined.size)
                val key = getOrCreateKey(ctx)
                val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(GCM_TAG_BITS, iv))
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (e: Exception) {
                // Fail closed + loud: never degrade into pasting stale material back into the clipboard.
                android.util.Log.e(TAG, "Clipboard envelope decryption failed — clear skipped (no-op)", e)
                null
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
        // P0-5: Values are encrypted in the WorkManager DB — decrypt before comparison.
        // V2-FIX H-?: any decryption failure is a LOUD no-op — we never write to the
        // clipboard on a path we cannot verify (that would either leak material or
        // irreversibly destroy the user's current clipboard with an empty restore value).
        val encryptedToken = inputData.getString(KEY_TOKEN) ?: return ListenableWorker.Result.success()
        val token = decryptFromStorage(encryptedToken, context)
        val encryptedRestore = inputData.getString(KEY_RESTORE_TO)
        val restoreTo = encryptedRestore?.let { decryptFromStorage(it, context) }
        if (token.isNullOrEmpty() || (encryptedRestore != null && restoreTo == null)) {
            return ListenableWorker.Result.success()
        }

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

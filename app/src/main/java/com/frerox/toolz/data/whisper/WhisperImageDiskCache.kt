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

package com.frerox.toolz.data.whisper

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * V3-FIX (task F): encrypted on-disk cache for decrypted chat images.
 *
 * Scrolling a Whisper chat used to re-download AND re-decrypt every image because only
 * a small in-memory LRU survived between recompositions. This cache sits between that
 * memory LRU and the network in WhisperChatViewModel.loadEncryptedImage:
 *
 *   memory hit -> return
 *   disk hit   -> Keystore-decrypt -> memory put -> return
 *   otherwise  -> download path -> encrypted disk put -> memory put
 *
 * SECURITY POSTURE — never plaintext at rest:
 *  - The cache key binds the message id to the fingerprint of the peer key the bytes
 *    were decrypted with, so a partner key change can never silently serve stale
 *    material under another conversation's name.
 *  - Values are AES-256/GCM ciphertext under an Android Keystore key ([KEY_ALIAS])
 *    that never leaves hardware/TEE; a fresh IV prefixes each file.
 *  - Expired disappearing images are treated as absent and deleted on read.
 *
 * FAILURE PHILOSOPHY: any IO/crypto error is a cache MISS (logged once per process),
 * never a load failure — the caller falls through to the download path unchanged.
 */
@Singleton
class WhisperImageDiskCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cacheDir: File = File(context.filesDir, DIR_NAME)
    private val trimLock = Any()
    private val failureLogged = AtomicBoolean(false)

    /** Logs the FIRST cache fault only; afterwards this layer stays fully silent. */
    private fun logFailureOnce(e: Exception) {
        if (failureLogged.compareAndSet(false, true)) {
            android.util.Log.w(TAG, "Image disk cache disabled for this session after error", e)
        }
    }

    /**
     * AES-256/GCM key held in AndroidKeyStore, generated on first use.
     * Mirrors [com.frerox.toolz.di.KeystoreSessionManager]'s pattern.
     */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** Content-bound cache filename: SHA-256("messageId|peerKeyFingerprint") in hex + .img. */
    private fun fileFor(messageId: String, keyFp: String): File =
        File(cacheDir, sha256Hex("$messageId|$keyFp") + FILE_EXT)

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Encrypts [plainBytes] with a fresh IV; on-disk layout: iv(12B) || GCM(cipher+tag). */
    private fun encryptBlob(plainBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainBytes)
        val blob = ByteArray(cipher.iv.size + encrypted.size)
        System.arraycopy(cipher.iv, 0, blob, 0, cipher.iv.size)
        System.arraycopy(encrypted, 0, blob, cipher.iv.size, encrypted.size)
        return blob
    }

    /** Reverses [encryptBlob]; throws on tamper/truncation (caller treats as miss). */
    private fun decryptBlob(blob: ByteArray): ByteArray {
        if (blob.size <= IV_LEN) throw IllegalStateException("Truncated cache entry")
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, blob, 0, IV_LEN))
        return cipher.doFinal(blob, IV_LEN, blob.size - IV_LEN)
    }

    /**
     * Returns the decrypted image bytes previously stored via [put], or null on any
     * miss/fault. When the caller knows the attachment expiry ([expiresAtEpochMs],
     * epoch millis), an entry within [EXPIRY_SKEW_MS] of (or past) expiry is deleted
     * and reported as a miss so a disappearing image never renders after its deadline.
     * A hit refreshes the file's lastModified so the trim below stays true-LRU.
     */
    suspend fun get(messageId: String, keyFp: String, expiresAtEpochMs: Long? = null): ByteArray? {
        if (messageId.isBlank() || keyFp.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val file = fileFor(messageId, keyFp)
                if (!file.isFile) return@withContext null
                if (expiresAtEpochMs != null &&
                    System.currentTimeMillis() > expiresAtEpochMs - EXPIRY_SKEW_MS
                ) {
                    file.delete()
                    return@withContext null
                }
                val plain = decryptBlob(file.readBytes())
                runCatching { file.setLastModified(System.currentTimeMillis()) }
                plain
            } catch (e: Exception) {
                logFailureOnce(e)
                null
            }
        }
    }

    /**
     * Stores [plainBytes] under the (messageId, keyFp) pair as Keystore-encrypted
     * ciphertext, written atomically (temp file + rename), then trims the directory
     * back under the 64 MB budget. Best-effort: failures are swallowed after one log.
     */
    suspend fun put(messageId: String, keyFp: String, plainBytes: ByteArray) {
        if (messageId.isBlank() || keyFp.isBlank() || plainBytes.isEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                cacheDir.mkdirs()
                val blob = encryptBlob(plainBytes)
                val target = fileFor(messageId, keyFp)
                val tmp = File.createTempFile("wimg_", TMP_EXT, cacheDir)
                try {
                    tmp.writeBytes(blob)
                    // Same-directory rename is atomic on POSIX filesystems; readers can
                    // therefore never observe a half-written entry.
                    if (!tmp.renameTo(target)) {
                        tmp.copyTo(target, overwrite = true)
                        tmp.delete()
                    }
                } finally {
                    tmp.delete()
                }
                trimBudget()
            } catch (e: Exception) {
                logFailureOnce(e)
            }
        }
    }

    /** Wipes every cached entry. Called from sign-out / clearAllLocalData paths. */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            try {
                cacheDir.listFiles()?.forEach { it.delete() }
                cacheDir.delete()
            } catch (e: Exception) {
                logFailureOnce(e)
            }
        }
    }

    /**
     * Internal LRU trim: keeps total cached bytes at or below [MAX_TOTAL_BYTES] by
     * evicting the oldest-lastModified files first. Runs inline on put; a concurrent
     * put racing the sweep is harmless (worst case the budget is exceeded until the
     * next put).
     */
    private fun trimBudget() {
        synchronized(trimLock) {
            val files = cacheDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(FILE_EXT) }
                ?: return
            var total = files.sumOf { it.length() }
            if (total <= MAX_TOTAL_BYTES) return
            for (file in files.sortedBy { it.lastModified() }) {
                if (total <= MAX_TOTAL_BYTES) break
                val size = file.length()
                if (file.delete()) total -= size
            }
        }
    }

    private companion object {
        const val TAG = "WhisperImgCache"
        const val DIR_NAME = "whisper_img_cache"
        const val FILE_EXT = ".img"
        const val TMP_EXT = ".part"
        const val KEY_ALIAS = "whisper_imgcache_key"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        const val GCM_TAG_BITS = 128
        /** Small safety margin so an entry never survives past its own expiry render check. */
        const val EXPIRY_SKEW_MS = 30_000L
        const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
    }
}

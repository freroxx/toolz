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

package com.frerox.toolz.data.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.frerox.toolz.util.security.KeyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ClipboardBlobStore"
private const val MAGIC = "TZB1"
private const val IV_BYTES = 12
private const val TAG_BITS = 128
private const val MAX_ITEM_BYTES = 25L * 1024L * 1024L
private const val SHARE_CACHE_MAX_AGE_MS = 60L * 60L * 1000L

data class BlobRef(val id: String, val sizeBytes: Long)

/**
 * Content-addressed, AES-GCM encrypted attachment store.
 *
 * The database only ever stores the SHA-256 blob id and metadata. Bytes are
 * encrypted with a key derived from the app's Keystore-backed vault passphrase,
 * so copying the files directory alone does not expose clipboard images/files.
 */
@Singleton
class ClipboardBlobStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clipboardDao: ClipboardDao,
) {
    private val dir: File by lazy {
        File(context.filesDir, "clipboard_blobs").apply { mkdirs() }
    }
    private val shareCacheDir: File by lazy {
        File(context.cacheDir, "clipboard_share").apply { mkdirs() }
    }
    private val secureRandom = SecureRandom()
    private val key: SecretKeySpec by lazy {
        val master = KeyManager.getOrCreateMasterKey(context)
        val digest = MessageDigest.getInstance("SHA-256").digest(master)
        SecretKeySpec(digest, "AES")
    }

    /**
     * Blobs written by an in-flight capture/restore but not yet referenced by
     * an attachment row. GC must never delete these: the row is inserted a few
     * milliseconds later. Ref-counted because two pipelines may stage the same
     * content-addressed blob concurrently.
     */
    private val stagedRefCounts = HashMap<String, Int>()

    fun maxItemBytes(): Long = MAX_ITEM_BYTES

    fun protectStaged(ids: Collection<String>) {
        if (ids.isEmpty()) return
        synchronized(stagedRefCounts) {
            ids.forEach { id -> stagedRefCounts[id] = (stagedRefCounts[id] ?: 0) + 1 }
        }
    }

    fun releaseStaged(ids: Collection<String>) {
        if (ids.isEmpty()) return
        synchronized(stagedRefCounts) {
            ids.forEach { id ->
                val remaining = (stagedRefCounts[id] ?: 1) - 1
                if (remaining <= 0) stagedRefCounts.remove(id) else stagedRefCounts[id] = remaining
            }
        }
    }

    private fun isStaged(id: String): Boolean = synchronized(stagedRefCounts) {
        (stagedRefCounts[id] ?: 0) > 0
    }

    private fun stagedSnapshot(): Set<String> = synchronized(stagedRefCounts) {
        stagedRefCounts.keys.toSet()
    }

    suspend fun put(
        input: InputStream,
        maxBytes: Long = MAX_ITEM_BYTES,
        protectForStaging: Boolean = false,
    ): BlobRef? = withContext(Dispatchers.IO) {
        try {
            val bytes = input.use { readLimited(it, maxBytes) } ?: return@withContext null
            putBytes(bytes, protectForStaging)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store attachment", e)
            null
        }
    }

    suspend fun putBytes(
        bytes: ByteArray,
        protectForStaging: Boolean = false,
    ): BlobRef? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty() || bytes.size.toLong() > MAX_ITEM_BYTES) return@withContext null
        val id = ClipboardHashing.sha256(bytes)
        val plainSize = bytes.size.toLong()
        // Protect before the file is visible so a concurrent GC cannot remove
        // it between write and attachment insert.
        if (protectForStaging) protectStaged(listOf(id))
        try {
            val target = File(dir, "$id.bin")
            if (target.exists()) return@withContext BlobRef(id, plainSize)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            val encrypted = cipher.doFinal(bytes)

            val temp = File.createTempFile("blob_", ".tmp", dir)
            temp.outputStream().use { out ->
                out.write(MAGIC.toByteArray(Charsets.US_ASCII))
                out.write(iv)
                out.write(encrypted)
                out.flush()
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            BlobRef(id, plainSize)
        } catch (e: Exception) {
            if (protectForStaging) releaseStaged(listOf(id))
            Log.e(TAG, "Failed to encrypt attachment", e)
            null
        }
    }

    suspend fun readBytes(id: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val file = File(dir, "$id.bin")
            if (!file.exists()) return@withContext null
            val raw = file.readBytes()
            if (raw.size < MAGIC.length + IV_BYTES) return@withContext null
            val magic = String(raw, 0, MAGIC.length, Charsets.US_ASCII)
            if (magic != MAGIC) return@withContext null
            val iv = raw.copyOfRange(MAGIC.length, MAGIC.length + IV_BYTES)
            val encrypted = raw.copyOfRange(MAGIC.length + IV_BYTES, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read attachment $id", e)
            null
        }
    }

    suspend fun open(id: String): InputStream? {
        val bytes = readBytes(id) ?: return null
        return ByteArrayInputStream(bytes)
    }

    suspend fun bitmap(id: String, maxDimension: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bytes = readBytes(id) ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (bounds.outWidth / sample > maxDimension * 2 ||
                bounds.outHeight / sample > maxDimension * 2
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode attachment $id", e)
            null
        }
    }

    suspend fun copyToShareCache(id: String, fileName: String?): File? = withContext(Dispatchers.IO) {
        try {
            // Decrypted share copies are sensitive. Never let them accumulate:
            // drop anything older than the short share window before staging.
            pruneShareCache()
            val bytes = readBytes(id) ?: return@withContext null
            val safeName = sanitizeFileName(fileName)
            // createTempFile guarantees a unique name even for rapid repeat shares.
            val target = File.createTempFile("clip_", "_$safeName", shareCacheDir)
            target.outputStream().use { it.write(bytes) }
            target
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stage attachment for sharing", e)
            null
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        try {
            File(dir, "$id.bin").delete()
        } catch (_: Exception) {
        }
    }

    /**
     * Deletes a blob only when no attachment row references it. Capture
     * rollbacks must use this: blobs are content-addressed, so an identical
     * attachment may already be shared by other clips.
     */
    suspend fun deleteIfUnreferenced(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (clipboardDao.countBlobReferences(id) > 0) return@withContext false
            // Another capture/restore may still be about to insert its row.
            if (isStaged(id)) return@withContext false
            File(dir, "$id.bin").delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete blob $id", e)
            false
        }
    }

    suspend fun exists(id: String): Boolean = withContext(Dispatchers.IO) {
        File(dir, "$id.bin").exists()
    }

    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        dir.listFiles()?.filter { it.isFile && it.name.endsWith(".bin") }?.sumOf { it.length() } ?: 0L
    }

    /**
     * Removes blobs that no attachment row references any more. In-flight
     * staged blobs are always preserved, plus any ids the caller explicitly
     * protects (capture quota trimming passes its staged set).
     */
    suspend fun gc(protectedIds: Set<String> = emptySet()): Int = withContext(Dispatchers.IO) {
        try {
            val referenced = clipboardDao.getReferencedBlobIds().toSet()
            val protected = stagedSnapshot() + protectedIds
            var removed = 0
            dir.listFiles()?.forEach { file ->
                if (!file.isFile || !file.name.endsWith(".bin")) return@forEach
                val id = file.name.removeSuffix(".bin")
                if (id !in referenced && id !in protected) {
                    if (file.delete()) removed++
                }
            }
            removed
        } catch (e: Exception) {
            Log.w(TAG, "Blob GC failed", e)
            0
        }
    }

    /** Deletes decrypted share copies older than [maxAgeMs]. */
    suspend fun pruneShareCache(maxAgeMs: Long = SHARE_CACHE_MAX_AGE_MS) =
        withContext(Dispatchers.IO) {
            try {
                val cutoff = System.currentTimeMillis() - maxAgeMs
                shareCacheDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.lastModified() < cutoff) file.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Share cache prune failed", e)
            }
        }

    suspend fun clearShareCache() = withContext(Dispatchers.IO) {
        shareCacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun readLimited(input: InputStream, maxBytes: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun sanitizeFileName(name: String?): String {
        val cleaned = name?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.take(80)
        return cleaned?.takeIf { it.isNotBlank() } ?: "attachment"
    }
}

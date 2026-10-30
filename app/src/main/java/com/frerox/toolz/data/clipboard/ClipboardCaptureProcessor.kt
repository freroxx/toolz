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
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import android.util.Log
import com.frerox.toolz.data.password.PasswordDao
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ClipboardCapture"
private const val MAX_TEXT_CHARS = 100_000
private const val MAX_TEXT_BYTES = 2L * 1024L * 1024L
private const val MAX_PASSWORD_LOOKUP_CHARS = 256
private val TEXT_APPLICATION_MIMES = setOf(
    "application/json",
    "application/ld+json",
    "application/xml",
    "application/xhtml+xml",
    "application/javascript",
    "application/x-javascript",
    "application/ecmascript",
    "application/x-yaml",
    "application/yaml",
    "application/sql",
    "application/graphql",
    "application/x-sh",
    "application/x-httpd-php",
    "application/rtf",
)

/**
 * The single capture pipeline shared by the foreground service, popup, share
 * target, ACTION_PROCESS_TEXT, QS tile and manual paste.
 *
 * Responsibilities:
 *  - normalize every clip shape (text, HTML, URI, multi-item) into one row;
 *  - encrypt and content-address attachments through [ClipboardBlobStore];
 *  - dedupe by SHA-256 and bump recency instead of inserting twins;
 *  - classify + detect Password Vault matches;
 *  - apply the sensitive model (mask, no expiry, no AI, optional auto-lock);
 *  - enforce item/total quotas through [ClipboardRetentionManager].
 */
@Singleton
class ClipboardCaptureProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clipboardDao: ClipboardDao,
    private val classifier: ClipboardClassifier,
    private val passwordDao: PasswordDao,
    private val blobStore: ClipboardBlobStore,
    private val settingsRepository: SettingsRepository,
    private val retentionManager: ClipboardRetentionManager,
) {
    /**
     * Serializes captures so two pipelines can never stage the same blob and
     * then race each other's cleanup/trim. Cheap: a capture is a few ms of IO.
     */
    private val captureMutex = Mutex()

    enum class Outcome {
        INSERTED,
        /** Same content re-copied: timestamp bumped so it moves to top. */
        BUMPED,
        IGNORED_BLANK,
        IGNORED_SENSITIVE,
        IGNORED_TOO_LARGE,
        IGNORED_QUOTA,
        FAILED,
    }

    data class CaptureResult(
        val outcome: Outcome,
        val entryId: Int? = null,
        val entry: ClipboardEntry? = null,
        val sensitive: Boolean = false,
    )

    /**
     * Capture one normalized system/manual payload.
     *
     * @param excludeSensitive when true (user preference) sensitive clips are
     *   discarded instead of stored masked. Default is false: store masked.
     */
    suspend fun process(
        payload: ClipboardPayload,
        excludeSensitive: Boolean,
    ): CaptureResult = withContext(Dispatchers.IO) {
        try {
            captureMutex.withLock { processInternal(payload, excludeSensitive) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed", e)
            CaptureResult(Outcome.FAILED)
        }
    }

    /** Compatibility wrapper used by older call sites and tests. */
    suspend fun processText(
        raw: String?,
        source: String,
        excludeSensitive: Boolean,
        maxChars: Int = 50_000,
    ): Outcome {
        val text = raw.orEmpty()
        if (text.isBlank()) return Outcome.IGNORED_BLANK
        val clipped = if (text.length > maxChars) text.take(maxChars) else text
        return process(
            ClipboardPayload(
                items = listOf(ClipboardPayloadItem(text = clipped)),
                source = source,
            ),
            excludeSensitive = excludeSensitive,
        ).outcome
    }

    private suspend fun processInternal(
        payload: ClipboardPayload,
        excludeSensitive: Boolean,
    ): CaptureResult {
        val itemQuota = settingsRepository.clipboardItemQuotaMb.first() * 1024L * 1024L
        val totalQuota = settingsRepository.clipboardTotalQuotaMb.first() * 1024L * 1024L
        val blobBytesBeforeStaging = blobStore.totalBytes()

        val textParts = mutableListOf<String>()
        var html: String? = null
        var mime: String? = null
        var primaryFileName: String? = null
        var hasImage = false
        var hasFile = false
        var totalBytes = 0L
        val staged = mutableListOf<StagedAttachment>()
        // Every blob successfully protected via putBytes(protectForStaging).
        // The finally block releases the ref-count and rolls back on failure.
        val protectedBlobIds = mutableListOf<String>()
        var insertedClipId: Int? = null
        var committed = false

        try {
            for (item in payload.items) {
                // Preserve exact text: leading indentation and trailing
                // newlines are meaningful for code and shell clips.
                addTextPart(textParts, item.text)
                if (item.html != null && html == null) html = item.html
                if (item.mimeType != null && mime == null) mime = item.mimeType

                val uri = item.uri ?: continue
                val resolvedMime = item.mimeType
                    ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
                val fileName = item.fileName ?: queryDisplayName(uri)
                val isImage = resolvedMime?.startsWith("image/") == true
                val isHtml = resolvedMime?.contains("html") == true
                val isTextMime = resolvedMime == null ||
                    resolvedMime.startsWith("text/") ||
                    resolvedMime in TEXT_APPLICATION_MIMES

                if (isTextMime && !isImage) {
                    // Text-like URI (text/plain, text/html, unknown): read as
                    // text only when the bytes actually decode as text. An
                    // unknown MIME is sniffed so binary files never become
                    // mojibake text rows.
                    val text = readTextUri(uri, MAX_TEXT_BYTES)
                    if (text != null) {
                        if (isHtml) {
                            // Store raw markup for rich targets, but expose
                            // readable plain text to search, copy and AI.
                            if (html == null) html = text
                            addTextPart(textParts, htmlToPlainText(text))
                        } else {
                            addTextPart(textParts, text)
                        }
                        continue
                    }
                }

                val bytes = readBytesLimited(uri, itemQuota)
                    ?: return CaptureResult(Outcome.IGNORED_TOO_LARGE)
                if (bytes.isEmpty()) continue
                if (bytes.size.toLong() > itemQuota) {
                    return CaptureResult(Outcome.IGNORED_TOO_LARGE)
                }
                val blob = blobStore.putBytes(bytes, protectForStaging = true)
                    ?: return CaptureResult(Outcome.FAILED)
                protectedBlobIds.add(blob.id)
                if (blob.sizeBytes > itemQuota) {
                    return CaptureResult(Outcome.IGNORED_TOO_LARGE)
                }
                // A multi-item clip may repeat the same URI. Content-addressed
                // blobs are already identical; do not create duplicate rows.
                if (staged.any { it.blobId == blob.id }) continue

                var width: Int? = null
                var height: Int? = null
                if (isImage) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    width = bounds.outWidth.takeIf { it > 0 }
                    height = bounds.outHeight.takeIf { it > 0 }
                    hasImage = true
                } else {
                    hasFile = true
                }
                if (primaryFileName == null) primaryFileName = fileName
                if (mime == null) mime = resolvedMime
                totalBytes += blob.sizeBytes
                staged.add(
                    StagedAttachment(
                        blobId = blob.id,
                        fileName = fileName,
                        mimeType = resolvedMime,
                        sizeBytes = blob.sizeBytes,
                        width = width,
                        height = height,
                    ),
                )
            }

            val combinedText = textParts
                .joinToString("\n")
                .let { if (it.length > MAX_TEXT_CHARS) it.take(MAX_TEXT_CHARS) else it }
            // Some apps publish HTML without a plain-text representation. Keep
            // the clip instead of silently dropping it; the raw HTML is still
            // stored in htmlContent for rich targets.
            val htmlFallback = if (combinedText.isBlank() && !html.isNullOrBlank()) {
                htmlToPlainText(html).take(MAX_TEXT_CHARS)
            } else {
                null
            }
            val effectiveText = combinedText.ifBlank { htmlFallback.orEmpty() }
            val hasAttachments = staged.isNotEmpty()
            if (effectiveText.isBlank() && !hasAttachments) {
                return CaptureResult(Outcome.IGNORED_BLANK)
            }

            val itemCount = payload.items.size
            val type = when {
                hasImage && hasFile -> ClipboardType.MULTI
                hasImage && (effectiveText.isNotBlank() || itemCount > 1) -> ClipboardType.MULTI
                hasFile && (effectiveText.isNotBlank() || itemCount > 1) -> ClipboardType.MULTI
                hasImage -> ClipboardType.IMAGE
                hasFile -> ClipboardType.FILE
                itemCount > 1 -> ClipboardType.MULTI
                else -> classifier.classify(effectiveText)
            }

            val content = effectiveText.ifBlank {
                primaryFileName ?: if (hasImage) "[image]" else "[file]"
            }

            val vaultMatch = detectPasswordVaultMatch(effectiveText)
            val sensitiveExtra = payload.isSensitiveExtra || payload.items.any { it.isSensitive }
            val classification = if (type in setOf(ClipboardType.IMAGE, ClipboardType.FILE, ClipboardType.MULTI)) {
                // A vault match on an attachment filename/placeholder must
                // also mark the clip sensitive: isLocked implies isSensitive.
                val mediaSensitive = sensitiveExtra || vaultMatch
                ClipClassification(
                    type = type,
                    isSensitive = mediaSensitive,
                    reason = when {
                        vaultMatch -> ClipSensitivityReason.PASSWORD_VAULT
                        sensitiveExtra -> ClipSensitivityReason.EXTRA
                        else -> null
                    },
                    passwordVaultMatch = vaultMatch,
                )
            } else {
                classifier.classifyDetailed(
                    text = effectiveText,
                    isSensitiveExtra = sensitiveExtra,
                    passwordVaultMatch = vaultMatch,
                )
            }
            val sensitive = classification.isSensitive

            if (excludeSensitive && sensitive) {
                return CaptureResult(Outcome.IGNORED_SENSITIVE, sensitive = true)
            }

            val textBytes = content.toByteArray(Charsets.UTF_8).size.toLong()
            val sizeBytes = textBytes + totalBytes
            if (sizeBytes > itemQuota) {
                return CaptureResult(Outcome.IGNORED_TOO_LARGE, sensitive = sensitive)
            }

            // Staged blobs are already on disk, so measure the real footprint.
            // Content-addressed dedupe adds zero bytes; protected staged blobs
            // survive trimToQuota's GC and are cleaned by the finally block if
            // the capture is ultimately rejected.
            val blobBytesAfterStaging = blobStore.totalBytes()
            val stagedNewBytes = (blobBytesAfterStaging - blobBytesBeforeStaging)
                .coerceAtLeast(0L)
            if (stagedNewBytes > totalQuota) {
                return CaptureResult(Outcome.IGNORED_QUOTA, sensitive = sensitive)
            }
            if (blobBytesAfterStaging > totalQuota) {
                retentionManager.trimToQuota(totalQuota, protectedBlobIds.toSet())
                if (blobStore.totalBytes() > totalQuota) {
                    return CaptureResult(Outcome.IGNORED_QUOTA, sensitive = sensitive)
                }
            }

            val hash = ClipboardHashing.contentHash(
                content = content,
                html = html,
                attachments = staged.map {
                    ClipboardHashAttachment(it.blobId, it.fileName, it.mimeType)
                },
            )
            val existing = clipboardDao.getBySha256(hash)
            if (existing != null) {
                // Same bytes, but the new capture may carry extra sensitivity
                // (password-manager flag, vault match). Never downgrade flags the
                // user set manually; only escalate. isLocked always implies
                // isSensitive, including for legacy inconsistent rows.
                val mergedVault = existing.passwordVaultMatch || classification.passwordVaultMatch
                val mergedLocked = existing.isLocked || classification.passwordVaultMatch
                val mergedSensitive = existing.isSensitive || sensitive || mergedLocked
                val mergedReason = existing.sensitivityReason
                    ?: classification.reason
                    ?: if (mergedLocked) ClipSensitivityReason.PASSWORD_VAULT else null
                val mergedExpiry = if (mergedSensitive || mergedLocked) null else existing.expiresAt
                clipboardDao.bumpEntry(
                    id = existing.id,
                    timestamp = payload.timestamp,
                    source = payload.source,
                    sourceApp = payload.sourcePackage,
                )
                if (mergedSensitive != existing.isSensitive ||
                    mergedVault != existing.passwordVaultMatch ||
                    mergedLocked != existing.isLocked ||
                    mergedReason != existing.sensitivityReason
                ) {
                    clipboardDao.updateSensitivity(
                        id = existing.id,
                        sensitive = mergedSensitive,
                        reason = mergedReason,
                        vaultMatch = mergedVault,
                        locked = mergedLocked,
                    )
                }
                if (mergedExpiry != existing.expiresAt) {
                    clipboardDao.setExpiresAt(existing.id, mergedExpiry)
                }
                if (mergedSensitive || mergedLocked) {
                    // A summary generated before the clip became sensitive must go.
                    clipboardDao.clearAiDetails(existing.id, ClipAiState.SKIPPED_SENSITIVE)
                }
                val refreshed = clipboardDao.getEntryById(existing.id) ?: existing.copy(
                    isSensitive = mergedSensitive,
                    sensitivityReason = mergedReason,
                    isLocked = mergedLocked,
                    passwordVaultMatch = mergedVault,
                    expiresAt = mergedExpiry,
                )
                return CaptureResult(
                    outcome = Outcome.BUMPED,
                    entryId = existing.id,
                    entry = refreshed,
                    sensitive = mergedSensitive,
                )
            }

            val retentionDays = settingsRepository.clipboardRetentionDays.first()
            val expiresAt = if (!sensitive && retentionDays > 0) {
                payload.timestamp + retentionDays * 24L * 60L * 60L * 1000L
            } else {
                null
            }

            val entry = ClipboardEntry(
                content = content,
                timestamp = payload.timestamp,
                type = classification.type,
                isPinned = false,
                previewUrl = null,
                sourceApp = payload.sourcePackage,
                summary = null,
                isAiProcessed = false,
                contentHash = content.hashCode(),
                source = payload.source,
                contentHashSha256 = hash,
                htmlContent = html,
                mimeType = mime ?: if (hasImage) "image/*" else null,
                sizeBytes = sizeBytes,
                attachmentCount = staged.size,
                isSensitive = sensitive,
                sensitivityReason = classification.reason,
                isLocked = classification.passwordVaultMatch,
                passwordVaultMatch = classification.passwordVaultMatch,
                metadataJson = buildMetadata(
                    payload = payload,
                    attachments = staged,
                    itemCount = itemCount,
                    hasText = effectiveText.isNotBlank(),
                ),
                deletedAt = null,
                expiresAt = expiresAt,
                aiState = if (sensitive) ClipAiState.SKIPPED_SENSITIVE else ClipAiState.IDLE,
                aiError = null,
            )

            val id = clipboardDao.insert(entry)
            if (id <= 0L) {
                return CaptureResult(Outcome.FAILED)
            }
            val clipId = id.toInt()
            insertedClipId = clipId
            staged.forEachIndexed { index, attachment ->
                clipboardDao.insertAttachment(
                    ClipboardAttachment(
                        clipId = clipId,
                        blobId = attachment.blobId,
                        fileName = attachment.fileName,
                        mimeType = attachment.mimeType,
                        sizeBytes = attachment.sizeBytes,
                        position = index,
                        width = attachment.width,
                        height = attachment.height,
                    ),
                )
            }
            // Attachments are all referenced now; a later failure must not
            // roll back a valid clip.
            committed = true

            retentionManager.enforceAfterCapture()
            val inserted = try {
                clipboardDao.getEntryById(clipId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Captured clip could not be re-read", e)
                null
            }
            return CaptureResult(
                outcome = Outcome.INSERTED,
                entryId = clipId,
                entry = inserted ?: entry.copy(id = clipId),
                sensitive = sensitive,
            )
        } finally {
            // Release staging protection exactly once for every protected blob.
            blobStore.releaseStaged(protectedBlobIds)
            if (!committed) {
                // Remove a partially inserted clip and its attachment rows
                // before deleting any staged bytes.
                insertedClipId?.let { id ->
                    runCatching {
                        clipboardDao.deleteAttachmentsForClip(id)
                        clipboardDao.deleteById(id)
                    }.onFailure { e ->
                        Log.w(TAG, "Failed to roll back clipboard insert", e)
                    }
                }
                protectedBlobIds.distinct().forEach { blobId ->
                    blobStore.deleteIfUnreferenced(blobId)
                }
            }
        }
    }

    private suspend fun detectPasswordVaultMatch(text: String): Boolean {
        val candidate = text.trim()
        if (candidate.isEmpty() || candidate.length > MAX_PASSWORD_LOOKUP_CHARS) return false
        if (candidate.contains('\n')) return false
        return try {
            passwordDao.countByPassword(candidate) > 0
        } catch (e: Exception) {
            Log.w(TAG, "Password vault lookup failed", e)
            false
        }
    }

    private fun buildMetadata(
        payload: ClipboardPayload,
        attachments: List<StagedAttachment>,
        itemCount: Int,
        hasText: Boolean,
    ): String? = try {
        JSONObject().apply {
            put("source", payload.source)
            payload.sourcePackage?.let { put("sourcePackage", it) }
            payload.label?.let { put("label", it) }
            put("itemCount", itemCount)
            // Distinguishes genuine text from a filename/placeholder stored
            // in `content` for image/file clips.
            put("hasText", hasText)
            if (attachments.isNotEmpty()) {
                val array = org.json.JSONArray()
                attachments.forEach { attachment ->
                    array.put(
                        JSONObject().apply {
                            put("blobId", attachment.blobId)
                            attachment.fileName?.let { put("name", it) }
                            attachment.mimeType?.let { put("mime", it) }
                            put("size", attachment.sizeBytes)
                        },
                    )
                }
                put("attachments", array)
            }
        }.toString()
    } catch (_: Exception) {
        null
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
    } catch (_: Exception) {
        uri.lastPathSegment
    }

    private fun readTextUri(uri: Uri, maxBytes: Long): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = readLimited(input, maxBytes) ?: return null
            decodeText(bytes)
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Decodes UTF-8 only when the bytes look like text. Binary payloads with
     * NUL bytes or a high share of control characters are rejected so they
     * are stored as attachments instead of mojibake text rows.
     */
    private fun decodeText(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return ""
        val sample = bytes.copyOf(minOf(bytes.size, 8 * 1024))
        if (sample.any { it == 0.toByte() }) return null
        var controls = 0
        for (byte in sample) {
            val value = byte.toInt() and 0xFF
            if (value < 0x09 || value in 0x0E..0x1F || value == 0x7F) controls++
        }
        if (controls * 100 > sample.size * 2) return null
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            null
        }
    }

    /** Adds a text part once; repeated items in a multi-clip must not double it. */
    private fun addTextPart(parts: MutableList<String>, value: String?) {
        if (value.isNullOrBlank()) return
        if (parts.any { it == value }) return
        parts.add(value)
    }

    private fun readBytesLimited(uri: Uri, maxBytes: Long): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { readLimited(it, maxBytes) }
    } catch (_: Exception) {
        null
    }

    /**
     * Converts HTML-only clip data into searchable, copyable plain text. The
     * raw HTML is still stored separately so rich targets keep their markup.
     */
    private fun htmlToPlainText(html: String): String = try {
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
    } catch (_: Exception) {
        html.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun readLimited(input: InputStream, maxBytes: Long): ByteArray? {
        val out = ByteArrayOutputStream()
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

    private data class StagedAttachment(
        val blobId: String,
        val fileName: String?,
        val mimeType: String?,
        val sizeBytes: Long,
        val width: Int?,
        val height: Int?,
    )
}

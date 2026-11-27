/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-device history transfer.
 *
 * The E2EE ratchet is forward secret, so a new install cannot re-open messages
 * that were addressed to the old device. This archive carries only plaintext
 * this account already read on the old device, line-delimited as JSON, and it
 * is written INSIDE the password-protected Toolz backup container — there is no
 * server-side copy and no new key material.
 *
 * On import each line is re-sealed with the NEW install's hardware-backed
 * Keystore key by [WhisperPlaintextVault] / [WhisperSelfReadStore], so the
 * archive password is only a transport secret and is never stored.
 */
@Singleton
class WhisperHistoryArchive @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
    private val selfReadStore: WhisperSelfReadStore,
    private val vault: WhisperPlaintextVault,
    private val sealer: WhisperSelfReadSealer,
) {
    companion object {
        /** Entry name inside the `.tzbk` zip. */
        const val ENTRY_PATH = "whisper/history.jsonl"

        /** Hard ceiling so a hostile archive cannot exhaust storage/time. */
        private const val MAX_IMPORT_LINES = 100_000

        /** 64 MB is far above any realistic text history; refuse larger files. */
        private const val MAX_PENDING_BYTES = 64L * 1024 * 1024

        private const val PENDING_FILE = "whisper_pending_history.sealed"
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class HistoryLine(
        val id: String,
        /** "out" for the user's own messages, "in" for received ones. */
        val dir: String = "in",
        val hash: String = "",
        val text: String,
    )

    /**
     * Writes every plaintext this account can still open, newest first. Returns
     * the number of messages written (0 means nothing to transfer).
     */
    suspend fun exportTo(output: OutputStream): Int = withContext(Dispatchers.IO) {
        val owner = currentOwner() ?: return@withContext 0
        val entries = vault.exportEntries(owner) + selfReadStore.exportEntries(owner)
        if (entries.isEmpty()) return@withContext 0

        val writer = output.bufferedWriter(Charsets.UTF_8)
        var written = 0
        for (entry in entries) {
            val line = HistoryLine(
                id = entry.messageId,
                dir = entry.direction,
                hash = entry.contentHash,
                text = entry.plaintext,
            )
            writer.write(json.encodeToString(HistoryLine.serializer(), line))
            writer.newLine()
            written++
        }
        writer.flush()
        written
    }

    /**
     * Re-seals archived plaintext under the current account. Entries whose id
     * is blank or whose direction is unknown are skipped, never trusted into
     * the wrong table.
     *
     * Returns the number imported, or **-1** when nobody is signed in yet: the
     * archive is then sealed into app-private storage and replayed by
     * [importPending] right after Whisper sign-in (the normal device-switch
     * order is restore-first, sign-in-second).
     */
    suspend fun importFrom(input: InputStream): Int = withContext(Dispatchers.IO) {
        val owner = currentOwner()
        if (owner == null) {
            return@withContext if (stagePending(input)) -1 else 0
        }
        importStream(input, owner)
    }

    /** Replays an archive staged while signed out. Safe to call on every login. */
    suspend fun importPending(): Int = withContext(Dispatchers.IO) {
        val owner = currentOwner() ?: return@withContext 0
        val file = pendingFile()
        if (!file.exists()) return@withContext 0
        val sealed = runCatching { file.readText() }.getOrNull()
        if (sealed.isNullOrBlank()) {
            runCatching { file.delete() }
            return@withContext 0
        }
        val plain = sealer.open(sealed)
        if (plain == null) {
            // Keystore changed/unavailable: the staged copy can never be read.
            runCatching { file.delete() }
            return@withContext 0
        }
        val imported = runCatching { importStream(plain.inputStream(), owner) }.getOrDefault(0)
        runCatching { file.delete() }
        imported
    }

    private suspend fun importStream(input: InputStream, owner: String): Int {
        var imported = 0
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            while (imported < MAX_IMPORT_LINES) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val record = runCatching {
                    json.decodeFromString(HistoryLine.serializer(), line)
                }.getOrNull() ?: continue
                if (record.id.isBlank() || record.text.isBlank()) continue
                if (record.dir != "in" && record.dir != "out") continue

                if (record.dir == "out") {
                    runCatching {
                        selfReadStore.importEntry(
                            ownerId = owner,
                            messageId = record.id,
                            contentHash = record.hash,
                            plaintext = record.text,
                        )
                    }
                } else {
                    runCatching {
                        vault.importEntry(
                            ownerId = owner,
                            messageId = record.id,
                            contentHash = record.hash,
                            plaintext = record.text,
                        )
                    }
                }
                imported++
            }
        }
        return imported
    }

    /**
     * Seals the archive into app-private storage with the Keystore key so it is
     * never plaintext at rest while the user completes sign-in.
     */
    private fun stagePending(input: InputStream): Boolean {
        val bytes = runCatching {
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                total += read
                if (total > MAX_PENDING_BYTES) return false
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        }.getOrNull() ?: return false
        if (bytes.isEmpty()) return false
        val sealed = sealer.seal(bytes) ?: return false
        return runCatching { pendingFile().writeText(sealed) }.isSuccess
    }

    private fun pendingFile(): File = File(context.noBackupFilesDir, PENDING_FILE)

    private fun currentOwner(): String? =
        supabase.auth.currentUserOrNull()?.id?.takeIf { it.isNotBlank() }
}

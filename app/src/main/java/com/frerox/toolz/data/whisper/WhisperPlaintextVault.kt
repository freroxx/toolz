/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.util.Log
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One plaintext snapshot moved between devices by the encrypted history
 * archive. [contentHash] is the SHA-256 of the wire ciphertext so previews
 * keep resolving after restore.
 */
data class WhisperHistoryEntry(
    val messageId: String,
    val contentHash: String,
    val plaintext: String,
    /** "in" for received messages (vault) or "out" for the user's own (self-read). */
    val direction: String = "in",
)

/**
 * Device-local plaintext of messages this account has already opened.
 *
 * WHY THIS EXISTS: the Double Ratchet and the identity envelopes are forward
 * secret by design, so a message key is destroyed once a frame is opened. If
 * the session store is lost (a desync that resets the chain, a rotation race,
 * a cleared/restored session file, or a sign-out/sign-in cycle on the same
 * install), the ciphertext still exists in Room and on the server but nothing
 * on the device can open it again — it renders as
 * "This message can no longer be decrypted on this device" forever.
 *
 * DESIGN: the first time a message is successfully opened, its plaintext is
 * sealed with the hardware-backed Keystore AES key (the same seam as
 * [WhisperSelfReadStore]) and stored here. The blob is additionally inside the
 * SQLCipher-encrypted Room database, and it is bound to the message id
 * (`PV1|messageId|plaintext`) so a blob cannot be swapped onto another row.
 *
 * SCOPE: incoming messages only. The user's own outgoing rows already have a
 * durable copy in [WhisperSelfReadStore]. Rows are keyed by ownerId so another
 * account can never read them, kept across sign-out (signing back in must still
 * render history), and wiped by [WhisperRepository.clearAllLocalData].
 *
 * This cache never weakens the protocol: it only re-serves plaintext that this
 * device was already authorized to read. It cannot decrypt anything the device
 * never opened, and it is not uploaded anywhere.
 */
@Entity(
    tableName = "whisper_plaintext_vault",
    indices = [
        Index(value = ["ownerId", "contentHash"]),
        Index(value = ["ownerId", "createdAtMs"]),
    ],
)
data class WhisperPlaintextVaultEntity(
    @PrimaryKey val messageId: String,
    val ownerId: String,
    /** SHA-256 of the wire ciphertext, for previews that carry no message id. */
    val contentHash: String,
    /** Keystore-AES sealed `PV1|messageId|plaintext`. */
    val sealed: String,
    val createdAtMs: Long,
)

@Dao
interface WhisperPlaintextVaultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WhisperPlaintextVaultEntity)

    @Query("SELECT * FROM whisper_plaintext_vault WHERE ownerId = :ownerId AND messageId = :messageId LIMIT 1")
    suspend fun get(ownerId: String, messageId: String): WhisperPlaintextVaultEntity?

    @Query(
        "SELECT * FROM whisper_plaintext_vault WHERE ownerId = :ownerId AND contentHash = :contentHash " +
            "ORDER BY createdAtMs DESC LIMIT 1",
    )
    suspend fun getByContentHash(ownerId: String, contentHash: String): WhisperPlaintextVaultEntity?

    @Query("DELETE FROM whisper_plaintext_vault WHERE messageId = :messageId")
    suspend fun delete(messageId: String)

    @Query("DELETE FROM whisper_plaintext_vault WHERE messageId IN (:messageIds)")
    suspend fun deleteMany(messageIds: List<String>)

    /** Export window for the password-protected backup archive. */
    @Query(
        "SELECT * FROM whisper_plaintext_vault WHERE ownerId = :ownerId " +
            "ORDER BY createdAtMs DESC LIMIT :limit",
    )
    suspend fun getRecent(ownerId: String, limit: Int): List<WhisperPlaintextVaultEntity>

    /**
     * Bounded growth: keep only the newest [keep] rows per owner. Called after
     * every write so the vault can never grow without limit.
     */
    @Query(
        "DELETE FROM whisper_plaintext_vault WHERE ownerId = :ownerId AND messageId NOT IN (" +
            "SELECT messageId FROM whisper_plaintext_vault WHERE ownerId = :ownerId " +
            "ORDER BY createdAtMs DESC LIMIT :keep)",
    )
    suspend fun prune(ownerId: String, keep: Int)

    @Query("DELETE FROM whisper_plaintext_vault")
    suspend fun clearAll()
}

@Singleton
class WhisperPlaintextVault @Inject constructor(
    private val dao: WhisperPlaintextVaultDao,
    private val sealer: WhisperSelfReadSealer,
) {
    companion object {
        private const val TAG = "WhisperVault"

        /**
         * Upper bound per account. Deliberately larger than the self-read store:
         * the goal is to keep a usable history window, while still capping the
         * plaintext footprint on disk.
         */
        const val MAX_PER_OWNER = 20_000

        private const val PREFIX = "PV1|"
    }

    /** Best-effort by contract: a Keystore failure must never break rendering. */
    suspend fun remember(
        ownerId: String,
        messageId: String,
        plaintext: String,
        ciphertext: String,
    ) {
        if (ownerId.isBlank() || messageId.isBlank() || plaintext.isBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val sealed = sealer.seal(payload(messageId, plaintext).toByteArray())
                    ?: return@runCatching
                dao.upsert(
                    WhisperPlaintextVaultEntity(
                        messageId = messageId,
                        ownerId = ownerId,
                        contentHash = contentHash(ciphertext),
                        sealed = sealed,
                        createdAtMs = System.currentTimeMillis(),
                    ),
                )
                dao.prune(ownerId, MAX_PER_OWNER)
            }.onFailure {
                Log.w(TAG, "plaintext vault write skipped (${it.javaClass.simpleName})")
            }
        }
    }

    /** Returns the cached plaintext for one message, or null when absent/broken. */
    suspend fun recall(ownerId: String, messageId: String): String? {
        if (ownerId.isBlank() || messageId.isBlank()) return null
        val row = runCatching { dao.get(ownerId, messageId) }.getOrNull() ?: return null
        val plain = open(row, ownerId)
        if (plain == null) runCatching { dao.delete(row.messageId) }
        return plain
    }

    /** Resolves a preview by wire ciphertext (conversation RPCs omit the id). */
    suspend fun recallByCiphertext(ownerId: String, ciphertext: String): String? {
        if (ownerId.isBlank() || ciphertext.isBlank()) return null
        val hash = contentHash(ciphertext)
        val row = runCatching { dao.getByContentHash(ownerId, hash) }.getOrNull() ?: return null
        return open(row, ownerId)
    }

    suspend fun forget(messageId: String) {
        if (messageId.isBlank()) return
        withContext(Dispatchers.IO) { runCatching { dao.delete(messageId) } }
    }

    suspend fun forgetMany(messageIds: List<String>) {
        val valid = messageIds.filter { it.isNotBlank() }
        if (valid.isEmpty()) return
        withContext(Dispatchers.IO) {
            valid.chunked(200).forEach { chunk -> runCatching { dao.deleteMany(chunk) } }
        }
    }

    /** Account-deletion wipe. */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) { runCatching { dao.clearAll() } }
    }

    /**
     * Plaintext snapshot for the password-protected backup archive. Only rows
     * this device can actually open are returned; broken blobs are skipped.
     */
    suspend fun exportEntries(ownerId: String): List<WhisperHistoryEntry> {
        if (ownerId.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val rows = runCatching { dao.getRecent(ownerId, MAX_PER_OWNER) }.getOrDefault(emptyList())
            rows.mapNotNull { row ->
                val plain = open(row, ownerId) ?: return@mapNotNull null
                WhisperHistoryEntry(
                    messageId = row.messageId,
                    contentHash = row.contentHash,
                    plaintext = plain,
                )
            }
        }
    }

    /**
     * Re-seals an archived entry with THIS install's Keystore key. The archived
     * hash is preserved so preview-by-ciphertext keeps working after restore.
     */
    suspend fun importEntry(
        ownerId: String,
        messageId: String,
        contentHash: String,
        plaintext: String,
    ) {
        if (ownerId.isBlank() || messageId.isBlank() || plaintext.isBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val sealed = sealer.seal(payload(messageId, plaintext).toByteArray())
                    ?: return@runCatching
                dao.upsert(
                    WhisperPlaintextVaultEntity(
                        messageId = messageId,
                        ownerId = ownerId,
                        contentHash = contentHash,
                        sealed = sealed,
                        createdAtMs = System.currentTimeMillis(),
                    ),
                )
                dao.prune(ownerId, MAX_PER_OWNER)
            }.onFailure {
                Log.w(TAG, "plaintext vault import skipped (${it.javaClass.simpleName})")
            }
        }
    }

    private fun open(row: WhisperPlaintextVaultEntity, ownerId: String): String? {
        if (row.ownerId != ownerId) return null
        val text = sealer.open(row.sealed)?.decodeToString() ?: return null
        if (!text.startsWith(PREFIX)) return null
        val rest = text.removePrefix(PREFIX)
        val sep = rest.indexOf('|')
        if (sep <= 0) return null
        if (rest.substring(0, sep) != row.messageId) {
            Log.w(TAG, "vault id mismatch — refusing swapped blob")
            return null
        }
        return rest.substring(sep + 1)
    }

    private fun payload(messageId: String, plaintext: String): String = "$PREFIX$messageId|$plaintext"

    private fun contentHash(ciphertext: String): String =
        MessageDigest.getInstance("SHA-256").digest(ciphertext.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

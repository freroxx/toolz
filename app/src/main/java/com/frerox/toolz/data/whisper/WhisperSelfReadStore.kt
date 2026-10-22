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
 * Device-local self-read copies of the user's OWN outgoing messages.
 *
 * WHY THIS EXISTS (field defect, October 2026): the Double Ratchet is
 * one-directional. The sender's ratchet derives fresh message keys for every
 * frame it seals and never retains the key needed to open its own ciphertext
 * again — that is the whole point of forward secrecy. Room is a
 * ciphertext-only cache, so after a process restart (or on a realtime echo that
 * races the send), an own v3 row had no path back to plaintext and rendered
 * "This message can no longer be decrypted on this device" forever. The old
 * in-process `sentV3PlaintextMemo` only papered over it until the process died.
 *
 * DESIGN: at send time (before the network insert, so a realtime echo can never
 * win the race) the plaintext is sealed with the hardware-backed Keystore AES
 * key ([WhisperCrypto.wrapWithKeystoreAes]) and stored in this table. The blob
 * is additionally inside the SQLCipher-encrypted Room database, so it is
 * double-protected at rest and is NOT the peer-facing ciphertext. It is bound
 * to the message id (included in the sealed payload) so a row blob cannot be
 * swapped onto another message.
 *
 * SCOPE: keyed by (ownerId, messageId). Signing into another account can never
 * read the previous account's copies. Copies are intentionally NOT deleted on
 * sign-out — signing back into the same account must still render its history —
 * and are wiped by [WhisperRepository.clearAllLocalData].
 *
 * These are the sender's own messages only. Incoming messages have no self
 * copy; they are opened through their ratchet/envelope path as before.
 */
@Entity(
    tableName = "whisper_self_read",
    indices = [
        Index(value = ["ownerId", "contentHash"]),
        Index(value = ["ownerId", "createdAtMs"]),
    ],
)
data class WhisperSelfReadEntity(
    @PrimaryKey val messageId: String,
    val ownerId: String,
    /** SHA-256 of the wire ciphertext — lets conversation previews resolve without an id. */
    val contentHash: String,
    /** Keystore-AES sealed `SR1|messageId|plaintext`. */
    val sealed: String,
    val createdAtMs: Long,
)

@Dao
interface WhisperSelfReadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WhisperSelfReadEntity)

    @Query("SELECT * FROM whisper_self_read WHERE ownerId = :ownerId AND messageId = :messageId LIMIT 1")
    suspend fun get(ownerId: String, messageId: String): WhisperSelfReadEntity?

    @Query("SELECT * FROM whisper_self_read WHERE ownerId = :ownerId AND messageId IN (:messageIds)")
    suspend fun getMany(ownerId: String, messageIds: List<String>): List<WhisperSelfReadEntity>

    @Query(
        "SELECT * FROM whisper_self_read WHERE ownerId = :ownerId AND contentHash = :contentHash " +
            "ORDER BY createdAtMs DESC LIMIT 1",
    )
    suspend fun getByContentHash(ownerId: String, contentHash: String): WhisperSelfReadEntity?

    @Query("DELETE FROM whisper_self_read WHERE messageId = :messageId")
    suspend fun delete(messageId: String)

    @Query("DELETE FROM whisper_self_read WHERE messageId IN (:messageIds)")
    suspend fun deleteMany(messageIds: List<String>)

    /**
     * Bounded growth: keep only the newest [keep] copies for this owner. The
     * oldest entries are pruned after every write, so the table can never grow
     * with the message history.
     */
    @Query(
        "DELETE FROM whisper_self_read WHERE ownerId = :ownerId AND messageId NOT IN (" +
            "SELECT messageId FROM whisper_self_read WHERE ownerId = :ownerId " +
            "ORDER BY createdAtMs DESC LIMIT :keep)",
    )
    suspend fun prune(ownerId: String, keep: Int)

    @Query("DELETE FROM whisper_self_read")
    suspend fun clearAll()
}

/**
 * Sealing seam (same pattern as WhisperSessionSecretProtector): production wraps
 * with the hardware-backed Keystore AES key; JVM tests inject an in-memory fake so
 * the store's ownership / id-binding / pruning logic is testable off-device.
 */
interface WhisperSelfReadSealer {
    /** Protects the self-read payload at rest; null means "refuse to store". */
    fun seal(plain: ByteArray): String?

    /** Reverses [seal]; null on any failure. */
    fun open(sealed: String): ByteArray?
}

@Singleton
class KeystoreSelfReadSealer @Inject constructor(
    private val crypto: WhisperCrypto,
) : WhisperSelfReadSealer {
    override fun seal(plain: ByteArray): String? = crypto.wrapWithKeystoreAes(plain)
    override fun open(sealed: String): ByteArray? = crypto.unwrapWithKeystoreAes(sealed)
}

@Singleton
class WhisperSelfReadStore @Inject constructor(
    private val dao: WhisperSelfReadDao,
    private val sealer: WhisperSelfReadSealer,
) {
    companion object {
        private const val TAG = "WhisperSelfRead"

        /** Keep in step with the Room flow's 500-row window plus headroom. */
        const val MAX_PER_OWNER = 1_500

        private const val PREFIX = "SR1|"
    }

    /**
     * Persists a self-read copy for [messageId]. Best-effort by contract: a
     * Keystore failure must never block sending (the message still reaches the
     * peer; only this device's own history view degrades).
     *
     * @param ciphertext the exact wire ciphertext stored server-side; hashed so
     *   conversation previews (which do not carry a message id) can resolve it.
     */
    suspend fun remember(
        ownerId: String,
        messageId: String,
        plaintext: String,
        ciphertext: String,
    ) {
        if (ownerId.isBlank() || messageId.isBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val sealed = sealer.seal(payload(messageId, plaintext).toByteArray())
                    ?: return@runCatching
                dao.upsert(
                    WhisperSelfReadEntity(
                        messageId = messageId,
                        ownerId = ownerId,
                        contentHash = contentHash(ciphertext),
                        sealed = sealed,
                        createdAtMs = System.currentTimeMillis(),
                    ),
                )
                dao.prune(ownerId, MAX_PER_OWNER)
            }.onFailure {
                Log.w(TAG, "self-read copy skipped for ${messageId.take(8)}… (${it.javaClass.simpleName})")
            }
        }
    }

    /** Returns the sealed plaintext for one own message, or null. */
    suspend fun recall(ownerId: String, messageId: String): String? {
        if (ownerId.isBlank() || messageId.isBlank()) return null
        val row = runCatching { dao.get(ownerId, messageId) }.getOrNull() ?: return null
        val plain = open(row, ownerId)
        if (plain == null) runCatching { dao.delete(row.messageId) }
        return plain
    }

    /** Bulk form used by the Room read path; one indexed query per emission. */
    suspend fun recallAll(ownerId: String, messageIds: List<String>): Map<String, String> {
        if (ownerId.isBlank() || messageIds.isEmpty()) return emptyMap()
        val rows = runCatching { dao.getMany(ownerId, messageIds) }.getOrNull().orEmpty()
        if (rows.isEmpty()) return emptyMap()
        val broken = mutableListOf<String>()
        val out = HashMap<String, String>(rows.size)
        for (row in rows) {
            val plain = open(row, ownerId)
            if (plain != null) out[row.messageId] = plain else broken += row.messageId
        }
        if (broken.isNotEmpty()) runCatching { broken.forEach { dao.delete(it) } }
        return out
    }

    /**
     * Resolves a preview by wire ciphertext (the conversations RPC returns the
     * last row's content but no message id).
     */
    suspend fun recallByCiphertext(ownerId: String, ciphertext: String): String? {
        if (ownerId.isBlank() || ciphertext.isBlank()) return null
        val hash = contentHash(ciphertext)
        val row = runCatching { dao.getByContentHash(ownerId, hash) }.getOrNull() ?: return null
        return open(row, ownerId)
    }

    /** Account-deletion wipe. */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            runCatching { dao.clearAll() }
        }
    }

    /**
     * Drops the copy for a message the user deleted. Deleted message text must not
     * linger in the self-read table after the row itself is gone.
     */
    suspend fun forget(messageId: String) {
        if (messageId.isBlank()) return
        withContext(Dispatchers.IO) {
            runCatching { dao.delete(messageId) }
        }
    }

    /** Bulk form for clear-history / blocked-partner purge. */
    suspend fun forgetMany(messageIds: List<String>) {
        val valid = messageIds.filter { it.isNotBlank() }
        if (valid.isEmpty()) return
        withContext(Dispatchers.IO) {
            valid.chunked(200).forEach { chunk -> runCatching { dao.deleteMany(chunk) } }
        }
    }

    private fun open(row: WhisperSelfReadEntity, ownerId: String): String? {
        if (row.ownerId != ownerId) return null
        val text = sealer.open(row.sealed)?.decodeToString() ?: return null
        if (!text.startsWith(PREFIX)) return null
        val rest = text.removePrefix(PREFIX)
        val sep = rest.indexOf('|')
        if (sep <= 0) return null
        val id = rest.substring(0, sep)
        if (id != row.messageId) {
            Log.w(TAG, "self-read id mismatch — refusing swapped blob")
            return null
        }
        return rest.substring(sep + 1)
    }

    private fun payload(messageId: String, plaintext: String): String = "$PREFIX$messageId|$plaintext"

    private fun contentHash(ciphertext: String): String =
        MessageDigest.getInstance("SHA-256").digest(ciphertext.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

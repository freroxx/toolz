/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression cover for the sent-history self-read store.
 *
 * Field defect (October 2026): sent v3 frames rendered "This message can no
 * longer be decrypted on this device" after any process restart because the
 * sender's ratchet cannot re-open its own ciphertext and Room stores ciphertext
 * only. These tests pin the two properties the fix relies on:
 *  - a copy written at send time is readable only by the same owner account;
 *  - the sealed payload is bound to its message id, so a blob moved onto another
 *    row is rejected (and pruned) instead of rendering the wrong text.
 */
class WhisperSelfReadStoreTest {

    /** Reversible, deterministic stand-in for the Keystore AES wrapper. */
    private class FakeSealer : WhisperSelfReadSealer {
        override fun seal(plain: ByteArray): String? =
            "s:" + Base64.getEncoder().encodeToString(plain)

        override fun open(sealed: String): ByteArray? =
            if (sealed.startsWith("s:")) {
                runCatching { Base64.getDecoder().decode(sealed.removePrefix("s:")) }.getOrNull()
            } else {
                null
            }
    }

    private class FakeDao : WhisperSelfReadDao {
        val rows = ConcurrentHashMap<String, WhisperSelfReadEntity>()

        override suspend fun upsert(entry: WhisperSelfReadEntity) {
            rows[entry.messageId] = entry
        }

        override suspend fun get(ownerId: String, messageId: String): WhisperSelfReadEntity? =
            rows[messageId]?.takeIf { it.ownerId == ownerId }

        override suspend fun getMany(ownerId: String, messageIds: List<String>): List<WhisperSelfReadEntity> =
            messageIds.mapNotNull { rows[it] }.filter { it.ownerId == ownerId }

        override suspend fun getByContentHash(ownerId: String, contentHash: String): WhisperSelfReadEntity? =
            rows.values.filter { it.ownerId == ownerId && it.contentHash == contentHash }
                .maxByOrNull { it.createdAtMs }

        override suspend fun delete(messageId: String) {
            rows.remove(messageId)
        }

        override suspend fun deleteMany(messageIds: List<String>) {
            messageIds.forEach { rows.remove(it) }
        }

        override suspend fun prune(ownerId: String, keep: Int) {
            rows.values
                .filter { it.ownerId == ownerId }
                .sortedByDescending { it.createdAtMs }
                .drop(keep)
                .forEach { rows.remove(it.messageId) }
        }

        override suspend fun clearAll() {
            rows.clear()
        }
    }

    private fun store(dao: FakeDao = FakeDao()) = WhisperSelfReadStore(dao, FakeSealer())

    @Test
    fun `own sent plaintext is recalled after restart`() = runTest {
        val dao = FakeDao()
        WhisperSelfReadStore(dao, FakeSealer()).remember("owner-a", "m1", "hello world", "cipher-m1")

        // A fresh store instance over the same rows == a cold start (no in-process memo).
        val afterRestart = WhisperSelfReadStore(dao, FakeSealer())
        assertEquals("hello world", afterRestart.recall("owner-a", "m1"))
    }

    @Test
    fun `copy is not readable by another account`() = runTest {
        val store = store()
        store.remember("owner-a", "m1", "hello world", "cipher-m1")

        assertNull(store.recall("owner-b", "m1"))
        assertEquals(emptyMap<String, String>(), store.recallAll("owner-b", listOf("m1")))
    }

    @Test
    fun `preview resolves by wire ciphertext`() = runTest {
        val store = store()
        store.remember("owner-a", "m1", "preview text", "exact-wire-ciphertext")

        assertEquals("preview text", store.recallByCiphertext("owner-a", "exact-wire-ciphertext"))
        assertNull(store.recallByCiphertext("owner-a", "different-ciphertext"))
    }

    @Test
    fun `a sealed blob moved onto another message id is rejected and pruned`() = runTest {
        val dao = FakeDao()
        val sealer = FakeSealer()
        val store = WhisperSelfReadStore(dao, sealer)
        // Seal a payload for m1, then store it under m2 (local tampering / DB swap).
        val foreign = checkNotNull(sealer.seal("SR1|m1|secret for m1".toByteArray()))
        dao.rows["m2"] = WhisperSelfReadEntity(
            messageId = "m2",
            ownerId = "owner-a",
            contentHash = "hash-m2",
            sealed = foreign,
            createdAtMs = 1L,
        )

        assertNull(store.recall("owner-a", "m2"))
        assertNull(dao.rows["m2"])
    }

    @Test
    fun `delete forgets the copy`() = runTest {
        val store = store()
        store.remember("owner-a", "m1", "to be deleted", "cipher-m1")
        store.forget("m1")

        assertNull(store.recall("owner-a", "m1"))
    }

    @Test
    fun `retention keeps only the newest copies`() = runTest {
        val dao = FakeDao()
        val store = WhisperSelfReadStore(dao, FakeSealer())
        val total = WhisperSelfReadStore.MAX_PER_OWNER + 5
        repeat(total) { i ->
            store.remember("owner-a", "m$i", "text $i", "cipher-$i")
        }

        assertEquals(WhisperSelfReadStore.MAX_PER_OWNER, dao.rows.size)
        assertNull(store.recall("owner-a", "m0"))
        assertEquals("text ${total - 1}", store.recall("owner-a", "m${total - 1}"))
    }
}

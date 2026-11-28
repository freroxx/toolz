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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cover for the incoming-history vault: the store that keeps a message this
 * device already opened readable after the ratchet session is lost (desync,
 * rotation race, sign-out/sign-in) instead of showing a permanent lock.
 */
class WhisperPlaintextVaultTest {

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

    private class FakeDao : WhisperPlaintextVaultDao {
        val rows = ConcurrentHashMap<String, WhisperPlaintextVaultEntity>()

        override suspend fun upsert(entry: WhisperPlaintextVaultEntity) {
            rows[entry.messageId] = entry
        }

        override suspend fun get(ownerId: String, messageId: String): WhisperPlaintextVaultEntity? =
            rows[messageId]?.takeIf { it.ownerId == ownerId }

        override suspend fun getByContentHash(ownerId: String, contentHash: String): WhisperPlaintextVaultEntity? =
            rows.values.filter { it.ownerId == ownerId && it.contentHash == contentHash }
                .maxByOrNull { it.createdAtMs }

        override suspend fun delete(messageId: String) {
            rows.remove(messageId)
        }

        override suspend fun deleteMany(messageIds: List<String>) {
            messageIds.forEach { rows.remove(it) }
        }

        override suspend fun getRecent(ownerId: String, limit: Int): List<WhisperPlaintextVaultEntity> =
            rows.values.filter { it.ownerId == ownerId }
                .sortedByDescending { it.createdAtMs }
                .take(limit)

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

    private fun store(dao: FakeDao = FakeDao()) = WhisperPlaintextVault(dao, FakeSealer())

    @Test
    fun `opened incoming message survives a lost session`() = runTest {
        val dao = FakeDao()
        WhisperPlaintextVault(dao, FakeSealer()).remember("owner-a", "m1", "hello there", "wire-m1")

        val afterRestart = WhisperPlaintextVault(dao, FakeSealer())
        assertEquals("hello there", afterRestart.recall("owner-a", "m1"))
    }

    @Test
    fun `vault is isolated per account`() = runTest {
        val store = store()
        store.remember("owner-a", "m1", "hello", "wire-m1")

        assertNull(store.recall("owner-b", "m1"))
        assertNull(store.recallByCiphertext("owner-b", "wire-m1"))
    }

    @Test
    fun `preview resolves by wire ciphertext`() = runTest {
        val store = store()
        store.remember("owner-a", "m1", "preview", "wire-preview")

        assertEquals("preview", store.recallByCiphertext("owner-a", "wire-preview"))
        assertNull(store.recallByCiphertext("owner-a", "wire-other"))
    }

    @Test
    fun `a sealed blob moved onto another id is rejected and pruned`() = runTest {
        val dao = FakeDao()
        val sealer = FakeSealer()
        val store = WhisperPlaintextVault(dao, sealer)
        val foreign = checkNotNull(sealer.seal("PV1|m1|secret for m1".toByteArray()))
        dao.rows["m2"] = WhisperPlaintextVaultEntity(
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
    fun `blank plaintext is never stored`() = runTest {
        val dao = FakeDao()
        val store = WhisperPlaintextVault(dao, FakeSealer())
        store.remember("owner-a", "m1", "   ", "wire-m1")

        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `retention keeps only the newest entries`() = runTest {
        val dao = FakeDao()
        val store = WhisperPlaintextVault(dao, FakeSealer())
        val total = WhisperPlaintextVault.MAX_PER_OWNER + 3
        repeat(total) { i ->
            store.remember("owner-a", "m$i", "text $i", "wire-$i")
        }

        assertEquals(WhisperPlaintextVault.MAX_PER_OWNER, dao.rows.size)
        assertNull(store.recall("owner-a", "m0"))
    }

    @Test
    fun `export then import re-seals entries for the new install`() = runTest {
        val source = store()
        source.remember("owner-a", "m1", "first", "wire-1")
        source.remember("owner-a", "m2", "second", "wire-2")

        val exported = source.exportEntries("owner-a")
        assertEquals(2, exported.size)

        // Simulate the new device: different sealer instance, empty rows.
        val targetDao = FakeDao()
        val target = WhisperPlaintextVault(targetDao, FakeSealer())
        exported.forEach { entry ->
            target.importEntry("owner-b", entry.messageId, entry.contentHash, entry.plaintext)
        }

        assertEquals("first", target.recall("owner-b", "m1"))
        assertEquals("second", target.recallByCiphertext("owner-b", "wire-2"))
        // Imported rows must never be readable by the old owner id.
        assertNull(target.recall("owner-a", "m1"))
    }

    @Test
    fun `delete and clear remove plaintext`() = runTest {
        val store = store()
        store.remember("owner-a", "m1", "one", "wire-1")
        store.remember("owner-a", "m2", "two", "wire-2")

        store.forget("m1")
        assertNull(store.recall("owner-a", "m1"))

        store.clearAll()
        assertNull(store.recall("owner-a", "m2"))
    }
}

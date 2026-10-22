/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Upgrade/adoption rules for the on-disk session layout
 * (`whisper_sessions/<sha256(owner)>/<sha256(peer)>.json`).
 *
 * Field defect (October 2026): the first account-scoped build swept every
 * pre-scoping session file into whichever account happened to be active first.
 * On a device with two accounts A and B chatting with each other, B's session
 * with A (stored as `<sha256(A)>.json`) was swallowed by A's directory, so B
 * could not decrypt A's frames and both conversations rendered locked.
 *
 * These tests pin the per-peer, lazy adoption that replaced the sweep — the
 * exact file layout, not just the API shape.
 */
class WhisperSessionFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val accountA = "11111111-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val accountB = "22222222-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

    private fun root(): File = tmp.newFolder("whisper_sessions")

    private fun legacySingleLevel(root: File, peerId: String, body: String): File {
        val f = File(root, WhisperSessionFiles.sha256Hex(peerId) + ".json")
        f.writeText(body)
        return f
    }

    private fun scopedDir(root: File, accountId: String): File =
        File(root, WhisperSessionFiles.sha256Hex(accountId))

    /** Two accounts on one install: each keeps the file whose *peer* it is. */
    @Test
    fun `legacy files are adopted per peer not per account`() {
        val root = root()
        val aSessionWithB = legacySingleLevel(root, accountB, "A->B")
        val bSessionWithA = legacySingleLevel(root, accountA, "B->A")

        val adoptedForA = WhisperSessionFiles.adopt(root, peerId = accountB, scope = accountA)
        val adoptedForB = WhisperSessionFiles.adopt(root, peerId = accountA, scope = accountB)

        assertEquals("A->B", adoptedForA?.readText())
        assertEquals("B->A", adoptedForB?.readText())
        // Moved out of the legacy root — a second account can never steal it back.
        assertFalse(aSessionWithB.exists())
        assertFalse(bSessionWithA.exists())
        assertEquals(
            WhisperSessionFiles.fileFor(root, accountB, accountA).absolutePath,
            adoptedForA?.absolutePath,
        )
        assertEquals(
            WhisperSessionFiles.fileFor(root, accountA, accountB).absolutePath,
            adoptedForB?.absolutePath,
        )
    }

    /** Repairs devices already hit by the eager sweep. */
    @Test
    fun `mis-swept self-named file is reclaimed by the counterpart account`() {
        val root = root()
        // Old migration put B's session-with-A (peer A) inside A's own directory.
        val wrongDir = scopedDir(root, accountA).apply { mkdirs() }
        val misSwept = File(wrongDir, WhisperSessionFiles.sha256Hex(accountA) + ".json")
        misSwept.writeText("B->A")

        // A itself can never use it: that would be a session whose peer is A.
        assertNull(WhisperSessionFiles.legacySource(root, peerId = accountA, scope = accountA))

        val adopted = WhisperSessionFiles.adopt(root, peerId = accountA, scope = accountB)

        assertEquals("B->A", adopted?.readText())
        assertFalse(misSwept.exists())
    }

    /** A correct per-account file is authoritative; legacy leftovers are ignored. */
    @Test
    fun `existing scoped file is never overwritten`() {
        val root = root()
        val scoped = WhisperSessionFiles.fileFor(root, accountB, accountA)
        scoped.parentFile!!.mkdirs()
        scoped.writeText("live")
        val legacy = legacySingleLevel(root, accountB, "stale")

        assertNull(WhisperSessionFiles.adopt(root, peerId = accountB, scope = accountA))
        assertEquals("live", scoped.readText())
        assertTrue(legacy.exists())
    }

    @Test
    fun `nothing to adopt is a clean no-op`() {
        val root = root()
        assertNull(WhisperSessionFiles.adopt(root, peerId = accountB, scope = accountA))
        assertNull(WhisperSessionFiles.adopt(root, peerId = accountA, scope = accountA))
    }

    @Test
    fun `layout hashes both owner and peer`() {
        val root = root()
        val path = WhisperSessionFiles.fileFor(root, peerId = accountB, scope = accountA)
        assertEquals(WhisperSessionFiles.sha256Hex(accountA), path.parentFile!!.name)
        assertEquals(WhisperSessionFiles.sha256Hex(accountB) + ".json", path.name)
        assertFalse(path.parentFile!!.name.contains(accountA))
    }
}

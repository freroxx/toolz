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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHashingTest {

    @Test
    fun `sha256 is stable lowercase hex`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ClipboardHashing.sha256("abc"),
        )
    }

    @Test
    fun `content hash is deterministic`() {
        val first = ClipboardHashing.contentHash("hello", "<b>hello</b>")
        val second = ClipboardHashing.contentHash("hello", "<b>hello</b>")
        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun `content hash changes with content html or attachments`() {
        val base = ClipboardHashing.contentHash("hello")
        assertNotEquals(base, ClipboardHashing.contentHash("hello world"))
        assertNotEquals(base, ClipboardHashing.contentHash("hello", "<b>hello</b>"))
        assertNotEquals(
            base,
            ClipboardHashing.contentHash(
                "hello",
                attachments = listOf(ClipboardHashAttachment("abc123", "a.png", "image/png")),
            ),
        )
    }

    @Test
    fun `attachment order does not change the hash`() {
        val one = ClipboardHashAttachment("aaa", "a.png", "image/png")
        val two = ClipboardHashAttachment("bbb", "b.png", "image/png")
        assertEquals(
            ClipboardHashing.contentHash("multi", attachments = listOf(one, two)),
            ClipboardHashing.contentHash("multi", attachments = listOf(two, one)),
        )
    }

    @Test
    fun `attachment metadata participates in the hash`() {
        val first = ClipboardHashing.contentHash(
            "file",
            attachments = listOf(ClipboardHashAttachment("abc", "a.png", "image/png")),
        )
        val renamed = ClipboardHashing.contentHash(
            "file",
            attachments = listOf(ClipboardHashAttachment("abc", "b.png", "image/png")),
        )
        assertNotEquals(first, renamed)
    }

    @Test
    fun `format marker prevents collisions with raw sha256`() {
        val hashed = ClipboardHashing.contentHash("abc")
        assertNotEquals(ClipboardHashing.sha256("abc"), hashed)
        assertTrue(hashed.all { it in "0123456789abcdef" })
    }
}

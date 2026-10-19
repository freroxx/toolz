/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.data.whisper.WhisperStoryLocalEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Poster grouping is shared by the hub rail, the Chats-tab strip, and the
 * viewer's segment order. These cases pin the merge/expiry rules, the
 * deterministic unseen-first order, and the promise that a friend-open never
 * swipes into my own ups.
 */
class WhisperStoryGroupingTest {

    private val me = profile("me", "Me")
    private val alice = profile("a", "Alice")
    private val bob = profile("b", "Bob")
    private val cara = profile("c", "Cara")

    @Test
    fun testBuild_mineFirst_groupsByOwner_sortsAndDedupes() {
        val groups = buildPosterGroups(
            mine = listOf(
                story("m2", "me", 30_000L),
                story("m1", "me", 60_000L),
            ),
            friends = listOf(
                story("a2", "a", 20_000L),
                story("a1", "a", 80_000L),
                story("a2", "a", 20_000L),
            ),
            profiles = mapOf("me" to me, "a" to alice),
            seen = emptySet(),
            nowMs = NOW,
        )

        assertEquals(2, groups.size)
        assertTrue(groups[0].isMine)
        assertEquals("me", groups[0].ownerId)
        assertEquals("Me", groups[0].ownerName)
        assertEquals(listOf("m2", "m1"), groups[0].stories.map { it.storyId })
        // Own ring is never counted as unseen, even with an empty seen set.
        assertEquals(0, groups[0].unseenCount)

        val friend = groups[1]
        assertFalse(friend.isMine)
        assertEquals("a", friend.ownerId)
        assertEquals("Alice", friend.ownerName)
        assertEquals(listOf("a2", "a1"), friend.stories.map { it.storyId })
        assertEquals(2, friend.unseenCount)
        assertEquals(NOW + 20_000L, friend.soonestExpiryMs)
    }

    @Test
    fun testBuild_railOrder_isUnseenFirst_thenSoonest_thenName_thenId() {
        val groups = buildPosterGroups(
            mine = emptyList(),
            friends = listOf(
                story("a1", "a", 20_000L),
                story("b1", "b", 5_000L),
                story("c1", "c", 10_000L),
                story("d1", "d", 10_000L),
                story("e1", "e", 10_000L),
            ),
            profiles = mapOf("a" to alice, "b" to bob, "c" to cara, "d" to profile("d", "Dan"), "e" to profile("e", "Cara")),
            seen = setOf("b1"),
            nowMs = NOW,
        )

        // c/e tie on unseen + expiry + name → story owner id is the last key.
        // d shares the expiry but sorts after Cara. a is unseen but expires
        // later; b is seen so it falls behind every bright ring.
        assertEquals(listOf("c", "e", "d", "a", "b"), groups.map { it.ownerId })
        assertEquals(listOf(1, 1, 1, 1, 0), groups.map { it.unseenCount })
    }

    @Test
    fun testViewerOrder_friendOpen_startsWithSelected_andExcludesMine() {
        val groups = buildPosterGroups(
            mine = listOf(story("m1", "me", 60_000L)),
            friends = listOf(
                story("a1", "a", 30_000L),
                story("b1", "b", 10_000L),
            ),
            profiles = mapOf("me" to me, "a" to alice, "b" to bob),
            seen = emptySet(),
            nowMs = NOW,
        )

        val forAlice = viewerOrderFor(groups, "a")
        assertEquals(listOf("a", "b"), forAlice.map { it.ownerId })
        assertTrue(forAlice.none { it.isMine })

        val forBob = viewerOrderFor(groups, "b")
        assertEquals(listOf("b", "a"), forBob.map { it.ownerId })
        assertTrue(forBob.none { it.isMine })

        // Unknown owner (stale route/story) is a silent no-op.
        assertTrue(viewerOrderFor(groups, "missing").isEmpty())

        // A bare hub-open keeps the rail order: mine first, then friends.
        val hubOrder = viewerOrderFor(groups, null)
        assertEquals(listOf("me", "b", "a"), hubOrder.map { it.ownerId })
    }

    @Test
    fun testViewerOrder_mineOpen_isOneSegment_only() {
        val groups = buildPosterGroups(
            mine = listOf(story("m1", "me", 60_000L)),
            friends = listOf(story("a1", "a", 30_000L)),
            profiles = mapOf("me" to me, "a" to alice),
            seen = emptySet(),
            nowMs = NOW,
        )

        val mine = viewerOrderFor(groups, "me")
        assertEquals(1, mine.size)
        assertTrue(mine.single().isMine)
        assertEquals(listOf("m1"), mine.single().stories.map { it.storyId })
    }

    @Test
    fun testStartIndex_firstUnseenElseZero_andExpiredRowsDrop() {
        val groups = buildPosterGroups(
            mine = emptyList(),
            friends = listOf(
                story("a1", "a", 10_000L),
                story("a2", "a", 20_000L),
                story("a3", "a", 30_000L),
                story("expired", "a", 0L),
                story("", "a", 5_000L),
                story("bad_owner", "", 5_000L),
            ),
            profiles = mapOf("a" to alice),
            seen = setOf("a1"),
            nowMs = NOW,
        )

        val poster = groups.single()
        assertEquals(listOf("a1", "a2", "a3"), poster.stories.map { it.storyId })
        assertEquals(2, poster.unseenCount)
        assertEquals(1, startIndexIn(poster, setOf("a1")))
        assertEquals(0, startIndexIn(poster, emptySet()))
        assertEquals(0, startIndexIn(poster, setOf("a1", "a2", "a3")))
        assertEquals(0, startIndexIn(poster, setOf("nope")))
        assertEquals("Alice", poster.ownerName)
    }

    private fun profile(id: String, name: String) = WhisperProfile(
        id = id,
        username = id,
        displayName = name,
    )

    private fun story(
        id: String,
        ownerId: String,
        expiresInMs: Long,
    ) = WhisperStoryLocalEntity(
        storyId = id,
        ownerId = ownerId,
        sealedDescriptor = "sealed",
        expiresAtMs = NOW + expiresInMs,
        receiptsEnabled = false,
        state = if (ownerId == "me") "own" else "active",
        updatedAtMs = 0L,
    )

    private companion object {
        const val NOW = 1_000_000L
    }
}

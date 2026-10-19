/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.data.whisper.WhisperStoryLocalEntity

/**
 * One poster in the Daily Ups rail / one viewer segment. Stories are live,
 * deduplicated, and sorted into reading order; [unseenCount] is forced to 0
 * for my own poster (views of my own ups never affect my rail).
 */
data class StoryPoster(
    val ownerId: String,
    val ownerName: String?,
    val picture: ByteArray?,
    val isMine: Boolean,
    val stories: List<WhisperStoryLocalEntity>,
    val soonestExpiryMs: Long,
    val unseenCount: Int,
)

/** Lightweight per-poster row used by the Chats-tab stories strip. */
data class StoryPosterRing(
    val ownerId: String,
    val name: String,
    val picture: ByteArray?,
    val storyCount: Int,
    val unseenCount: Int,
    val soonestExpiryMs: Long,
)

/**
 * Pure grouping for both Daily Ups surfaces. Duplicate owner ids merge, blank
 * or expired rows disappear, and the rail order is deterministic:
 * unseen-first, soonest expiry, name, then owner id as the final tie-break.
 */
internal fun buildPosterGroups(
    mine: List<WhisperStoryLocalEntity>,
    friends: List<WhisperStoryLocalEntity>,
    profiles: Map<String, WhisperProfile>,
    seen: Set<String>,
    nowMs: Long,
): List<StoryPoster> {
    val mineGroups = groupForOwner(mine, profiles, seen, nowMs, isMine = true)
        .sortedWith(railOrder)
    val friendGroups = groupForOwner(friends, profiles, seen, nowMs, isMine = false)
        .sortedWith(railOrder)
    return mineGroups + friendGroups
}

/**
 * Builds the viewer's immutable order for one open gesture:
 * - my poster → exactly one segment (never pages into friends);
 * - a friend → that friend first, then other friends unseen-first;
 * - null → the hub order (mine first, then friends).
 *
 * A friend-open NEVER includes my own poster, so swiping can't cross into my
 * stories from a friend's ring. Unknown owners produce an empty order.
 */
internal fun viewerOrderFor(
    groups: List<StoryPoster>,
    selectedOwnerId: String?,
): List<StoryPoster> {
    if (selectedOwnerId == null) return groups
    val selected = groups.firstOrNull { it.ownerId == selectedOwnerId } ?: return emptyList()
    if (selected.isMine) return listOf(selected)
    val others = groups
        .filter { !it.isMine && it.ownerId != selected.ownerId }
        .sortedWith(railOrder)
    return listOf(selected) + others
}

/** First unseen story, else the first story. Always a valid index when non-empty. */
internal fun startIndexIn(poster: StoryPoster, seen: Set<String>): Int {
    val firstUnseen = poster.stories.indexOfFirst { it.storyId !in seen }
    return if (firstUnseen >= 0) firstUnseen else 0
}

private val railOrder = compareByDescending<StoryPoster> { it.unseenCount > 0 }
    .thenBy { it.soonestExpiryMs }
    .thenBy { it.ownerName.orEmpty() }
    .thenBy { it.ownerId }

private fun groupForOwner(
    rows: List<WhisperStoryLocalEntity>,
    profiles: Map<String, WhisperProfile>,
    seen: Set<String>,
    nowMs: Long,
    isMine: Boolean,
): List<StoryPoster> = rows
    .filter { it.ownerId.isNotBlank() && it.storyId.isNotBlank() && it.expiresAtMs > nowMs }
    .groupBy { it.ownerId }
    .map { (ownerId, ownerRows) ->
        val stories = ownerRows
            .sortedWith(compareBy({ it.expiresAtMs }, { it.storyId }))
            .distinctBy { it.storyId }
        StoryPoster(
            ownerId = ownerId,
            ownerName = profiles[ownerId]?.effectiveName?.takeIf { it.isNotBlank() },
            picture = null,
            isMine = isMine,
            stories = stories,
            soonestExpiryMs = stories.first().expiresAtMs,
            unseenCount = if (isMine) 0 else stories.count { it.storyId !in seen },
        )
    }

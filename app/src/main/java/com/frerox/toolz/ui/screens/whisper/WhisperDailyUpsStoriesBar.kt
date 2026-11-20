/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperDailyUpsConfig
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback

/**
 * Instagram-stories-style circles ABOVE the groups list (v1.1.7 round): my
 * profile bubble — with a bottom-right "+" that starts the daily-up upload —
 * FIRST, then one circle per live friend POSTER (not per story), horizontally
 * scrollable.
 *
 * Deliberately the cheapest daily-ups surface in the app: Room observes only —
 * no media decrypt, no story-server round trip, no image fetch (media stays in
 * the hub, same posture as [RingStrip]). Ring brightness and the ≥2 badge come
 * from the device-local seen store; expired circles vanish within the VM's 60s
 * ticker, never leaving a ghost day.
 */
@Composable
internal fun DailyUpsStoriesBar(
    viewModel: WhisperGroupsViewModel = hiltViewModel(),
    onCompose: () -> Unit,
    onOpenPoster: (String) -> Unit,
) {
    if (!WhisperDailyUpsConfig.ENABLED) return
    val strip by viewModel.dailyUpsStrip.collectAsStateWithLifecycle()
    val haptic = rememberToolzHapticFeedback()
    val youLabel = stringResource(R.string.st_Whisper_You)
    val someone = stringResource(R.string.st_Whisper_DailyUp_Someone)
    val newUpLabel = stringResource(R.string.st_Whisper_DailyUp_NewUp)
    val seenLabel = stringResource(R.string.st_Whisper_DailyUp_SeenState)
    val unseenLabel = stringResource(R.string.st_Whisper_DailyUp_UnseenState)
    val createActionLabel = stringResource(R.string.st_Whisper_DailyUp_CreateAction)

    // The own bubble is ALWAYS present: it is the compose entry, exactly like
    // Instagram keeps "your story" on an empty day.
    // The parent Chats list already insets its items: adding 16dp here pushed
    // the strip 16dp further in than the group cards below it.
    // LazyRow, not Row+horizontalScroll: with a long friend list the eager
    // Row composed (and queued decode for) every avatar at once. Lazy keeps
    // that bounded to the circles that are actually visible.
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        item(key = "me") {
            val me = strip.me
            val ownName = me?.effectiveName?.takeIf { it.isNotBlank() } ?: youLabel
            val myId = me?.id ?: "daily_ups_me"
            val ownLabel = if (strip.mineLive) stringResource(R.string.st_Whisper_DailyUp_YourDay) else newUpLabel
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(68.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .semantics {
                        // A live own bubble opens the ring on tap, so the "+" badge
                        // (cleared from semantics with the bubble) would otherwise
                        // leave no screen-reader path to posting a new Up.
                        if (strip.mineLive) {
                            customActions = listOf(
                                CustomAccessibilityAction(createActionLabel) {
                                    haptic.click()
                                    onCompose()
                                    true
                                },
                            )
                        }
                        onClick(label = ownLabel) {
                            haptic.click()
                            if (strip.mineLive && me?.id != null) {
                                onOpenPoster(me.id)
                            } else {
                                onCompose()
                            }
                            true
                        }
                    }
                    .bouncyClick {
                        haptic.click()
                        if (strip.mineLive && me?.id != null) {
                            onOpenPoster(me.id)
                        } else {
                            onCompose()
                        }
                    }
                    .padding(vertical = 4.dp),
            ) {
                StoryBubble(
                    ownerId = myId,
                    name = ownName,
                    profile = strip.me,
                    ringColor = if (strip.mineLive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    ringWidth = if (strip.mineLive) 3.dp else 2.dp,
                    plusBadge = true,
                    badgeCount = 0,
                    onPlusClick = {
                        haptic.click()
                        onCompose()
                    },
                )
                Text(
                    text = ownLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (strip.mineLive) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        items(strip.friendPosters, key = { it.ownerId }) { poster ->
            val name = poster.name.ifBlank { someone }
            val openLabel = stringResource(
                if (poster.storyCount == 1) {
                    R.string.st_Whisper_DailyUp_RailLabelOne
                } else {
                    R.string.st_Whisper_DailyUp_RailLabel
                },
                name,
                poster.storyCount,
                if (poster.unseenCount > 0) unseenLabel else seenLabel,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(68.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .semantics {
                        onClick(label = openLabel) {
                            haptic.click()
                            onOpenPoster(poster.ownerId)
                            true
                        }
                    }
                    .bouncyClick {
                        haptic.click()
                        onOpenPoster(poster.ownerId)
                    }
                    .padding(vertical = 4.dp),
            ) {
                StoryBubble(
                    ownerId = poster.ownerId,
                    name = name,
                    profile = strip.friendsById[poster.ownerId],
                    ringColor = if (poster.unseenCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    plusBadge = false,
                    badgeCount = poster.unseenCount,
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (poster.unseenCount > 0) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * One story circle: 2dp ring + stable initial avatar, optional "+" badge pinned
 * bottom-right (own bubble only) or an unseen-count badge (≥2) at top-right.
 */
@Composable
private fun StoryBubble(
    ownerId: String,
    name: String,
    ringColor: Color,
    plusBadge: Boolean,
    badgeCount: Int,
    ringWidth: Dp = 2.dp,
    profile: WhisperProfile? = null,
    onPlusClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .border(ringWidth, ringColor, CircleShape)
            .padding(3.dp)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (profile != null) {
            // Same decrypt/cache path as chat rows, so the strip shows the
            // friend's real picture instead of initials.
            WhisperAvatar(
                profile = profile,
                size = 48.dp,
                modifier = Modifier.clearAndSetSemantics {},
            )
        } else {
            GroupAvatar(
                name = name,
                groupId = ownerId,
                size = 48.dp,
            )
        }
        if (plusBadge) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.BottomEnd)
                    .then(
                        if (onPlusClick != null) {
                            Modifier.clickable(onClick = onPlusClick)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.st_Whisper_DailyUp_AddCd),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        if (badgeCount >= 2) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.TopEnd),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

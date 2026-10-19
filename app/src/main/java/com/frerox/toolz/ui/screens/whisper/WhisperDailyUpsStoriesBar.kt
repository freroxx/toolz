/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperDailyUpsConfig
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

    // The own bubble is ALWAYS present: it is the compose entry, exactly like
    // Instagram keeps "your story" on an empty day.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val ownName = strip.me?.effectiveName?.takeIf { it.isNotBlank() } ?: youLabel
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClickLabel = newUpLabel) {
                    haptic.click()
                    onCompose()
                }
                .padding(vertical = 4.dp),
        ) {
            StoryBubble(
                ownerId = strip.me?.id ?: "daily_ups_me",
                name = ownName,
                ringColor = if (strip.mineLive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                },
                plusBadge = true,
                badgeCount = 0,
            )
            Text(
                text = newUpLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        strip.friendPosters.forEach { poster ->
            val name = poster.name.ifBlank { someone }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = name) {
                        haptic.click()
                        onOpenPoster(poster.ownerId)
                    }
                    .padding(vertical = 4.dp),
            ) {
                StoryBubble(
                    ownerId = poster.ownerId,
                    name = name,
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
                    fontWeight = FontWeight.SemiBold,
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
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .border(2.dp, ringColor, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        GroupAvatar(
            name = name,
            groupId = ownerId,
            size = 48.dp,
        )
        if (plusBadge) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.BottomEnd),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
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

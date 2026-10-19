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
import androidx.compose.material.icons.rounded.Person
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
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback

/**
 * Instagram-stories-style circles ABOVE the groups list (v1.1.7 round): my
 * profile bubble — with a bottom-right "+" that starts the daily-up upload —
 * FIRST, then one circle per live friend poster, horizontally scrollable.
 *
 * Deliberately the cheapest daily-ups surface in the app: Room observes only —
 * no media decrypt, no story-server round trip, no image fetch (media stays in
 * the hub, same posture as [RingStrip]). Liverness follows the VM's 60s ticker,
 * so expired circles vanish within a minute without a ghost day.
 */
@Composable
internal fun DailyUpsStoriesBar(
    viewModel: WhisperGroupsViewModel = hiltViewModel(),
    onCompose: () -> Unit,
    onOpenHub: () -> Unit,
) {
    if (!WhisperDailyUpsConfig.ENABLED) return
    val strip by viewModel.dailyUpsStrip.collectAsStateWithLifecycle()
    val haptic = rememberToolzHapticFeedback()

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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    onClickLabel = stringResource(R.string.st_Whisper_DailyUp_AddCd),
                    onClick = {
                        haptic.click()
                        onCompose()
                    },
                )
                .padding(vertical = 4.dp),
        ) {
            StoryBubble(
                profile = strip.me,
                ringColor = if (strip.mineLive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                },
                plusBadge = true,
                fallbackInitial = null,
                contentDescription = stringResource(R.string.st_Whisper_DailyUp_AddCd),
            )
            Text(
                text = stringResource(R.string.st_Whisper_DailyUp_NewUp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        strip.friendPosters.forEach { poster ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        onClickLabel = poster.effectiveName,
                        onClick = {
                            haptic.click()
                            onOpenHub()
                        },
                    )
                    .padding(vertical = 4.dp),
            ) {
                StoryBubble(
                    profile = poster,
                    ringColor = MaterialTheme.colorScheme.primary,
                    plusBadge = false,
                    fallbackInitial = poster.effectiveName.take(1).uppercase(),
                    contentDescription = poster.effectiveName,
                )
                Text(
                    text = poster.effectiveName,
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
 * One story circle: 2dp ring + avatar with initials fallback (this lane never
 * decrypts avatars that fail resolution — mirrors the hub's ring language),
 * optional "+" badge pinned bottom-right (own bubble only).
 */
@Composable
private fun StoryBubble(
    profile: WhisperProfile?,
    ringColor: Color,
    plusBadge: Boolean,
    fallbackInitial: String?,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .border(2.dp, ringColor, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (profile != null) {
                    WhisperAvatar(profile = profile, size = 48.dp)
                } else if (fallbackInitial != null) {
                    Text(
                        text = fallbackInitial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = contentDescription,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (plusBadge) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
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
    }
}

/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.R
import com.frerox.toolz.ui.theme.LocalIsDarkTheme

/**
 * The one semantic accent Daily Ups needs beyond the M3 palette: close-friend
 * ups are green everywhere (hub card, chats strip, viewer). It is deliberately
 * a fixed pair rather than a theme slot so the meaning cannot drift with a
 * future dynamic-color seed. Contrast-checked against both surface families.
 */
@Composable
internal fun closeFriendsRingColor() =
    if (LocalIsDarkTheme.current) androidx.compose.ui.graphics.Color(0xFF6FDC8C)
    else androidx.compose.ui.graphics.Color(0xFF1B7A45)

/** Softer fill used behind the "Close friends" label chip. */
@Composable
internal fun closeFriendsContainerColor() =
    closeFriendsRingColor().copy(alpha = 0.16f)

/** Compact green "Close friends" marker used on cards and in the viewer. */
@Composable
internal fun CloseFriendsChip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = closeFriendsContainerColor(),
        contentColor = closeFriendsRingColor(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = stringResource(R.string.st_Whisper_DailyUp_CloseFriends),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

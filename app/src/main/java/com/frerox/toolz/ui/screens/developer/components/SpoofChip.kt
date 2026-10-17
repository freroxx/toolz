/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Required watermark on every spoofed tool screen. Small, honest, greppable.
 */
@Composable
fun SpoofChip(
    modifier: Modifier = Modifier,
    label: String = "SPOOF • LOCAL",
) {
    Surface(
        modifier = modifier.semantics { contentDescription = "Spoofed test data" },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Science, contentDescription = null, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun SpoofBanner(
    modifier: Modifier = Modifier,
    text: String = "SPOOF SANDBOX — fake local data. Nothing was sent, saved, or deleted.",
    onOpenControls: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { m -> if (onOpenControls != null) m.clickable(onClick = onOpenControls) else m }
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Rounded.Science,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = 1,
        )
        if (onOpenControls != null) {
            Text(
                "CONTROLS →",
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Shared pieces of the Developer Mode console: the master control card and
 * the confirmation dialog used by destructive local actions.
 */

package com.frerox.toolz.ui.screens.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.ui.screens.developer.components.DevCard
import com.frerox.toolz.ui.screens.developer.components.DevLeadIcon

/**
 * Master switch plus the two reversible bulk actions. [onToggle] only ever
 * receives false from this card because the console is hidden while off;
 * turning it back on goes through the locked screen.
 */
@Composable
fun DeveloperMasterCard(
    activeCount: Int,
    onToggle: (Boolean) -> Unit,
    onEnableAll: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = DevToolId.entries.size
    DevCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DevLeadIcon(icon = Icons.Rounded.Science)
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Developer Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (activeCount == 0) {
                        "On · no sandboxes active"
                    } else {
                        "On · $activeCount of $total sandboxes active"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = true,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = "Developer Mode" },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

        Text(
            text = if (activeCount == 0) {
                "Every tool is using live data. Turn on a sandbox to show fixture values."
            } else {
                "Sandboxes replace data in memory only. Real files, databases, radios and sensors stay untouched."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onEnableAll,
                enabled = activeCount < total,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("Enable all", maxLines = 1)
            }
            OutlinedButton(
                onClick = onClearAll,
                enabled = activeCount > 0,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("Return to live", maxLines = 1)
            }
        }
    }
}

@Composable
fun DevConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    icon: ImageVector = Icons.Rounded.Warning,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(title) },
        text = {
            Text(
                text = body,
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

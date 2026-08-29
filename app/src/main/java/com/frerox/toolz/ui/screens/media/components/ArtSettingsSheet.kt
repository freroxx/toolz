package com.frerox.toolz.ui.screens.media.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtSettingsSheet(
    artShape: String,
    rotationEnabled: Boolean,
    onSetArtShape: (String) -> Unit,
    onToggleRotation: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Art settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Text(
                "Customize how the cover art looks in the full player.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Shape selection
            Text("Art shape", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = artShape == "CIRCLE",
                    onClick = { onSetArtShape("CIRCLE") },
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                    icon = { Icon(Icons.Rounded.Circle, null, modifier = Modifier.size(18.dp)) },
                    label = { Text("Circle") }
                )
                SegmentedButton(
                    selected = artShape == "SQUARE",
                    onClick = { onSetArtShape("SQUARE") },
                    shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                    icon = { Icon(Icons.Rounded.CropSquare, null, modifier = Modifier.size(18.dp)) },
                    label = { Text("Square") }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Rotation toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Rotate art", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Slowly rotate the cover like a vinyl record while playing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rotationEnabled,
                    onCheckedChange = { onToggleRotation() }
                )
            }

            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

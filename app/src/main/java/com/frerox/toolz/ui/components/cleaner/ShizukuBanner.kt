package com.frerox.toolz.ui.components.cleaner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ShizukuBanner(isGranted: Boolean, onGrantClick: ()->Unit, onDismiss: ()->Unit, modifier: Modifier = Modifier) {
    if (isGranted) return
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.12f))) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Security, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Unlock privileged cleaning", style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
                Text("Grant Shizuku for hidden caches & /data", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onGrantClick, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Grant", style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium)) }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f)) }
        }
    }
}

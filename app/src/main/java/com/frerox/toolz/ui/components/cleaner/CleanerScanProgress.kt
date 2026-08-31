package com.frerox.toolz.ui.components.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CleanerScanProgress(currentCategory: String, progress: Float, filesScanned: Int, foundSize: Long, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.TravelExplore, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Spacer(Modifier.height(16.dp))
        Text(currentCategory.ifBlank { "Scanning…" }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { progress.coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth(0.85f).height(4.dp).clip(RoundedCornerShape(12.dp)), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("$filesScanned", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp)); Text("Files", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(Formatter.formatFileSize(LocalContext.current, foundSize), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp)); Text("Found", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

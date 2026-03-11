package com.frerox.toolz.ui.screens.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PdfFileList(
    files: List<PdfFile>,
    onFileClick: (PdfFile) -> Unit,
    onMenuClick: (PdfFile) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdge(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.05f to Color.Black,
                    0.95f to Color.Black,
                    1f to Color.Transparent
                ),
                length = 24.dp
            ),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp, top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "RECENT DOCUMENTS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, start = 8.dp),
                letterSpacing = 2.sp
            )
        }
        items(files) { file ->
            PdfFileItem(
                file = file,
                onClick = { onFileClick(file) },
                onMenuClick = { onMenuClick(file) }
            )
        }
    }
}

@Composable
fun PdfFileItem(
    file: PdfFile,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatSize(file.size)} • ${formatDate(file.lastModified)}".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert, 
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatSize(size: Long): String {
    val kb = size / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) "%.2f MB".format(mb) else "%.2f KB".format(kb)
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000)) // MediaStore returns seconds
}

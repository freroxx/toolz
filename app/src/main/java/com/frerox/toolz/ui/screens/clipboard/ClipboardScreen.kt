package com.frerox.toolz.ui.screens.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardScreen(
    viewModel: ClipboardViewModel,
    onBack: () -> Unit,
    onConvertToTask: (String) -> Unit = {}
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val groups = remember(entries) { viewModel.groupedEntries(entries) }
    val listState = rememberLazyListState()
    var showClearAllConfirmation by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredEntries = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) entries
        else entries.filter { it.content.contains(searchQuery, ignoreCase = true) }
    }
    
    val displayGroups = remember(filteredEntries, groups) {
        if (searchQuery.isBlank()) groups
        else viewModel.groupedEntries(filteredEntries)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshClipboard()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                "CLIPBOARD",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleMedium,
                                letterSpacing = 2.sp
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { isSearchActive = !isSearchActive },
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSearchActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    if (isSearchActive) Icons.Rounded.SearchOff else Icons.Rounded.Search,
                                    contentDescription = "Search",
                                    tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { viewModel.refreshClipboard() },
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary)
                            }

                            if (entries.isNotEmpty()) {
                                IconButton(
                                    onClick = { showClearAllConfirmation = true },
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear All")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )

                    if (isSearchActive) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search clipboard...", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp)) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (showClearAllConfirmation) {
            AlertDialog(
                onDismissRequest = { showClearAllConfirmation = false },
                title = { Text("Clear All History?", color = MaterialTheme.colorScheme.onSurface) },
                text = { Text("This will permanently remove all unpinned items from your clipboard history.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAll()
                            showClearAllConfirmation = false
                        }
                    ) {
                        Text("CLEAR ALL", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllConfirmation = false }) {
                        Text("CANCEL")
                    }
                },
                shape = RoundedCornerShape(32.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        Box(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (entries.isEmpty()) {
                EmptyClipboardState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdges(top = 16.dp, bottom = 16.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    displayGroups.forEach { group ->
                        item(key = "header_${group.label}") {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                group.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )
                        }

                        items(group.entries, key = { it.id }) { entry ->
                            val index = listState.firstVisibleItemIndex
                            LiquidStackCard(
                                entry = entry,
                                scrollOffset = index,
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Toolz Clip", entry.content))
                                    Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = { viewModel.deleteEntry(entry) },
                                onPin = { viewModel.togglePin(entry.id) },
                                onAction = { action -> 
                                    if (action == "convert_to_task") {
                                        onConvertToTask(entry.content)
                                    } else {
                                        handleContextualAction(context, action, entry)
                                    }
                                }
                            )
                        }

                        item(key = "divider_${group.label}") {
                            SquigglyDivider()
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyClipboardState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = RoundedCornerShape(48.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            ) {
                Icon(
                    Icons.Rounded.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.padding(32.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Your clipboard is empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Copy anything and it shows here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LiquidStackCard(
    entry: ClipboardEntry,
    scrollOffset: Int,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onAction: (String) -> Unit
) {
    val tiltDeg = remember(scrollOffset) { (scrollOffset % 5) * 0.3f }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationZ = tiltDeg
                shadowElevation = 12f
            }
            .bouncyClick(onClick = onCopy),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TypeIcon(entry.type, entry.content)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.type,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }
                IconButton(onClick = onPin, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = "Pin",
                        tint = if (entry.isPinned) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (entry.type == "CODE") FontFamily.Monospace else FontFamily.Default,
                    lineHeight = 20.sp
                ),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ActionChip("Copy", Icons.Rounded.ContentCopy) { onCopy() }
                ActionChip("Task", Icons.AutoMirrored.Rounded.PlaylistAdd) { onAction("convert_to_task") }

                when (entry.type) {
                    "PHONE" -> {
                        ActionChip("Call", Icons.Rounded.Call) { onAction("call") }
                    }
                    "URL", "SOCIAL" -> {
                        ActionChip("Open", Icons.Rounded.OpenInBrowser) { onAction("open_url") }
                    }
                    "EMAIL" -> {
                        ActionChip("Email", Icons.Rounded.Email) { onAction("email") }
                    }
                    else -> {
                        ActionChip("Share", Icons.Rounded.Share) { onAction("share") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            val timeString = remember(entry.timestamp) { formatTimestamp(entry.timestamp) }
            Text(
                text = timeString,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun TypeIcon(type: String, content: String) {
    when (type) {
        "COLOR" -> {
            val color = try {
                Color(android.graphics.Color.parseColor(content.trim()))
            } catch (_: Exception) {
                MaterialTheme.colorScheme.primary
            }
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(14.dp),
                color = color,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {}
        }
        "URL" -> TypeIconBox(Icons.Rounded.Link, Color(0xFF1A73E8))
        "SOCIAL" -> TypeIconBox(Icons.Rounded.Public, Color(0xFF0D47A1))
        "PHONE" -> TypeIconBox(Icons.Rounded.Phone, Color(0xFF34A853))
        "OTP" -> TypeIconBox(Icons.Rounded.Key, Color(0xFFFFA000))
        "EMAIL" -> TypeIconBox(Icons.Rounded.Email, Color(0xFFEA4335))
        "MATHS" -> TypeIconBox(Icons.Rounded.Calculate, Color(0xFF9C27B0))
        "PERSONAL" -> TypeIconBox(Icons.Rounded.Favorite, Color(0xFFE91E63))
        "CODE" -> TypeIconBox(Icons.Rounded.Code, Color(0xFF00BCD4))
        "ADDRESS" -> TypeIconBox(Icons.Rounded.LocationOn, Color(0xFFFF5722))
        "CRYPTO" -> TypeIconBox(Icons.Rounded.CurrencyBitcoin, Color(0xFFF7931A))
        else -> TypeIconBox(Icons.Rounded.ContentPaste, MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TypeIconBox(icon: ImageVector, color: Color) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SquigglyDivider() {
    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val infiniteTransition = rememberInfiniteTransition(label = "squiggle")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "squigglePhase"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .padding(vertical = 4.dp)
    ) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val amplitude = 4f
        val wavelength = 40f
        val path = Path()

        path.moveTo(0f, midY)
        var x = 0f
        while (x <= width) {
            val y = midY + amplitude * sin((x / wavelength * 2 * Math.PI + phase).toDouble()).toFloat()
            path.lineTo(x, y)
            x += 2f
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )
    }
}

private fun handleContextualAction(context: Context, action: String, entry: ClipboardEntry) {
    when (action) {
        "call" -> {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${entry.content.trim()}"))
            context.startActivity(intent)
        }
        "whatsapp" -> {
            try {
                val phone = entry.content.trim().replace("[^\\d+]".toRegex(), "")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone"))
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "WhatsApp not found", Toast.LENGTH_SHORT).show()
            }
        }
        "open_url" -> {
            try {
                val content = entry.content.trim()
                val url = if (content.startsWith("http")) content
                          else if (content.startsWith("www.")) "https://$content"
                          else if (entry.type == "SOCIAL") "https://$content"
                          else if (entry.type == "CRYPTO") {
                              if (content.startsWith("0x")) "https://etherscan.io/address/$content"
                              else "https://www.blockchain.com/explorer/addresses/btc/$content"
                          }
                          else "https://www.google.com/search?q=$content"

                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
            }
        }
        "email" -> {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${entry.content.trim()}"))
            context.startActivity(intent)
        }
        "share" -> {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, entry.content)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

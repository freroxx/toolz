package com.frerox.toolz.ui.screens.notifications

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.frerox.toolz.data.notifications.NotificationEntry
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationVaultScreen(
    onNavigateBack: () -> Unit,
    viewModel: NotificationVaultViewModel = hiltViewModel()
) {
    val notifications by viewModel.notifications.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedAppDetails by remember { mutableStateOf<AppDetails?>(null) }
    var showNotificationMenu by remember { mutableStateOf<NotificationEntry?>(null) }
    var showVaultSettings by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NOTIFICATION VAULT", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp)
                        Text("ANTI-RECALL ENGINE ACTIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showVaultSettings = true },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Vault Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            VaultSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) }
            )

            CategoryStrip(
                categories = categories,
                selectedCategory = selectedCategory,
                onCategorySelect = { viewModel.setCategory(it) }
            )

            AnimatedContent(
                targetState = notifications.isEmpty(),
                transitionSpec = { 
                    (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f))
                        .togetherWith(fadeOut(animationSpec = tween(200))) 
                },
                label = "listContent",
                modifier = Modifier.weight(1f)
            ) { isEmpty ->
                if (isEmpty) {
                    EmptyVaultState(searchQuery.isNotEmpty())
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                                .fadingEdge(
                                    brush = Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        0.05f to Color.Black,
                                        0.95f to Color.Black,
                                        1f to Color.Transparent
                                    ),
                                    length = 24.dp
                                ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(notifications, key = { it.id }) { notification ->
                                SwipeToDeleteContainer(
                                    onDelete = { viewModel.deleteNotification(notification.id) }
                                ) {
                                    NotificationVaultCard(
                                        notification = notification,
                                        onDelete = { viewModel.deleteNotification(notification.id) },
                                        onLongClick = { showNotificationMenu = notification }
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                        
                        if (notifications.isNotEmpty() && searchQuery.isEmpty() && selectedCategory == "All") {
                            FloatingActionButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear All")
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("PURGE HISTORY?", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
                text = { Text("This will permanently delete all captured notifications from your device storage.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAll()
                            showDeleteDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("PURGE ALL", fontWeight = FontWeight.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("CANCEL", fontWeight = FontWeight.Bold)
                    }
                },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        if (showNotificationMenu != null) {
            val notification = showNotificationMenu!!
            ModalBottomSheet(
                onDismissRequest = { showNotificationMenu = null },
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 48.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    model = ImageRequest.Builder(context)
                                        .data(try { context.packageManager.getApplicationIcon(notification.packageName) } catch(e: Exception) { null })
                                        .crossfade(true)
                                        .build()
                                ),
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(notification.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text(notification.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    Text("VAULT ACTIONS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                    Spacer(Modifier.height(12.dp))
                    
                    val menuItems = listOf(
                        Triple("Hide notifications from this app", Icons.Rounded.VisibilityOff, {
                            viewModel.hideApp(notification.packageName)
                            showNotificationMenu = null
                            Toast.makeText(context, "App hidden from vault", Toast.LENGTH_SHORT).show()
                        }),
                        Triple("Analyze app footprint", Icons.Rounded.Analytics, {
                            scope.launch {
                                selectedAppDetails = viewModel.getAppDetails(notification.packageName)
                                showNotificationMenu = null
                            }
                        }),
                        Triple("Launch application", Icons.AutoMirrored.Rounded.Launch, {
                            try {
                                val intent = context.packageManager.getLaunchIntentForPackage(notification.packageName)
                                if (intent != null) {
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(context, "Cannot open app", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open app", Toast.LENGTH_SHORT).show()
                            }
                            showNotificationMenu = null
                        })
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        menuItems.forEach { (label, icon, action) ->
                            Surface(
                                onClick = { action() },
                                modifier = Modifier.fillMaxWidth().height(60.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(16.dp))
                                    Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedAppDetails?.let { details ->
            AppDetailsDialog(details = details, onDismiss = { selectedAppDetails = null })
        }

        if (showVaultSettings) {
            VaultSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showVaultSettings = false }
            )
        }
    }
}

@Composable
fun AppDetailsDialog(details: AppDetails, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val timeString = details.lastNotification?.let { 
        SimpleDateFormat("HH:mm, MMM dd", Locale.getDefault()).format(Date(it.timestamp))
    } ?: "N/A"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                try { context.packageManager.getApplicationIcon(details.packageName) } catch(e: Exception) { null }
                            ),
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(details.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text(details.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(Modifier.height(32.dp))
                Text("VAULT STATISTICS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                Spacer(Modifier.height(16.dp))
                
                DetailRow("TOTAL ENTRIES", "${details.totalNotifications}")
                DetailRow("LATEST CAPTURE", timeString)
                
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("CLOSE", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text(value, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSettingsDialog(
    viewModel: NotificationVaultViewModel,
    onDismiss: () -> Unit
) {
    val hiddenApps by viewModel.hiddenApps.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val appMappings by viewModel.appMappings.collectAsState()
    val distinctPackages by viewModel.distinctPackages.collectAsState()
    
    var newCategoryName by remember { mutableStateOf("") }
    var selectedPackageToMap by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("VAULT CONFIGURATION", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, letterSpacing = 1.sp) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null) }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "MANAGED SECTIONS", 
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Black, 
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                
                categories.forEach { category ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(category, fontWeight = FontWeight.Black)
                            if (category != "All" && category != "General") {
                                IconButton(onClick = { viewModel.removeCategory(category) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.RemoveCircle, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("New Section Name") },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    IconButton(
                        onClick = {
                            if (newCategoryName.isNotBlank()) {
                                viewModel.addCategory(newCategoryName)
                                newCategoryName = ""
                            }
                        },
                        modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }

                Spacer(Modifier.height(40.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "APPLICATION MAPPING", 
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Black, 
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                
                distinctPackages.forEach { pkg ->
                    val currentCat = appMappings[pkg] ?: "Auto"
                    Surface(
                        onClick = { selectedPackageToMap = pkg },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                                Image(
                                    painter = rememberAsyncImagePainter(try { context.packageManager.getApplicationIcon(pkg) } catch(e: Exception) { null }),
                                    contentDescription = null,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pkg, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                Text("SECTION: $currentCat", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                Spacer(Modifier.height(40.dp))
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "BLACKLISTED APPS", 
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Black, 
                        color = MaterialTheme.colorScheme.error,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                
                if (hiddenApps.isEmpty()) {
                    Text("No apps hidden from vault tracking.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 8.dp))
                } else {
                    hiddenApps.forEach { pkg ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(pkg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { viewModel.unhideApp(pkg) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Visibility, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(48.dp))
            }
        }

        selectedPackageToMap?.let { pkg ->
            AlertDialog(
                onDismissRequest = { selectedPackageToMap = null },
                title = { Text("SELECT SECTION", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                        (listOf("Auto") + categories).forEach { cat ->
                            Surface(
                                onClick = { 
                                    viewModel.mapAppToCategory(pkg, if (cat == "Auto") "" else cat)
                                    selectedPackageToMap = null
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = if ((appMappings[pkg] ?: "Auto") == cat || (cat == "Auto" && appMappings[pkg].isNullOrEmpty())) 
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                border = if ((appMappings[pkg] ?: "Auto") == cat) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Text(cat, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Black)
                            }
                        }
                    }
                },
                confirmButton = {},
                shape = RoundedCornerShape(32.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteContainer(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                    else -> Color.Transparent
                }, label = ""
            )
            val scale by animateFloatAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1.2f else 0.8f, label = ""
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
                    tint = Color.White
                )
            }
        },
        enableDismissFromStartToEnd = false,
        content = { content() }
    )
}

@Composable
fun VaultSearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        placeholder = { Text("Search encrypted logs...") },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    )
}

@Composable
fun CategoryStrip(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            val isSelected = selectedCategory == category
            Surface(
                modifier = Modifier.bouncyClick { onCategorySelect(category) },
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Text(
                    text = category.uppercase(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationVaultCard(
    notification: NotificationEntry, 
    onDelete: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    val timeString = remember(notification.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(notification.timestamp))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .combinedClickable(
                onClick = { isExpanded = !isExpanded },
                onLongClick = { onLongClick() }
            ),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            1.dp, 
            if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) 
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    val iconPainter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(context)
                            .data(try { context.packageManager.getApplicationIcon(notification.packageName) } catch(e: Exception) { null })
                            .crossfade(true)
                            .build()
                    )
                    Image(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            notification.appName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            timeString,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    
                    Text(
                        notification.title ?: "NO TITLE",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            
            Text(
                notification.text ?: "",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp
            )

            if (isExpanded) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyVaultState(isFiltering: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Icon(
                if (isFiltering) Icons.Rounded.SearchOff else Icons.Rounded.HistoryEdu,
                null,
                modifier = Modifier.padding(32.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            if (isFiltering) "NO RESULTS MATCHED" else "VAULT IS SECURELY EMPTY",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            letterSpacing = (-1).sp
        )
        Text(
            if (isFiltering) "Adjust your search parameters" else "Toolz hasn't intercepted any notifications yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

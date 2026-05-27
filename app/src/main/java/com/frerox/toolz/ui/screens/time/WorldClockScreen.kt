package com.frerox.toolz.ui.screens.time

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WorldClockScreen(
    viewModel: WorldClockViewModel,
    onBack: () -> Unit
) {
    val clocks by viewModel.clocks.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "WORLD CLOCK",
                subtitle = "Global Temporal Distribution",
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ExpressiveFabMenu(
                        items = listOf(
                            Triple("Add City", Icons.Rounded.Language, { showAddDialog = true }),
                            Triple("Settings", Icons.Rounded.Settings, { vibrationManager?.vibrateClick() })
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            ToolzHorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.padding(bottom = 16.dp),
                content = {
                    FilledIconButton(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            showAddDialog = true 
                        },
                        modifier = Modifier.size(48.dp),
                        shape = SmallExpressiveShape
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add City")
                    }
                },
                trailingContent = {
                    clickableItem(
                        onClick = { vibrationManager?.vibrateClick() },
                        icon = { Icon(Icons.Rounded.Sort, null) },
                        label = "SORT"
                    )
                }
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .padding(top = padding.calculateTopPadding())
        ) {
            if (clocks.isEmpty()) {
                EmptyClocksStateExpressive { showAddDialog = true }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 24.dp, bottom = 120.dp)),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 140.dp, top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(clocks.size, key = { i -> clocks[i].zoneId }) { index ->
                        val clock = clocks[index]
                        StaggeredEntrance(index = index) {
                            WorldClockCardExpressive(
                                clock = clock,
                                onDelete = { 
                                    vibrationManager?.vibrateClick()
                                    viewModel.removeZone(clock.zoneId) 
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            TimeZonePickerDialogExpressive(
                availableZones = viewModel.availableZones,
                onDismiss = { showAddDialog = false },
                onZoneSelected = { zoneId ->
                    vibrationManager?.vibrateSuccess()
                    viewModel.addZone(zoneId)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun EmptyClocksStateExpressive(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(160.dp),
            shape = SquircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Language,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(48.dp))
        Text(
            "TEMPORAL HUB EMPTY", 
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            "Add cities from around the globe to synchronize your workflow across multiple time zones.", 
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp, bottom = 48.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        ToolzExpressiveButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(72.dp), shape = BouncyShape) {
            Text("INITIALIZE TRACKER", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun WorldClockCardExpressive(
    clock: WorldClockItem,
    onDelete: () -> Unit
) {
    val vibrationManager = LocalVibrationManager.current
    val backgroundColor = if (clock.isNight) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    }

    val icon = if (clock.isNight) Icons.Rounded.NightlightRound else Icons.Rounded.WbSunny
    val iconColor = if (clock.isNight) Color(0xFF9FA8DA) else Color(0xFFFFA000)

    ExpressiveCard(
        onClick = { vibrationManager?.vibrateTick() },
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape,
        containerColor = backgroundColor,
        border = BorderStroke(
            1.5.dp, 
            if (clock.isLocal) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) 
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = iconColor.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = clock.cityName.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = (-0.5).sp
                    )
                }
                
                Spacer(Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = BouncyShape
                    ) {
                        Text(
                            text = clock.offset,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = clock.date.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
                
                if (clock.isLocal) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = SmallExpressiveShape
                    ) {
                        Text(
                            "CURRENT LOCATION",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = clock.currentTime,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 44.sp,
                        letterSpacing = (-2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (!clock.isLocal) {
                    Spacer(Modifier.height(16.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp).clip(BouncyShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeZonePickerDialogExpressive(
    availableZones: List<String>,
    onDismiss: () -> Unit,
    onZoneSelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredZones = availableZones.filter { it.contains(searchQuery, ignoreCase = true) }
    val performanceMode = LocalPerformanceMode.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = SquircleShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .padding(horizontal = 24.dp)
        ) {
            Text(
                "GLOBAL DIRECTORY", 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search location ID...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                singleLine = true,
                shape = SquircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            LazyColumn(
                modifier = Modifier.weight(1f).then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 16.dp)),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredZones) { zoneId ->
                    val cityName = zoneId.substringAfter("/").replace("_", " ")
                    val region = zoneId.substringBefore("/", "")
                    
                    Surface(
                        onClick = { onZoneSelected(zoneId) },
                        shape = BouncyShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                    ) {
                        ListItem(
                            headlineContent = { 
                                Text(
                                    cityName,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.bodyLarge
                                ) 
                            },
                            supportingContent = { 
                                if (region.isNotEmpty()) {
                                    Text(region.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp) 
                                }
                            },
                            trailingContent = {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

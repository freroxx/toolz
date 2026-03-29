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
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldClockScreen(
    viewModel: WorldClockViewModel,
    onBack: () -> Unit
) {
    val clocks by viewModel.clocks.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val performanceMode = LocalPerformanceMode.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("WORLD CLOCK", fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.Language, contentDescription = "Add Clock", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("ADD CITY", fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .padding(padding)
        ) {
            if (clocks.isEmpty()) {
                EmptyClocksState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                        .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 24.dp, bottom = 24.dp)),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 100.dp, top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(clocks, key = { it.zoneId }) { clock ->
                        WorldClockItemRow(
                            clock = clock,
                            onDelete = { viewModel.removeZone(clock.zoneId) }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            TimeZonePickerDialog(
                availableZones = viewModel.availableZones,
                onDismiss = { showAddDialog = false },
                onZoneSelected = { zoneId ->
                    viewModel.addZone(zoneId)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun EmptyClocksState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(140.dp),
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Language,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "GLOBAL TIME TRACKER", 
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp
        )
        Text(
            "Add cities from around the globe to track their current time and local date relative to you.", 
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun WorldClockItemRow(
    clock: WorldClockItem,
    onDelete: () -> Unit
) {
    val backgroundColor = if (clock.isNight) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    }

    val icon = if (clock.isNight) Icons.Rounded.NightlightRound else Icons.Rounded.WbSunny
    val iconColor = if (clock.isNight) Color(0xFF9FA8DA) else Color(0xFFFFA000)

    Surface(
        modifier = Modifier.fillMaxWidth().bouncyClick { },
        shape = RoundedCornerShape(28.dp),
        color = backgroundColor,
        border = BorderStroke(
            1.dp, 
            if (clock.isLocal) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) 
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon, 
                        contentDescription = null, 
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = clock.cityName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = clock.offset,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = clock.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (clock.isLocal) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "YOUR LOCATION",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = clock.currentTime,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (!clock.isLocal) {
                    Spacer(Modifier.height(8.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(16.dp),
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
fun TimeZonePickerDialog(
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
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .padding(horizontal = 24.dp)
        ) {
            @Suppress("DEPRECATION")
            Text(
                "SELECT TIMEZONE", 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search city or region...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            LazyColumn(
                modifier = Modifier.weight(1f).then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 16.dp)),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredZones) { zoneId ->
                    val cityName = zoneId.substringAfter("/").replace("_", " ")
                    val region = zoneId.substringBefore("/", "")
                    
                    Surface(
                        onClick = { onZoneSelected(zoneId) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
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
                                    @Suppress("DEPRECATION")
                                    Text(region.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) 
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

package com.frerox.toolz.ui.screens.time

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldClockScreen(
    viewModel: WorldClockViewModel,
    onBack: () -> Unit
) {
    val clocks by viewModel.clocks.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("World Clock", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Clock")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(clocks) { clock ->
                ClockItem(
                    clock = clock,
                    onDelete = if (!clock.isLocal) { { viewModel.removeZone(clock.zoneId) } } else null
                )
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
fun ClockItem(clock: WorldClockItem, onDelete: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (clock.isLocal) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.large
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
                    Text(
                        text = clock.cityName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (clock.isLocal) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    if (clock.isLocal) {
                        Surface(
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "LOCAL",
                                modifier = Modifier.padding(horizontal = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                Text(
                    text = clock.date,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (clock.isLocal) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = clock.zoneId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = clock.currentTime,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = if (clock.isLocal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Timezone") },
        text = {
            Column(modifier = Modifier.height(400.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search city or region...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(filteredZones) { zoneId ->
                        ListItem(
                            headlineContent = { Text(zoneId.replace("_", " ")) },
                            modifier = Modifier.clickable { onZoneSelected(zoneId) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

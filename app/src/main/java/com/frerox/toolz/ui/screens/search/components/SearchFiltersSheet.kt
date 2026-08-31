/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFiltersSheet(onDismiss: () -> Unit, onApply: (String) -> Unit) {
    var selectedTime by remember { mutableStateOf("Any time") }
    var siteFilter by remember { mutableStateOf("") }
    var safeLevel by remember { mutableStateOf("Moderate") }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.FilterList, null, tint = MaterialTheme.colorScheme.primary)
                Text("Search Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text("Time range", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Any time","Past hour","Past 24h","Past week","Past month","Past year").forEach{ label ->
                    FilterChip(selected = selectedTime==label, onClick = { selectedTime = label }, label = { Text(label) }, leadingIcon = if(selectedTime==label){ { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) } } else null)
                }
            }
            OutlinedTextField(value = siteFilter, onValueChange = { siteFilter = it }, label = { Text("Site filter (e.g., wikipedia.org)") }, placeholder = { Text("site:example.com") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
            Text("SafeSearch", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Off","Moderate","Strict").forEach{ lvl ->
                    FilterChip(selected = safeLevel==lvl, onClick = { safeLevel = lvl }, label = { Text(lvl) })
                }
            }
            Button(onClick = {
                val q = buildString{
                    if(siteFilter.isNotBlank()) append(" site:${siteFilter.trim()}")
                    val t = when(selectedTime){ "Past hour"->"&tbs=qdr:h"; "Past 24h"->"&tbs=qdr:d"; "Past week"->"&tbs=qdr:w"; "Past month"->"&tbs=qdr:m"; "Past year"->"&tbs=qdr:y"; else->""}
                    append(t)
                }
                onApply(q); onDismiss()
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Apply filters") }
        }
    }
}
@Composable private fun FlowRow(modifier: Modifier = Modifier, horizontalArrangement: Arrangement.Horizontal = Arrangement.Start, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(modifier = modifier, horizontalArrangement = horizontalArrangement, verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

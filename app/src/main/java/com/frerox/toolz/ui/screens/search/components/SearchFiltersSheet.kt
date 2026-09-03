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
import androidx.compose.ui.res.stringResource
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
                Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_filters_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_filters_time_range), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Any time" to com.frerox.toolz.R.string.st_SearchScreen_ws_filter_anytime,
                    "Past hour" to com.frerox.toolz.R.string.st_SearchScreen_ws_filter_past_hour,
                    "Past 24h" to com.frerox.toolz.R.string.st_SearchScreen_ws_filter_past_24h,
                    "Past week" to com.frerox.toolz.R.string.st_SearchScreen_ws_filter_past_week,
                    "Past month" to com.frerox.toolz.R.string.st_SearchScreen_ws_filter_past_month,
                    "Past year" to com.frerox.toolz.R.string.st_SearchScreen_ws_filter_past_year,
                ).forEach{ (value, labelRes) ->
                    val label = stringResource(labelRes)
                    FilterChip(selected = selectedTime==value, onClick = { selectedTime = value }, label = { Text(label) }, leadingIcon = if(selectedTime==value){ { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) } } else null)
                }
            }
            OutlinedTextField(value = siteFilter, onValueChange = { siteFilter = it }, label = { Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_filters_site_label)) }, placeholder = { Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_filters_site_hint)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
            Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_filters_safesearch), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Off" to com.frerox.toolz.R.string.st_SearchScreen_ws_filter_off,
                    "Moderate" to com.frerox.toolz.R.string.st_SearchScreen_ws_filter_moderate,
                    "Strict" to com.frerox.toolz.R.string.st_SearchScreen_ws_filter_strict,
                ).forEach{ (value, labelRes) ->
                    val lvl = stringResource(labelRes)
                    FilterChip(selected = safeLevel==value, onClick = { safeLevel = value }, label = { Text(lvl) })
                }
            }
            Button(onClick = {
                val q = buildString{
                    if(siteFilter.isNotBlank()) append(" site:${siteFilter.trim()}")
                    val t = when(selectedTime){ "Past hour"->"&tbs=qdr:h"; "Past 24h"->"&tbs=qdr:d"; "Past week"->"&tbs=qdr:w"; "Past month"->"&tbs=qdr:m"; "Past year"->"&tbs=qdr:y"; else->""}
                    append(t)
                }
                onApply(q); onDismiss()
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_filters_apply)) }
        }
    }
}
@Composable private fun FlowRow(modifier: Modifier = Modifier, horizontalArrangement: Arrangement.Horizontal = Arrangement.Start, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(modifier = modifier, horizontalArrangement = horizontalArrangement, verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

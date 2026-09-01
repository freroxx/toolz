package com.frerox.toolz.ui.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchOptionsSheet(
    onDismiss: () -> Unit,
    currentEngine: String,
    engineHealth: Map<String, com.frerox.toolz.data.search.WebSearchRepository.EngineHealth> = emptyMap(),
    onEngineSelect: (String) -> Unit,
    adBlockEnabled: Boolean,
    onAdBlockToggle: (Boolean) -> Unit,
    currentDns: String,
    onDnsClick: () -> Unit,
    safeSearch: Boolean,
    onSafeSearchToggle: (Boolean) -> Unit,
    isIncognito: Boolean,
    onIncognitoToggle: (Boolean) -> Unit,
    autofillEnabled: Boolean,
    onAutofillToggle: (Boolean) -> Unit,
    showGreetingCard: Boolean,
    onGreetingCardToggle: (Boolean) -> Unit,
    onPresetSelect: (String) -> Unit = {},
    onCustomizeAdBlock: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Search options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Browsing, privacy & search in one place", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            Text("SEARCH ENGINE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val engines = listOf("META" to "Meta (Yahoo • Qwant • Marginalia)", "YAHOO" to "Yahoo", "QWANT" to "Qwant", "MARGINALIA" to "Marginalia", "BING" to "Bing")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                engines.forEach { (id, label) ->
                    val isSelected = currentEngine.equals(id, ignoreCase = true)
                    val health = engineHealth[id]
                    Surface(
                        onClick = { onEngineSelect(id) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (health != null) {
                                val dotColor = when (health) {
                                    com.frerox.toolz.data.search.WebSearchRepository.EngineHealth.OK -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                    com.frerox.toolz.data.search.WebSearchRepository.EngineHealth.COOLDOWN -> androidx.compose.ui.graphics.Color(0xFFFFC107)
                                    com.frerox.toolz.data.search.WebSearchRepository.EngineHealth.FAILING -> androidx.compose.ui.graphics.Color(0xFFF44336)
                                    else -> MaterialTheme.colorScheme.outline
                                }
                                Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                if (isSelected) Text("Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (isSelected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(8.dp))

            Text("SAFETY & PRIVACY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("LOW" to "Low", "BASIC" to "Balanced", "MAX" to "Max").forEach { (k, l) ->
                    FilledTonalButton(
                        onClick = { onPresetSelect(k) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text(l, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Spacer(Modifier.height(8.dp))

            SettingsToggleRow(
                title = "Show Greeting & Stats",
                subtitle = "Display greeting card on home",
                checked = showGreetingCard,
                onCheckedChange = onGreetingCardToggle,
                leadingIcon = { Icon(Icons.Rounded.WavingHand, null, modifier = Modifier.size(20.dp)) }
            )
            SettingsToggleRow(
                title = "Safe search",
                subtitle = "Filter explicit results",
                checked = safeSearch,
                onCheckedChange = onSafeSearchToggle,
                leadingIcon = { Icon(Icons.Rounded.FamilyRestroom, null, modifier = Modifier.size(20.dp)) }
            )
            SettingsToggleRow(
                title = stringResource(R.string.st_SearchScreen_a1b2),
                subtitle = stringResource(R.string.st_SearchScreen_b3d4),
                checked = adBlockEnabled,
                onCheckedChange = onAdBlockToggle,
                leadingIcon = { Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(20.dp)) }
            )
            if (adBlockEnabled) {
                TextButton(onClick = onCustomizeAdBlock, modifier = Modifier.padding(start = 54.dp)) {
                    Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Customize block lists", style = MaterialTheme.typography.labelLarge)
                }
            }
            SettingsToggleRow(
                title = stringResource(R.string.st_SearchScreen_i5m6),
                subtitle = stringResource(R.string.st_SearchScreen_d7s8),
                checked = isIncognito,
                onCheckedChange = onIncognitoToggle,
                leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null, modifier = Modifier.size(20.dp)) }
            )
            SettingsToggleRow(
                title = stringResource(R.string.st_SearchScreen_p9a0),
                subtitle = stringResource(R.string.st_SearchScreen_f1c2),
                checked = autofillEnabled,
                onCheckedChange = onAutofillToggle,
                leadingIcon = { Icon(Icons.Rounded.Key, null, modifier = Modifier.size(20.dp)) }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = onDnsClick,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Dns, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DNS provider", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(currentDns.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

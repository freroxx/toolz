/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * One generic sandbox configurator used by all 18 tools. Controls come from
 * DevToolId.fieldSpecs and write through the ViewModel; no tool-specific
 * editors, no direct DataStore access. Slider writes commit on release and
 * text writes debounce briefly, so dragging does not hammer storage.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.SpoofParams
import com.frerox.toolz.ui.screens.developer.SpoofFieldSpec
import com.frerox.toolz.ui.screens.developer.fieldSpecs
import com.frerox.toolz.ui.screens.developer.icon
import com.frerox.toolz.ui.screens.developer.openRoute
import com.frerox.toolz.ui.screens.developer.spoofFixtureSummary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpoofConfiguratorSheet(
    tool: DevToolId,
    params: SpoofParams,
    active: Boolean,
    onUpdate: ((SpoofParams) -> SpoofParams) -> Unit,
    onToggle: (Boolean) -> Unit,
    onOpenRoute: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val specs = tool.fieldSpecs
    val route = tool.openRoute

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DevLeadIcon(
                            icon = tool.icon,
                            containerColor = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (active) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = tool.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = spoofFixtureSummary(tool, params),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DevPill(
                            text = if (active) "Active" else "Off",
                            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    DevToggleRow(
                        icon = Icons.Rounded.Science,
                        title = "Sandbox active",
                        supporting = "Fixture data stays in this app; real data and hardware are untouched.",
                        checked = active,
                        onCheckedChange = onToggle,
                    )
                }

                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }

                if (specs.isEmpty()) {
                    item {
                        DevInlineNotice(
                            icon = Icons.Rounded.Science,
                            text = "This sandbox uses a fixed local fixture. There are no values to edit.",
                        )
                    }
                } else {
                    items(specs, key = { it.label }) { spec ->
                        when (spec) {
                            is SpoofFieldSpec.FixtureText -> FixtureTextField(
                                spec = spec,
                                params = params,
                                onCommit = { value ->
                                    onUpdate { spec.set(it, value.take(spec.maxLength)) }
                                },
                            )
                            is SpoofFieldSpec.FixtureSwitch -> FixtureSwitchRow(
                                spec = spec,
                                params = params,
                                onCheckedChange = { value ->
                                    onUpdate { spec.set(it, value) }
                                },
                            )
                            is SpoofFieldSpec.FixtureSlider -> DevSlider(
                                label = spec.label,
                                value = spec.get(params),
                                range = spec.min..spec.max,
                                formatValue = spec.format,
                                onValueChange = { value ->
                                    onUpdate { spec.set(it, value) }
                                },
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Values are local fixtures. They never enter the database, network or system settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Done")
                }
                Button(
                    onClick = {
                        onOpenRoute(route)
                        onDismiss()
                    },
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowOutward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Open tool",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FixtureTextField(
    spec: SpoofFieldSpec.FixtureText,
    params: SpoofParams,
    onCommit: (String) -> Unit,
) {
    val current = spec.get(params)
    var text by remember(spec.label) { mutableStateOf(current) }
    var committed by remember(spec.label) { mutableStateOf(current) }
    var dirty by remember(spec.label) { mutableStateOf(false) }

    // Accept DataStore echoes only while the field is clean. While the user is
    // typing, an older echo must not reset or reorder the current edit.
    LaunchedEffect(current, dirty) {
        when {
            dirty && current == text -> {
                committed = current
                dirty = false
            }
            !dirty && current != text -> {
                text = current
                committed = current
            }
        }
    }

    LaunchedEffect(text) {
        if (text == committed) return@LaunchedEffect
        delay(350)
        if (text != committed) {
            committed = text
            onCommit(text)
        }
    }

    val latestText by rememberUpdatedState(text)
    val latestCommitted by rememberUpdatedState(committed)
    val focusManager = LocalFocusManager.current
    DisposableEffect(Unit) {
        onDispose {
            if (latestText != latestCommitted) onCommit(latestText)
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            val next = value.take(spec.maxLength)
            text = next
            dirty = next != committed
        },
        label = { Text(spec.label) },
        supportingText = {
            Text("${text.length}/${spec.maxLength}")
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                if (text != committed) {
                    committed = text
                    onCommit(text)
                }
                focusManager.clearFocus()
            },
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun FixtureSwitchRow(
    spec: SpoofFieldSpec.FixtureSwitch,
    params: SpoofParams,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = spec.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (spec.subtitle.isNotBlank()) {
                Text(
                    text = spec.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = spec.get(params),
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = spec.label },
        )
    }
}

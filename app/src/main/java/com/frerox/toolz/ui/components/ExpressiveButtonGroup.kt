package com.frerox.toolz.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.theme.ToolzTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Expressive Button Group with overflow support.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzExpressiveButtonGroup(
    modifier: Modifier = Modifier,
    content: ButtonGroupScope.() -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    ButtonGroup(
        modifier = modifier,
        overflowIndicator = { menuState ->
            FilledIconButton(
                onClick = {
                    haptic.tick()
                    menuState.show() 
                },
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More",
                )
            }
        },
        content = content
    )
}

/**
 * Connected Button Group for segmented controls with expressive shapes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzConnectedButtonGroup(
    selectedIndex: Int,
    options: List<String>,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    unCheckedIcons: List<ImageVector> = emptyList(),
    checkedIcons: List<ImageVector> = emptyList(),
) {
    val haptic = rememberToolzHapticFeedback()
    
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        val modifiers = listOf(
            Modifier.weight(1f),
            Modifier.weight(1.5f),
            Modifier.weight(1f),
        )

        options.forEachIndexed { index, label ->
            val interactionSource = remember { MutableInteractionSource() }

            ToggleButton(
                checked = selectedIndex == index,
                onCheckedChange = {
                    if (it) {
                        haptic.tick()
                        onOptionSelected(index)
                    }
                },
                modifier = modifiers.getOrElse(index) { Modifier.weight(1f) }
                    .expressivePressScale(interactionSource, enabled)
                    .semantics { role = Role.RadioButton },
                enabled = enabled,
                interactionSource = interactionSource,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
            ) {
                val hasIcons = unCheckedIcons.size > index && checkedIcons.size > index
                if (hasIcons) {
                    Icon(
                        if (selectedIndex == index) checkedIcons[index] else unCheckedIcons[index],
                        contentDescription = label,
                    )
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                }
                Text(label)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExpressiveButtonGroupPreview() {
    ToolzTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ToolzExpressiveButtonGroup {
                clickableItem(onClick = {}, label = "Option 1")
                clickableItem(onClick = {}, label = "Option 2")
                clickableItem(onClick = {}, label = "Option 3")
            }

            var selectedIndex by remember { mutableIntStateOf(0) }
            ToolzConnectedButtonGroup(
                selectedIndex = selectedIndex,
                options = listOf("Search", "Settings", "Home"),
                unCheckedIcons = listOf(
                    Icons.Outlined.Search,
                    Icons.Outlined.Settings,
                    Icons.Outlined.Home
                ),
                checkedIcons = listOf(
                    Icons.Filled.Search,
                    Icons.Filled.Settings,
                    Icons.Filled.Home
                ),
                onOptionSelected = { selectedIndex = it }
            )
        }
    }
}

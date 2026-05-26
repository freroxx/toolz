package com.frerox.toolz.ui.components

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
import com.frerox.toolz.ui.theme.LocalVibrationManager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.theme.ToolzTheme

/**
 * Expressive Button Group with overflow support.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzExpressiveButtonGroup(
    modifier: Modifier = Modifier,
    content: ButtonGroupScope.() -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    ButtonGroup(
        modifier = modifier,
        overflowIndicator = { menuState ->
            FilledIconButton(
                onClick = {
                    vibrationManager?.vibrateTick()
                    // Using show/dismiss directly as isExpanded check might have API version issues
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
    enabled: Boolean = true
) {
    val vibrationManager = LocalVibrationManager.current
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, label ->
            ToggleButton(
                checked = selectedIndex == index,
                onCheckedChange = {
                    if (it) {
                        vibrationManager?.vibrateTick()
                        onOptionSelected(index)
                    }
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                enabled = enabled,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selectedIndex == index) FontWeight.Black else FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true)
@Composable
private fun ExpressiveButtonGroupPreview() {
    ToolzTheme {
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
                options = listOf("Work", "Home", "Other"),
                onOptionSelected = { selectedIndex = it }
            )
        }
    }
}

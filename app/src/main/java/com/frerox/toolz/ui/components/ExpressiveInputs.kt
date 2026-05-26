package com.frerox.toolz.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme

/**
 * Custom switch with expressive animations and physics.
 */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val vibrationManager = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current
    
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 0.dp,
        animationSpec = if (performanceMode) tween(100) else spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "switch_thumb"
    )
    
    val containerColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "switch_container"
    )
    
    val thumbScale by animateFloatAsState(
        targetValue = if (checked) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "switch_thumb_scale"
    )

    Surface(
        checked = checked,
        onCheckedChange = {
            vibrationManager?.vibrateTick()
            onCheckedChange(it)
        },
        enabled = enabled,
        modifier = modifier
            .size(52.dp, 32.dp)
            .clip(CircleShape),
        color = containerColor,
        shape = CircleShape,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
                    .background(
                        if (checked) MaterialTheme.colorScheme.onPrimary 
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        CircleShape
                    )
            )
        }
    }
}

/**
 * Expressive slider with physics-based thumb and active track.
 */
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    isPlaying: Boolean = false,
) {
    val vibrationManager = LocalVibrationManager.current
    
    Slider(
        value = value,
        onValueChange = {
            if (value != it) {
                vibrationManager?.vibrateTick()
            }
            onValueChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        thumb = {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 1.4f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
                label = "slider_thumb_scale"
            )
            
            Surface(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                shape = CircleShape,
                color = colors.thumbColor,
                shadowElevation = 4.dp,
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.5f))
            ) {}
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    onSearch: (String) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val currentOnSearch by rememberUpdatedState(onSearch)

    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = {
            expanded = false
            currentOnSearch(it)
        },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
        enabled = enabled,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}

@Preview(showBackground = true)
@Composable
private fun ExpressiveInputsPreview() {
    ToolzTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            var checked by remember { mutableStateOf(true) }
            ExpressiveSwitch(checked = checked, onCheckedChange = { checked = it })
            
            var sliderValue by remember { mutableFloatStateOf(0.5f) }
            ExpressiveSlider(value = sliderValue, onValueChange = { sliderValue = it })
            
            ExpressiveSearchField(
                query = "",
                onQueryChange = {},
                placeholder = { Text("Search...") }
            )
        }
    }
}

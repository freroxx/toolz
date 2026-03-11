package com.frerox.toolz.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.settings.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    settingsRepository: SettingsRepository,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val color1 by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        targetValue = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
        label = "c1"
    )
    val color2 by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
        targetValue = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
        label = "c2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(color1, color2, MaterialTheme.colorScheme.background)))
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "steps"
        ) { currentStep ->
            when (currentStep) {
                1 -> WelcomeStep(onNext = { step = 2 })
                2 -> NameStep(
                    name = name,
                    onNameChange = { name = it },
                    onComplete = {
                        scope.launch {
                            settingsRepository.setUserName(name)
                            settingsRepository.setOnboardingCompleted(true)
                            onFinish()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.WavingHand,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Spacer(Modifier.height(48.dp))
        
        Text(
            text = "Welcome to Toolz",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            letterSpacing = (-1).sp
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = "Your all-in-one precision toolkit for science, productivity, and everyday utility.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(Modifier.height(64.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        Text(
            text = "Made by frerox",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(0.6f)
        )
    }
}

@Composable
fun NameStep(
    name: String,
    onNameChange: (String) -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Hi! What's your name?",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = "We'll use this to personalize your dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(48.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text("Enter your name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (name.isNotBlank()) onComplete() }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = onComplete,
            enabled = name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Continue", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Rounded.CheckCircle, null)
            }
        }
    }
}

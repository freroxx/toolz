/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A modifier that detects a very long press (10 seconds) specifically for the 
 * screenshot bypass mechanism requested by testers/admins.
 */
fun Modifier.screenshotBypassGesture(
    onTrigger: () -> Unit
): Modifier = pointerInput(Unit) {
    coroutineScope {
        awaitPointerEventScope {
            while (true) {
                awaitFirstDown()
                var isHeld = true
                val timerJob = launch {
                    delay(10_000) // 10 seconds requirement
                    if (isHeld) {
                        onTrigger()
                    }
                }
                waitForUpOrCancellation()
                isHeld = false
                timerJob.cancel()
            }
        }
    }
}

@Composable
fun WhisperScreenshotBypassDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text("Admin Authentication", fontWeight = FontWeight.Bold) 
        },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                placeholder = { Text("SSForWhisperTester") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty()
            ) {
                Text("Verify")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

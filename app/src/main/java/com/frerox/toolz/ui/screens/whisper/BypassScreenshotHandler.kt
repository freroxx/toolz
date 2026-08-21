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
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.frerox.toolz.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A modifier that detects a very long press (10 seconds) specifically for the 
 * screenshot bypass mechanism requested by testers/admins.
 */
fun Modifier.screenshotBypassGesture(
    onTrigger: () -> Unit
): Modifier = composed {
    val currentOnTrigger by rememberUpdatedState(onTrigger)
    pointerInput(Unit) {
        coroutineScope {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown()
                    var isHeld = true
                    val timerJob = launch {
                        delay(10_000) // 10 seconds requirement
                        if (isHeld) {
                            currentOnTrigger()
                        }
                    }
                    waitForUpOrCancellation()
                    isHeld = false
                    timerJob.cancel()
                }
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
            Text(stringResource(R.string.st_Whisper_Bypass_Title), fontWeight = FontWeight.Bold) 
        },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.st_Whisper_Bypass_PasswordLabel)) },
                placeholder = { Text(stringResource(R.string.st_Whisper_Bypass_PasswordLabel)) },
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
                Text(stringResource(R.string.st_Whisper_Verify))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.st_Whisper_Cancel))
            }
        }
    )
}

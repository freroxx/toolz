/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.frerox.toolz.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * V2-FIX: the 10-second hold requirement extracted from a magic literal so testers/admins
 * tuning the gesture (and tests) touch exactly one named place. Long enough to be
 * deliberate (no accidental triggers), short enough to stay usable.
 */
private const val BYPASS_GESTURE_HOLD_MS = 10_000L

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
                        delay(BYPASS_GESTURE_HOLD_MS)
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
    onConfirm: (String) -> Unit,
    // V2-FIX (verify in-flight guard): while the password verdict is pending the caller
    // flips this on; Confirm locks and shows progress so taps can't double-submit.
    isVerifying: Boolean = false,
) {
    var password by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        title = { 
            Text(stringResource(R.string.st_Whisper_Bypass_Title), fontWeight = FontWeight.Bold) 
        },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                enabled = !isVerifying,
                label = { Text(stringResource(R.string.st_Whisper_Bypass_PasswordLabel)) },
                placeholder = { Text(stringResource(R.string.st_Whisper_Bypass_PasswordLabel)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                // V2-FIX (secret-entry hygiene): password keyboard type — no suggestion
                // bar, and the IME never learns/autocompletes the bypass secret. No
                // autofill hints are set anywhere on this field, so none need clearing.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty() && !isVerifying
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.st_Whisper_Verify))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isVerifying) {
                Text(stringResource(R.string.st_Whisper_Cancel))
            }
        }
    )
}

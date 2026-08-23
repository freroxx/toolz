/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class WhisperToastType {
    ERROR,
    SUCCESS,
    INFO
}

data class WhisperToastData(
    val message: String,
    val type: WhisperToastType = WhisperToastType.ERROR,
    // V2-FIX L-?: dead `= System.currentTimeMillis()` default removed — show() always
    // supplies the monotonic id, so the default only invited accidental duplicate ids.
    val id: Long
)

@Stable
/**
 * Holds the toast queue for one Whisper screen.
 *
 * V2-FIX (main-thread confinement): this state is NOT thread-safe. [show], [dismiss] and
 * the internal queue/counter MUST be called from the Android main thread (Compose UI);
 * [currentToast] is a StateFlow so composition-side reads are always safe.
 */
class WhisperToastState {
    // Monotonic id so two toasts fired within the same millisecond still trigger
    // distinct (re)composition of the toast host animation.
    // P2-10 FIX: Queue toasts so rapid errors aren't lost; previously show() overwrote.
    private val idCounter = java.util.concurrent.atomic.AtomicLong(0L)
    private val queue = ArrayDeque<WhisperToastData>()
    private val _currentToast = MutableStateFlow<WhisperToastData?>(null)
    val currentToast = _currentToast.asStateFlow()

    fun show(message: String, type: WhisperToastType = WhisperToastType.ERROR) {
        if (message.isBlank()) return
        val data = WhisperToastData(message, type, id = idCounter.incrementAndGet())
        if (_currentToast.value == null) {
            _currentToast.value = data
        } else {
            // Cap queue at 5 to avoid spam.
            if (queue.size < 5) queue.addLast(data)
            else android.util.Log.w("WhisperToast", "Toast queue full (cap 5) — dropped message: ${data.message.take(80)}")
        }
    }

    fun dismiss() {
        if (queue.isNotEmpty()) {
            _currentToast.value = queue.removeFirst()
        } else {
            _currentToast.value = null
        }
    }
}

@Composable
fun rememberWhisperToastState(): WhisperToastState {
    return remember { WhisperToastState() }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhisperToastHost(
    hostState: WhisperToastState,
    modifier: Modifier = Modifier
) {
    // V2-FIX L-?: lifecycle-aware collection — stops observing while the UI is stopped
    // instead of ticking the auto-dismiss timer against a stopped composition.
    val currentToast by hostState.currentToast.collectAsStateWithLifecycle()
    // The AnimatedVisibility content stays composed for the whole exit transition, but
    // currentToast is already null by then; keep the last non-null toast so the exit
    // animation renders the actual message instead of an empty container.
    var lastToast by remember { mutableStateOf<WhisperToastData?>(null) }

    LaunchedEffect(currentToast) {
        currentToast?.let {
            lastToast = it
            delay(3500)
            if (hostState.currentToast.value?.id == it.id) {
                hostState.dismiss()
            }
        }
    }

    AnimatedVisibility(
        visible = currentToast != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
        ) + fadeOut(),
        modifier = modifier
    ) {
        lastToast?.let { toast ->
            WhisperToastItem(
                data = toast,
                onDismiss = { hostState.dismiss() }
            )
        }
    }
}

@Composable
private fun WhisperToastItem(
    data: WhisperToastData,
    onDismiss: () -> Unit
) {
    val (containerColor, contentColor, icon) = when (data.type) {
        WhisperToastType.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Rounded.ErrorOutline
        )
        WhisperToastType.SUCCESS -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Rounded.CheckCircle
        )
        WhisperToastType.INFO -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Rounded.Info
        )
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = data.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    // V2-FIX L-?: hardcoded "Dismiss" replaced with an existing generic
                    // Whisper resource — no new string id needed.
                    contentDescription = stringResource(R.string.st_Whisper_Close),
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

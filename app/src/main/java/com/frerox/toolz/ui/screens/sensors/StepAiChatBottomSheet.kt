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

package com.frerox.toolz.ui.screens.sensors

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.*

private val QUICK_PROMPTS = listOf(
    "Give me a tip! 💡",
    "Analyze my week 📊",
    "Motivate me! 🔥",
    "Am I on track? 🎯",
    "What should I eat? 🥗"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StepAiChatBottomSheet(
    onDismissRequest: () -> Unit,
    state: StepState,
    onSendMessage: (String) -> Unit,
    onContinueInAssistant: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    var messageText by remember { mutableStateOf("") }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(state.aiChatHistory.size) {
        if (state.aiChatHistory.isNotEmpty()) {
            listState.animateScrollToItem(state.aiChatHistory.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent, // Using glassy container inside
        dragHandle = null,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding(),
            color = AiDesign.surfaceColor().copy(alpha = 0.98f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Header ────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.AutoAwesome, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "AI FITNESS COACH",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            AnimatedContent(
                                targetState = state.isAiLoading,
                                transitionSpec = {
                                    fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                                },
                                label = "TypingStatus"
                            ) { loading ->
                                if (loading) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ExpressiveTypingDots(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.height(16.dp)
                                        )
                                        Text(
                                            "Coach is typing…",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "${state.aiMood} • ${state.aiStyle}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { onSendMessage("Give me a daily tip!") }) {
                            Icon(
                                Icons.Rounded.Refresh, "Quick tip",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        ToolzOutlinedExpressiveButton(
                            onClick = onContinueInAssistant,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.OpenInNew, null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("FULL CHAT", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // ── Message list ──────────────────────────────────────────
                Box(modifier = Modifier.weight(1f)) {
                    if (state.aiChatHistory.isEmpty() && !state.isAiLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                modifier = Modifier.size(100.dp),
                                shape = SquircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome, null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .alpha(0.5f),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                "Your AI Coach is ready.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "Ask anything — tips, motivation, or analysis.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                items(QUICK_PROMPTS) { prompt ->
                                    SuggestionChip(
                                        onClick = { onSendMessage(prompt) },
                                        label = {
                                            Text(
                                                prompt,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        },
                                        shape = CircleShape,
                                        border = SuggestionChipDefaults.suggestionChipBorder(
                                            enabled = true,
                                            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(state.aiChatHistory.size, key = { state.aiChatHistory[it].id }) { index ->
                                val msg = state.aiChatHistory[index]
                                SharedChatBubble(
                                    message = msg,
                                    isCoach = true,
                                    onLinkClick = { /* Handle if needed */ },
                                    onLongPress = { /* Handle copy if needed */ }
                                )
                            }

                            if (state.isAiLoading) {
                                item {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 14.dp, top = 8.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(34.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Rounded.AutoAwesome, null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(
                                                topStart = 4.dp,
                                                topEnd = 22.dp,
                                                bottomStart = 22.dp,
                                                bottomEnd = 22.dp
                                            ),
                                            color = AiDesign.glassColor(),
                                            border = BorderStroke(1.dp, AiDesign.glassBorder())
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(
                                                    horizontal = 16.dp,
                                                    vertical = 12.dp
                                                )
                                            ) {
                                                ExpressiveTypingDots(
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            item { Spacer(Modifier.height(12.dp)) }
                        }
                    }
                }

                // ── Input bar ─────────────────────────────────────────────
                SharedAiInputBar(
                    inputText = messageText,
                    isLoading = state.isAiLoading,
                    onInputChange = { messageText = it },
                    onSend = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    },
                    onCancel = { /* Handle if needed */ },
                    placeholder = "Message your coach…",
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

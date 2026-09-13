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

package com.frerox.toolz.ui.screens.ai

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.R
import com.frerox.toolz.data.ai.AiMessage
import com.frerox.toolz.ui.components.AiDesign
import com.frerox.toolz.ui.components.MarkdownContent
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.components.stripMarkdown
import kotlinx.coroutines.launch

enum class SelectTextMode {
    FORMATTED,
    RAW,
    CLEAN
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiSelectTextScreen(
    message: AiMessage,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptics = rememberToolzHapticFeedback()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var currentMode by remember { mutableStateOf(SelectTextMode.FORMATTED) }

    val rawText = message.text
    val cleanText = remember(rawText) { stripMarkdown(rawText) }

    val currentTextToCopy = when (currentMode) {
        SelectTextMode.FORMATTED -> cleanText
        SelectTextMode.RAW -> rawText
        SelectTextMode.CLEAN -> cleanText
    }

    val charCount = currentTextToCopy.length
    val wordCount = remember(currentTextToCopy) {
        currentTextToCopy.split(Regex("\\s+")).count { it.isNotBlank() }
    }

    Scaffold(
        containerColor = AiDesign.surfaceColor(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.st_AiSelectText_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            stringResource(R.string.st_AiSelectText_stats, charCount, wordCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = AiDesign.textColor(0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.st_OnboardingScreen_4f2d),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(currentTextToCopy))
                            haptics.click()
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.st_AiSelectText_copied),
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.st_AiSelectText_copy_all),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            haptics.click()
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, currentTextToCopy)
                                    },
                                    context.getString(R.string.st_AiAssistantScreen_5d6e)
                                )
                            )
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.st_AiAssistantScreen_3c4d),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AiDesign.surfaceColor()
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                color = AiDesign.cardColor(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, AiDesign.glassBorder())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(currentTextToCopy))
                            haptics.click()
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.st_AiSelectText_copied),
                                    duration = SnackbarDuration.Short
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.st_AiSelectText_copy_all))
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.click()
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, currentTextToCopy)
                                    },
                                    context.getString(R.string.st_AiAssistantScreen_5d6e)
                                )
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Segmented mode selector
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = currentMode == SelectTextMode.FORMATTED,
                    onClick = {
                        haptics.click()
                        currentMode = SelectTextMode.FORMATTED
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    icon = {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                ) {
                    Text(stringResource(R.string.st_AiSelectText_formatted), maxLines = 1)
                }

                SegmentedButton(
                    selected = currentMode == SelectTextMode.RAW,
                    onClick = {
                        haptics.click()
                        currentMode = SelectTextMode.RAW
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    icon = {
                        Icon(
                            Icons.Rounded.Code,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                ) {
                    Text(stringResource(R.string.st_AiSelectText_raw), maxLines = 1)
                }

                SegmentedButton(
                    selected = currentMode == SelectTextMode.CLEAN,
                    onClick = {
                        haptics.click()
                        currentMode = SelectTextMode.CLEAN
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    icon = {
                        Icon(
                            Icons.Rounded.FormatClear,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                ) {
                    Text(stringResource(R.string.st_AiSelectText_clean), maxLines = 1)
                }
            }

            // Tip Banner
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.st_AiSelectText_tip),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Main Text Container wrapped in SelectionContainer
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(22.dp),
                color = AiDesign.glassColor(),
                border = BorderStroke(1.dp, AiDesign.glassBorder())
            ) {
                SelectionContainer(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        AnimatedContent(
                            targetState = currentMode,
                            transitionSpec = {
                                fadeIn(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) togetherWith
                                        fadeOut(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium))
                            },
                            label = "SelectTextContent"
                        ) { mode ->
                            when (mode) {
                                SelectTextMode.FORMATTED -> {
                                    MarkdownContent(
                                        markdown = rawText,
                                        baseFontSize = 16.sp,
                                        textColor = AiDesign.textColor()
                                    )
                                }
                                SelectTextMode.RAW -> {
                                    Text(
                                        text = rawText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 15.sp,
                                            lineHeight = 22.sp
                                        ),
                                        color = AiDesign.textColor()
                                    )
                                }
                                SelectTextMode.CLEAN -> {
                                    Text(
                                        text = cleanText,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 16.sp,
                                            lineHeight = 26.sp
                                        ),
                                        color = AiDesign.textColor()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

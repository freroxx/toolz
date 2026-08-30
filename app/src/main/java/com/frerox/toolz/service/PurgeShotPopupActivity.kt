/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.service

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.frerox.toolz.MainActivity
import com.frerox.toolz.data.purgeshot.PurgeShotHandler
import com.frerox.toolz.data.purgeshot.PurgeShotPreset
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.ui.components.PurgeShotPopup
import com.frerox.toolz.ui.screens.purgeshot.PurgeShotViewModel
import com.frerox.toolz.ui.theme.ToolzTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Popup shown immediately after screenshot(s) are detected.
 * Supports both single and batched screenshots.
 */
@AndroidEntryPoint
class PurgeShotPopupActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val urisList: List<String> = intent.getStringArrayListExtra("uris")
            ?: listOfNotNull(intent.getStringExtra("uri"))
        val namesList: List<String> = intent.getStringArrayListExtra("displayNames")
            ?: listOfNotNull(intent.getStringExtra("displayName"))
        val pathsList: List<String> = intent.getStringArrayListExtra("paths")
            ?: listOfNotNull(intent.getStringExtra("path"))

        val uriList = urisList.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        val displayName = if (uriList.size > 1) "${uriList.size} Screenshots" else (namesList.firstOrNull() ?: "Screenshot")
        val sizeLabel = if (uriList.size <= 1) intent.getStringExtra("sizeLabel") else null

        setContent {
            ToolzTheme {
                val viewModel: PurgeShotViewModel = hiltViewModel()
                val presets by viewModel.activePresets.collectAsState(initial = PurgeShotPreset.defaults())
                val autoDuration by viewModel.autoDurationMs.collectAsState(initial = 15 * 60_000L)

                PurgeShotPopup(
                    screenshotUri = uriList.firstOrNull(),
                    displayName = displayName,
                    presets = presets,
                    autoDurationMillis = autoDuration,
                    fileSizeLabel = sizeLabel,
                    screenshotUris = uriList,
                    onSelectDuration = { preset ->
                        viewModel.enqueueMultiple(urisList, namesList, pathsList, preset.durationMillis, preset.label)
                        lifecycleScope.launch {
                            PurgeShotHandler.showScheduledNotification(
                                this@PurgeShotPopupActivity,
                                settingsRepository,
                                urisList.size,
                                preset.label
                            )
                        }
                        finish()
                    },
                    onDeleteNow = {
                        viewModel.deleteMultiple(urisList, pathsList)
                        finish()
                    },
                    onDismiss = { finish() },
                    onKeepForever = { finish() },
                    onOpenSettings = {
                        startActivity(
                            android.content.Intent(this@PurgeShotPopupActivity, MainActivity::class.java).apply {
                                putExtra("navigate_to", "purgeshot")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
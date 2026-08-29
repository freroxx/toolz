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
import com.frerox.toolz.MainActivity
import com.frerox.toolz.data.purgeshot.PurgeShotPreset
import com.frerox.toolz.ui.components.PurgeShotPopup
import com.frerox.toolz.ui.screens.purgeshot.PurgeShotViewModel
import com.frerox.toolz.ui.theme.ToolzTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Popup shown immediately after a screenshot is detected. Because the detection that triggers
 * this is nearly always running from a background context — [PurgeShotService]'s ContentObserver
 * callback or [PurgeShotObserverJobService]'s JobScheduler dispatch, neither of which has a
 * visible Activity on the back stack — this must be launched with FLAG_ACTIVITY_NEW_TASK, and on
 * a locked or off screen it also needs to explicitly ask to show over the lock screen and turn
 * the screen on. Without both of those the launch either throws (missing NEW_TASK from a
 * non-Activity context) or is silently dropped by Android 10+ background-activity-start limits,
 * which is almost certainly why the popup "sometimes never shows" for screenshots taken while
 * Toolz itself isn't the foreground app.
 */
@AndroidEntryPoint
class PurgeShotPopupActivity : ComponentActivity() {
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

        val uriStr = intent.getStringExtra("uri")
        val displayName = intent.getStringExtra("displayName") ?: "Screenshot"
        val path = intent.getStringExtra("path")
        val sizeLabel = intent.getStringExtra("sizeLabel")
        val uri = uriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }

        setContent {
            ToolzTheme {
                val viewModel: PurgeShotViewModel = hiltViewModel()
                val presets by viewModel.activePresets.collectAsState(initial = PurgeShotPreset.defaults())
                val autoDuration by viewModel.autoDurationMs.collectAsState(initial = 15 * 60_000L)

                PurgeShotPopup(
                    screenshotUri = uri,
                    displayName = displayName,
                    presets = presets,
                    autoDurationMillis = autoDuration,
                    fileSizeLabel = sizeLabel,
                    onSelectDuration = { preset ->
                        viewModel.enqueueForPopup(uriStr, displayName, path, preset.durationMillis, preset.label)
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
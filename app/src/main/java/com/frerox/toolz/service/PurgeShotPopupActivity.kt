/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.service

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.purgeshot.PurgeShotPreset
import com.frerox.toolz.ui.components.PurgeShotPopup
import com.frerox.toolz.ui.screens.purgeshot.PurgeShotViewModel
import com.frerox.toolz.ui.theme.ToolzTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class PurgeShotPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // Allow drawing over status bar; keep immersive feel
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val uriStr = intent.getStringExtra("uri")
        val displayName = intent.getStringExtra("displayName") ?: "Screenshot"
        val path = intent.getStringExtra("path")
        val sizeLabel = intent.getStringExtra("sizeLabel") // optional blur-aware metadata
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
                        startActivity(android.content.Intent(this@PurgeShotPopupActivity, com.frerox.toolz.MainActivity::class.java).apply {
                            putExtra("navigate_to", "purgeshot")
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        })
                        finish()
                    }
                )
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}

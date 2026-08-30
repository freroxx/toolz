/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.service

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.frerox.toolz.MainActivity
import com.frerox.toolz.data.purgeshot.PopupCandidate
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
 * Single-task popup activity that displays over the screen when screenshots are taken.
 * Reactively displays all accumulated screenshots in real-time if multiple screenshots
 * are taken while the popup is visible (solving the popup screenshotting loop).
 */
@AndroidEntryPoint
class PurgeShotPopupActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PurgeShotHandler.setPopupActive(true)

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

        // Seed initial candidate if active batch is currently empty
        intent.getStringExtra("uri")?.let { uriStr ->
            val uri = runCatching { Uri.parse(uriStr) }.getOrNull()
            if (uri != null) {
                val displayName = intent.getStringExtra("displayName") ?: "Screenshot"
                val path = intent.getStringExtra("path")
                val sizeLabel = intent.getStringExtra("sizeLabel")
                PurgeShotHandler.addCandidateDirectly(
                    PopupCandidate(uri, displayName, path, sizeLabel)
                )
            }
        }

        setContent {
            ToolzTheme {
                val viewModel: PurgeShotViewModel = hiltViewModel()
                val presets: List<PurgeShotPreset> by viewModel.activePresets.collectAsStateWithLifecycle(initialValue = PurgeShotPreset.defaults())
                val autoDuration: Long by viewModel.autoDurationMs.collectAsStateWithLifecycle(initialValue = 15 * 60_000L)
                val activeBatch: List<PopupCandidate> by PurgeShotHandler.activeBatchFlow.collectAsStateWithLifecycle()

                val uris: List<Uri> = activeBatch.map { it.uri }
                val displayName: String = if (activeBatch.size > 1) {
                    "${activeBatch.size} Screenshots"
                } else {
                    activeBatch.firstOrNull()?.displayName ?: (intent.getStringExtra("displayName") ?: "Screenshot")
                }
                val sizeLabel: String? = if (activeBatch.size <= 1) {
                    activeBatch.firstOrNull()?.sizeLabel ?: intent.getStringExtra("sizeLabel")
                } else null

                PurgeShotPopup(
                    screenshotUri = uris.firstOrNull(),
                    displayName = displayName,
                    presets = presets,
                    autoDurationMillis = autoDuration,
                    fileSizeLabel = sizeLabel,
                    screenshotUris = uris,
                    onSelectDuration = { preset ->
                        val currentItems: List<PopupCandidate> = if (activeBatch.isNotEmpty()) {
                            activeBatch
                        } else {
                            val u = intent.getStringExtra("uri")
                            if (u != null) {
                                listOf(
                                    PopupCandidate(
                                        Uri.parse(u),
                                        intent.getStringExtra("displayName") ?: "Screenshot",
                                        intent.getStringExtra("path"),
                                        sizeLabel
                                    )
                                )
                            } else emptyList()
                        }
                        val urisList: List<String> = currentItems.map { it.uri.toString() }
                        val namesList: List<String> = currentItems.map { it.displayName }
                        val pathsList: List<String?> = currentItems.map { it.filePath }

                        viewModel.enqueueMultiple(urisList, namesList, pathsList, preset.durationMillis, preset.label)
                        PurgeShotHandler.clearActiveBatch()
                        finish()
                    },
                    onDeleteNow = {
                        val currentItems: List<PopupCandidate> = if (activeBatch.isNotEmpty()) {
                            activeBatch
                        } else {
                            val u = intent.getStringExtra("uri")
                            if (u != null) {
                                listOf(
                                    PopupCandidate(
                                        Uri.parse(u),
                                        intent.getStringExtra("displayName") ?: "Screenshot",
                                        intent.getStringExtra("path"),
                                        sizeLabel
                                    )
                                )
                            } else emptyList()
                        }
                        val urisList: List<String> = currentItems.map { it.uri.toString() }
                        val pathsList: List<String?> = currentItems.map { it.filePath }
                        viewModel.deleteMultiple(urisList, pathsList)
                        PurgeShotHandler.clearActiveBatch()
                        finish()
                    },
                    onDismiss = {
                        val currentItems = activeBatch.ifEmpty {
                            listOfNotNull(intent.getStringExtra("uri")?.let { Uri.parse(it) })
                        }
                        PurgeShotHandler.markAllHandled(currentItems.map { it.toString() })
                        PurgeShotHandler.clearActiveBatch()
                        finish()
                    },
                    onKeepForever = {
                        val currentItems = activeBatch.ifEmpty {
                            listOfNotNull(intent.getStringExtra("uri")?.let { Uri.parse(it) })
                        }
                        PurgeShotHandler.markAllHandled(currentItems.map { it.toString() })
                        PurgeShotHandler.clearActiveBatch()
                        finish()
                    },
                    onOpenSettings = {
                        val currentItems = activeBatch.ifEmpty {
                            listOfNotNull(intent.getStringExtra("uri")?.let { Uri.parse(it) })
                        }
                        PurgeShotHandler.markAllHandled(currentItems.map { it.toString() })
                        startActivity(
                            android.content.Intent(this@PurgeShotPopupActivity, MainActivity::class.java).apply {
                                putExtra("navigate_to", "purgeshot")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                        )
                        PurgeShotHandler.clearActiveBatch()
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PurgeShotHandler.setPopupActive(true)
        intent.getStringExtra("uri")?.let { uriStr ->
            val uri = runCatching { Uri.parse(uriStr) }.getOrNull()
            if (uri != null) {
                val displayName = intent.getStringExtra("displayName") ?: "Screenshot"
                val path = intent.getStringExtra("path")
                val sizeLabel = intent.getStringExtra("sizeLabel")
                PurgeShotHandler.addCandidateDirectly(
                    PopupCandidate(uri, displayName, path, sizeLabel)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PurgeShotHandler.setPopupActive(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        PurgeShotHandler.setPopupActive(false)
        PurgeShotHandler.clearActiveBatch()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        PurgeShotHandler.clearActiveBatch()
        finish()
    }
}
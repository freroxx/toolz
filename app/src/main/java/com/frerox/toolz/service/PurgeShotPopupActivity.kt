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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
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
 *
 * The activity is intentionally reachable from both a direct background launch and the
 * high-priority prompt notification. Pending prompts are persisted in
 * [com.frerox.toolz.data.settings.SettingsRepository] before any launch is attempted, so
 * opening from the notification after process death still shows the right screenshots.
 */
@AndroidEntryPoint
class PurgeShotPopupActivity : ComponentActivity() {

    companion object {
        /** Action used by the prompt notification and the direct launch. */
        const val ACTION_SHOW_PROMPT = "com.frerox.toolz.PURGESHOT_SHOW_PROMPT"
    }

    @Inject lateinit var settingsRepository: SettingsRepository

    /** True once pending prompts have been read from disk (or after a failed read). */
    private val promptsLoaded = androidx.compose.runtime.mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PurgeShotHandler.setPopupActive(true)
        // The notification has done its job: the popup is now on screen.
        PurgeShotHandler.cancelPromptNotification(this)

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

        seedFromIntent(intent)

        // Restore anything the persisted queue is still holding. This is what
        // makes a prompt survive process death or a blocked direct launch.
        lifecycleScope.launch {
            try {
                PurgeShotHandler.restorePendingPrompts(this@PurgeShotPopupActivity, settingsRepository)
            } catch (_: Exception) {
                // A DataStore failure must not trap the user in an empty popup.
            } finally {
                promptsLoaded.value = true
                if (PurgeShotHandler.activeBatchFlow.value.isEmpty()) finish()
            }
        }

        setContent {
            ToolzTheme {
                val viewModel: PurgeShotViewModel = hiltViewModel()
                val presets: List<PurgeShotPreset> by viewModel.activePresets.collectAsStateWithLifecycle(initialValue = PurgeShotPreset.defaults())
                val autoDuration: Long by viewModel.autoDurationMs.collectAsStateWithLifecycle(initialValue = 15 * 60_000L)
                val activeBatch: List<PopupCandidate> by PurgeShotHandler.activeBatchFlow.collectAsStateWithLifecycle()

                if (!promptsLoaded.value && activeBatch.isEmpty()) {
                    // Brief gate while the persisted prompt queue loads. Avoids
                    // flashing an empty popup when opened from the notification.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@ToolzTheme
                }

                val uris: List<Uri> = activeBatch.map { it.uri }
                val displayName: String = if (activeBatch.size > 1) {
                    getString(R.string.st_PurgeShot_ScreenshotsCount, activeBatch.size)
                } else {
                    activeBatch.firstOrNull()?.displayName
                        ?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.st_PurgeShot_Title)
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
                        val items = currentItems(activeBatch)
                        viewModel.enqueueMultiple(
                            items.map { it.uri.toString() },
                            items.map { it.displayName },
                            items.map { it.filePath },
                            preset.durationMillis,
                            preset.label,
                        )
                        resolveAndFinish(items)
                    },
                    onDeleteNow = {
                        val items = currentItems(activeBatch)
                        viewModel.deleteMultiple(
                            items.map { it.uri.toString() },
                            items.map { it.filePath },
                        )
                        resolveAndFinish(items)
                    },
                    onDismiss = {
                        // Dismiss intentionally keeps the file. Remember the
                        // decision so the same screenshot never prompts again.
                        resolveAndFinish(currentItems(activeBatch))
                    },
                    onKeepForever = {
                        resolveAndFinish(currentItems(activeBatch))
                    },
                    onOpenSettings = {
                        val items = currentItems(activeBatch)
                        PurgeShotHandler.resolvePendingAsync(settingsRepository, items)
                        PurgeShotHandler.clearActiveBatch()
                        startActivity(
                            Intent(this@PurgeShotPopupActivity, MainActivity::class.java).apply {
                                putExtra("navigate_to", "purgeshot")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }

    /** Seeds the first candidate from the launch intent, if present. */
    private fun seedFromIntent(intent: Intent?) {
        val uriStr = intent?.getStringExtra("uri") ?: return
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return
        PurgeShotHandler.addCandidateDirectly(
            PopupCandidate(
                uri = uri,
                displayName = intent.getStringExtra("displayName").orEmpty(),
                filePath = intent.getStringExtra("path"),
                sizeLabel = intent.getStringExtra("sizeLabel"),
            )
        )
    }

    private fun currentItems(activeBatch: List<PopupCandidate>): List<PopupCandidate> =
        activeBatch.ifEmpty {
            listOfNotNull(
                intent.getStringExtra("uri")?.let { uriStr ->
                    PopupCandidate(
                        uri = Uri.parse(uriStr),
                        displayName = intent.getStringExtra("displayName").orEmpty(),
                        filePath = intent.getStringExtra("path"),
                        sizeLabel = intent.getStringExtra("sizeLabel"),
                    )
                }
            )
        }

    /**
     * Persists the user's decision off the activity lifecycle, then closes.
     * Using the handler's process-wide scope means finishing/rotating cannot
     * cancel the DataStore write and resurrect the prompt later.
     */
    private fun resolveAndFinish(items: List<PopupCandidate>) {
        PurgeShotHandler.resolvePendingAsync(settingsRepository, items)
        PurgeShotHandler.clearActiveBatch()
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PurgeShotHandler.setPopupActive(true)
        seedFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        PurgeShotHandler.setPopupActive(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        PurgeShotHandler.setPopupActive(false)
        // Only discard the batch when the popup is actually gone. A
        // configuration change or process recreation must be able to restore
        // what the user was deciding on.
        if (isFinishing) {
            PurgeShotHandler.clearActiveBatch()
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Back is a dismissal: keep the file, remember the decision, close.
        resolveAndFinish(currentItems(PurgeShotHandler.activeBatchFlow.value))
    }
}

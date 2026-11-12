/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.service

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
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
import kotlinx.coroutines.flow.collect
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

    /** Mirrors the DataStore setting so back-press handling can read it synchronously. */
    @Volatile private var strictModeEnabled = true

    /** True once a button was pressed; prevents strict-mode re-show/cleanup races. */
    @Volatile private var decisionMade = false

    /** Incremented when strict mode blocks a back gesture; drives the inline hint. */
    private val strictBlockPulse = androidx.compose.runtime.mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Defaults to strict (on) and updates as soon as DataStore emits.
        lifecycleScope.launch {
            runCatching {
                settingsRepository.purgeShotStrictMode.collect { strictModeEnabled = it }
            }
        }

        // Back is only a dismissal when strict mode is off. With strict mode on
        // the prompt stays until the user makes an explicit choice.
        onBackPressedDispatcher.addCallback(this) {
            if (strictModeEnabled) {
                strictBlockPulse.intValue++
            } else {
                resolveAndFinish(currentItems(PurgeShotHandler.activeBatchFlow.value))
            }
        }

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
        // Never let a stray outside tap close the prompt at the window level.
        // Outside taps are handled explicitly in Compose and are ignored while
        // strict mode is enabled.
        setFinishOnTouchOutside(false)

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
                val strictMode: Boolean by settingsRepository.purgeShotStrictMode
                    .collectAsStateWithLifecycle(initialValue = true)

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
                    strictMode = strictMode,
                    strictBlockPulse = strictBlockPulse.intValue,
                    onSelectDuration = { preset ->
                        decisionMade = true
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
                        decisionMade = true
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
                        decisionMade = true
                        resolveAndFinish(currentItems(activeBatch))
                    },
                    onKeepForever = {
                        decisionMade = true
                        resolveAndFinish(currentItems(activeBatch))
                    },
                    onOpenSettings = {
                        decisionMade = true
                        PurgeShotHandler.cancelStrictReshow()
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
        decisionMade = true
        PurgeShotHandler.cancelStrictReshow()
        PurgeShotHandler.setPopupActive(false)
        PurgeShotHandler.resolvePendingAsync(settingsRepository, items)
        PurgeShotHandler.clearActiveBatch()
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        decisionMade = false
        PurgeShotHandler.setPopupActive(true)
        seedFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        PurgeShotHandler.setPopupActive(true)
        // If we were backgrounded, the popup is visible again — cancel a
        // pending strict re-show.
        PurgeShotHandler.cancelStrictReshow()
    }

    override fun onStop() {
        super.onStop()
        // Strict mode: leaving the prompt without choosing must not silently
        // dismiss it. Re-surface it once the app is in the background, unless
        // the screen is off (the task will simply reappear on unlock) or a
        // configuration change is recreating the activity.
        if (decisionMade || !strictModeEnabled || isChangingConfigurations) return
        if (isFinishing) return
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
        if (powerManager?.isInteractive == false) return
        if (PurgeShotHandler.activeBatchFlow.value.isEmpty()) return
        PurgeShotHandler.setPopupActive(false)
        PurgeShotHandler.scheduleStrictReshow(
            this,
            settingsRepository,
            reason = "popup left without a choice",
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            PurgeShotHandler.setPopupActive(true)
            PurgeShotHandler.cancelStrictReshow()
            return
        }
        // Desktop-style windowing (ChromeOS, freeform, split-screen) can leave
        // the activity RESUMED while another window takes focus — `onStop`
        // never fires there, so strict mode also watches focus.
        if (decisionMade || !strictModeEnabled || isFinishing || isChangingConfigurations) return
        if (lifecycle.currentState != androidx.lifecycle.Lifecycle.State.RESUMED) return
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
        if (powerManager?.isInteractive == false) return
        if (PurgeShotHandler.activeBatchFlow.value.isEmpty()) return
        PurgeShotHandler.setPopupActive(false)
        PurgeShotHandler.scheduleStrictReshow(
            this,
            settingsRepository,
            reason = "window lost focus without a choice",
        )
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
}

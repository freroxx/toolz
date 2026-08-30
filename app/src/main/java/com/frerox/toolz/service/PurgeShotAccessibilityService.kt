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

package com.frerox.toolz.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.frerox.toolz.data.purgeshot.PurgeShotRepository
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.shizuku.ShizukuHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dedicated accessibility service for screenshot detection that works
 * even when Toolz is killed or in background.
 *
 * Why this is needed: ContentObserver in PurgeShotService dies with the
 * process. JobScheduler is batched (900ms + Doze). On many OEMs (Samsung,
 * Xiaomi, OnePlus) screenshots are inserted with DATE_TAKEN=0 and only via
 * Files, but still the observer misses when process is dead.
 *
 * System-level accessibility events for screenshots are delivered
 * regardless of Toolz process state:
 * - TYPE_NOTIFICATION_STATE_CHANGED from com.android.systemui containing
 *   "Screenshot captured" / "Screen captured" etc
 * - TYPE_WINDOW_STATE_CHANGED / CONTENT_CHANGED for the screenshot
 *   preview overlay (class "Screenshot" or similar)
 */
@AndroidEntryPoint
class PurgeShotAccessibilityService : AccessibilityService() {

    @Inject lateinit var repository: PurgeShotRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastTriggerMs = 0L

    companion object {
        private const val TAG = "PurgeShotA11y"
        private const val DEBOUNCE_MS = 900L

        private val SCREENSHOT_KEYWORDS = listOf(
            "screenshot", "screenshots",
            "screen captured", "screen capture", "screen shot",
            "captured", "capture",
            "screencap",
            "截屏", "截图",              // Chinese (MIUI, EMUI, ColorOS)
            "capture d'écran",          // French
            "captura de pantalla",      // Spanish
            "schermata",                // Italian
            "bildschirmfoto",           // German
            "schermafbeelding"          // Dutch
        )

        /**
         * Packages that post screenshot-related notifications on various OEM skins.
         * Pixel/AOSP = com.android.systemui
         * Samsung = com.samsung.android.app.smartcapture / com.samsung.android.screenshot
         * Xiaomi/MIUI = com.miui.screenshot / com.miui.screenrecorder
         * OnePlus/OxygenOS = com.oneplus.screenshot
         * OPPO/ColorOS = com.oppo.screenshot / com.coloros.screenshot
         * Vivo/FunTouchOS = com.vivo.screenshot
         * Huawei/EMUI = com.huawei.screenshot
         * Realme = com.realme.screenshot / com.oppo.screenshot
         * We also match any package whose notification text matches SCREENSHOT_KEYWORDS —
         * this covers unknown/future OEMs automatically.
         */
        private val SCREENSHOT_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            "com.google.android.systemui",
            // Samsung
            "com.samsung.android.app.smartcapture",
            "com.samsung.android.screenshot",
            "com.samsung.android.galaxyfinder",
            // Xiaomi / MIUI
            "com.miui.screenshot",
            "com.miui.screenrecorder",
            // OnePlus / OxygenOS
            "com.oneplus.screenshot",
            // OPPO / ColorOS
            "com.oppo.screenshot",
            "com.coloros.screenshot",
            // Vivo / FunTouchOS
            "com.vivo.screenshot",
            // Huawei / EMUI
            "com.huawei.screenshot",
            // Realme
            "com.realme.screenshot"
        )

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, PurgeShotAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabled)
            while (colonSplitter.hasNext()) {
                if (colonSplitter.next().equals(expected, ignoreCase = true)) return true
            }
            return enabled.contains(context.packageName) && enabled.contains(PurgeShotAccessibilityService::class.java.simpleName)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "PurgeShotAccessibilityService connected")
        // Re-arm the outside-Toolz detection stack on every (re)connection IF enabled.
        scope.launch {
            try {
                val enabled = settingsRepository.purgeShotEnabled.first()
                if (enabled) {
                    PurgeShotObserverJobService.schedule(applicationContext)
                    if (ShizukuHelper.isAuthorized()) {
                        (applicationContext as? com.frerox.toolz.ToolzApplication)?.shizukuWatcher?.start()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()
        if (now - lastTriggerMs < DEBOUNCE_MS) return

        val pkg = event.packageName?.toString()
        val type = event.eventType

        var shouldTrigger = false
        var reason = ""

        when (type) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // Match known OEM screenshot packages explicitly, but ALSO match any package
                // whose notification text contains screenshot keywords — this covers OEMs
                // we haven't listed yet without needing another release.
                val isScreenshotPkg = pkg != null && pkg in SCREENSHOT_PACKAGES
                val texts = mutableListOf<String>()
                event.text.forEach { texts.add(it.toString()) }
                val parcel = event.parcelableData
                if (parcel is Notification) {
                    val extras = parcel.extras
                    extras.getCharSequence(Notification.EXTRA_TITLE)?.let { texts.add(it.toString()) }
                    extras.getCharSequence(Notification.EXTRA_TEXT)?.let { texts.add(it.toString()) }
                    extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { texts.add(it.toString()) }
                    extras.getCharSequence("android.subText")?.let { texts.add(it.toString()) }
                }
                val combined = texts.joinToString(" ").lowercase()
                val keywordMatch = combined.isNotBlank() && SCREENSHOT_KEYWORDS.any { kw -> combined.contains(kw) }
                if (isScreenshotPkg && keywordMatch) {
                    shouldTrigger = true
                    reason = "notification:known_pkg:$pkg"
                } else if (keywordMatch) {
                    // Unknown OEM: keyword match alone is enough — the cost of a false positive
                    // (detectAndHandle runs, finds no fresh screenshot, returns false) is tiny.
                    shouldTrigger = true
                    reason = "notification:keyword_only:$pkg"
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val cls = event.className?.toString() ?: ""
                val txt = event.text.joinToString(" ")
                val combined = "$cls $txt".lowercase()
                if (combined.contains("screenshot") || combined.contains("screen capture") || combined.contains("screencap")) {
                    shouldTrigger = true
                    reason = "window:$cls"
                } else if (pkg != null && pkg in SCREENSHOT_PACKAGES &&
                    (cls.contains("Screenshot", ignoreCase = true) || txt.contains("Screenshot", ignoreCase = true))) {
                    shouldTrigger = true
                    reason = "oem_window:$pkg:$cls"
                }
            }
        }

        if (!shouldTrigger) return
        lastTriggerMs = now
        Log.i(TAG, "Screenshot accessibility event matched ($reason) pkg=$pkg type=$type")

        scope.launch {
            try {
                // CRITICAL FIX: default to false on DataStore read failure (fail closed).
                // When the accessibility framework cold-starts this process after Toolz was killed,
                // purgeShotEnabled.first() may throw before DataStore is fully initialized.
                // Defaulting to true previously forced PurgeShot on for everyone — now we
                // fail closed to respect the default.
                val enabled = try { settingsRepository.purgeShotEnabled.first() } catch (_: Exception) { false }
                if (!enabled) {
                    Log.d(TAG, "purgeShot disabled, ignore a11y trigger")
                    return@launch
                }
                // Re-arm JobScheduler so it stays alive for future screenshots
                try { PurgeShotObserverJobService.schedule(applicationContext) } catch (_: Exception) {}

                val ok = PurgeShotDetector.detectAndHandle(
                    context = applicationContext,
                    repository = repository,
                    settingsRepository = settingsRepository,
                    awaitSettle = true,
                    isPoll = false
                )
                Log.i(TAG, "a11y trigger detectAndHandle result=$ok reason=$reason")
            } catch (e: Exception) {
                Log.w(TAG, "a11y trigger failed", e)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Log.i(TAG, "destroyed")
    }
}

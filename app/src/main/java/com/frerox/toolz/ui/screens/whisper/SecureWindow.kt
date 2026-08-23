package com.frerox.toolz.ui.screens.whisper

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

private fun Context.findActivity(): Activity? = generateSequence(this) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()

/** Sets FLAG_SECURE on the hosting window so sensitive content never appears in
 *  screenshots, screen recordings, or the recents preview. FLAG_SECURE applies while
 *  this composable is on screen and is cleared on leave, so non-Whisper screens are
 *  never affected by it.
 *  P1-18 FIX: Reference-counted — multiple SecureWindow hosts no longer clear the
 *  flag while another still needs it.
 *
 *  NOTE: `bypassEnabled` reflects a single app-wide settings flag — when enabled it
 *  disables FLAG_SECURE for EVERY Whisper screen, not just the one showing the toggle.
 *  Intentional (a per-screen bypass would still expose the recents preview), but it is
 *  global rather than per-screen. */
// V2-FIX H-16: per-window refcounting — the old process-global Int was wrong once more
// than one Activity/window hosted SecureWindow: a dispose on one screen dropped the shared
// count to zero and cleared FLAG_SECURE for a DIFFERENT window that still needed it.
// Counts are now tracked per android.view.Window under the same lock, and flags are only
// mutated on each window's own count transitions (0→1 adds, →0 clears).
private val secureWindowRefCounts = mutableMapOf<Window, Int>()
private val secureWindowLock = Any()

@Composable
fun SecureWindow(bypassEnabled: Boolean = false) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(bypassEnabled) {
            val window = view.context.findActivity()?.window
                ?: return@DisposableEffect onDispose {}

            val needsSecure = !bypassEnabled
            if (needsSecure) {
                synchronized(secureWindowLock) {
                    val newCount = (secureWindowRefCounts[window] ?: 0) + 1
                    secureWindowRefCounts[window] = newCount
                    if (newCount == 1) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            } else {
                // V2-FIX H-16: the bypass path clears ONLY its own host window, and only
                // while that window's requirement has flipped off (no remaining hosts on
                // this window want the flag). Other windows' entries are never touched.
                synchronized(secureWindowLock) {
                    if ((secureWindowRefCounts[window] ?: 0) == 0) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }

            onDispose {
                if (needsSecure) {
                    synchronized(secureWindowLock) {
                        // V2-FIX H-16: dispose decrements THIS window's count; the flag is
                        // cleared only when this window's last host leaves. A missing entry
                        // means another dispose already cleaned this window up.
                        val current = secureWindowRefCounts[window] ?: return@synchronized
                        if (current <= 1) {
                            secureWindowRefCounts.remove(window)
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            secureWindowRefCounts[window] = current - 1
                        }
                    }
                }
            }
        }
    }
}
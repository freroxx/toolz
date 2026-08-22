package com.frerox.toolz.ui.screens.whisper

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
private var secureWindowRefCount = 0
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
                    if (secureWindowRefCount == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    secureWindowRefCount++
                }
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            onDispose {
                if (needsSecure) {
                    synchronized(secureWindowLock) {
                        secureWindowRefCount = (secureWindowRefCount - 1).coerceAtLeast(0)
                        if (secureWindowRefCount == 0) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
    }
}
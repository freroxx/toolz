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
 *  never affected by it. */
@Composable
fun SecureWindow(bypassEnabled: Boolean = false) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(bypassEnabled) {
            val window = view.context.findActivity()?.window
                ?: return@DisposableEffect onDispose {}
            
            if (!bypassEnabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            
            onDispose {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
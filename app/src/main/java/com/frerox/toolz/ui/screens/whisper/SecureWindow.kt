package com.frerox.toolz.ui.screens.whisper

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView

/** Sets FLAG_SECURE on the hosting window so sensitive content never appears in
 *  screenshots, screen recordings, or the recents preview. */
@Composable
fun SecureWindow() {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
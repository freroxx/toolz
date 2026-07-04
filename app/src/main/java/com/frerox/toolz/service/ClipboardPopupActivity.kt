package com.frerox.toolz.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.frerox.toolz.ui.components.ClipboardPopup
import com.frerox.toolz.ui.theme.ToolzTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ClipboardPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            ToolzTheme {
                ClipboardPopup(
                    onDismiss = { finish() },
                    onManageHistory = { finish() }
                )
            }
        }
    }
}

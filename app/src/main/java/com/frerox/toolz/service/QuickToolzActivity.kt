package com.frerox.toolz.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.frerox.toolz.ui.components.QuickToolzBottomSheet
import com.frerox.toolz.ui.theme.ToolzTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class QuickToolzActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            ToolzTheme {
                var showSheet by remember { mutableStateOf(true) }
                
                if (showSheet) {
                    QuickToolzBottomSheet(
                        onDismiss = {
                            showSheet = false
                            finish()
                        },
                        onNavigate = { route: String ->
                            // Here we should navigate to MainActivity with the route
                            startActivity(android.content.Intent(this, com.frerox.toolz.MainActivity::class.java).apply {
                                putExtra(com.frerox.toolz.MainActivity.EXTRA_NAVIGATE_TO, route)
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                            })
                            showSheet = false
                            finish()
                        }
                    )
                }
            }
        }
    }
}

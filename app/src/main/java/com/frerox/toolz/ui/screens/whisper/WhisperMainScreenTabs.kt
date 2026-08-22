package com.frerox.toolz.ui.screens.whisper

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * P0-6b FIX: First extraction from WhisperMainScreen.kt (2925 → modular).
 * Previously all 3 tabs (Chats/Discover/Profile) + scaffolding lived in one
 * 2925-line file. This file owns the tab-level composables so MainScreen
 * only wires Scaffold + Nav. Next PR moves full tab bodies here.
 *
 * This stub keeps the build green while the staged split lands; real tab
 * implementations are already in WhisperMainScreen and will be moved
 * function-by-function to avoid a megadiff.
 */
object WhisperMainScreenTabs {
    // Placeholder — real extraction tracked as GitHub issue: "Split WhisperMainScreen god file"
    // ChatsTab, DiscoverTab, ProfileTab will live here as @Composable functions.
    // See: WhisperMainScreen.kt:300-900 chats hub, 900-1800 discover, 1800-2925 profile.
}

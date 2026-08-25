/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.frerox.toolz.data.whisper.WhisperAvatarLoader

/**
 * V6-R7 AVATARS: app-wide access point for the encrypted-avatar loader so
 * [com.frerox.toolz.ui.screens.whisper.WhisperAvatar] (used across 4 screens)
 * can resolve ImgBB-hosted ciphertext without threading dependencies through
 * every call site. Provided once in MainActivity; null in previews/tests, where
 * avatars gracefully fall back to the initials rendering.
 */
val LocalWhisperAvatarLoader = staticCompositionLocalOf<WhisperAvatarLoader?> { null }

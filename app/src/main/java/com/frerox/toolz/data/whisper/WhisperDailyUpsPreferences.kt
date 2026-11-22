/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Device-local Daily Ups preferences.
 *
 * The close-friends list is intentionally NEVER uploaded: the server only sees
 * the recipient snapshot of each individual story, so "who is in my close
 * friends" cannot be reconstructed from backend data. Both settings are
 * account-scoped UI preferences and are wiped with the rest of the local data
 * on sign-out/deletion.
 *
 * "Show who viewed" defaults to ON: receipts are the feature's social contract,
 * and the composer still lets the poster turn them off for future ups.
 */
@Singleton
class WhisperDailyUpsPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _closeFriends = MutableStateFlow(loadCloseFriends())
    val closeFriends: StateFlow<Set<String>> = _closeFriends.asStateFlow()

    private val _showViewers = MutableStateFlow(prefs.getBoolean(KEY_SHOW_VIEWERS, true))
    val showViewers: StateFlow<Boolean> = _showViewers.asStateFlow()

    private val _saveToDevice = MutableStateFlow(prefs.getBoolean(KEY_SAVE_TO_DEVICE, false))
    val saveToDevice: StateFlow<Boolean> = _saveToDevice.asStateFlow()

    fun setCloseFriend(userId: String, close: Boolean) {
        if (userId.isBlank()) return
        _closeFriends.update { current ->
            if (close) current + userId else current - userId
        }
        prefs.edit()
            .putStringSet(KEY_CLOSE_FRIENDS, _closeFriends.value)
            .apply()
    }

    fun toggleCloseFriend(userId: String) {
        setCloseFriend(userId, userId !in _closeFriends.value)
    }

    fun setShowViewers(on: Boolean) {
        _showViewers.value = on
        prefs.edit().putBoolean(KEY_SHOW_VIEWERS, on).apply()
    }

    fun setSaveToDevice(on: Boolean) {
        _saveToDevice.value = on
        prefs.edit().putBoolean(KEY_SAVE_TO_DEVICE, on).apply()
    }

    /** Account deletion / local wipe: no list or setting may survive. */
    fun clearAll() {
        _closeFriends.value = emptySet()
        _showViewers.value = true
        _saveToDevice.value = false
        prefs.edit().clear().apply()
    }

    private fun loadCloseFriends(): Set<String> =
        prefs.getStringSet(KEY_CLOSE_FRIENDS, emptySet())
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    private companion object {
        const val PREFS_NAME = "whisper_daily_ups_prefs"
        const val KEY_CLOSE_FRIENDS = "close_friends"
        const val KEY_SHOW_VIEWERS = "show_viewers"
        const val KEY_SAVE_TO_DEVICE = "save_to_device"
    }
}

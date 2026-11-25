/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
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
 * friends" cannot be reconstructed from backend data.
 *
 * Everything here is keyed by the signed-in account. Sign-out keeps the
 * account's own history on disk (so signing back in restores it), which means
 * an unscoped file would hand the next account the previous account's close
 * friends. [bindAccount] switches the active key set; a later sign-in of the
 * same account gets its own list back.
 *
 * "Show who viewed" defaults to ON: receipts are the feature's social contract,
 * and the composer still lets the poster turn them off for future ups.
 */
@Singleton
class WhisperDailyUpsPreferences @Inject constructor(
    @ApplicationContext context: Context,
    private val supabase: SupabaseClient,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Account whose key prefix is active; null = no session bound yet. */
    private var accountId: String? = null

    private val _closeFriends = MutableStateFlow<Set<String>>(emptySet())
    val closeFriends: StateFlow<Set<String>> = _closeFriends.asStateFlow()

    private val _showViewers = MutableStateFlow(true)
    val showViewers: StateFlow<Boolean> = _showViewers.asStateFlow()

    private val _saveToDevice = MutableStateFlow(false)
    val saveToDevice: StateFlow<Boolean> = _saveToDevice.asStateFlow()

    init {
        // Earlier builds stored these keys unscoped. They cannot be attributed
        // to an account, so dropping them is the only privacy-safe migration.
        removeLegacyUnscopedKeys()
    }

    /**
     * Points every read/write at [userId]'s key set. Idempotent; call it from
     * the screens that read these preferences (composer + profile) so an
     * account switch on the same process never serves the old list.
     */
    fun bindAccount(userId: String?) {
        val next = userId?.takeIf { it.isNotBlank() }
        if (next == accountId) return
        accountId = next
        _closeFriends.value = loadCloseFriends()
        _showViewers.value = prefs.getBoolean(scoped(KEY_SHOW_VIEWERS), true)
        _saveToDevice.value = prefs.getBoolean(scoped(KEY_SAVE_TO_DEVICE), false)
    }

    /** Convenience for callers that already hold a session. */
    fun bindCurrentAccount() {
        bindAccount(supabase.auth.currentUserOrNull()?.id)
    }

    fun setCloseFriend(userId: String, close: Boolean) {
        if (userId.isBlank()) return
        _closeFriends.update { current ->
            if (close) current + userId else current - userId
        }
        prefs.edit()
            .putStringSet(scoped(KEY_CLOSE_FRIENDS), _closeFriends.value)
            .apply()
    }

    fun toggleCloseFriend(userId: String) {
        setCloseFriend(userId, userId !in _closeFriends.value)
    }

    fun setShowViewers(on: Boolean) {
        _showViewers.value = on
        prefs.edit().putBoolean(scoped(KEY_SHOW_VIEWERS), on).apply()
    }

    fun setSaveToDevice(on: Boolean) {
        _saveToDevice.value = on
        prefs.edit().putBoolean(scoped(KEY_SAVE_TO_DEVICE), on).apply()
    }

    /**
     * Account deletion / local wipe: only THIS account's keys may disappear —
     * another account's list on the same device is not ours to destroy.
     */
    fun clearAll() {
        // Resolve the account first: the wipe runs while the session is still
        // valid, and a never-bound instance must not delete the wrong keys.
        bindCurrentAccount()
        val editor = prefs.edit()
        // Bound account → only its keys. No account at all (session already
        // gone during deletion) → drop every key; there is no other identity
        // on this device we can safely attribute the leftovers to.
        val keys = accountId?.let { id ->
            prefs.all.keys.filter { it.startsWith("$id|") }
        } ?: prefs.all.keys.toList()
        keys.forEach(editor::remove)
        editor.apply()
        _closeFriends.value = emptySet()
        _showViewers.value = true
        _saveToDevice.value = false
    }

    private fun loadCloseFriends(): Set<String> =
        prefs.getStringSet(scoped(KEY_CLOSE_FRIENDS), emptySet())
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    private fun scoped(base: String): String = scopePrefix() + base

    private fun scopePrefix(): String = "${accountId ?: ANONYMOUS}|"

    private fun removeLegacyUnscopedKeys() {
        val legacy = listOf(KEY_CLOSE_FRIENDS, KEY_SHOW_VIEWERS, KEY_SAVE_TO_DEVICE)
        if (legacy.none(prefs::contains)) return
        val editor = prefs.edit()
        legacy.forEach(editor::remove)
        editor.apply()
    }

    private companion object {
        const val PREFS_NAME = "whisper_daily_ups_prefs"
        const val ANONYMOUS = "anonymous"
        const val KEY_CLOSE_FRIENDS = "close_friends"
        const val KEY_SHOW_VIEWERS = "show_viewers"
        const val KEY_SAVE_TO_DEVICE = "save_to_device"
    }
}

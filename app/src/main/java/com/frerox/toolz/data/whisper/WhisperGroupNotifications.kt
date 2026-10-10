/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Phase-2 groups: per-group notification toggle.
 *
 * Recipient matrix (decided with the user):
 * - owner + admins: toggle ON by default; join/leave always in scope, and the
 *   owner additionally sees decline ("other") notifications;
 * - members: toggle OFF by default; when enabled they see join/leave only.
 *
 * Stored as explicit overrides only (`gnotify_<groupId>` → 0/1); the default
 * is computed from the caller's role, so a promotion/demotion instantly flips
 * the default without a stored row. Local-only like mute prefs.
 */
@Singleton
class WhisperGroupNotifPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("whisper_group_notif", Context.MODE_PRIVATE)

    private val _overrides = MutableStateFlow(loadOverrides())
    val overrides: StateFlow<Map<String, Boolean>> = _overrides.asStateFlow()

    private fun loadOverrides(): Map<String, Boolean> =
        prefs.all.mapNotNull { (k, v) ->
            if (k.startsWith(KEY_PREFIX) && v is Int) k.removePrefix(KEY_PREFIX) to (v == 1) else null
        }.toMap()

    /** Explicit override for a group, null when the role default applies. */
    fun overrideOf(groupId: String): Boolean? = _overrides.value[groupId]

    /** Effective toggle: explicit override wins, else the role default. */
    fun isEnabled(groupId: String, isOwnerOrAdmin: Boolean): Boolean =
        overrideOf(groupId) ?: isOwnerOrAdmin

    fun setEnabled(groupId: String, enabled: Boolean) {
        prefs.edit().putInt(KEY_PREFIX + groupId, if (enabled) 1 else 0).apply()
        _overrides.update { it + (groupId to enabled) }
    }

    /** Wipe on account deletion (same hygiene as mute prefs). */
    suspend fun clearAll() {
        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            prefs.edit().clear().commit()
        }
        if (!ok) android.util.Log.w(TAG, "clearAll commit failed")
        _overrides.update { emptyMap() }
    }

    private companion object {
        const val TAG = "WhisperGroupNotif"
        const val KEY_PREFIX = "gnotify_"
    }
}

/**
 * Owner/admin event copy: randomized playful templates with {user}/{group}
 * slots. No-repeat-twice guard per kind (in-memory only).
 */
object WhisperGroupNotifTemplates {
    private val JOIN = listOf(
        "%1\$s is here with us!",
        "%1\$s finally joined %2\$s!",
        "%1\$s just walked in — say hi!",
        "Everyone welcome %1\$s!",
        "%1\$s accepted the invite — %2\$s grows!",
        "Look who showed up — %1\$s!",
        "%1\$s joined the party at %2\$s!",
        "Fresh face in %2\$s: welcome, %1\$s!",
        "%1\$s made it — %2\$s is complete!",
        "Say hello to %1\$s, newest in %2\$s!",
    )
    private val LEAVE = listOf(
        "%1\$s slipped out of %2\$s.",
        "%1\$s has left the building.",
        "We lost one — %1\$s left %2\$s.",
        "%1\$s said goodbye (for now?).",
        "%1\$s walked out of %2\$s.",
        "One chair emptier in %2\$s — %1\$s left.",
        "%1\$s is gone. %2\$s carries on.",
        "%1\$s checked out of %2\$s.",
    )
    private val DECLINE = listOf(
        "%1\$s refused the invite to %2\$s.",
        "%1\$s said no to %2\$s.",
        "No luck — %1\$s turned down %2\$s.",
        "%1\$s passed on %2\$s. Maybe next time.",
        "Invite declined: %1\$s won't join %2\$s.",
        "%1\$s is sitting this one out (%2\$s).",
    )

    private var lastJoinIdx = -1
    private var lastLeaveIdx = -1
    private var lastDeclineIdx = -1

    @Synchronized
    fun joinText(user: String, group: String, random: Random = Random): String {
        val idx = distinctIndex(JOIN.size, lastJoinIdx, random)
        lastJoinIdx = idx
        return JOIN[idx].format(user, group)
    }

    @Synchronized
    fun leaveText(user: String, group: String, random: Random = Random): String {
        val idx = distinctIndex(LEAVE.size, lastLeaveIdx, random)
        lastLeaveIdx = idx
        return LEAVE[idx].format(user, group)
    }

    @Synchronized
    fun declineText(user: String, group: String, random: Random = Random): String {
        val idx = distinctIndex(DECLINE.size, lastDeclineIdx, random)
        lastDeclineIdx = idx
        return DECLINE[idx].format(user, group)
    }

    private fun distinctIndex(size: Int, last: Int, random: Random): Int {
        if (size <= 1) return 0
        var idx = random.nextInt(size)
        if (idx == last) idx = (idx + 1) % size
        return idx
    }

    /** Test seam: pool sizes. */
    fun joinPoolSize(): Int = JOIN.size
    fun leavePoolSize(): Int = LEAVE.size
    fun declinePoolSize(): Int = DECLINE.size
}

/** Which group event kinds can ping. */
enum class GroupNotifScope { JOIN, LEAVE, DECLINE }

/**
 * Pure recipient rule (unit-tested): actor never pings themselves; the
 * per-group toggle (role-defaulted) gates everything; joins/leaves reach any
 * opted-in role while declines reach the owner only.
 */
fun shouldNotifyGroupEvent(
    kind: GroupNotifScope,
    actorId: String,
    me: String,
    amOwner: Boolean,
    toggleOn: Boolean,
): Boolean {
    if (!toggleOn || actorId == me || actorId.isBlank() || me.isBlank()) return false
    return when (kind) {
        GroupNotifScope.JOIN, GroupNotifScope.LEAVE -> true
        GroupNotifScope.DECLINE -> amOwner
    }
}

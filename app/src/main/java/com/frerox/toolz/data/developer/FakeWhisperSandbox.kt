/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Sandboxed Whisper fixture: RAM-only data. No Supabase, no realtime,
 * no auth, no network. Used by the developer console preview AND (when
 * spoof_whisper is active) as the data source for Whisper screens instead
 * of the real repository. Everything the user sees here is local.
 */

package com.frerox.toolz.data.developer

import com.frerox.toolz.data.whisper.WhisperConversation
import com.frerox.toolz.data.whisper.WhisperMessage
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.data.whisper.WhisperUiState
import java.util.Locale

data class FakeWhisperContact(
    val id: String,
    val name: String,
    val lastMessage: String,
    val unread: Int = 0,
)

data class FakeWhisperMessage(
    val id: String,
    val fromMe: Boolean,
    val text: String,
    val time: String,
)

object FakeWhisperSandbox {
    const val BANNER = "SANDBOX FIXTURE • OFFLINE • NOTHING LEAVES THIS DEVICE"

    /** The local fixture account — no Supabase session backs this id. */
    const val MY_ID = "spoof-self"

    val contacts = listOf(
        FakeWhisperContact("debug-ada", "Ada • Debug", "can you see this fixture thread?", 3),
        FakeWhisperContact("debug-null", "Null • Pointer", "this message never touched the server", 0),
        FakeWhisperContact("debug-group", "sandbox-testers (5)", "Ada: fixtures only, no sync", 12),
    )

    val thread = listOf(
        FakeWhisperMessage("m1", false, "Hey — this is a LOCAL fixture.", "09:41"),
        FakeWhisperMessage("m2", true, "So sending here does nothing online?", "09:42"),
        FakeWhisperMessage("m3", false, "Exactly. No Supabase, no realtime, no push. Airplane-safe.", "09:42"),
        FakeWhisperMessage("m4", false, "Turn master OFF to return to your real chats.", "09:43"),
        FakeWhisperMessage("m5", true, "QR verify in the sandbox always passes (fixture).", "09:44"),
    )

    // ─────────────────────────────────────────────────────────
    // Live-screen fixtures (real whisper models, spoof_whisper)
    // ─────────────────────────────────────────────────────────

    private val partners = listOf(
        TalkPartners("debug-ada", "ada_fixture", "Ada • Debug", lastText = "can you see this fixture thread?", unread = 3),
        TalkPartners("debug-null", "null_pointer", "Null • Pointer", lastText = "this message never touched the server", unread = 0),
        TalkPartners("debug-sam", "sam_fixture", "Sam • Fixture", lastText = "airplane mode? what airplane mode", unread = 1),
    )

    private data class TalkPartners(
        val id: String,
        val username: String,
        val name: String,
        val lastText: String,
        val unread: Int = 0,
    )

    private fun nowIso(): String = java.time.OffsetDateTime.now().toString()

    /** Fake local account shown in the profile tab. */
    val myProfile = WhisperProfile(
        id = MY_ID,
        username = "fixture_self",
        displayName = "Fixture Pilot",
        bio = "Developer sandbox fixture — nothing here is real.",
        publicKey = "FIXTURE-LOCAL",
        createdAt = nowIso(),
        updatedAt = nowIso(),
    )

    /** Fixture partner by id (falls back to a generated ghost). */
    fun profileFor(id: String): WhisperProfile {
        val known = partners.firstOrNull { it.id == id }
        return if (known != null) {
            WhisperProfile(
                id = known.id,
                username = known.username,
                displayName = known.name,
                bio = "Local sandbox fixture — this contact is not real.",
                publicKey = "FIXTURE-${known.id.uppercase(Locale.ROOT)}",
                createdAt = nowIso(),
                updatedAt = nowIso(),
            )
        } else {
            WhisperProfile(
                id = id,
                username = id,
                displayName = "Sandbox Ghost",
                bio = "Local sandbox fixture — this contact is not real.",
                publicKey = "FIXTURE-LOCAL",
                createdAt = nowIso(),
                updatedAt = nowIso(),
            )
        }
    }

    /** Contact list the main screen renders when sandboxed. */
    fun conversations(): List<WhisperConversation> = partners.map { p ->
        WhisperConversation(
            otherUser = profileFor(p.id),
            lastMessage = WhisperMessage(
                id = "spoof-last-${p.id}",
                senderId = if (p.unread > 0) p.id else MY_ID,
                receiverId = if (p.unread > 0) MY_ID else p.id,
                content = p.lastText,
                isRead = p.unread == 0,
                createdAt = nowIso(),
            ),
            unreadCount = p.unread,
        )
    }

    /** Friends tab content. */
    fun friends(): List<WhisperProfile> = partners.map { profileFor(it.id) }

    /** Fixed fake thread shown when opening a sandboxed chat. */
    fun threadFor(peerId: String): List<WhisperMessage> = thread.mapIndexed { i, f ->
        WhisperMessage(
            id = "spoof-${peerId}-$i",
            senderId = if (f.fromMe) MY_ID else peerId,
            receiverId = if (f.fromMe) peerId else MY_ID,
            content = f.text,
            isRead = true,
            createdAt = nowIso(),
        )
    }

    /** Canned local-only auto-replies so sending in the sandbox feels alive. */
    fun autoReplyFor(text: String): String {
        val canned = listOf(
            "Fixture reply: \"$text\" stayed 100% on this device.",
            "Sandbox autonomy: nothing left the phone, nothing ever will.",
            "Received (RAM-only). Airplane mode approves this message.",
            "Fixture thread confirms delivery to nowhere.",
        )
        return canned[(text.hashCode().let { if (it < 0) -it else it }) % canned.size]
    }

    /**
     * The complete hub state the main screen collects when the sandbox is on —
     * typed with the real models so every list/tab renders without special cases.
     */
    fun hubState(): WhisperUiState = WhisperUiState(
        isLoading = false,
        currentProfile = myProfile,
        conversations = conversations(),
        friends = friends(),
        searchResults = emptyList(),
        recommendedProfiles = emptyList(),
        discoverProfiles = emptyList(),
        mutedUserIds = emptySet(),
    )
}

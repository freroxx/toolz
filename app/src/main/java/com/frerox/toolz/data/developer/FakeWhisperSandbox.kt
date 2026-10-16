/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Controlled Whisper sandbox: RAM-only fixtures. No Supabase, no realtime,
 * no auth, no network. Used by the developer console preview AND (when
 * spoof_whisper is active) as the data source for Whisper screens instead
 * of the real repository. Everything the user sees here is local.
 */

package com.frerox.toolz.data.developer

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
    const val BANNER = "SPOOF SANDBOX • OFFLINE • NOTHING LEAVES THIS DEVICE"

    val contacts = listOf(
        FakeWhisperContact("debug-ada", "Ada • Debug", "can you see this fake thread?", 3),
        FakeWhisperContact("debug-null", "Null • Pointer", "this message never touched the server", 0),
        FakeWhisperContact("debug-group", "sandbox-testers (5)", "Ada: fixtures only, no sync", 12),
    )

    val thread = listOf(
        FakeWhisperMessage("m1", false, "Hey — this is a LOCAL fixture.", "09:41"),
        FakeWhisperMessage("m2", true, "So sending here does nothing online?", "09:42"),
        FakeWhisperMessage("m3", false, "Exactly. No Supabase, no realtime, no push. Airplane-safe.", "09:42"),
        FakeWhisperMessage("m4", false, "Turn master OFF to return to your real chats.", "09:43"),
        FakeWhisperMessage("m5", true, "QR verify in sandbox always passes (fake).", "09:44"),
    )
}

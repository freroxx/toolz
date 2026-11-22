/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

/**
 * Who a Daily Up was shared with. The audience itself is still an immutable
 * per-recipient snapshot (server side); this value only labels that snapshot so
 * the poster and every recipient can render the same "close friends" affordance.
 *
 * [wire] is persisted in `whisper_stories.audience_kind` and must stay stable.
 */
enum class WhisperStoryAudience(val wire: String) {
    EVERYONE("everyone"),
    CLOSE_FRIENDS("close"),
    CUSTOM("custom"),
    ;

    companion object {
        /** Unknown/legacy values degrade to EVERYONE, never crash a decode. */
        fun fromWire(raw: String?): WhisperStoryAudience =
            entries.firstOrNull { it.wire == raw } ?: EVERYONE
    }
}

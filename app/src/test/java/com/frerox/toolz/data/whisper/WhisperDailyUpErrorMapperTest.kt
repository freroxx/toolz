/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import com.frerox.toolz.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vocabulary contract between the repository throws, the server-raised
 * exception literals, and [mapDailyUpError]. Pinning these keeps the snackbar
 * copy deterministic when either side's wording shifts.
 */
class WhisperDailyUpErrorMapperTest {

    @Test
    fun mapsServerQuotaLiteral() {
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrQuota,
            mapDailyUpError("quota exceeded"),
        )
    }

    @Test
    fun mapsStaleClientVersionLiteral() {
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrVersion,
            mapDailyUpError("stale client version"),
        )
    }

    @Test
    fun mapsAudienceNotCurrentFriends() {
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrNotFriend,
            mapDailyUpError("story audience must be current friends"),
        )
    }

    @Test
    fun mapsReactionVocabulary() {
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrReaction,
            mapDailyUpError("unknown reaction emoji"),
        )
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrReaction,
            mapDailyUpError("story reactions are for the audience"),
        )
    }

    @Test
    fun mapsPreprocessorRejections() {
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrUnsupported,
            mapDailyUpError("unsupported photo format"),
        )
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrTooBig,
            mapDailyUpError("photo too large"),
        )
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrDecode,
            mapDailyUpError("photo decoding failed"),
        )
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrMetadata,
            mapDailyUpError("photo metadata could not be stripped"),
        )
    }

    @Test
    fun mapsOpenPhaseFailures() {
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrLocked,
            mapDailyUpError("locked story"),
        )
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrCorrupt,
            mapDailyUpError("corrupt story image"),
        )
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrExpired,
            mapDailyUpError("no such story"),
        )
    }

    @Test
    fun mapsSessionAndTimeoutFailures() {
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrUnreachable,
            mapDailyUpError("could not start your secure session"),
        )
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrSync,
            mapDailyUpError("timed out"),
        )
    }

    @Test
    fun mapsAuthGate() {
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrAuth,
            mapDailyUpError("Sign in to share Daily Ups."),
        )
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrAuth,
            mapDailyUpError("authenticated caller required"),
        )
    }

    @Test
    fun nullOrBlankAndUnknownReturnNull() {
        assertNull(mapDailyUpError(null))
        assertNull(mapDailyUpError(""))
        assertNull(mapDailyUpError(" "))
        assertNull(mapDailyUpError("invalid recipients payload")) // bug class → raw fallback
    }

    @Test
    fun mappingIsCaseInsensitive() {
        assertEquals(
            R.string.st_Whisper_DailyUp_ErrQuota,
            mapDailyUpError("QUOTA EXCEEDED"),
        )
    }

    // ── Config parity with the server plane ─────────────────────────────────

    @Test
    fun reactionPaletteMatchesServerWhitelist() {
        // whisper_story_react whitelists exactly these six emojis.
        assertEquals(
            listOf("👍", "❤️", "😂", "😮", "😢", "🙏"),
            WhisperDailyUpsConfig.REACTION_EMOJIS,
        )
        assertEquals(6, WhisperDailyUpsConfig.REACTION_EMOJIS.distinct().size)
    }

    @Test
    fun configMirrorsServerBounds() {
        assertEquals(24L * 60 * 60 * 1000, WhisperDailyUpsConfig.TTL_MS)
        assertEquals(5, WhisperDailyUpsConfig.DAILY_LIMIT)
        assertEquals(96, WhisperDailyUpsConfig.MAX_RECIPIENTS)
        assertEquals(18, WhisperDailyUpsConfig.MIN_VERSION_CODE)
        assertEquals(24L * 60 * 60, WhisperDailyUpsConfig.UPLOAD_TTL_SECONDS)
        assertEquals(32_768, WhisperDailyUpsConfig.DESCRIPTOR_MAX_CHARS)
        assertFalse(WhisperDailyUpsConfig.ENABLED) // Hidden from UI in this update
    }
}

/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the why-disabled precedence for the pinned Share bar. These are pure
 * so they run without a ViewModel, Compose runtime, or device.
 */
class WhisperComposeCtaTest {

    @Test
    fun readyDraftReturnsNone() {
        assertEquals(
            ShareBlock.None,
            dailyUpShareBlock(
                hasPhoto = true,
                remaining = 1,
                audienceSize = 1,
                publishing = false,
                published = false,
            ),
        )
    }

    @Test
    fun missingPhotoWinsOverAudienceAndQuota() {
        assertEquals(
            ShareBlock.NoPhoto,
            dailyUpShareBlock(
                hasPhoto = false,
                remaining = 0,
                audienceSize = 0,
                publishing = false,
                published = false,
            ),
        )
    }

    @Test
    fun emptyAudienceAndExhaustedQuotaAreDistinct() {
        assertEquals(
            ShareBlock.NoAudience,
            dailyUpShareBlock(
                hasPhoto = true,
                remaining = 0,
                audienceSize = 0,
                publishing = false,
                published = false,
            ),
        )
        assertEquals(
            ShareBlock.NoQuota,
            dailyUpShareBlock(
                hasPhoto = true,
                remaining = 0,
                audienceSize = 1,
                publishing = false,
                published = false,
            ),
        )
    }

    @Test
    fun busyWinsWhilePublishingOrAfterPublish() {
        assertEquals(
            ShareBlock.Busy,
            dailyUpShareBlock(
                hasPhoto = false,
                remaining = 0,
                audienceSize = 0,
                publishing = true,
                published = false,
            ),
        )
        assertEquals(
            ShareBlock.Busy,
            dailyUpShareBlock(
                hasPhoto = true,
                remaining = 3,
                audienceSize = 2,
                publishing = false,
                published = true,
            ),
        )
    }
}

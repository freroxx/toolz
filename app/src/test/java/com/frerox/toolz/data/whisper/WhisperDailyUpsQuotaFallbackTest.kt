/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A quota read can fail (offline, timeout, signed out). Treating that as
 * "nothing posted today" resets the meter to a full day and lets the composer
 * upload a photo the server refuses for quota — so a null read must keep the
 * last known count.
 */
class WhisperDailyUpsQuotaFallbackTest {

    @Test
    fun `failed read keeps the last known count`() {
        assertEquals(4, quotaAfterRead(previous = 4, fresh = null))
    }

    @Test
    fun `failed read never invents a fresh day`() {
        assertEquals(
            WhisperDailyUpsConfig.DAILY_LIMIT,
            quotaAfterRead(previous = WhisperDailyUpsConfig.DAILY_LIMIT, fresh = null),
        )
    }

    @Test
    fun `successful read wins, including zero`() {
        assertEquals(0, quotaAfterRead(previous = 4, fresh = 0))
        assertEquals(5, quotaAfterRead(previous = 0, fresh = 5))
    }
}

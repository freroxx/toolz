package com.frerox.toolz.news

import com.frerox.toolz.ui.screens.news.NewsTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * NewsTime is pure on (epoch, now) so these tests run on the JVM without
 * Android. Today/Yesterday labels are injected, keeping the logic
 * localization-agnostic. Cases avoid exact formatted output that depends on
 * the host locale; boundaries are constructed to be timezone-stable
 * (same instant = always today, exactly -24h = always the previous day).
 */
class NewsTimeTest {

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val HOUR_MS = 60L * 60 * 1000
        const val MINUTE_MS = 60L * 1000
        const val NOW = 1_800_000_000_000L // fixed reference "now"
        const val TODAY_LABEL = "Today"
        const val YESTERDAY_LABEL = "Yesterday"
    }

    // ── newsDate ────────────────────────────────────────────────────────────

    @Test
    fun newsDate_zeroOrNegative_returnsEmpty() {
        assertEquals("", NewsTime.newsDate(0L, NOW, TODAY_LABEL, YESTERDAY_LABEL))
        assertEquals("", NewsTime.newsDate(-5L, NOW, TODAY_LABEL, YESTERDAY_LABEL))
    }

    @Test
    fun newsDate_sameInstant_isToday() {
        assertEquals(TODAY_LABEL, NewsTime.newsDate(NOW, NOW, TODAY_LABEL, YESTERDAY_LABEL))
    }

    @Test
    fun newsDate_exactly24hAgo_isYesterday() {
        // Exactly one day earlier always lands on the previous calendar date,
        // regardless of the host timezone or local midnight.
        assertEquals(
            YESTERDAY_LABEL,
            NewsTime.newsDate(NOW - DAY_MS, NOW, TODAY_LABEL, YESTERDAY_LABEL)
        )
    }

    @Test
    fun newsDate_future_isToday() {
        assertEquals(TODAY_LABEL, NewsTime.newsDate(NOW + HOUR_MS, NOW, TODAY_LABEL, YESTERDAY_LABEL))
    }

    @Test
    fun newsDate_thisYear_isShortMonthDay() {
        // 3 days back is in the same year as "today" (except a Dec→Jan edge;
        // in that edge case the year variant is used, which is also non-empty).
        val result = NewsTime.newsDate(NOW - 3 * DAY_MS, NOW, TODAY_LABEL, YESTERDAY_LABEL)
        assertTrue("expected a rendered date, got '$result'", result.isNotEmpty())
        assertFalse(result.contains(TODAY_LABEL))
        assertFalse(result.contains(YESTERDAY_LABEL))
    }

    @Test
    fun newsDate_priorYear_includesYear() {
        // 2020-06-15T12:00:00Z is in a different year than any plausible
        // test-run date; the year must appear in some form.
        val publishAt = Instant.parse("2020-06-15T12:00:00Z").toEpochMilli()
        val now = Instant.parse("2026-03-10T12:00:00Z").toEpochMilli()
        val result = NewsTime.newsDate(publishAt, now, TODAY_LABEL, YESTERDAY_LABEL)
        assertTrue("expected year in '$result'", result.contains("2020"))
        assertFalse(result.contains(TODAY_LABEL))
    }

    // ── isRecent ────────────────────────────────────────────────────────────

    @Test
    fun isRecent_within7Days_true() {
        assertTrue(NewsTime.isRecent(NOW - 72 * HOUR_MS, NOW))
        assertTrue(NewsTime.isRecent(NOW - 1, NOW))
    }

    @Test
    fun isRecent_olderThan7Days_false() {
        assertFalse(NewsTime.isRecent(NOW - 7 * DAY_MS, NOW))
        assertFalse(NewsTime.isRecent(NOW - 8 * DAY_MS, NOW))
    }

    @Test
    fun isRecent_futureOrMissing_false() {
        assertFalse(NewsTime.isRecent(0L, NOW))
        assertFalse(NewsTime.isRecent(NOW + 1, NOW))
    }

    // ── isExpired ───────────────────────────────────────────────────────────

    @Test
    fun isExpired_nullNeverExpires() {
        assertFalse(NewsTime.isExpired(null, NOW))
    }

    @Test
    fun isExpired_atOrPastDeadline_true() {
        assertTrue(NewsTime.isExpired(NOW - 1, NOW))
        assertTrue(NewsTime.isExpired(NOW, NOW))
    }

    @Test
    fun isExpired_futureDeadline_false() {
        assertFalse(NewsTime.isExpired(NOW + HOUR_MS, NOW))
    }

    // ── relativeShort ───────────────────────────────────────────────────────

    @Test
    fun relativeShort_underOneMinute_isAtLeast1m() {
        assertEquals("1m", NewsTime.relativeShort(NOW, NOW))
        assertEquals("1m", NewsTime.relativeShort(NOW - 59_000, NOW))
    }

    @Test
    fun relativeShort_minutesHoursDays() {
        assertEquals("5m", NewsTime.relativeShort(NOW - 5 * MINUTE_MS, NOW))
        assertEquals("1h", NewsTime.relativeShort(NOW - 90 * MINUTE_MS, NOW))
        assertEquals("5h", NewsTime.relativeShort(NOW - 5 * HOUR_MS, NOW))
        assertEquals("2d", NewsTime.relativeShort(NOW - 2 * DAY_MS, NOW))
    }

    @Test
    fun relativeShort_beyond7Days_empty() {
        assertEquals("", NewsTime.relativeShort(NOW - 7 * DAY_MS, NOW))
        assertEquals("", NewsTime.relativeShort(NOW - 30 * DAY_MS, NOW))
    }

    @Test
    fun relativeShort_invalidOrFuture_emptyOrClamped() {
        assertEquals("", NewsTime.relativeShort(0L, NOW))
        // Future sync timestamps clamp to the 1m floor instead of going negative.
        assertEquals("1m", NewsTime.relativeShort(NOW + 5 * MINUTE_MS, NOW))
    }
}

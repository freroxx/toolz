/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.util

/**
 * Pure quality policy for YouTube video downloads.
 *
 * Centralizes the "never silently save 360p as 1080p" rules so both the
 * Media Downloader sheet and [com.frerox.toolz.worker.VideoDownloadWorker]
 * agree. All functions are pure (no Android deps) for unit testing.
 */
object VideoQualityPolicy {

    /** Standard ladder offered in sheets, high → low. */
    val ladder = listOf(2160, 1440, 1080, 720, 480, 360, 240)

    /**
     * Direct muxed stream is acceptable without trying DASH/yt-dlp when it meets
     * the request, or is close enough for SD (480p ask with 360p muxed saves a
     * yt-dlp round-trip; 720p+ ask with 360p muxed must NOT be accepted).
     */
    fun isDirectAcceptable(directHeight: Int, requestedHeight: Int, isAudio: Boolean = false): Boolean {
        if (isAudio) return true
        if (directHeight <= 0 || requestedHeight <= 0) return false
        if (directHeight >= requestedHeight) return true
        // SD leniency: 480p ceiling with 360p muxed is only 120p short.
        if (requestedHeight <= 480 && directHeight >= 360) return true
        return false
    }

    /**
     * DASH video+audio pair is worth muxing (vs trying yt-dlp) when it carries
     * real HD value: >= 50% of the ceiling and at least 480p for HD asks.
     * SD ceilings accept anything >= min(ceiling, 360).
     */
    fun isHdPairAcceptable(pairHeight: Int, ceiling: Int): Boolean {
        if (pairHeight <= 0 || ceiling <= 0) return false
        if (ceiling <= 480) return pairHeight >= minOf(ceiling, 360)
        return pairHeight >= (ceiling * 0.5).toInt().coerceAtLeast(480)
    }

    /**
     * Honest options for a probe result. Empty probe = unknown → full ladder
     * with if-available semantics (caller adds suffixes). Non-empty → cap at
     * real max so a 360p-only video never offers 1080p/720p.
     */
    fun offeredHeights(probedHeights: List<Int>): List<Int> {
        if (probedHeights.isEmpty()) return ladder
        val maxH = probedHeights.maxOrNull() ?: return ladder
        return ladder.filter { it <= maxH || (it == 360 || it == 240) }
            .let { capped ->
                // Always keep at least 360p + 240p fallbacks.
                (capped + listOf(360, 240)).distinct().sortedDescending()
            }
    }

    /** True when the saved file is a real downgrade worth surfacing to the user. */
    fun isDowngrade(actualHeight: Int?, requestedHeight: Int): Boolean {
        if (actualHeight == null || actualHeight <= 0 || requestedHeight <= 0) return false
        return actualHeight < requestedHeight - 60
    }
}

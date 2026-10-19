/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

/** Shared window for navigation double-tap suppression (1.2 s, one destination). */
internal const val NAV_TAP_WINDOW_MS = 1_200L

/**
 * VM-owned elapsed-time gate for navigation entry points.
 *
 * Unlike a composable `remember` lock, this survives recomposition and needs no
 * delayed reset coroutine: every tap is judged against the last ACCEPTED tap.
 * Pure Kotlin (no Android imports), so the window contract is unit-testable.
 */
internal class ElapsedTapGate(private val windowMs: Long = NAV_TAP_WINDOW_MS) {

    private var lastAcceptedAtMs: Long? = null

    /** Records and accepts when outside the window; otherwise rejects. */
    fun tryAccept(nowMs: Long): Boolean {
        val last = lastAcceptedAtMs
        if (last != null && nowMs - last < windowMs) return false
        lastAcceptedAtMs = nowMs
        return true
    }

    fun reset() {
        lastAcceptedAtMs = null
    }
}

/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.util.Log
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * PHASE 1 (roadmap §1.3): privacy-safe, device-local protocol diagnostics.
 *
 * Design constraints:
 *  - Ring buffer of the last [CAP] protocol events, process-local, never leaves the
 *    device except through the explicit debug-build export button.
 *  - Cheap: one synchronized deque append; safe from any thread.
 *  - Never logs message content, ids, or key material — only protocol *shapes*.
 */
object ProtocolDiagnostics {

    private const val TAG = "WhisperProtoDiag"
    private const val CAP = 60
    private val buf = ArrayDeque<String>(CAP)
    private val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")

    /** Counters surfaced in the diagnostics dialog (decrypt outcomes, heals, sweeps). */
    val counters = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun log(event: String) {
        val line = "${LocalDateTime.now().format(fmt)}  $event"
        synchronized(buf) {
            buf.addLast(line)
            while (buf.size > CAP) buf.removeFirst()
        }
        Log.d(TAG, event)
    }

    fun increment(counter: String) {
        counters.merge(counter, 1L, Long::plus)
    }

    fun snapshot(): List<String> = synchronized(buf) { buf.toList() }

    fun clear() {
        synchronized(buf) { buf.clear() }
        counters.clear()
    }
}

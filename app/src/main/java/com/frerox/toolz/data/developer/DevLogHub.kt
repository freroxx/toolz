/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * In-memory thread-safe log recorder for developer mode diagnostics.
 * Captures internal app events, sandbox transitions, database operations and
 * handled errors in a rolling ring buffer (up to 500 lines).
 *
 * Lines use the same `seconds.millis pid tid LEVEL/Tag: message` shape as
 * Android's `logcat -v epoch`, so the local buffer and real logcat can be
 * merged chronologically by the developer console.
 */

package com.frerox.toolz.data.developer

import android.util.Log
import java.util.concurrent.ConcurrentLinkedDeque

object DevLogHub {
    const val TAG = "DevSpoof"
    private const val MAX_LOG_LINES = 500
    private val buffer = ConcurrentLinkedDeque<String>()

    init {
        log(Log.INFO, TAG, "Developer diagnostic log recorder initialized")
    }

    fun v(tag: String, msg: String) = log(Log.VERBOSE, tag, msg)
    fun d(tag: String, msg: String) = log(Log.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(Log.INFO, tag, msg)
    fun w(tag: String, msg: String, tr: Throwable? = null) = log(Log.WARN, tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable? = null) = log(Log.ERROR, tag, msg, tr)

    fun log(priority: Int, tag: String, msg: String, tr: Throwable? = null) {
        val levelChar = when (priority) {
            Log.VERBOSE -> 'V'
            Log.DEBUG -> 'D'
            Log.INFO -> 'I'
            Log.WARN -> 'W'
            Log.ERROR -> 'E'
            Log.ASSERT -> 'F'
            else -> 'I'
        }

        val nowMs = System.currentTimeMillis()
        val seconds = nowMs / 1000L
        val millis = (nowMs % 1000L).toString().padStart(3, '0')
        val pid = android.os.Process.myPid()
        val tid = android.os.Process.myTid()
        val errorSuffix = if (tr != null) "\n${Log.getStackTraceString(tr)}" else ""
        val formatted = "$seconds.$millis $pid $tid $levelChar/$tag: $msg$errorSuffix"

        // Mirror to system logcat so privileged/full-system views include it.
        try {
            Log.println(priority, tag, if (tr != null) "$msg\n${Log.getStackTraceString(tr)}" else msg)
        } catch (_: Exception) {
            // logcat may be unavailable; the in-memory ring buffer still works.
        }

        buffer.addLast(formatted)
        while (buffer.size > MAX_LOG_LINES) {
            buffer.pollFirst()
        }
    }

    fun getLines(): List<String> = buffer.toList()

    /** Empties the local buffer; callers own any "cleared" status message. */
    fun clear() {
        buffer.clear()
    }
}

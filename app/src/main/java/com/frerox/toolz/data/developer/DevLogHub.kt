/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * In-memory thread-safe log recorder for developer mode diagnostics.
 * Captures all internal app events, sandbox state transitions, database operations,
 * and errors into a rolling ring buffer (up to 500 lines).
 *
 * This guarantees live, reliable log streaming even on devices where Android OS
 * restricts third-party process access to system logcat.
 */

package com.frerox.toolz.data.developer

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object DevLogHub {
    private const val MAX_LOG_LINES = 500
    private val buffer = ConcurrentLinkedDeque<String>()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val _linesFlow = MutableStateFlow<List<String>>(emptyList())
    val linesFlow: StateFlow<List<String>> = _linesFlow.asStateFlow()

    init {
        log(Log.INFO, "ToolzDev", "Developer diagnostic log recorder initialized")
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

        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val pid = android.os.Process.myPid()
        val tid = android.os.Process.myTid()
        val errorSuffix = if (tr != null) "\n${Log.getStackTraceString(tr)}" else ""
        val formatted = "$timestamp $pid $tid $levelChar $tag: $msg$errorSuffix"

        // Also output to system logcat
        try {
            if (tr != null) {
                Log.println(priority, tag, "$msg\n${Log.getStackTraceString(tr)}")
            } else {
                Log.println(priority, tag, msg)
            }
        } catch (_: Exception) {}

        buffer.addLast(formatted)
        while (buffer.size > MAX_LOG_LINES) {
            buffer.pollFirst()
        }

        _linesFlow.value = buffer.toList()
    }

    fun getLines(): List<String> = buffer.toList()

    fun clear() {
        buffer.clear()
        _linesFlow.value = emptyList()
        log(Log.INFO, "ToolzDev", "Logcat buffer cleared by user")
    }
}

/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Small, immutable models shared by the developer console. Keeping them free
 * of Android UI types makes the report/formatter logic unit-testable.
 */

package com.frerox.toolz.data.developer

/** Snapshot of the running app process, refreshed once per second. */
data class DevAppInfo(
    val versionName: String = "",
    val versionCode: Long = 0L,
    val packageName: String = "",
    val pid: Int = 0,
    val processUptimeMs: Long = 0L,
    val heapUsedBytes: Long = 0L,
    val heapMaxBytes: Long = 0L,
    val nativeHeapBytes: Long = 0L,
    val threads: Int = 0,
    val heapUsedFraction: Float = 0f,
    val deviceLabel: String = "",
    val androidLabel: String = "",
    val abi: String = "",
    val buildType: String = "",
    val densityDpi: Int = 0,
    val debuggable: Boolean = false,
)

/** Row count for one Room table. */
data class DevTableCount(val name: String, val rows: Int)

/** One crash-lab event. Stored locally in DataStore, never uploaded. */
data class CrashLabEvent(
    val type: String,
    val atMs: Long,
    val note: String = "",
)

data class DevStorageInfo(
    val cacheBytes: Long = 0L,
    val filesBytes: Long = 0L,
    val databaseBytes: Long = 0L,
)

data class DevTableState(
    val tables: List<DevTableCount> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class DevPrefsState(
    val values: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class DevStorageState(
    val info: DevStorageInfo = DevStorageInfo(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class DevShizukuState(
    val installed: Boolean = false,
    val available: Boolean = false,
    val authorized: Boolean = false,
)

/** One-shot snackbar payload; the id makes repeated identical messages distinct. */
data class DevMessage(val id: Long, val text: String)

/** Logcat stream width. System scope needs Shizuku; app scope always works. */
enum class LogcatScope(val label: String, val description: String) {
    APP("This app", "PID-filtered stream from the local ring buffer and logcat"),
    SYSTEM("Full system", "Every process via Shizuku (requires authorization)"),
}

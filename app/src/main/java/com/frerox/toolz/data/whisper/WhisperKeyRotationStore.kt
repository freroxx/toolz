package com.frerox.toolz.data.whisper

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cheap FS auto-rotate — stores last rotation timestamp.
 * v3: 90 days → v4: 30 days + jitter, new-login guard, retry-safe.
 * No users yet, so no migration needed. First run seeds now().
 * Used by WhisperViewModel heartbeat to drive crypto.stageNewKeyPair()/commit.
 * Complexity tax ~0 vs full Double Ratchet (1 value vs 500).
 * Better: 30 days means at most 30 days of history exposed if Keystore leaks,
 * vs 90 before. Still cheap (1 server write per month).
 *
 * P0-1 FIX (reviewwhisper.md): [ROTATE_INTERVAL_MS] is now the SINGLE source of
 * truth. WhisperRepository.getKeyTrustInfo / sendMessage and every UI string are
 * aligned to it — the old code rotated every 30 days while the key-trust
 * heuristic assumed weekly rotations and the copy literally said "every week".
 */
@Singleton
class WhisperKeyRotationStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("whisper_key_rotation", Context.MODE_PRIVATE)

    fun lastRotateMs(): Long = prefs.getLong(KEY_LAST_ROTATE, 0L)

    // V2-FIX (reviewwhisper.md): markRotated is suspend and fsync'd commit() runs inside
    // Dispatchers.IO — a synchronous commit() on the main thread (heartbeat/rotation UI
    // paths) risks jank/ANR.
    suspend fun markRotated(now: Long = System.currentTimeMillis()) {
        val ok = withContext(Dispatchers.IO) {
            prefs.edit().putLong(KEY_LAST_ROTATE, now).commit()
        }
        if (!ok) Log.w(TAG, "markRotated commit failed")
    }

    /** True if interval elapsed since last rotation. Better: 30d + 0-6h jitter to avoid fleet thundering herd. */
    suspend fun shouldRotate(now: Long = System.currentTimeMillis()): Boolean {
        val last = lastRotateMs()
        if (last == 0L) {
            // Seed on first run — don't rotate immediately, just record.
            markRotated(now)
            return false
        }
        // Add up to 6h jitter so all installs don't rotate at same ms.
        val jitter = (last % (6 * 60 * 60 * 1000))
        return (now - last) >= (ROTATE_INTERVAL_MS + jitter)
    }

    suspend fun clear() {
        val ok = withContext(Dispatchers.IO) { prefs.edit().clear().commit() }
        if (!ok) Log.w(TAG, "clear commit failed")
    }

    // Exposed for ViewModel logging
    fun intervalDays(): Int = (ROTATE_INTERVAL_MS / (24 * 60 * 60 * 1000)).toInt()

    companion object {
        private const val TAG = "WhisperKeyRotation"
        const val KEY_LAST_ROTATE = "last_rotate_ms"
        /** 30 days — referenced by WhisperRepository key-change classification (P0-1). */
        const val ROTATE_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000

        /**
         * A server-side profile `updated_at` fresher than this is treated as "the key
         * was JUST rotated" (auto or manual) rather than a suspicious stale change.
         */
        const val FRESH_ROTATION_WINDOW_MS = 30L * 60 * 1000
    }
}

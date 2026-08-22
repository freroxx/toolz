package com.frerox.toolz.data.whisper

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cheap FS auto-rotate — stores last rotation timestamp.
 * v3: 90 days → v4: 30 days + jitter, new-login guard, retry-safe.
 * No users yet, so no migration needed. First run seeds now().
 * Used by WhisperViewModel heartbeat to call crypto.rotateKeyPair().
 * Complexity tax ~0 vs full Double Ratchet (1 value vs 500).
 * Better: 30 days means at most 30 days of history exposed if Keystore leaks,
 * vs 90 before. Still cheap (1 server write per month).
 */
@Singleton
class WhisperKeyRotationStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("whisper_key_rotation", Context.MODE_PRIVATE)

    fun lastRotateMs(): Long = prefs.getLong(KEY_LAST_ROTATE, 0L)

    fun markRotated(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_ROTATE, now).commit()
    }

    /** True if interval elapsed since last rotation. Better: 30d + 0-6h jitter to avoid fleet thundering herd. */
    fun shouldRotate(now: Long = System.currentTimeMillis()): Boolean {
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

    /** Force rotate check on new login: if server key older than interval, rotate now. */
    fun shouldRotateOnLogin(serverPublicKeyAgeMs: Long): Boolean {
        // If we have no local record but server suggests old key, rotate.
        return serverPublicKeyAgeMs >= ROTATE_INTERVAL_MS
    }

    fun clear() {
        prefs.edit().clear().commit()
    }

    // Exposed for ViewModel logging
    fun intervalDays(): Int = (ROTATE_INTERVAL_MS / (24 * 60 * 60 * 1000)).toInt()

    private companion object {
        const val KEY_LAST_ROTATE = "last_rotate_ms"
        const val ROTATE_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days — better than 90, still cheap (was 90)
    }
}

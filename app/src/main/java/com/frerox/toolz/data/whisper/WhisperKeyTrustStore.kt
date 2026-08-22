/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which public key each conversation partner last used, plus which
 * keys the user has explicitly verified in person. Lets Whisper detect a
 * changed key (possible MITM) and surface it without blocking messaging.
 *
 * All writes are suspend + fsync'd commit on IO: key-trust data is security-
 * critical, so it must be durable before the caller proceeds — but it must
 * never block the main thread (the old runBlocking-on-Main workaround caused jank).
 */
@Singleton
class WhisperKeyTrustStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("whisper_key_trust", Context.MODE_PRIVATE)

    /** The last accepted public key for a user (base64), or null if never seen. */
    fun knownKey(userId: String): String? = prefs.getString("known_$userId", null)

    /** The public key the user explicitly verified for a user (base64), or null. */
    fun verifiedKey(userId: String): String? = prefs.getString("verified_$userId", null)

    /** Timestamp of when known key was last stored — for 7-day polished rotation. */
    fun knownKeyTimestamp(userId: String): Long = prefs.getLong("known_ts_$userId", 0L)

    /** Accept a key as "known" without marking it verified. Durable, off-main. */
    suspend fun rememberKey(userId: String, publicKey: String) {
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString("known_$userId", publicKey)
                .putLong("known_ts_$userId", now)
                .commit()
        }
    }

    /** Accept a key as known AND verified (fingerprint compared in person). Durable, off-main. */
    suspend fun markVerified(userId: String, publicKey: String) {
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString("known_$userId", publicKey)
                .putString("verified_$userId", publicKey)
                .putLong("known_ts_$userId", now)
                .commit()
        }
    }

    /** Drop all trust records for a user (e.g. when blocking or unfriending). */
    suspend fun forgetUser(userId: String) {
        // known_ts_ is removed too — a stale timestamp could otherwise skew the
        // expected-rotation heuristic if the user is re-added later.
        // M-7 FIX (reviewwhisper.md): commit() is fsync'd — must never run on Main.
        withContext(Dispatchers.IO) {
            prefs.edit().remove("known_$userId").remove("verified_$userId").remove("known_ts_$userId").commit()
        }
    }

    /** Wipe every trust record on this device (account deletion). */
    suspend fun clearAll() {
        // M-7 FIX: same as forgetUser — durable write off the calling thread.
        withContext(Dispatchers.IO) {
            prefs.edit().clear().commit()
        }
    }
}
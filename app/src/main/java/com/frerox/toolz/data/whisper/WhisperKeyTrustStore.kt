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
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
 *
 * V3-FIX (H-11): TOFU anchors + verified flags moved from the plaintext
 * whisper_key_trust.xml to EncryptedSharedPreferences (new file
 * whisper_key_trust_enc.xml, Keystore-backed MasterKey alias "whisper_kt_master")
 * so they are no longer readable at rest. A one-time idempotent migration copies
 * any legacy plaintext entries into the encrypted store, then clears + deletes
 * the old file.
 */
@Singleton
class WhisperKeyTrustStore @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    // Lazy (thread-safe) init so Keystore/ESP setup — and the one-time migration —
    // run exactly once, on first use, not during Hilt graph construction on Main.
    private val prefs: SharedPreferences by lazy { createEncryptedPrefs() }

    /** The last accepted public key for a user (base64), or null if never seen. */
    fun knownKey(userId: String): String? = prefs.getString("known_$userId", null)

    /** The public key the user explicitly verified for a user (base64), or null. */
    fun verifiedKey(userId: String): String? = prefs.getString("verified_$userId", null)

    /** Timestamp of when known key was last stored — for 7-day polished rotation. */
    fun knownKeyTimestamp(userId: String): Long = prefs.getLong("known_ts_$userId", 0L)

    /** Accept a key as "known" without marking it verified. Durable, off-main. */
    suspend fun rememberKey(userId: String, publicKey: String) {
        val now = System.currentTimeMillis()
        // V2-FIX (reviewwhisper.md): commit result checked, failure logged once — a
        // silently dropped trust record would re-trigger MITM warnings on next send.
        withContext(Dispatchers.IO) {
            val ok = prefs.edit()
                .putString("known_$userId", publicKey)
                .putLong("known_ts_$userId", now)
                .commit()
            if (!ok) Log.w(TAG, "rememberKey commit failed for $userId")
        }
    }

    /** Accept a key as known AND verified (fingerprint compared in person). Durable, off-main. */
    suspend fun markVerified(userId: String, publicKey: String) {
        val now = System.currentTimeMillis()
        // V2-FIX (reviewwhisper.md): commit result checked, failure logged once.
        withContext(Dispatchers.IO) {
            val ok = prefs.edit()
                .putString("known_$userId", publicKey)
                .putString("verified_$userId", publicKey)
                .putLong("known_ts_$userId", now)
                .commit()
            if (!ok) Log.w(TAG, "markVerified commit failed for $userId")
        }
    }

    /** Drop all trust records for a user (e.g. when blocking or unfriending). */
    suspend fun forgetUser(userId: String) {
        // known_ts_ is removed too — a stale timestamp could otherwise skew the
        // expected-rotation heuristic if the user is re-added later.
        // M-7 FIX (reviewwhisper.md): commit() is fsync'd — must never run on Main.
        // V2-FIX (reviewwhisper.md): commit result checked, failure logged once.
        withContext(Dispatchers.IO) {
            val ok = prefs.edit().remove("known_$userId").remove("verified_$userId").remove("known_ts_$userId").commit()
            if (!ok) Log.w(TAG, "forgetUser commit failed for $userId")
        }
    }

    /** Wipe every trust record on this device (account deletion). */
    suspend fun clearAll() {
        // M-7 FIX: same as forgetUser — durable write off the calling thread.
        // V2-FIX (reviewwhisper.md): commit result checked, failure logged once.
        withContext(Dispatchers.IO) {
            val ok = prefs.edit().clear().commit()
            if (!ok) Log.w(TAG, "clearAll commit failed")
        }
    }

    /**
     * V3-FIX (H-11): creates the EncryptedSharedPreferences-backed store and performs
     * the one-time legacy plaintext migration. Idempotent — skipped whenever the old
     * file is missing or already empty (e.g. fresh install or migration completed on a
     * previous launch).
     *
     * Ordering guarantee: the copy into ESP is fsync'd via commit() BEFORE the legacy
     * file is cleared + deleted, so a crash in between can lose neither anchor set.
     * If the encrypted-side commit fails, the legacy file is deliberately kept intact
     * for a retry on next launch.
     */
    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encrypted = EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        migrateLegacyPlaintextInto(encrypted)
        return encrypted
    }

    private fun migrateLegacyPlaintextInto(target: SharedPreferences) {
        val legacy = appContext.getSharedPreferences(LEGACY_PREFS_FILE, Context.MODE_PRIVATE)
        val entries = legacy.all
        if (entries.isEmpty()) return
        val editor = target.edit()
        for ((key, value) in entries) {
            when (value) {
                is String -> editor.putString(key, value)
                is Long -> editor.putLong(key, value)
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        val ok = editor.commit()
        if (!ok) {
            Log.w(TAG, "key-trust ESP migration commit failed; legacy file kept for retry")
            return
        }
        // Clear first so the in-memory legacy prefs snapshot cannot rewrite the file
        // behind our back, then remove the plaintext file from disk entirely.
        legacy.edit().clear().commit()
        File(appContext.applicationInfo.dataDir, "shared_prefs/$LEGACY_PREFS_FILE.xml").delete()
        Log.i(TAG, "Migrated ${entries.size} key-trust entries into encrypted prefs")
    }

    private companion object {
        const val TAG = "WhisperKeyTrust"
        // V3-FIX (H-11): new encrypted store file + Keystore master key alias.
        const val PREFS_FILE = "whisper_key_trust_enc"
        const val MASTER_KEY_ALIAS = "whisper_kt_master"

        /** Legacy plaintext file migrated once into [PREFS_FILE]; deleted after the copy. */
        const val LEGACY_PREFS_FILE = "whisper_key_trust"
    }
}
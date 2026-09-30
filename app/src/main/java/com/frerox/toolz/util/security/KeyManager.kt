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

package com.frerox.toolz.util.security

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

object KeyManager {
    private const val TAG = "KeyManager"
    private const val PREFS_NAME = "toolz_vault_prefs"
    private const val KEY_PASSPHRASE = "vault_passphrase"
    private const val HEX_LENGTH = 64

    @Synchronized
    fun getOrCreateMasterKey(context: Context): ByteArray {
        return getOrCreateMasterKeyString(context).toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Fail-closed: if the Keystore/EncryptedSharedPreferences stack is broken,
     * throw instead of returning a hardcoded passphrase shared by every device.
     */
    @Synchronized
    fun getOrCreateMasterKeyString(context: Context): String {
        val sharedPreferences = openPrefs(context)
        val existing = sharedPreferences.getString(KEY_PASSPHRASE, null)
        if (existing != null) {
            require(isValidPassphrase(existing)) { "Stored vault passphrase has invalid format" }
            return existing
        }
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val passphrase = bytes.joinToString("") { "%02x".format(it) }
        val committed = sharedPreferences.edit().putString(KEY_PASSPHRASE, passphrase).commit()
        if (!committed) {
            Log.e(TAG, "Failed to persist vault passphrase")
            throw IllegalStateException("Could not persist vault passphrase")
        }
        return passphrase
    }

    fun restoreMasterKey(context: Context, passphrase: String) {
        require(isValidPassphrase(passphrase.trim())) { "SQLCipher passphrase must be 64 hex chars" }
        val committed = openPrefs(context).edit()
            .putString(KEY_PASSPHRASE, passphrase.trim())
            .commit()
        if (!committed) throw IllegalStateException("Could not restore vault passphrase")
    }

    private fun isValidPassphrase(value: String): Boolean {
        if (value.length != HEX_LENGTH) return false
        return value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    private fun openPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

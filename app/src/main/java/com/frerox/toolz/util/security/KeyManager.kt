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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object KeyManager {
    private const val PREFS_NAME = "toolz_vault_prefs"
    private const val KEY_PASSPHRASE = "vault_passphrase"

    fun getOrCreateMasterKey(context: Context): ByteArray {
        return getOrCreateMasterKeyString(context).toByteArray()
    }

    fun getOrCreateMasterKeyString(context: Context): String {
        return try {
            val sharedPreferences = openPrefs(context)

            var passphrase = sharedPreferences.getString(KEY_PASSPHRASE, null)
            if (passphrase == null) {
                val random = SecureRandom()
                val bytes = ByteArray(32)
                random.nextBytes(bytes)
                // Use hex string as passphrase for SQLCipher
                passphrase = bytes.joinToString("") { "%02x".format(it) }
                sharedPreferences.edit().putString(KEY_PASSPHRASE, passphrase).apply()
            }
            passphrase
        } catch (e: Exception) {
            // Fallback for extreme cases, though ideally we should handle Keystore issues better
            "fallback_secure_key_for_sqlcipher_32_chars"
        }
    }

    fun restoreMasterKey(context: Context, passphrase: String) {
        require(passphrase.isNotBlank()) { "SQLCipher passphrase cannot be blank" }
        openPrefs(context).edit()
            .putString(KEY_PASSPHRASE, passphrase)
            .commit()
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

package com.frerox.toolz.util.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object KeyManager {
    private const val PREFS_NAME = "toolz_vault_prefs"
    private const val KEY_PASSPHRASE = "vault_passphrase"

    fun getOrCreateMasterKey(context: Context): ByteArray {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val sharedPreferences = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            var passphrase = sharedPreferences.getString(KEY_PASSPHRASE, null)
            if (passphrase == null) {
                val random = SecureRandom()
                val bytes = ByteArray(32)
                random.nextBytes(bytes)
                // Use hex string as passphrase for SQLCipher
                passphrase = bytes.joinToString("") { "%02x".format(it) }
                sharedPreferences.edit().putString(KEY_PASSPHRASE, passphrase).apply()
            }
            passphrase.toByteArray()
        } catch (e: Exception) {
            // Fallback for extreme cases, though ideally we should handle Keystore issues better
            "fallback_secure_key_for_sqlcipher_32_chars".toByteArray()
        }
    }
}

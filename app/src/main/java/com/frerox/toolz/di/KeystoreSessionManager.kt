/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.di

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [SessionManager] that encrypts the persisted Supabase session with an
 * Android Keystore-backed AES-GCM key before writing it to disk, so auth
 * tokens are never stored in plaintext.
 */
class KeystoreSessionManager(context: Context) : SessionManager {

    private val prefs = context.getSharedPreferences("whisper_auth_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    override suspend fun saveSession(session: UserSession) {
        val key = getOrCreateKey()
        val plain = json.encodeToString(UserSession.serializer(), session)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // P1 FIX: apply() is async — crash immediately after login loses session. Use commit() on IO.
        with(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(this) {
                prefs.edit()
                    .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    .putString(KEY_SESSION, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .commit()
            }
        }
    }

    override suspend fun loadSession(): UserSession {
        return loadSessionOrNull() ?: throw NoSessionFoundException()
    }

    override suspend fun loadSessionOrNull(): UserSession? {
        val encrypted = prefs.getString(KEY_SESSION, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)))
            val plain = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP))
            json.decodeFromString(UserSession.serializer(), String(plain, Charsets.UTF_8))
        } catch (e: Exception) {
            // Unreadable or tampered session: drop it and behave as signed out.
            prefs.edit().clear().commit()
            null
        }
    }

    override suspend fun deleteSession() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val KEY_ALIAS = "whisper_auth_session_key"
        const val KEY_IV = "session_iv"
        const val KEY_SESSION = "session_ciphertext"
        const val GCM_TAG_BITS = 128
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }
}
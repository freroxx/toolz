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

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperCrypto @Inject constructor() {

    companion object {
        private const val KEY_ALIAS = "whisper_e2ee_ec_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val AES_GCM_TAG_LEN = 128
        private const val IV_LEN = 12
    }

    init {
        ensureKeyPairExists()
    }

    private fun ensureKeyPairExists() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER)
                val parameterSpec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_AGREE_KEY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .build()
                kpg.initialize(parameterSpec)
                kpg.generateKeyPair()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getPublicKeyBase64(): String? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            val publicKey = entry?.certificate?.publicKey ?: return null
            Base64.encodeToString(publicKey.encoded, Base64.DEFAULT)
        } catch (_: Exception) { null }
    }

    private fun getPrivateKey(): PrivateKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            entry?.privateKey
        } catch (_: Exception) { null }
    }

    private fun parsePublicKey(base64PublicKey: String): PublicKey? {
        return try {
            val bytes = Base64.decode(base64PublicKey, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(bytes)
            val keyFactory = KeyFactory.getInstance("EC")
            keyFactory.generatePublic(keySpec)
        } catch (_: Exception) { null }
    }

    private fun deriveSharedKey(recipientPublicKeyBase64: String): SecretKeySpec? {
        val privateKey = getPrivateKey() ?: return null
        val recipientPubKey = parsePublicKey(recipientPublicKeyBase64) ?: return null
        return try {
                        val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(recipientPubKey, true)
            val sharedSecret = keyAgreement.generateSecret()
            
            android.util.Log.d("WhisperCrypto", "Derived shared secret (size: ${sharedSecret.size})")

            // HKDF-Extract: PRK = HMAC-SHA256(salt=ByteArray(32), IKM=sharedSecret)
            val mac = Mac.getInstance("HmacSHA256")
            val salt = ByteArray(32) // 32 bytes of 0x00
            mac.init(SecretKeySpec(salt, "HmacSHA256"))
            val prk = mac.doFinal(sharedSecret)
            
            // HKDF-Expand: OKM = T(1) where T(1) = HMAC-SHA256(PRK, info + 0x01)
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val info = "whisper-e2ee-v1".toByteArray(Charsets.UTF_8)
            val infoWithOne = ByteArray(info.size + 1)
            System.arraycopy(info, 0, infoWithOne, 0, info.size)
            infoWithOne[info.size] = 0x01.toByte()
            val okm = mac.doFinal(infoWithOne) // This is exactly 32 bytes for SHA-256
            
            SecretKeySpec(okm.sliceArray(0 until 16), "AES") // Use first 16 bytes for AES-128
        } catch (e: Exception) { 
            android.util.Log.e("WhisperCrypto", "Error deriving shared key", e)
            null 
        }
    }

    fun encryptMessage(plainText: String, recipientPublicKeyBase64: String): Pair<String, String>? {
        val secretKey = deriveSharedKey(recipientPublicKeyBase64) ?: return null
        return try {
            val iv = ByteArray(IV_LEN).apply { SecureRandom().nextBytes(this) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(AES_GCM_TAG_LEN, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val cipherTextBase64 = Base64.encodeToString(cipherBytes, Base64.DEFAULT)
            val ivBase64 = Base64.encodeToString(iv, Base64.DEFAULT)
            Pair(cipherTextBase64, ivBase64)
        } catch (e: Exception) {
            android.util.Log.e("WhisperCrypto", "Encryption failed", e)
            null
        }
    }

    fun decryptMessage(cipherTextBase64: String, ivBase64: String?, senderPublicKeyBase64: String?): String {
        val failSentinel = "\u26A0\uFE0F Decryption failed"
        if (ivBase64.isNullOrBlank() || senderPublicKeyBase64.isNullOrBlank()) {
            android.util.Log.e("WhisperCrypto", "Decryption failed: missing IV or sender key")
            return failSentinel
        }
        val secretKey = deriveSharedKey(senderPublicKeyBase64) ?: run {
            android.util.Log.e("WhisperCrypto", "Decryption failed: could not derive shared key")
            return failSentinel
        }
        return try {
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)
            val cipherBytes = Base64.decode(cipherTextBase64, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(AES_GCM_TAG_LEN, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("WhisperCrypto", "Decryption failed during cipher op: ${e.message}")
            failSentinel
        }
    }
}

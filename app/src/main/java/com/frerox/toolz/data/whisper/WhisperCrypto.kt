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
import java.security.MessageDigest
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
        private const val MAX_MESSAGE_CHARS = 8_192
        // ImgBB accepts 32 MB base64 input; ciphertext and base64 expansion require headroom.
        private const val MAX_ATTACHMENT_BYTES = WhisperImageCipherTransport.MAX_CIPHER_BYTES
        private val ATTACHMENT_AAD = "whisper-attachment-v1".toByteArray(Charsets.UTF_8)
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
            android.util.Log.e("WhisperCrypto", "Key pair generation failed", e)
        }
    }

    fun getPublicKeyBase64(): String? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            val publicKey = entry?.certificate?.publicKey ?: return null
            Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }

    /**
     * SHA-256 fingerprint of a base64 public key, rendered as 4 groups of 4
     * uppercase hex chars ("A1B2-C3D4-E5F6-7890"). Hashes the trimmed base64
     * string to stay byte-for-byte consistent with the fingerprint shown in
     * the user profile screen.
     */
    fun fingerprint(base64PublicKey: String?): String? {
        if (base64PublicKey.isNullOrBlank()) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(base64PublicKey.trim().toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02X".format(it) }
            hex.chunked(4).take(4).joinToString("-")
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
            val cleanKey = base64PublicKey.trim()
            val bytes = Base64.decode(cleanKey, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(bytes)
            val keyFactory = KeyFactory.getInstance("EC")
            keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            android.util.Log.e("WhisperCrypto", "parsePublicKey failed: ${e.message}")
            null
        }
    }

    private fun deriveSharedKey(recipientPublicKeyBase64: String): SecretKeySpec? {
        val privateKey = getPrivateKey() ?: return null
        val recipientPubKey = parsePublicKey(recipientPublicKeyBase64) ?: return null
        return try {
            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(recipientPubKey, true)
            val sharedSecret = keyAgreement.generateSecret()

            // HKDF-Extract: PRK = HMAC-SHA256(salt=ByteArray(32), IKM=sharedSecret)
            val mac = Mac.getInstance("HmacSHA256")
            val salt = ByteArray(32) // 32 bytes of zeros
            mac.init(SecretKeySpec(salt, "HmacSHA256"))
            val prk = mac.doFinal(sharedSecret)

            // HKDF-Expand: OKM = T(1) where T(1) = HMAC-SHA256(PRK, info + 0x01)
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val info = "whisper-e2ee-v1".toByteArray(Charsets.UTF_8)
            val infoWithOne = ByteArray(info.size + 1)
            System.arraycopy(info, 0, infoWithOne, 0, info.size)
            infoWithOne[info.size] = 0x01.toByte()
            val okm = mac.doFinal(infoWithOne) // 32 bytes for AES-256

            SecretKeySpec(okm, "AES")
        } catch (e: Exception) {
            android.util.Log.e("WhisperCrypto", "Error deriving shared key: ${e.message}")
            null
        }
    }

    fun encryptMessage(plainText: String, recipientPublicKeyBase64: String): Pair<String, String>? {
        if (plainText.length > MAX_MESSAGE_CHARS) return null
        val secretKey = deriveSharedKey(recipientPublicKeyBase64) ?: return null
        return try {
            val iv = ByteArray(IV_LEN).apply { SecureRandom().nextBytes(this) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(AES_GCM_TAG_LEN, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val cipherTextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            Pair(cipherTextBase64, ivBase64)
        } catch (e: Exception) {
            android.util.Log.e("WhisperCrypto", "Encryption failed", e)
            null
        }
    }

    fun decryptMessage(cipherTextBase64: String, ivBase64: String?, senderPublicKeyBase64: String?): String? {
        // If message is unencrypted (no IV) or missing public key, return as-is
        if (ivBase64.isNullOrBlank() || senderPublicKeyBase64.isNullOrBlank()) {
            return cipherTextBase64
        }
        val secretKey = deriveSharedKey(senderPublicKeyBase64) ?: return null
        return try {
            val iv = Base64.decode(ivBase64.trim(), Base64.DEFAULT)
            val cipherBytes = Base64.decode(cipherTextBase64.trim(), Base64.DEFAULT)
            if (iv.size != IV_LEN || cipherBytes.size < 16) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(AES_GCM_TAG_LEN, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("WhisperCrypto", "Decryption failed: ${e.message}")
            null
        }
    }

    /** Encrypts binary attachments using the same peer key as messages. The returned IV is stored alongside the blob. */
    fun encryptAttachment(bytes: ByteArray, recipientPublicKeyBase64: String): Pair<ByteArray, String>? {
        if (bytes.isEmpty() || bytes.size > MAX_ATTACHMENT_BYTES) return null
        val secretKey = deriveSharedKey(recipientPublicKeyBase64) ?: return null
        return try {
            val iv = ByteArray(IV_LEN).apply { SecureRandom().nextBytes(this) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(AES_GCM_TAG_LEN, iv))
            cipher.updateAAD(ATTACHMENT_AAD)
            cipher.doFinal(bytes) to Base64.encodeToString(iv, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("WhisperCrypto", "Attachment encryption failed", e)
            null
        }
    }

    fun decryptAttachment(cipherBytes: ByteArray, ivBase64: String?, senderPublicKeyBase64: String?): ByteArray? {
        if (cipherBytes.size < 16 || ivBase64.isNullOrBlank() || senderPublicKeyBase64.isNullOrBlank()) return null
        val secretKey = deriveSharedKey(senderPublicKeyBase64) ?: return null
        return try {
            val iv = Base64.decode(ivBase64.trim(), Base64.DEFAULT)
            if (iv.size != IV_LEN) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(AES_GCM_TAG_LEN, iv))
            cipher.updateAAD(ATTACHMENT_AAD)
            cipher.doFinal(cipherBytes)
        } catch (e: Exception) {
            android.util.Log.w("WhisperCrypto", "Attachment decryption failed: ${e.message}")
            null
        }
    }

    fun isCurrentPublicKey(publicKeyBase64: String?): Boolean =
        !publicKeyBase64.isNullOrBlank() && publicKeyBase64 == getPublicKeyBase64()
}

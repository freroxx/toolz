package com.frerox.toolz.util

import android.util.Base64
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class CryptoFormat {
    BASE64, HEX, BINARY, PLAINTEXT
}

enum class CryptoAlgorithm {
    AES, BASE64, HEX, BINARY, ROT13
}

object CryptoManager {
    private const val AES_KEY_SIZE = 256
    private const val PBKDF2_ITERATIONS = 65536
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 128

    private val base64Regex = Regex("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")
    private val hexRegex = Regex("^[0-9A-Fa-f]+$")
    private val binaryRegex = Regex("^[01\\s]+$")

    fun detectFormat(input: String): CryptoFormat {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return CryptoFormat.PLAINTEXT
        
        return when {
            binaryRegex.matches(trimmed) && trimmed.length >= 8 -> CryptoFormat.BINARY
            hexRegex.matches(trimmed) && trimmed.length % 2 == 0 -> CryptoFormat.HEX
            base64Regex.matches(trimmed) && trimmed.length >= 4 -> CryptoFormat.BASE64
            else -> CryptoFormat.PLAINTEXT
        }
    }

    /**
     * AES-256-GCM Encryption
     * Returns: salt + iv + ciphertext as Base64 string
     */
    fun encryptAes(plaintext: String, password: CharArray): Result<String> {
        return try {
            val salt = ByteArray(SALT_SIZE).apply { SecureRandom().nextBytes(this) }
            val iv = ByteArray(IV_SIZE).apply { SecureRandom().nextBytes(this) }
            
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
            
            val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.doFinal(plaintextBytes)
            
            // Clear sensitive data
            plaintextBytes.fill(0)
            
            val combined = salt + iv + ciphertext
            Result.success(Base64.encodeToString(combined, Base64.NO_WRAP))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * AES-256-GCM Decryption
     * Expects: salt + iv + ciphertext as Base64 string
     */
    fun decryptAes(combinedBase64: String, password: CharArray): Result<String> {
        return try {
            val combined = Base64.decode(combinedBase64, Base64.NO_WRAP)
            if (combined.size < SALT_SIZE + IV_SIZE) {
                return Result.failure(IllegalArgumentException("Malformed encrypted data"))
            }
            
            val salt = combined.sliceArray(0 until SALT_SIZE)
            val iv = combined.sliceArray(SALT_SIZE until SALT_SIZE + IV_SIZE)
            val ciphertext = combined.sliceArray(SALT_SIZE + IV_SIZE until combined.size)
            
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
            
            val decryptedBytes = cipher.doFinal(ciphertext)
            val result = String(decryptedBytes, Charsets.UTF_8)
            
            // Clear sensitive data
            decryptedBytes.fill(0)
            
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, AES_KEY_SIZE)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    // Encoding Utilities

    fun encodeBase64(input: String): String {
        return Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun decodeBase64(input: String): Result<String> {
        return try {
            val decoded = Base64.decode(input, Base64.NO_WRAP)
            Result.success(String(decoded, Charsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun encodeHex(input: String): String {
        return input.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
    }

    fun decodeHex(input: String): Result<String> {
        return try {
            val cleaned = input.filter { !it.isWhitespace() }
            val bytes = ByteArray(cleaned.length / 2)
            for (i in 0 until cleaned.length step 2) {
                bytes[i / 2] = cleaned.substring(i, i + 2).toInt(16).toByte()
            }
            Result.success(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun encodeBinary(input: String): String {
        return input.toByteArray(Charsets.UTF_8).joinToString(" ") { 
            val b = it.toInt() and 0xFF
            Integer.toBinaryString(b).padStart(8, '0')
        }
    }

    fun decodeBinary(input: String): Result<String> {
        return try {
            val parts = input.trim().split(Regex("\\s+"))
            val bytes = ByteArray(parts.size)
            for (i in parts.indices) {
                bytes[i] = parts[i].toInt(2).toByte()
            }
            Result.success(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun applyRot13(input: String): String {
        return input.map { char ->
            when (char) {
                in 'a'..'m' -> char + 13
                in 'n'..'z' -> char - 13
                in 'A'..'M' -> char + 13
                in 'N'..'Z' -> char - 13
                else -> char
            }
        }.joinToString("")
    }
}

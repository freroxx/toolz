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

package com.frerox.toolz.util

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.io.InputStream
import java.io.OutputStream
import java.io.FilterInputStream
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.AEADBadTagException

object CryptoManager {
    private const val AES_KEY_SIZE = 256
    private const val CHACHA20_KEY_SIZE = 256
    private const val PBKDF2_ITERATIONS = 65536
    // V2-FIX B1: OWASP-recommended PBKDF2-HMAC-SHA256 iteration count, used ONLY by the
    // dedicated encryptWithPassphrase/decryptWithPassphrase helpers (Whisper access
    // files). The shared PBKDF2_ITERATIONS stays frozen at 65536 because SmartEncrypter
    // users hold stored ciphertext derived with it; raising the constant in place would
    // permanently break decryption of that data.
    private const val PBKDF2_ITERATIONS_HARDENED = 310000
    // First byte of hardened-format payloads ('V'). Legacy payloads start with random
    // salt bytes, so a collision is handled by the legacy fallback in decryptWithPassphrase.
    private const val HARDENED_FORMAT_MARKER: Byte = 0x56
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 128
    private const val NONCE_SIZE = 12

    private val base64Regex = Regex("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")
    private val hexRegex = Regex("^[0-9A-Fa-f]+$")
    private val binaryRegex = Regex("^[01\\s]+$")

    enum class CryptoFormat {
        BASE64, HEX, BINARY, PLAINTEXT
    }

    enum class CryptoOperation {
        ENCRYPT, DECRYPT, HASH, ENCODE, DECODE
    }

    enum class CryptoAlgorithm {
        AES, CHACHA20, BASE64, HEX, BINARY, ROT13, MD5, SHA1, SHA256, SHA512, URL, MORSE, BASE32
    }

    fun detectFormat(input: String): CryptoFormat {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return CryptoFormat.PLAINTEXT

        return when {
            trimmed.startsWith("....") || trimmed.contains(" / ") || trimmed.contains(" -") -> CryptoFormat.PLAINTEXT // Likely Morse
            binaryRegex.matches(trimmed) && trimmed.length >= 8 -> CryptoFormat.BINARY
            hexRegex.matches(trimmed) && trimmed.length % 2 == 0 -> CryptoFormat.HEX
            base64Regex.matches(trimmed) && trimmed.length >= 4 -> {
                try {
                    Base64.decode(trimmed, Base64.NO_WRAP)
                    CryptoFormat.BASE64
                } catch (e: Exception) {
                    CryptoFormat.PLAINTEXT
                }
            }
            else -> CryptoFormat.PLAINTEXT
        }
    }

    fun suggestOperation(input: String, algorithm: CryptoAlgorithm): CryptoOperation {
        if (input.isBlank()) return CryptoOperation.ENCRYPT
        
        return when (algorithm) {
            CryptoAlgorithm.AES, CryptoAlgorithm.CHACHA20 -> {
                val format = detectFormat(input)
                if (format == CryptoFormat.BASE64) CryptoOperation.DECRYPT else CryptoOperation.ENCRYPT
            }
            CryptoAlgorithm.BASE64, CryptoAlgorithm.HEX, CryptoAlgorithm.BINARY, CryptoAlgorithm.URL, CryptoAlgorithm.MORSE, CryptoAlgorithm.BASE32 -> {
                val format = detectFormat(input)
                val isAlgorithmFormat = when (algorithm) {
                    CryptoAlgorithm.BASE64 -> format == CryptoFormat.BASE64
                    CryptoAlgorithm.HEX -> format == CryptoFormat.HEX
                    CryptoAlgorithm.BINARY -> format == CryptoFormat.BINARY
                    CryptoAlgorithm.MORSE -> input.trim().let { it.contains(".") || it.contains("-") }
                    CryptoAlgorithm.URL -> input.contains("%")
                    else -> false
                }
                if (isAlgorithmFormat) CryptoOperation.DECODE else CryptoOperation.ENCODE
            }
            CryptoAlgorithm.ROT13 -> CryptoOperation.ENCODE
            else -> CryptoOperation.HASH
        }
    }

    /**
     * AES-256-GCM Encryption
     * Returns: salt + iv + ciphertext as Base64 string
     */
    fun encryptAes(plaintext: String, password: CharArray): Pair<String, Boolean> {
        return try {
            val salt = ByteArray(SALT_SIZE).apply { SecureRandom().nextBytes(this) }
            val iv = ByteArray(IV_SIZE).apply { SecureRandom().nextBytes(this) }

            val key = deriveKey(password, salt, AES_KEY_SIZE)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))

            val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.doFinal(plaintextBytes)

            // Clear sensitive data
            plaintextBytes.fill(0.toByte())

            val combined = salt + iv + ciphertext
            Pair(Base64.encodeToString(combined, Base64.NO_WRAP), true)
        } catch (e: Exception) {
            Pair("Encryption failed: ${e.message}", false)
        }
    }

    /**
     * AES-256-GCM Decryption
     * Expects: salt + iv + ciphertext as Base64 string
     *
     * V2-FIX B1: format frozen (65536 iterations) for backward compatibility with
     * previously stored data; see [decryptWithPassphrase] for the hardened path.
     */
    fun decryptAes(combinedBase64: String, password: CharArray): Pair<String, Boolean> {
        return try {
            decodeSaltedLayout(Base64.decode(combinedBase64, Base64.NO_WRAP), password, PBKDF2_ITERATIONS)
        } catch (e: AEADBadTagException) {
            Pair("Invalid password or corrupted data", false)
        } catch (e: Exception) {
            Pair("Decryption failed: ${e.message}", false)
        }
    }

    // ── Hardened passphrase helpers (V2-FIX B1) ─────────────────────────────────────
    // The Whisper access .enc file protects a live credential behind a 6-digit code, so
    // it uses a dedicated KDF-hardened path WITHOUT changing encryptAes/decryptAes,
    // whose on-disk format must stay stable for SmartEncrypter-stored data.

    /**
     * AES-256-GCM encryption hardened for low-entropy passphrases:
     * PBKDF2-HMAC-SHA256 at 310000 iterations.
     * Output: marker byte 'V' + salt(16) + iv(12) + ciphertext+tag, Base64(NO_WRAP).
     */
    fun encryptWithPassphrase(plaintext: String, passphrase: CharArray): Pair<String, Boolean> {
        return try {
            val salt = ByteArray(SALT_SIZE).apply { SecureRandom().nextBytes(this) }
            val iv = ByteArray(IV_SIZE).apply { SecureRandom().nextBytes(this) }

            val key = deriveKey(passphrase, salt, AES_KEY_SIZE, PBKDF2_ITERATIONS_HARDENED)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))

            val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.doFinal(plaintextBytes)

            // Clear sensitive data
            plaintextBytes.fill(0.toByte())

            val combined = byteArrayOf(HARDENED_FORMAT_MARKER) + salt + iv + ciphertext
            Pair(Base64.encodeToString(combined, Base64.NO_WRAP), true)
        } catch (e: Exception) {
            Pair("Encryption failed: ${e.message}", false)
        }
    }

    /**
     * Decrypts [encryptWithPassphrase] output. Tries the marker-prefixed hardened
     * (310000-iteration) format first, then falls back to the legacy unmarked
     * 65536-iteration salt|iv|ciphertext layout so old .enc access files keep working;
     * this also covers a legacy file whose first random salt byte happens to collide
     * with the marker byte. Error semantics match [decryptAes].
     */
    fun decryptWithPassphrase(combinedBase64: String, passphrase: CharArray): Pair<String, Boolean> {
        // Be tolerant to whitespace/line-breaks: files may be transferred via email/cloud that wraps Base64 at 76 chars
        val cleaned = combinedBase64.trim().replace(Regex("\\s"), "")
        if (cleaned.isEmpty()) return Pair("Malformed encrypted data", false)
        val combined = try {
            // DEFAULT is whitespace-tolerant; NO_WRAP is strict — try lenient first
            try {
                Base64.decode(cleaned, Base64.DEFAULT)
            } catch (_: Exception) {
                Base64.decode(cleaned, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            return Pair("Malformed encrypted data", false)
        }
        if (combined.isNotEmpty() && combined[0] == HARDENED_FORMAT_MARKER) {
            val hardened = decodeSaltedLayout(
                combined.copyOfRange(1, combined.size),
                passphrase,
                PBKDF2_ITERATIONS_HARDENED,
            )
            if (hardened.second) return hardened
        }
        return decodeSaltedLayout(combined, passphrase, PBKDF2_ITERATIONS)
    }

    /**
     * Shared salt|iv|ciphertext layout decoder parameterized by iteration count so the
     * legacy (65536) and hardened (310000) formats share one implementation.
     */
    private fun decodeSaltedLayout(payload: ByteArray, password: CharArray, iterations: Int): Pair<String, Boolean> {
        return try {
            if (payload.size < SALT_SIZE + IV_SIZE) {
                return Pair("Malformed encrypted data", false)
            }

            val salt = payload.sliceArray(0 until SALT_SIZE)
            val iv = payload.sliceArray(SALT_SIZE until SALT_SIZE + IV_SIZE)
            val ciphertext = payload.sliceArray(SALT_SIZE + IV_SIZE until payload.size)

            val key = deriveKey(password, salt, AES_KEY_SIZE, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))

            val decryptedBytes = cipher.doFinal(ciphertext)
            val result = String(decryptedBytes, Charsets.UTF_8)

            // Clear sensitive data
            decryptedBytes.fill(0.toByte())

            Pair(result, true)
        } catch (e: AEADBadTagException) {
            Pair("Invalid password or corrupted data", false)
        } catch (e: Exception) {
            Pair("Decryption failed: ${e.message}", false)
        }
    }

    /**
     * ChaCha20-Poly1305 Encryption
     * Returns: salt + nonce + ciphertext as Base64 string
     */
    fun encryptChaCha20(plaintext: String, password: CharArray): Pair<String, Boolean> {
        return try {
            val salt = ByteArray(SALT_SIZE).apply { SecureRandom().nextBytes(this) }
            val nonce = ByteArray(NONCE_SIZE).apply { SecureRandom().nextBytes(this) }

            val key = deriveKey(password, salt, CHACHA20_KEY_SIZE)
            val cipher = Cipher.getInstance("ChaCha20-Poly1305/None/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(nonce))

            val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.doFinal(plaintextBytes)

            // Clear sensitive data
            plaintextBytes.fill(0.toByte())

            val combined = salt + nonce + ciphertext
            Pair(Base64.encodeToString(combined, Base64.NO_WRAP), true)
        } catch (e: Exception) {
            Pair("Encryption failed: ${e.message}", false)
        }
    }

    /**
     * ChaCha20-Poly1305 Decryption
     * Expects: salt + nonce + ciphertext as Base64 string
     */
    fun decryptChaCha20(combinedBase64: String, password: CharArray): Pair<String, Boolean> {
        return try {
            val combined = Base64.decode(combinedBase64, Base64.NO_WRAP)
            if (combined.size < SALT_SIZE + NONCE_SIZE) {
                return Pair("Malformed encrypted data", false)
            }

            val salt = combined.sliceArray(0 until SALT_SIZE)
            val nonce = combined.sliceArray(SALT_SIZE until SALT_SIZE + NONCE_SIZE)
            val ciphertext = combined.sliceArray(SALT_SIZE + NONCE_SIZE until combined.size)

            val key = deriveKey(password, salt, CHACHA20_KEY_SIZE)
            val cipher = Cipher.getInstance("ChaCha20-Poly1305/None/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(nonce))

            val decryptedBytes = cipher.doFinal(ciphertext)
            val result = String(decryptedBytes, Charsets.UTF_8)

            // Clear sensitive data
            decryptedBytes.fill(0.toByte())

            Pair(result, true)
        } catch (e: AEADBadTagException) {
            Pair("Invalid password or corrupted data", false)
        } catch (e: Exception) {
            Pair("Decryption failed: ${e.message}", false)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, keySize: Int): SecretKeySpec =
        // Legacy default (65536 iterations) preserved for all pre-B1 callers.
        deriveKey(password, salt, keySize, PBKDF2_ITERATIONS)

    private fun deriveKey(password: CharArray, salt: ByteArray, keySize: Int, iterations: Int): SecretKeySpec {
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec: KeySpec = PBEKeySpec(password, salt, iterations, keySize)
            val tmp = factory.generateSecret(spec)
            SecretKeySpec(tmp.encoded, "AES")
        } catch (e: Exception) {
            // Fallback for demonstration - NOT secure for production
            val keyBytes = ByteArray(keySize / 8) { 0.toByte() }
            for (i in keyBytes.indices) {
                keyBytes[i] = (password[i % password.size].toInt() and 0xFF).toByte()
            }
            SecretKeySpec(keyBytes, "AES")
        }
    }

    // Hash Functions
    fun hashMd5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: NoSuchAlgorithmException) {
            "MD5 not available"
        }
    }

    fun hashSha1(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: NoSuchAlgorithmException) {
            "SHA-1 not available"
        }
    }

    fun hashSha256(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: NoSuchAlgorithmException) {
            "SHA-256 not available"
        }
    }

    fun hashSha512(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-512")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: NoSuchAlgorithmException) {
            "SHA-512 not available"
        }
    }

    // Encoding Utilities

    fun encodeBase64(input: String): String {
        return Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun decodeBase64(input: String): Pair<String, Boolean> {
        return try {
            val decoded = Base64.decode(input, Base64.NO_WRAP)
            Pair(String(decoded, Charsets.UTF_8), true)
        } catch (e: Exception) {
            Pair("Decoding failed: ${e.message}", false)
        }
    }

    fun encodeHex(input: String): String {
        return input.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
    }

    fun decodeHex(input: String): Pair<String, Boolean> {
        return try {
            val cleaned = input.filter { !it.isWhitespace() }
            val bytes = ByteArray(cleaned.length / 2)
            for (i in 0 until cleaned.length step 2) {
                bytes[i / 2] = cleaned.substring(i, i + 2).toInt(16).toByte()
            }
            Pair(String(bytes, Charsets.UTF_8), true)
        } catch (e: Exception) {
            Pair("Decoding failed: ${e.message}", false)
        }
    }

    fun encodeBinary(input: String): String {
        return input.toByteArray(Charsets.UTF_8).joinToString(" ") {
            val b = it.toInt() and 0xFF
            Integer.toBinaryString(b).padStart(8, '0')
        }
    }

    fun decodeBinary(input: String): Pair<String, Boolean> {
        return try {
            val parts = input.trim().split(Regex("\\s+"))
            val bytes = ByteArray(parts.size)
            for (i in parts.indices) {
                bytes[i] = parts[i].toInt(2).toByte()
            }
            Pair(String(bytes, Charsets.UTF_8), true)
        } catch (e: Exception) {
            Pair("Decoding failed: ${e.message}", false)
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

    // New Algorithms

    fun encodeUrl(input: String): String {
        return java.net.URLEncoder.encode(input, "UTF-8")
    }

    fun decodeUrl(input: String): Pair<String, Boolean> {
        return try {
            Pair(java.net.URLDecoder.decode(input, "UTF-8"), true)
        } catch (e: Exception) {
            Pair("URL Decoding failed: ${e.message}", false)
        }
    }

    private val morseMap = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.",
        'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
        'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.",
        'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
        'Y' to "-.--", 'Z' to "--..", '1' to ".----", '2' to "..---", '3' to "...--",
        '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..",
        '9' to "----.", '0' to "-----", ' ' to "/"
    )

    private val reverseMorseMap = morseMap.entries.associateBy({ it.value }, { it.key })

    fun encodeMorse(input: String): String {
        return input.uppercase().map { morseMap[it] ?: "?" }.joinToString(" ")
    }

    fun decodeMorse(input: String): Pair<String, Boolean> {
        return try {
            val result = input.split(" ").map { reverseMorseMap[it] ?: '?' }.joinToString("")
            Pair(result, true)
        } catch (e: Exception) {
            Pair("Morse Decoding failed: ${e.message}", false)
        }
    }

    fun encodeBase32(input: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val bytes = input.toByteArray(Charsets.UTF_8)
        var i = 0
        var index = 0
        var digit = 0
        var currByte: Int
        var nextByte: Int
        val base32 = StringBuilder((bytes.size + 7) * 8 / 5)

        while (i < bytes.size) {
            currByte = if (bytes[i] >= 0) bytes[i].toInt() else bytes[i].toInt() + 256
            if (index > 3) {
                if (i + 1 < bytes.size) {
                    nextByte = if (bytes[i + 1] >= 0) bytes[i + 1].toInt() else bytes[i + 1].toInt() + 256
                } else {
                    nextByte = 0
                }
                digit = currByte and (0xFF shr index)
                index = (index + 5) % 8
                digit = (digit shl index) or (nextByte shr (8 - index))
                i++
            } else {
                digit = (currByte shr (8 - (index + 5))) and 0x1F
                index = (index + 5) % 8
                if (index == 0) i++
            }
            base32.append(alphabet[digit])
        }
        return base32.toString()
    }

    fun decodeBase32(input: String): Pair<String, Boolean> {
        return try {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            val map = alphabet.withIndex().associate { it.value to it.index }
            val cleaned = input.uppercase().filter { it in alphabet }
            val out = ByteArray(cleaned.length * 5 / 8)
            var buffer = 0
            var bitsLeft = 0
            var count = 0
            for (c in cleaned) {
                buffer = (buffer shl 5) or (map[c] ?: 0)
                bitsLeft += 5
                if (bitsLeft >= 8) {
                    out[count++] = (buffer shr (bitsLeft - 8)).toByte()
                    bitsLeft -= 8
                }
            }
            Pair(String(out, Charsets.UTF_8), true)
        } catch (e: Exception) {
            Pair("Base32 Decoding failed: ${e.message}", false)
        }
    }

    fun calculatePasswordStrength(password: String): Float {
        if (password.isEmpty()) return 0f
        var score = 0f
        if (password.length >= 8) score += 0.2f
        if (password.length >= 12) score += 0.2f
        if (password.any { it.isDigit() }) score += 0.2f
        if (password.any { it.isUpperCase() }) score += 0.2f
        if (password.any { !it.isLetterOrDigit() }) score += 0.2f
        return score
    }

    /**
     * Streaming AES-256-GCM Encryption
     */
    /**
     * Streaming AES-256-GCM Encryption
     */
    fun encryptStreamAes(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        totalSize: Long,
        onProgress: (Float) -> Unit
    ): Boolean {
        return try {
            onProgress(0.01f)
            val salt = ByteArray(SALT_SIZE).apply { SecureRandom().nextBytes(this) }
            val iv = ByteArray(IV_SIZE).apply { SecureRandom().nextBytes(this) }
            
            output.write(salt)
            output.write(iv)

            val key = deriveKey(password, salt, AES_KEY_SIZE)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))

            val countingInput = CountingInputStream(input)
            CipherOutputStream(output, cipher).use { cipherOutputStream ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int

                while (countingInput.read(buffer).also { bytesRead = it } != -1) {
                    cipherOutputStream.write(buffer, 0, bytesRead)
                    if (totalSize > 0) {
                        onProgress(0.01f + (countingInput.bytesRead.toFloat() / totalSize) * 0.98f)
                    }
                }
                cipherOutputStream.flush()
            }
            onProgress(1f)
            true
        } catch (e: AEADBadTagException) {
            throw Exception("AUTH_FAILURE")
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Streaming AES-256-GCM Decryption
     */
    fun decryptStreamAes(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        totalSize: Long,
        onProgress: (Float) -> Unit
    ): Boolean {
        var cipherInputStream: CipherInputStream? = null
        return try {
            onProgress(0.01f)
            val salt = ByteArray(SALT_SIZE)
            val iv = ByteArray(IV_SIZE)
            
            val countingInput = CountingInputStream(input)
            if (countingInput.read(salt) != SALT_SIZE || countingInput.read(iv) != IV_SIZE) {
                return false
            }

            val key = deriveKey(password, salt, AES_KEY_SIZE)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))

            cipherInputStream = CipherInputStream(countingInput, cipher)
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int

            while (true) {
                try {
                    bytesRead = cipherInputStream.read(buffer)
                    if (bytesRead == -1) break
                    output.write(buffer, 0, bytesRead)
                    if (totalSize > 0) {
                        // Use countingInput.bytesRead for more accurate progress of the encrypted stream
                        onProgress((countingInput.bytesRead.toFloat() / totalSize).coerceIn(0.01f, 0.99f))
                    }
                } catch (e: Exception) {
                    if (e is AEADBadTagException || e.cause is AEADBadTagException) {
                        throw Exception("AUTH_FAILURE")
                    }
                    throw e
                }
            }
            
            // Closing might throw AEADBadTagException if it hasn't been thrown yet
            try {
                cipherInputStream.close()
            } catch (e: Exception) {
                if (e is AEADBadTagException || e.cause is AEADBadTagException) {
                    throw Exception("AUTH_FAILURE")
                }
                throw e
            }
            
            onProgress(1f)
            true
        } catch (e: Exception) {
            if (e.message == "AUTH_FAILURE") throw e
            e.printStackTrace()
            false
        }
    }

    /**
     * Streaming ChaCha20-Poly1305 Encryption
     */
    fun encryptStreamChaCha20(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        totalSize: Long,
        onProgress: (Float) -> Unit
    ): Boolean {
        return try {
            onProgress(0.01f)
            val salt = ByteArray(SALT_SIZE).apply { SecureRandom().nextBytes(this) }
            val nonce = ByteArray(NONCE_SIZE).apply { SecureRandom().nextBytes(this) }
            
            output.write(salt)
            output.write(nonce)

            val key = deriveKey(password, salt, CHACHA20_KEY_SIZE)
            val cipher = Cipher.getInstance("ChaCha20-Poly1305/None/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(nonce))

            val countingInput = CountingInputStream(input)
            CipherOutputStream(output, cipher).use { cipherOutputStream ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int

                while (countingInput.read(buffer).also { bytesRead = it } != -1) {
                    cipherOutputStream.write(buffer, 0, bytesRead)
                    if (totalSize > 0) {
                        onProgress(0.01f + (countingInput.bytesRead.toFloat() / totalSize) * 0.98f)
                    }
                }
                cipherOutputStream.flush()
            }
            onProgress(1f)
            true
        } catch (e: AEADBadTagException) {
            throw Exception("AUTH_FAILURE")
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Streaming ChaCha20-Poly1305 Decryption
     */
    fun decryptStreamChaCha20(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        totalSize: Long,
        onProgress: (Float) -> Unit
    ): Boolean {
        var cipherInputStream: CipherInputStream? = null
        return try {
            onProgress(0.01f)
            val salt = ByteArray(SALT_SIZE)
            val nonce = ByteArray(NONCE_SIZE)
            
            val countingInput = CountingInputStream(input)
            if (countingInput.read(salt) != SALT_SIZE || countingInput.read(nonce) != NONCE_SIZE) {
                return false
            }

            val key = deriveKey(password, salt, CHACHA20_KEY_SIZE)
            val cipher = Cipher.getInstance("ChaCha20-Poly1305/None/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(nonce))

            cipherInputStream = CipherInputStream(countingInput, cipher)
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int

            while (true) {
                try {
                    bytesRead = cipherInputStream.read(buffer)
                    if (bytesRead == -1) break
                    output.write(buffer, 0, bytesRead)
                    if (totalSize > 0) {
                        onProgress((countingInput.bytesRead.toFloat() / totalSize).coerceIn(0.01f, 0.99f))
                    }
                } catch (e: Exception) {
                    if (e is AEADBadTagException || e.cause is AEADBadTagException) {
                        throw Exception("AUTH_FAILURE")
                    }
                    throw e
                }
            }

            try {
                cipherInputStream.close()
            } catch (e: Exception) {
                if (e is AEADBadTagException || e.cause is AEADBadTagException) {
                    throw Exception("AUTH_FAILURE")
                }
                throw e
            }

            onProgress(1f)
            true
        } catch (e: Exception) {
            if (e.message == "AUTH_FAILURE") throw e
            e.printStackTrace()
            false
        }
    }

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var bytesRead = 0L
            private set

        override fun read(): Int {
            val byte = super.read()
            if (byte != -1) bytesRead++
            return byte
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = super.read(b, off, len)
            if (read != -1) bytesRead += read
            return read
        }
    }

    fun generateQrCode(
        text: String,
        size: Int = 512,
        foregroundColor: Int = AndroidColor.BLACK,
        backgroundColor: Int = AndroidColor.WHITE,
        dotStyle: String = "SQUARE", // SQUARE, ROUNDED
        noteText: String? = null,
        noteSize: Float = 16f,
        notePosition: String = "BOTTOM" // TOP, BOTTOM
    ): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val hints = mutableMapOf<com.google.zxing.EncodeHintType, Any>()
            hints[com.google.zxing.EncodeHintType.MARGIN] = 1
            hints[com.google.zxing.EncodeHintType.CHARACTER_SET] = "UTF-8"
            
            // Optimization: Generate a minimal bit matrix and scale it during drawing
            // Use a fixed small size for calculation if possible, or just the necessary modules
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
            
            val modulesCount = bitMatrix.width
            val moduleSize = size.toFloat() / modulesCount

            // Calculate extra space for note
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = foregroundColor
                textSize = noteSize * 2
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            var extraHeight = 0
            val margin = 40
            val noteSpacing = 20
            
            val hasNote = !noteText.isNullOrBlank()
            if (hasNote) {
                val bounds = android.graphics.Rect()
                paint.getTextBounds(noteText!!, 0, noteText.length, bounds)
                extraHeight = bounds.height() + margin + noteSpacing
            }

            val totalHeight = size + extraHeight
            val bitmap = Bitmap.createBitmap(size, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(backgroundColor)

            val qrTop = if (hasNote && notePosition == "TOP") extraHeight else 0
            
            val isRounded = dotStyle == "ROUNDED"
            val dotPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = foregroundColor
                style = android.graphics.Paint.Style.FILL
            }

            // Draw QR modules - iterating over modules instead of pixels
            for (x in 0 until modulesCount) {
                for (y in 0 until modulesCount) {
                    if (bitMatrix.get(x, y)) {
                        val left = x * moduleSize
                        val top = qrTop + y * moduleSize
                        val right = (x + 1) * moduleSize
                        val bottom = qrTop + (y + 1) * moduleSize

                        if (isRounded) {
                            canvas.drawRoundRect(
                                left + 1f,
                                top + 1f,
                                right - 1f,
                                bottom - 1f,
                                moduleSize / 2.5f, moduleSize / 2.5f,
                                dotPaint
                            )
                        } else {
                            canvas.drawRect(left, top, right, bottom, dotPaint)
                        }
                    }
                }
            }

            if (hasNote) {
                val noteY = if (notePosition == "TOP") {
                    (extraHeight - noteSpacing).toFloat()
                } else {
                    (size + margin).toFloat()
                }
                canvas.drawText(noteText!!, (size / 2).toFloat(), noteY, paint)
            }

            bitmap
        } catch (e: Exception) {
            null
        }
    }
}


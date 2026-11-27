/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.util.secure

import com.frerox.toolz.util.CryptoManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Versioned, self-describing authenticated containers used by Whisper access
 * files and password-protected Toolz backups.
 *
 * Two physical shapes share the same cryptography:
 *
 *  - **Text** (`TOOLZ-AUBUP/2` + one JSON envelope). Small payloads that must
 *    survive e-mail/cloud transfer, so the bytes stay Base64 text. Legacy
 *    access files (raw Base64 with no header) keep using the old path.
 *  - **Binary** (`TOOLZ-BACKUP/2` + fixed header + AES-GCM stream). Used for
 *    archives that can be hundreds of megabytes; the plaintext never has to be
 *    buffered in memory.
 *
 * Both use PBKDF2-HMAC-SHA256 with a recorded work factor and AES-256-GCM.
 * The work factor is validated on read so a tampered file cannot force an
 * unbounded derivation, and it is *never* silently downgraded.
 */
object SecureContainer {

    /** Text envelope header. The trailing version is part of the format. */
    const val AUBUP_MAGIC = "TOOLZ-AUBUP/2"

    /** Binary envelope magic, exactly [BINARY_MAGIC_SIZE] bytes. */
    private val BINARY_MAGIC = "TOOLZ-BACKUP/2\n".toByteArray(Charsets.US_ASCII)
    const val BINARY_MAGIC_SIZE = 15

    const val KIND_AUBUP = "aubup"
    const val KIND_BACKUP = "backup"

    const val DEFAULT_ITERATIONS = CryptoManager.PBKDF2_ITERATIONS_CONTAINER

    /** Refuse to parse a text envelope larger than this before decrypting. */
    const val MAX_TEXT_BYTES = 2 * 1024 * 1024
    private const val MAX_SECRET_LENGTH = 256
    private const val MIN_PASSWORD_LENGTH = 8
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    private const val BINARY_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    enum class SecretMode {
        /** Six digits. Kept for compatibility with existing access-file UX. */
        PIN,

        /** At least eight characters, any alphabet. */
        PASSWORD,
    }

    enum class SecretWarning {
        NONE,
        /** A six-digit code is only 1,000,000 guesses; offline cracking is cheap. */
        WEAK_PIN,
        /** Accepted, but a longer passphrase is materially stronger. */
        SHORT_PASSWORD,
        /** All-digit passwords have a much smaller search space. */
        NUMERIC_PASSWORD,
    }

    data class Assessment(
        val mode: SecretMode,
        val valid: Boolean,
        val warning: SecretWarning,
    )

    /** Thrown when a container is well-formed but the secret is wrong. */
    class WrongSecretException : IllegalArgumentException("Incorrect secret or corrupted container")

    /** Thrown when a container is structurally invalid or truncated. */
    class MalformedContainerException(message: String) : IllegalArgumentException(message)

    @Serializable
    private data class TextEnvelope(
        @SerialName("kind") val kind: String,
        @SerialName("iter") val iterations: Int = DEFAULT_ITERATIONS,
        @SerialName("ct") val ciphertext: String,
    )

    // ── Secret policy ───────────────────────────────────────────────────────

    fun assessSecret(secret: String, mode: SecretMode): Assessment {
        val valid = when (mode) {
            SecretMode.PIN -> secret.length == 6 && secret.all { it in '0'..'9' }
            SecretMode.PASSWORD -> secret.length in MIN_PASSWORD_LENGTH..MAX_SECRET_LENGTH &&
                secret.isNotBlank()
        }
        val warning = when {
            !valid -> SecretWarning.NONE
            mode == SecretMode.PIN -> SecretWarning.WEAK_PIN
            secret.all { it.isDigit() } -> SecretWarning.NUMERIC_PASSWORD
            secret.length < 12 -> SecretWarning.SHORT_PASSWORD
            else -> SecretWarning.NONE
        }
        return Assessment(mode = mode, valid = valid, warning = warning)
    }

    /**
     * Mixes the user secret with the app binding. The separator is a NUL so no
     * (secret, binding) pair can collide with another pair by concatenation.
     */
    fun boundSecret(secret: String, binding: String): CharArray =
        (secret + '\u0000' + binding).toCharArray()

    // ── Text containers ─────────────────────────────────────────────────────

    fun isTextContainer(text: String): Boolean =
        text.trimStart().startsWith(AUBUP_MAGIC)

    /**
     * @throws IllegalArgumentException when the plaintext or secret is invalid.
     */
    fun sealText(
        kind: String,
        plaintext: String,
        secret: String,
        binding: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ): String {
        require(kind.isNotBlank()) { "Container kind is required" }
        require(secret.isNotEmpty() && secret.length <= MAX_SECRET_LENGTH) { "Invalid secret" }
        require(plaintext.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES) {
            "Payload is too large for a text container"
        }
        val (ciphertext, ok) = CryptoManager.encryptWithPassphraseAtIterations(
            plaintext = plaintext,
            passphrase = boundSecret(secret, binding),
            iterations = iterations,
        )
        if (!ok || ciphertext.isBlank()) error("Container encryption failed")
        val envelope = TextEnvelope(
            kind = kind,
            iterations = iterations,
            ciphertext = ciphertext,
        )
        return AUBUP_MAGIC + "\n" + json.encodeToString(TextEnvelope.serializer(), envelope)
    }

    /**
     * Opens a text container. Never returns partially-authenticated data: the
     * AEAD tag is verified before any JSON is parsed.
     */
    fun openText(
        text: String,
        secret: String,
        binding: String,
        expectedKind: String? = null,
    ): String {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_TEXT_BYTES) {
            throw MalformedContainerException("Container is too large")
        }
        val trimmed = text.trim()
        if (!trimmed.startsWith(AUBUP_MAGIC)) {
            throw MalformedContainerException("Not a Toolz secure container")
        }
        val body = trimmed.removePrefix(AUBUP_MAGIC).trimStart()
        val envelope = runCatching {
            json.decodeFromString(TextEnvelope.serializer(), body)
        }.getOrElse { throw MalformedContainerException("Malformed container envelope") }
        if (expectedKind != null && envelope.kind != expectedKind) {
            throw MalformedContainerException("Unexpected container kind")
        }
        if (envelope.ciphertext.isBlank() || envelope.ciphertext.length > MAX_TEXT_BYTES * 2) {
            throw MalformedContainerException("Malformed container ciphertext")
        }
        val (plaintext, ok) = CryptoManager.decryptWithPassphraseAtIterations(
            combinedBase64 = envelope.ciphertext,
            passphrase = boundSecret(secret, binding),
            iterations = envelope.iterations,
        )
        if (!ok) {
            if (plaintext.contains("Unsupported KDF", ignoreCase = true)) {
                throw MalformedContainerException("Unsupported KDF work factor")
            }
            throw WrongSecretException()
        }
        return plaintext
    }

    // ── Binary (streaming) containers ───────────────────────────────────────

    fun isBinaryContainer(prefix: ByteArray): Boolean {
        if (prefix.size < BINARY_MAGIC_SIZE) return false
        for (i in 0 until BINARY_MAGIC_SIZE) {
            if (prefix[i] != BINARY_MAGIC[i]) return false
        }
        return true
    }

    fun isBinaryContainer(input: InputStream): Boolean {
        input.mark(BINARY_MAGIC_SIZE)
        val prefix = ByteArray(BINARY_MAGIC_SIZE)
        val read = runCatching { input.read(prefix) }.getOrDefault(-1)
        runCatching { input.reset() }
        return read == BINARY_MAGIC_SIZE && isBinaryContainer(prefix)
    }

    /**
     * Streams [input] into an authenticated container on [output]. The caller
     * owns both streams; this method does not close them.
     */
    fun sealStream(
        input: InputStream,
        output: OutputStream,
        secret: String,
        binding: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ) {
        require(secret.isNotEmpty() && secret.length <= MAX_SECRET_LENGTH) { "Invalid secret" }
        require(iterations in CryptoManager.MIN_PASSPHRASE_ITERATIONS..CryptoManager.MAX_PASSPHRASE_ITERATIONS) {
            "Unsupported KDF work factor"
        }
        val salt = ByteArray(SALT_SIZE).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(IV_SIZE).apply { SecureRandom().nextBytes(this) }
        val key = deriveKey(boundSecret(secret, binding), salt, iterations)

        output.write(BINARY_MAGIC)
        DataOutputStream(output).apply {
            writeByte(BINARY_VERSION)
            writeInt(iterations)
            write(salt)
            write(iv)
            flush()
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        // Manual update/doFinal instead of CipherOutputStream: doFinal is where
        // the GCM tag is produced, so a failed write is surfaced instead of
        // being swallowed by a stream close.
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            val chunk = cipher.update(buffer, 0, read)
            if (chunk != null && chunk.isNotEmpty()) output.write(chunk)
        }
        val finalChunk = cipher.doFinal()
        if (finalChunk != null && finalChunk.isNotEmpty()) output.write(finalChunk)
        output.flush()
    }

    /**
     * Opens a binary container. The output is only trustworthy after this
     * method returns; on a bad tag the caller must discard it.
     */
    fun openStream(
        input: InputStream,
        output: OutputStream,
        secret: String,
        binding: String,
    ) {
        val data = DataInputStream(input)
        val magic = ByteArray(BINARY_MAGIC_SIZE)
        try {
            data.readFully(magic)
        } catch (_: Exception) {
            throw MalformedContainerException("Truncated container header")
        }
        if (!isBinaryContainer(magic)) {
            throw MalformedContainerException("Not a Toolz secure container")
        }
        val version = runCatching { data.readUnsignedByte() }
            .getOrElse { throw MalformedContainerException("Truncated container header") }
        if (version != BINARY_VERSION) {
            throw MalformedContainerException("Unsupported container version")
        }
        val iterations = runCatching { data.readInt() }
            .getOrElse { throw MalformedContainerException("Truncated container header") }
        if (iterations !in CryptoManager.MIN_PASSPHRASE_ITERATIONS..CryptoManager.MAX_PASSPHRASE_ITERATIONS) {
            throw MalformedContainerException("Unsupported KDF work factor")
        }
        val salt = ByteArray(SALT_SIZE)
        val iv = ByteArray(IV_SIZE)
        try {
            data.readFully(salt)
            data.readFully(iv)
        } catch (_: Exception) {
            throw MalformedContainerException("Truncated container header")
        }

        val key = deriveKey(boundSecret(secret, binding), salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        // Manual update/doFinal instead of CipherInputStream: some providers
        // swallow AEADBadTagException inside the stream and return truncated
        // plaintext, which would let a wrong password reach the zip reader.
        val buffer = ByteArray(64 * 1024)
        try {
            while (true) {
                val read = data.read(buffer)
                if (read < 0) break
                val chunk = cipher.update(buffer, 0, read)
                if (chunk != null && chunk.isNotEmpty()) output.write(chunk)
            }
            val finalChunk = cipher.doFinal()
            if (finalChunk != null && finalChunk.isNotEmpty()) output.write(finalChunk)
        } catch (t: Throwable) {
            when {
                t is AEADBadTagException -> throw WrongSecretException()
                t.cause is AEADBadTagException -> throw WrongSecretException()
                t is MalformedContainerException -> throw t
                else -> throw MalformedContainerException("Corrupted container")
            }
        }
        output.flush()
    }

    /** Convenience for small in-memory payloads (tests, diagnostics). */
    fun sealBytes(
        plaintext: ByteArray,
        secret: String,
        binding: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ): ByteArray {
        val out = ByteArrayOutputStream(plaintext.size + 64)
        sealStream(ByteArrayInputStream(plaintext), out, secret, binding, iterations)
        return out.toByteArray()
    }

    fun openBytes(
        container: ByteArray,
        secret: String,
        binding: String,
    ): ByteArray {
        val out = ByteArrayOutputStream(container.size)
        openStream(ByteArrayInputStream(container), out, secret, binding)
        return out.toByteArray()
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            val secret = factory.generateSecret(spec)
            SecretKeySpec(secret.encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}

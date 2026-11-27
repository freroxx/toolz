/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.util.secure

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the parts of [SecureContainer] that do not depend on
 * `android.util.Base64` (the binary stream path derives and runs AES-GCM
 * itself). The text envelope shares CryptoManager's Base64 helper, so its
 * round-trip lives in the instrumented suite.
 */
class SecureContainerTest {

    private val binding = "com.frerox.toolz|sha256:test-cert|pepper"

    // ── Secret policy ───────────────────────────────────────────────────────

    @Test
    fun assessSecret_pinRequiresExactlySixDigits() {
        assertTrue(SecureContainer.assessSecret("123456", SecureContainer.SecretMode.PIN).valid)
        assertFalse(SecureContainer.assessSecret("12345", SecureContainer.SecretMode.PIN).valid)
        assertFalse(SecureContainer.assessSecret("1234567", SecureContainer.SecretMode.PIN).valid)
        assertFalse(SecureContainer.assessSecret("12345a", SecureContainer.SecretMode.PIN).valid)
        assertFalse(SecureContainer.assessSecret("", SecureContainer.SecretMode.PIN).valid)
    }

    @Test
    fun assessSecret_passwordRequiresAtLeastEightCharacters() {
        assertFalse(SecureContainer.assessSecret("short", SecureContainer.SecretMode.PASSWORD).valid)
        assertFalse(SecureContainer.assessSecret("1234567", SecureContainer.SecretMode.PASSWORD).valid)
        assertTrue(SecureContainer.assessSecret("12345678", SecureContainer.SecretMode.PASSWORD).valid)
        assertTrue(SecureContainer.assessSecret("correct horse", SecureContainer.SecretMode.PASSWORD).valid)
    }

    @Test
    fun assessSecret_warnsAboutWeakChoices() {
        assertEquals(
            SecureContainer.SecretWarning.WEAK_PIN,
            SecureContainer.assessSecret("123456", SecureContainer.SecretMode.PIN).warning,
        )
        assertEquals(
            SecureContainer.SecretWarning.SHORT_PASSWORD,
            SecureContainer.assessSecret("abc12345", SecureContainer.SecretMode.PASSWORD).warning,
        )
        assertEquals(
            SecureContainer.SecretWarning.NUMERIC_PASSWORD,
            SecureContainer.assessSecret("123456789012", SecureContainer.SecretMode.PASSWORD).warning,
        )
        assertEquals(
            SecureContainer.SecretWarning.NONE,
            SecureContainer.assessSecret("correct horse battery", SecureContainer.SecretMode.PASSWORD).warning,
        )
    }

    @Test
    fun boundSecret_isInjectiveAcrossPairs() {
        // The NUL separator prevents ("ab", "c") colliding with ("a", "bc").
        assertFalse(
            SecureContainer.boundSecret("ab", "c").contentEquals(
                SecureContainer.boundSecret("a", "bc"),
            )
        )
        assertArrayEquals(
            "secret\u0000binding".toCharArray(),
            SecureContainer.boundSecret("secret", "binding"),
        )
    }

    // ── Format detection ────────────────────────────────────────────────────

    @Test
    fun isTextContainer_onlyMatchesTheAubupMagic() {
        assertTrue(SecureContainer.isTextContainer("TOOLZ-AUBUP/2\n{}"))
        assertTrue(SecureContainer.isTextContainer("  \nTOOLZ-AUBUP/2\n{}"))
        assertFalse(SecureContainer.isTextContainer("U2FsdGVkX1+legacyBase64"))
        assertFalse(SecureContainer.isTextContainer("TOOLZ-AUBUP/1\n{}"))
    }

    @Test
    fun isBinaryContainer_checksTheFullMagicPrefix() {
        val magic = "TOOLZ-BACKUP/2\n".toByteArray(Charsets.US_ASCII)
        assertTrue(SecureContainer.isBinaryContainer(magic))
        assertTrue(SecureContainer.isBinaryContainer(magic + byteArrayOf(1, 2, 3)))
        assertFalse(SecureContainer.isBinaryContainer(magic.copyOf(magic.size - 1)))
        assertFalse(SecureContainer.isBinaryContainer("PK\u0003\u0004zip".toByteArray()))
        assertFalse(SecureContainer.isBinaryContainer(ByteArray(0)))
    }

    // ── Binary streaming round-trip ─────────────────────────────────────────

    @Test
    fun binary_roundTrip_preservesArbitraryBytes() {
        val plaintext = ByteArray(200_000) { (it * 31 % 256).toByte() }
        val sealed = SecureContainer.sealBytes(
            plaintext = plaintext,
            secret = "correct horse battery",
            binding = binding,
            iterations = 100_000,
        )
        assertTrue(SecureContainer.isBinaryContainer(sealed))
        val opened = SecureContainer.openBytes(sealed, "correct horse battery", binding)
        assertArrayEquals(plaintext, opened)
    }

    @Test
    fun binary_roundTrip_handlesEmptyPayload() {
        val sealed = SecureContainer.sealBytes(
            plaintext = ByteArray(0),
            secret = "123456",
            binding = binding,
            iterations = 100_000,
        )
        assertArrayEquals(
            ByteArray(0),
            SecureContainer.openBytes(sealed, "123456", binding),
        )
    }

    @Test
    fun binary_wrongSecret_isRejected() {
        val sealed = SecureContainer.sealBytes(
            plaintext = "archive contents".toByteArray(),
            secret = "correct horse battery",
            binding = binding,
            iterations = 100_000,
        )
        assertThrows(SecureContainer.WrongSecretException::class.java) {
            SecureContainer.openBytes(sealed, "wrong horse battery", binding)
        }
    }

    @Test
    fun binary_wrongBinding_isRejected() {
        val sealed = SecureContainer.sealBytes(
            plaintext = "archive contents".toByteArray(),
            secret = "correct horse battery",
            binding = binding,
            iterations = 100_000,
        )
        assertThrows(SecureContainer.WrongSecretException::class.java) {
            SecureContainer.openBytes(sealed, "correct horse battery", "another.app|other")
        }
    }

    @Test
    fun binary_tamperedCiphertext_isRejected() {
        val sealed = SecureContainer.sealBytes(
            plaintext = "archive contents".toByteArray(),
            secret = "correct horse battery",
            binding = binding,
            iterations = 100_000,
        )
        // Flip the last byte, which is part of the GCM tag.
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()
        assertThrows(SecureContainer.WrongSecretException::class.java) {
            SecureContainer.openBytes(sealed, "correct horse battery", binding)
        }
    }

    @Test
    fun binary_truncatedHeader_isMalformed() {
        val sealed = SecureContainer.sealBytes(
            plaintext = "archive contents".toByteArray(),
            secret = "correct horse battery",
            binding = binding,
            iterations = 100_000,
        )
        assertThrows(SecureContainer.MalformedContainerException::class.java) {
            SecureContainer.openBytes(sealed.copyOf(20), "correct horse battery", binding)
        }
    }

    @Test
    fun binary_tamperedWorkFactor_isRejectedBeforeDeriving() {
        val sealed = SecureContainer.sealBytes(
            plaintext = "archive contents".toByteArray(),
            secret = "correct horse battery",
            binding = binding,
            iterations = 100_000,
        )
        // Header: magic (15) | version (1) | iterations (4). Push iterations
        // far above the cap; opening must refuse rather than spin.
        val offset = SecureContainer.BINARY_MAGIC_SIZE + 1
        val huge = intArrayOf(0x7F, 0xFF, 0xFF, 0xFF)
        huge.forEachIndexed { i, b -> sealed[offset + i] = b.toByte() }
        assertThrows(SecureContainer.MalformedContainerException::class.java) {
            SecureContainer.openBytes(sealed, "correct horse battery", binding)
        }
    }

    @Test
    fun binary_tamperedVersion_isMalformed() {
        val sealed = SecureContainer.sealBytes(
            plaintext = "archive contents".toByteArray(),
            secret = "correct horse battery",
            binding = binding,
            iterations = 100_000,
        )
        sealed[SecureContainer.BINARY_MAGIC_SIZE] = 9
        assertThrows(SecureContainer.MalformedContainerException::class.java) {
            SecureContainer.openBytes(sealed, "correct horse battery", binding)
        }
    }

    @Test
    fun binary_sealRefusesWorkFactorOutsideBounds() {
        assertThrows(IllegalArgumentException::class.java) {
            SecureContainer.sealBytes(
                plaintext = "x".toByteArray(),
                secret = "correct horse battery",
                binding = binding,
                iterations = 1_000,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SecureContainer.sealBytes(
                plaintext = "x".toByteArray(),
                secret = "correct horse battery",
                binding = binding,
                iterations = 5_000_000,
            )
        }
    }

    private fun <T : Throwable> assertThrows(type: Class<T>, block: () -> Unit): T {
        try {
            block()
        } catch (t: Throwable) {
            if (type.isInstance(t)) return type.cast(t)
            throw AssertionError("Expected ${type.name} but got ${t::class.java.name}", t)
        }
        throw AssertionError("Expected ${type.name} but nothing was thrown")
    }
}

/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.util.secure

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frerox.toolz.data.whisper.WhisperAccessPayload
import com.frerox.toolz.data.whisper.WhisperAubupManager
import com.frerox.toolz.util.CryptoManager
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device tests for the text container and for access-file v2/legacy
 * compatibility. These need the real `android.util.Base64` used by
 * CryptoManager, so they cannot live in the JVM suite.
 */
@RunWith(AndroidJUnit4::class)
class SecureContainerInstrumentedTest {

    private val binding = "com.frerox.toolz|sha256:test-cert|pepper"
    private val json = Json { ignoreUnknownKeys = true }

    private fun payloadJson(username: String = "alice_test"): String = json.encodeToString(
        WhisperAccessPayload(
            version = 2,
            app = "toolz_whisper",
            username = username,
            authType = "PASSWORD",
            credential = "correct horse battery",
            displayName = "Alice",
            createdAt = 1_758_000_000_000L,
            secretKind = "PASSWORD",
        )
    )

    @Test
    fun textContainer_roundTrip() {
        val sealed = SecureContainer.sealText(
            kind = SecureContainer.KIND_AUBUP,
            plaintext = payloadJson(),
            secret = "correct horse battery",
            binding = binding,
            iterations = 100_000,
        )
        assertTrue(SecureContainer.isTextContainer(sealed))
        val opened = SecureContainer.openText(
            text = sealed,
            secret = "correct horse battery",
            binding = binding,
            expectedKind = SecureContainer.KIND_AUBUP,
        )
        assertEquals(payloadJson(), opened)
    }

    @Test
    fun textContainer_wrongSecretOrBindingIsRejected() {
        val sealed = SecureContainer.sealText(
            kind = SecureContainer.KIND_AUBUP,
            plaintext = payloadJson(),
            secret = "correct horse battery",
            binding = binding,
            iterations = 100_000,
        )
        assertThrows(SecureContainer.WrongSecretException::class.java) {
            SecureContainer.openText(sealed, "wrong horse battery", binding)
        }
        assertThrows(SecureContainer.WrongSecretException::class.java) {
            SecureContainer.openText(sealed, "correct horse battery", "other.app|other")
        }
    }

    @Test
    fun textContainer_tamperedEnvelopeIsRejected() {
        val sealed = SecureContainer.sealText(
            kind = SecureContainer.KIND_AUBUP,
            plaintext = payloadJson(),
            secret = "correct horse battery",
            binding = binding,
            iterations = 100_000,
        )
        val tampered = sealed.dropLast(8) + "AAAAAAAA"
        assertThrows(SecureContainer.WrongSecretException::class.java) {
            SecureContainer.openText(tampered, "correct horse battery", binding)
        }
    }

    @Test
    fun textContainer_wrongKindIsRejected() {
        val sealed = SecureContainer.sealText(
            kind = SecureContainer.KIND_BACKUP,
            plaintext = payloadJson(),
            secret = "correct horse battery",
            binding = binding,
            iterations = 100_000,
        )
        assertThrows(SecureContainer.MalformedContainerException::class.java) {
            SecureContainer.openText(sealed, "correct horse battery", binding, SecureContainer.KIND_AUBUP)
        }
    }

    @Test
    fun textContainer_tamperedWorkFactorIsRejected() {
        val sealed = SecureContainer.sealText(
            kind = SecureContainer.KIND_AUBUP,
            plaintext = payloadJson(),
            secret = "correct horse battery",
            binding = binding,
            iterations = 100_000,
        )
        val tampered = sealed.replace("\"iter\":100000", "\"iter\":99999999")
        assertThrows(SecureContainer.MalformedContainerException::class.java) {
            SecureContainer.openText(tampered, "correct horse battery", binding)
        }
    }

    // ── Access-file compatibility ───────────────────────────────────────────

    @Test
    fun aubupV2_passwordPayloadDecodes() {
        val sealed = SecureContainer.sealText(
            kind = SecureContainer.KIND_AUBUP,
            plaintext = payloadJson(),
            secret = "correct horse battery",
            binding = binding,
        )
        val payload = WhisperAubupManager.decodeAccessPayload(sealed, "correct horse battery", binding)
        assertEquals("alice_test", payload.username)
        assertEquals(2, payload.version)
        assertEquals("PASSWORD", payload.secretKind)
        assertEquals("correct horse battery", payload.credential)
    }

    @Test
    fun aubupV2_pinPayloadDecodes() {
        val plain = json.encodeToString(
            WhisperAccessPayload(
                version = 2,
                username = "bob",
                authType = "TOKEN",
                credential = "token-value",
                secretKind = "PIN",
            )
        )
        val sealed = SecureContainer.sealText(
            kind = SecureContainer.KIND_AUBUP,
            plaintext = plain,
            secret = "123456",
            binding = binding,
        )
        val payload = WhisperAubupManager.decodeAccessPayload(sealed, "123456", binding)
        assertEquals("bob", payload.username)
        assertEquals("PIN", payload.secretKind)
    }

    @Test
    fun legacyAccessFile_stillDecrypts() {
        val plain = payloadJson(username = "legacy_user")
        val (encoded, ok) = CryptoManager.encryptWithPassphrase(
            plaintext = plain,
            passphrase = "123456".toCharArray(),
        )
        assertTrue(ok)
        val payload = WhisperAubupManager.decodeAccessPayload(encoded, "123456", binding)
        assertEquals("legacy_user", payload.username)
        // Legacy files have no secret_kind field.
        assertNull(payload.secretKind)
    }

    @Test
    fun legacyAccessFile_wrongCodeFails() {
        val (encoded, ok) = CryptoManager.encryptWithPassphrase(
            plaintext = payloadJson(),
            passphrase = "123456".toCharArray(),
        )
        assertTrue(ok)
        assertThrows(IllegalStateException::class.java) {
            WhisperAubupManager.decodeAccessPayload(encoded, "654321", binding)
        }
    }

    @Test
    fun legacyAccessFile_rejectsNonSixDigitSecret() {
        val (encoded, _) = CryptoManager.encryptWithPassphrase(
            plaintext = payloadJson(),
            passphrase = "123456".toCharArray(),
        )
        assertThrows(IllegalArgumentException::class.java) {
            WhisperAubupManager.decodeAccessPayload(encoded, "long password", binding)
        }
    }
}

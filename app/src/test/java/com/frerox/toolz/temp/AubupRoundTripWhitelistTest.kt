package com.frerox.toolz.temp
import com.frerox.toolz.util.CryptoManager
import com.frerox.toolz.data.whisper.WhisperAccessPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
class AubupRoundTripWhitelistTest {
    private val json = Json { ignoreUnknownKeys = true }
    @Test fun roundTrip_hardened_withCorrectCode() {
        val payload = WhisperAccessPayload(username="testuser", authType="PASSWORD", credential="TestPass12345", displayName="Test")
        val jsonStr = json.encodeToString(payload)
        val code = "123456"
        val (enc, ok) = CryptoManager.encryptWithPassphrase(jsonStr, code.toCharArray())
        assertTrue("encrypt ok", ok)
        val (dec, ok2) = CryptoManager.decryptWithPassphrase(enc, code.toCharArray())
        assertTrue(ok2)
        assertEquals(jsonStr, dec)
    }
    @Test fun roundTrip_wrappedBase64_withCorrectCode() {
        val payload = WhisperAccessPayload(username="testuser", authType="PASSWORD", credential="TestPass12345")
        val jsonStr = json.encodeToString(payload)
        val code = "000123"
        val (enc, ok) = CryptoManager.encryptWithPassphrase(jsonStr, code.toCharArray())
        assertTrue(ok)
        val wrapped = enc.chunked(76).joinToString("\n")
        val (dec, ok2) = CryptoManager.decryptWithPassphrase(wrapped, code.toCharArray())
        assertTrue("wrapped decrypt should succeed", ok2)
        assertEquals(jsonStr, dec)
    }
    @Test fun roundTrip_wrongCode_fails() {
        val payload = WhisperAccessPayload(username="alice", authType="TOKEN", credential="a".repeat(64))
        val jsonStr = json.encodeToString(payload)
        val (enc, ok) = CryptoManager.encryptWithPassphrase(jsonStr, "123456".toCharArray())
        assertTrue(ok)
        val (dec, ok2) = CryptoManager.decryptWithPassphrase(enc, "654321".toCharArray())
        assertFalse(ok2)
    }
    @Test fun roundTrip_withSpacesAndNewlines() {
        val payload = WhisperAccessPayload(username="bob", authType="PASSWORD", credential="secret")
        val jsonStr = json.encodeToString(payload)
        val (enc, ok) = CryptoManager.encryptWithPassphrase(jsonStr, "999999".toCharArray())
        assertTrue(ok)
        val withSpaces = "  \n" + enc + "\n  "
        val (dec, ok2) = CryptoManager.decryptWithPassphrase(withSpaces, "999999".toCharArray())
        assertTrue(ok2)
        assertEquals(jsonStr, dec)
    }
    @Test fun legacyFallback_stillWorks() {
        // Simulate old file created with encryptAes (65536 iterations, no marker) — decryptWithPassphrase should fallback to legacy
        val payload = WhisperAccessPayload(username="legacy", authType="PASSWORD", credential="oldpass")
        val jsonStr = json.encodeToString(payload)
        val code = "111222"
        // Use legacy encryptAes directly to mimic old files
        val (encLegacy, okLegacy) = CryptoManager.encryptAes(jsonStr, code.toCharArray())
        assertTrue(okLegacy)
        // decryptWithPassphrase should fallback to 65k and succeed
        val (dec, ok2) = CryptoManager.decryptWithPassphrase(encLegacy, code.toCharArray())
        assertTrue("legacy fallback should succeed", ok2)
        assertEquals(jsonStr, dec)
    }
}

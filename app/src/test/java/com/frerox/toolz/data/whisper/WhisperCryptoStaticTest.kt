package com.frerox.toolz.data.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H-11 FIX (reviewwhisper.md): pure-JVM tests for the static crypto surface.
 * [WhisperCrypto.computeFingerprint] is companion-scoped and needs no AndroidKeyStore,
 * so its contract — the security number users compare in person! — is pinned here:
 * format, stability, null/garbage rejection, and distinctness across different keys.
 * The Keystore-dependent round-trips remain in WhisperCryptoAadTest (instrumented).
 */
class WhisperCryptoStaticTest {

    // 65-byte uncompressed P-256 point prefix + arbitrary body: shape-realistic base64.
    private val keyA: String = java.util.Base64.getEncoder()
        .encodeToString(byteArrayOf(0x04) + ByteArray(64) { it.toByte() })

    private val keyB: String = java.util.Base64.getEncoder()
        .encodeToString(byteArrayOf(0x04) + ByteArray(64) { (it * 7).toByte() })

    @Test
    fun fingerprint_isNullForNullOrBlankInput() {
        assertNull(WhisperCrypto.computeFingerprint(null))
        assertNull(WhisperCrypto.computeFingerprint(""))
        assertNull(WhisperCrypto.computeFingerprint("   "))
    }

    @Test
    fun fingerprint_rejectsGarbageThatIsNotBase64() {
        assertNull(WhisperCrypto.computeFingerprint("!!!not-base64!!!"))
        assertNull(WhisperCrypto.computeFingerprint("@@@@@@@@@@@@@@@@@@"))
    }

    @Test
    fun fingerprint_hasEightGroupsOfFourUppercaseHex() {
        val fp = WhisperCrypto.computeFingerprint(keyA)
        assertNotNull(fp)
        val groups = fp!!.split("-")
        assertEquals(8, groups.size)
        groups.forEach { group ->
            assertEquals(4, group.length)
            assertTrue(group.all { it in '0'..'9' || it in 'A'..'F' })
        }
    }

    @Test
    fun fingerprint_isStableForTheSameKey() {
        assertEquals(
            WhisperCrypto.computeFingerprint(keyA),
            WhisperCrypto.computeFingerprint(keyA),
        )
        // Whitespace around the input must not change the result.
        assertEquals(
            WhisperCrypto.computeFingerprint(keyA),
            WhisperCrypto.computeFingerprint("  $keyA\n"),
        )
    }

    @Test
    fun fingerprint_differsAcrossKeys() {
        val a = WhisperCrypto.computeFingerprint(keyA)
        val b = WhisperCrypto.computeFingerprint(keyB)
        assertNotNull(a)
        assertNotNull(b)
        assertFalse(a == b)
    }

    @Test
    fun tombstoneConstants_areAlignedWithServerWritePath() {
        // Guards the H-5 unification: the repository writes DISPLAY_TEXT, so the
        // display constant must be what isTombstone recognizes.
        assertTrue(WhisperTombstone.isTombstone(WhisperTombstone.DISPLAY_TEXT))
        assertTrue(WhisperTombstone.isTombstone(WhisperTombstone.CONTENT_LEGACY))
    }

    @Test
    fun legacyAadFallback_isScopedToPreCutoffRows() {
        // V3-FIX (scoped legacy-AAD retirement): the no-AAD / constant-AAD retries may
        // only run for rows created STRICTLY before 2026-09-01T00:00:00Z.
        // Undated rows parse to 0L and stay legacy-eligible; Long.MAX_VALUE (the
        // "unknown age" default) is never legacy.
        assertTrue(WhisperCrypto.legacyFallbackAllowed(0L))
        assertTrue(WhisperCrypto.legacyFallbackAllowed(WhisperCrypto.LEGACY_AAD_CUTOFF_EPOCH_MS - 1))
        assertFalse(WhisperCrypto.legacyFallbackAllowed(WhisperCrypto.LEGACY_AAD_CUTOFF_EPOCH_MS))
        assertFalse(WhisperCrypto.legacyFallbackAllowed(WhisperCrypto.LEGACY_AAD_CUTOFF_EPOCH_MS + 1))
        assertFalse(WhisperCrypto.legacyFallbackAllowed(Long.MAX_VALUE))
    }
}

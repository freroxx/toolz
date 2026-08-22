package com.frerox.toolz.data.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperErrorMapperTest {

    @Test
    fun testIsSessionExpired_detectsKeywords() {
        assertTrue(WhisperErrorMapper.isSessionExpired(Exception("JWT expired: signature is invalid")))
        assertTrue(WhisperErrorMapper.isSessionExpired(Exception("invalid claim in token")))
        assertTrue(WhisperErrorMapper.isSessionExpired(Exception("session expired, please login again")))
        assertTrue(WhisperErrorMapper.isSessionExpired(Exception("user_not_found on server")))

        assertFalse(WhisperErrorMapper.isSessionExpired(Exception("Network connect timed out")))
        assertFalse(WhisperErrorMapper.isSessionExpired(Exception("Rate limit reached")))
    }

    @Test
    fun testIsNotFound_detects404() {
        assertTrue(WhisperErrorMapper.isNotFound(Exception("Error 404: Not Found")))
        assertTrue(WhisperErrorMapper.isNotFound(Exception("No rows found in query")))
        assertFalse(WhisperErrorMapper.isNotFound(Exception("500 Internal Server Error")))
    }

    @Test
    fun testIsDuplicateKey_detectsUniqueViolations() {
        assertTrue(WhisperErrorMapper.isDuplicateKey(Exception("duplicate key value violates unique constraint")))
        assertTrue(WhisperErrorMapper.isDuplicateKey(Exception("violates constraint 23505")))
        assertFalse(WhisperErrorMapper.isDuplicateKey(Exception("Connection reset")))
    }

    @Test
    fun testIsPermanentError_detectsValidationErrors() {
        assertTrue(WhisperErrorMapper.isPermanentError(IllegalArgumentException("Invalid parameter")))
        assertTrue(WhisperErrorMapper.isPermanentError(IllegalStateException("Bad state")))
        assertFalse(WhisperErrorMapper.isPermanentError(Exception("SocketTimeoutException")))
    }

    @Test
    fun testMap_sessionExpiredReturnsSentinel() {
        val mapped = WhisperErrorMapper.map(Exception("JWT expired"))
        assertTrue(mapped is UiText.DynamicString)
        assertEquals(WhisperErrorMapper.SESSION_EXPIRED_SENTINEL, (mapped as UiText.DynamicString).value)
    }
}

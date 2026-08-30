@file:OptIn(InternalAPI::class)

package com.frerox.toolz.data.whisper

import com.frerox.toolz.R
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnknownRestException
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.utils.EmptyContent
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.util.Attributes
import io.ktor.utils.io.InternalAPI
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperErrorMapperTest {

    /**
     * V2-FIX (reviewwhisper.md): builds a real [RestException] carrying an HTTP status
     * code so the structured status-code branches (401 vs 403 vs 404) are testable.
     * Uses only public ktor API; the client/engine is never executed.
     */
    @OptIn(InternalAPI::class)
    private fun restException(statusCode: HttpStatusCode, error: String = "error"): RestException {
        val requestData = HttpRequestData(
            url = Url("http://localhost/rest/v1/messages"),
            method = HttpMethod.Get,
            headers = Headers.Empty,
            body = EmptyContent,
            executionContext = Job(),
            attributes = Attributes(true),
        )
        val responseData = HttpResponseData(
            statusCode = statusCode,
            requestTime = GMTDate.START,
            headers = Headers.Empty,
            version = HttpProtocolVersion.HTTP_1_1,
            body = "",
            callContext = Job(),
        )
        return HttpClient(OkHttp).use { client ->
            val call = HttpClientCall(client, requestData, responseData)
            UnknownRestException(error, call.response)
        }
    }

    @Test
    fun testIsSessionExpired_detectsKeywords() {
        assertTrue(WhisperErrorMapper.isSessionExpired(Exception("JWT expired: signature is invalid")))
        assertTrue(WhisperErrorMapper.isSessionExpired(Exception("invalid claim in token")))
        assertTrue(WhisperErrorMapper.isSessionExpired(Exception("session expired, please login again")))

        // V2-FIX (reviewwhisper.md) H-7: user_not_found is a lookup miss, not expiry.
        assertFalse(WhisperErrorMapper.isSessionExpired(Exception("user_not_found on server")))

        assertFalse(WhisperErrorMapper.isSessionExpired(Exception("Network connect timed out")))
        assertFalse(WhisperErrorMapper.isSessionExpired(Exception("Rate limit reached")))
    }

    @Test
    fun testIsSessionExpired_restExceptionStatusCodes() {
        // V2-FIX (reviewwhisper.md) H-7: ONLY 401 counts as session-expired...
        assertTrue(WhisperErrorMapper.isSessionExpired(restException(HttpStatusCode.Unauthorized)))
        // ...403 (and 402) must NOT sign the user out anymore.
        assertFalse(WhisperErrorMapper.isSessionExpired(restException(HttpStatusCode.Forbidden)))
        assertFalse(WhisperErrorMapper.isSessionExpired(restException(HttpStatusCode.PaymentRequired)))
        assertFalse(WhisperErrorMapper.isSessionExpired(restException(HttpStatusCode.NotFound)))
        // Structured JWT error codes are still recognized.
        assertTrue(WhisperErrorMapper.isSessionExpired(restException(HttpStatusCode.BadRequest, error = "invalid_jwt")))
    }

    @Test
    fun testIsNotFound_requiresStructured404() {
        // V2-FIX (reviewwhisper.md): structured RestException 404 still detected...
        assertTrue(WhisperErrorMapper.isNotFound(restException(HttpStatusCode.NotFound)))
        // ...anchored "HTTP 404" phrase still accepted for non-RestException transports...
        assertTrue(WhisperErrorMapper.isNotFound(Exception("request failed with HTTP 404")))
        // ...loose substrings no longer match.
        assertFalse(WhisperErrorMapper.isNotFound(Exception("Error 404: Not Found")))
        assertFalse(WhisperErrorMapper.isNotFound(Exception("No rows found in query")))
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

    @Test
    fun testMap_structuredStatusBeatsPhraseMatching() {
        // V2-FIX (reviewwhisper.md): a 500 whose message mentions a lower-tier keyword
        // must surface as ServerBusy, not be misrouted into the phrase buckets below it.
        val mapped = WhisperErrorMapper.map(restException(HttpStatusCode.InternalServerError, error = "Connection reset: offline database"))
        assertTrue(mapped is UiText.StringResource)
        assertEquals(R.string.st_Whisper_Error_ServerBusy, (mapped as UiText.StringResource).resId)
    }

    @Test
    fun testMap_aubupRecoveryAndAccessFileErrors() {
        val incorrectCode = WhisperErrorMapper.map(Exception("Incorrect Whisper Code or corrupted access file."))
        assertTrue(incorrectCode is UiText.StringResource)
        assertEquals(R.string.st_Whisper_Error_AubupIncorrectCode, (incorrectCode as UiText.StringResource).resId)

        val invalidPassword = WhisperErrorMapper.map(Exception("Invalid password or corrupted data"))
        assertTrue(invalidPassword is UiText.StringResource)
        assertEquals(R.string.st_Whisper_Error_AubupIncorrectCode, (invalidPassword as UiText.StringResource).resId)

        val invalidFile = WhisperErrorMapper.map(Exception("This file is not a valid Whisper Access File."))
        assertTrue(invalidFile is UiText.StringResource)
        assertEquals(R.string.st_Whisper_Error_AubupInvalidFile, (invalidFile as UiText.StringResource).resId)

        val codeLength = WhisperErrorMapper.map(Exception("Whisper Code must be exactly 6 digits."))
        assertTrue(codeLength is UiText.StringResource)
        assertEquals(R.string.st_Whisper_Aubup_CodeLengthError, (codeLength as UiText.StringResource).resId)

        val noCredentials = WhisperErrorMapper.map(Exception("No credentials found for @alice in Password Vault. Please re-enter credentials."))
        assertTrue(noCredentials is UiText.StringResource)
        assertEquals(R.string.st_Whisper_Error_AubupNoCredentials, (noCredentials as UiText.StringResource).resId)

        val encryptFailed = WhisperErrorMapper.map(Exception("Encryption failed with the provided Whisper Code."))
        assertTrue(encryptFailed is UiText.StringResource)
        assertEquals(R.string.st_Whisper_Error_AubupEncryptionFailed, (encryptFailed as UiText.StringResource).resId)

        val unreadable = WhisperErrorMapper.map(Exception("Access file exceeds the 1 MB limit"))
        assertTrue(unreadable is UiText.StringResource)
        assertEquals(R.string.st_Whisper_Error_AccessFileUnreadable, (unreadable as UiText.StringResource).resId)
    }

    @Test(expected = CancellationException::class)
    fun testMap_cancellationPropagates() {
        // V2-FIX (reviewwhisper.md) L-13: cancellation must rethrow, never map.
        WhisperErrorMapper.map(CancellationException("job cancelled"), "send")
    }

    @Test(expected = CancellationException::class)
    fun testLog_cancellationPropagates() {
        // V2-FIX (reviewwhisper.md) L-13: same contract for log().
        WhisperErrorMapper.log(CancellationException("job cancelled"), "flush")
    }
}

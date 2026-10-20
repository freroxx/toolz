/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.update

import com.frerox.toolz.R
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the error taxonomy: every category must carry a plain
 * user message, HTTP mapping must not call 4xx failures retryable, and
 * connection problems must land in the offline/timeout buckets.
 */
class UpdateErrorTest {

    @Test
    fun httpMappingSeparatesBusyFromBroken() {
        assertTrue(UpdateError.fromHttp(403) is UpdateError.RateLimited)
        assertTrue(UpdateError.fromHttp(429) is UpdateError.RateLimited)
        assertTrue(UpdateError.fromHttp(403).retryable)
        assertTrue(UpdateError.fromHttp(429).retryable)

        assertEquals(UpdateError.Http(404), UpdateError.fromHttp(404))
        assertFalse(UpdateError.fromHttp(404).retryable)
        assertFalse(UpdateError.fromHttp(400).retryable)

        assertTrue(UpdateError.fromHttp(500).retryable)
        assertTrue(UpdateError.fromHttp(503).retryable)
        assertTrue(UpdateError.fromHttp(408).retryable)
        assertTrue(UpdateError.fromHttp(416).retryable)
    }

    @Test
    fun throwableMappingKeepsNetworkFailuresRetryable() {
        assertTrue(UpdateError.fromThrowable(UnknownHostException("dns")) is UpdateError.Offline)
        assertTrue(UpdateError.fromThrowable(ConnectException("refused")) is UpdateError.Offline)
        assertTrue(UpdateError.fromThrowable(NoRouteToHostException("route")) is UpdateError.Offline)
        assertTrue(UpdateError.fromThrowable(SSLException("tls")) is UpdateError.Offline)
        assertTrue(UpdateError.fromThrowable(IOException("socket")) is UpdateError.Offline)

        assertTrue(UpdateError.fromThrowable(SocketTimeoutException("slow")) is UpdateError.Timeout)
        assertTrue(UpdateError.fromThrowable(InterruptedIOException("slow")) is UpdateError.Timeout)

        val unknown = UpdateError.fromThrowable(IllegalStateException("boom"))
        assertTrue(unknown is UpdateError.Unknown)
        assertEquals("boom", unknown.detail)
        assertTrue(unknown.retryable)
    }

    @Test
    fun everyCategoryHasALocalizedMessage() {
        val all = listOf(
            UpdateError.Offline,
            UpdateError.Timeout,
            UpdateError.RateLimited(),
            UpdateError.Http(500),
            UpdateError.NoCompatibleBuild,
            UpdateError.Integrity(),
            UpdateError.Verification(),
            UpdateError.Storage,
            UpdateError.InstallFailed(),
            UpdateError.Unknown(),
        )
        for (error in all) {
            assertTrue("No copy for $error", error.messageRes != 0)
        }
    }

    @Test
    fun verificationUsesTheReasonCopyUnlessOverridden() {
        val signer = UpdateError.Verification(reason = ApkIdentity.Reason.SignerMismatch)
        assertEquals(R.string.st_Update_ErrorSignerMismatch, signer.messageRes)
        assertFalse(signer.retryable)

        val custom = UpdateError.Verification(
            overrideMessageRes = R.string.st_Update_ErrorInstall,
        )
        assertEquals(R.string.st_Update_ErrorInstall, custom.messageRes)

        assertEquals("HTTP 500", UpdateError.Http(500).detail)
    }
}

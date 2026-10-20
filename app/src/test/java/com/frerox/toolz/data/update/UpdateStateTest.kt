/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.update

import com.frerox.toolz.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the state helpers and the user-facing formatters.
 */
class UpdateStateTest {

    private val update = PendingUpdate(
        version = "1.1.8",
        versionCode = 19,
        changelog = "### Highlights\n- Faster downloads",
        downloadUrl = "https://example.com/toolz.apk",
        sizeBytes = 3_200_000L,
        sha256 = null,
        isCritical = false,
        abi = "arm64-v8a",
        source = "github",
    )

    private val apk = File("/tmp/toolz.apk")

    @Test
    fun pendingUpdateIsExposedForEveryLiveState() {
        assertEquals(update, UpdateManagerState.Available(update).pendingUpdate())
        assertEquals(
            update,
            UpdateManagerState.Downloading(update, 10L, 100L, 5L, 10f).pendingUpdate(),
        )
        assertEquals(update, UpdateManagerState.Verifying(update, apk).pendingUpdate())
        assertEquals(
            update,
            UpdateManagerState.ReadyToInstall(update, apk).pendingUpdate(),
        )
        assertEquals(update, UpdateManagerState.Installing(update, apk).pendingUpdate())
    }

    @Test
    fun pendingUpdateIsNullWhenNoCandidateOwnsTheState() {
        assertNull(UpdateManagerState.Idle.pendingUpdate())
        assertNull(UpdateManagerState.Checking.pendingUpdate())
        assertNull(UpdateManagerState.UpToDate.pendingUpdate())
        assertNull(UpdateManagerState.Installed("1.1.8").pendingUpdate())
        assertNull(
            UpdateManagerState.Error(UpdateError.Timeout).pendingUpdate(),
        )
    }

    @Test
    fun errorStateMirrorsTheErrorFlags() {
        val retryable = UpdateManagerState.Error(UpdateError.Offline, retryDownload = true)
        assertEquals(UpdateError.Offline.messageRes, retryable.error.messageRes)
        assertTrue(retryable.retryable)

        val fatal = UpdateManagerState.Error(
            UpdateError.Verification(reason = ApkIdentity.Reason.NotAnApk),
        )
        assertEquals(R.string.st_Update_ErrorNotApk, fatal.error.messageRes)
        assertFalse(fatal.retryable)
    }

    @Test
    fun releaseNotesFlagFollowsTheChangelog() {
        assertTrue(update.hasReleaseNotes)
        assertFalse(update.copy(changelog = "   ").hasReleaseNotes)
    }

    @Test
    fun formatBytesScalesAndGuardsNegatives() {
        assertEquals("--", formatBytes(-1))
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("1.0 MB", formatBytes(1024L * 1024))
        assertEquals("1.00 GB", formatBytes(1024L * 1024 * 1024))
    }

    @Test
    fun etaSplitsIntoLocalizablePieces() {
        assertEquals(Eta.Seconds(0), etaOf(0, 100))
        assertNull(etaOf(1000, 0))
        assertNull(etaOf(1000, -5))
        assertEquals(Eta.Seconds(10), etaOf(1000, 100))
        assertEquals(Eta.Minutes(2, 30), etaOf(15_000L, 100L))
        assertEquals(Eta.Hours(1, 1), etaOf(3700, 1))
    }

    @Test
    fun relativeAgeCoarsensNaturally() {
        assertEquals(RelativeAge.JustNow, relativeAge(-1))
        assertEquals(RelativeAge.JustNow, relativeAge(0))
        assertEquals(RelativeAge.JustNow, relativeAge(59_000))
        assertEquals(RelativeAge.Minutes(1), relativeAge(60_000))
        assertEquals(RelativeAge.Minutes(5), relativeAge(5 * 60_000L))
        assertEquals(RelativeAge.Hours(1), relativeAge(60 * 60_000L))
        assertEquals(RelativeAge.Hours(3), relativeAge(3 * 60 * 60_000L))
        assertEquals(RelativeAge.Days(1), relativeAge(25 * 60 * 60_000L))
        assertEquals(RelativeAge.Days(2), relativeAge(50 * 60 * 60_000L))
    }
}

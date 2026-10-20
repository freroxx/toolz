/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the APK identity gate: only a newer build of the same
 * package signed with an overlapping certificate may reach the installer.
 */
class ApkIdentityTest {

    private val installedSigners = setOf("AAAA", "BBBB")

    private fun evaluate(
        installedPackage: String? = "com.frerox.toolz",
        installedVersionCode: Long = 18L,
        signers: Set<String> = installedSigners,
        apkPackage: String? = "com.frerox.toolz",
        apkVersionCode: Long = 19L,
        apkSigners: Set<String> = setOf("AAAA"),
    ) = ApkIdentity.evaluate(
        installedPackage = installedPackage,
        installedVersionCode = installedVersionCode,
        installedSigners = signers,
        apkPackage = apkPackage,
        apkVersionCode = apkVersionCode,
        apkSigners = apkSigners,
    )

    @Test
    fun validUpdatePasses() {
        assertNull(evaluate())
        // Rotation-style overlap: one shared signer is enough.
        assertNull(evaluate(apkSigners = setOf("CCCC", "BBBB")))
    }

    @Test
    fun missingPackageFailsAsNotAnApk() {
        assertEquals(ApkIdentity.Reason.NotAnApk, evaluate(installedPackage = null))
        assertEquals(ApkIdentity.Reason.NotAnApk, evaluate(apkPackage = null))
        assertEquals(ApkIdentity.Reason.NotAnApk, evaluate(installedPackage = " "))
        assertEquals(ApkIdentity.Reason.NotAnApk, evaluate(apkPackage = ""))
    }

    @Test
    fun differentPackageIsRejected() {
        assertEquals(ApkIdentity.Reason.WrongPackage, evaluate(apkPackage = "com.evil.toolz"))
    }

    @Test
    fun sameOrOlderVersionCodeIsRejected() {
        assertEquals(ApkIdentity.Reason.NotNewer, evaluate(apkVersionCode = 18L))
        assertEquals(ApkIdentity.Reason.NotNewer, evaluate(apkVersionCode = 17L))
        assertEquals(ApkIdentity.Reason.NotNewer, evaluate(apkVersionCode = 0L))
    }

    @Test
    fun unreadableSignersAreRejectedNotTrusted() {
        assertEquals(ApkIdentity.Reason.SignerUnavailable, evaluate(signers = emptySet()))
        assertEquals(ApkIdentity.Reason.SignerUnavailable, evaluate(apkSigners = emptySet()))
    }

    @Test
    fun disjointSignersAreRejected() {
        assertEquals(ApkIdentity.Reason.SignerMismatch, evaluate(apkSigners = setOf("CCCC")))
    }

    @Test
    fun everyReasonMapsToItsOwnMessage() {
        val messages = ApkIdentity.Reason.entries.map { reason ->
            UpdateError.Verification(reason = reason).messageRes
        }
        assertTrue(messages.all { it != 0 })
        assertEquals(
            "Each reason needs its own copy",
            messages.size,
            messages.toSet().size,
        )
    }
}

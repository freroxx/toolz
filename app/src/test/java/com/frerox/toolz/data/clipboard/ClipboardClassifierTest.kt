/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.data.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardClassifierTest {

    private val classifier = ClipboardClassifier()

    @Test
    fun `links with digits are not mistaken for secrets`() {
        val result = classifier.classifyDetailed("https://nebula.tools/status/12345")
        assertEquals(ClipboardType.LINK, result.type)
        assertFalse(result.isSensitive)
        assertNull(result.reason)
    }

    @Test
    fun `social links are classified as social`() {
        assertEquals(
            ClipboardType.SOCIAL,
            classifier.classify("https://github.com/frerox/toolz"),
        )
    }

    @Test
    fun `plain email and phone keep their types`() {
        assertEquals(ClipboardType.EMAIL, classifier.classify("fixture-squad@toolz.local"))
        assertEquals(ClipboardType.PHONE, classifier.classify("+65 6000 0100"))
    }

    @Test
    fun `colors and math are not secrets`() {
        assertEquals(ClipboardType.COLOR, classifier.classify("#FFB300"))
        assertEquals(ClipboardType.MATH, classifier.classify("42 * 1.618"))
    }

    @Test
    fun `six digit codes are otp and sensitive`() {
        val result = classifier.classifyDetailed("483920")
        assertEquals(ClipboardType.OTP, result.type)
        assertTrue(result.isSensitive)
        assertEquals(ClipSensitivityReason.OTP, result.reason)
    }

    @Test
    fun `inline otp keywords extract the code`() {
        val result = classifier.classifyDetailed("Your verification code is 90210")
        assertEquals(ClipboardType.OTP, result.type)
        assertEquals(ClipSensitivityReason.OTP, result.reason)
        assertEquals("90210", classifier.extractOtp("Your verification code is 90210"))
    }

    @Test
    fun `luhn valid card is sensitive card`() {
        val result = classifier.classifyDetailed("4242 4242 4242 4242")
        assertEquals(ClipboardType.CARD, result.type)
        assertTrue(result.isSensitive)
        assertEquals(ClipSensitivityReason.CARD, result.reason)
        assertTrue(classifier.luhnValid("4242 4242 4242 4242"))
    }

    @Test
    fun `card shaped number that fails luhn is still masked`() {
        val result = classifier.classifyDetailed("4242 4242 4242 4241")
        assertTrue(result.isSensitive)
        assertEquals(ClipSensitivityReason.CARD, result.reason)
        assertFalse(classifier.luhnValid("4242 4242 4242 4241"))
    }

    @Test
    fun `labelled password is sensitive`() {
        val result = classifier.classifyDetailed("password: hunter2")
        assertEquals(ClipboardType.SECRET, result.type)
        assertTrue(result.isSensitive)
        assertEquals(ClipSensitivityReason.LABEL, result.reason)
    }

    @Test
    fun `bare high entropy token is a secret`() {
        val result = classifier.classifyDetailed("Xk9!mQ2#pL7\$vR4")
        assertTrue(result.isSensitive)
        assertEquals(ClipSensitivityReason.SECRET, result.reason)
    }

    @Test
    fun `password vault match always wins`() {
        val result = classifier.classifyDetailed(
            text = "Correct-Horse-Battery-Staple",
            passwordVaultMatch = true,
        )
        assertEquals(ClipboardType.SECRET, result.type)
        assertTrue(result.isSensitive)
        assertEquals(ClipSensitivityReason.PASSWORD_VAULT, result.reason)
    }

    @Test
    fun `sensitive extra marks ordinary text`() {
        val result = classifier.classifyDetailed("hello there", isSensitiveExtra = true)
        assertTrue(result.isSensitive)
        assertEquals(ClipSensitivityReason.EXTRA, result.reason)
    }

    @Test
    fun `masking never leaks the original characters`() {
        val masked = classifier.mask("hunter2\nsecond line")
        assertFalse(masked.contains("hunter2"))
        assertFalse(masked.contains("second line"))
        assertTrue(masked.lines().size == 2)
        assertTrue(masked.lines().all { it.isBlank() || it.all { c -> c == '•' } })
    }

    @Test
    fun `blank text is plain and not sensitive`() {
        val result = classifier.classifyDetailed("   ")
        assertEquals(ClipboardType.TEXT, result.type)
        assertFalse(result.isSensitive)
    }

    @Test
    fun `type keys are stable`() {
        assertEquals("link", classifier.typeKey(ClipboardType.URL))
        assertEquals("card", classifier.typeKey(ClipboardType.CARD))
        assertEquals("text", classifier.typeKey("UNKNOWN_AI_TYPE"))
    }
}

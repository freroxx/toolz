/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the dotted version comparison shared by the GitHub
 * and manifest update sources.
 */
class UpdateVersionTest {

    @Test
    fun normalizeStripsWhitespaceAndLeadingV() {
        assertEquals("1.1.7", UpdateVersion.normalize("  v1.1.7 "))
        assertEquals("1.1.7", UpdateVersion.normalize("V1.1.7"))
        assertEquals("1.1.7", UpdateVersion.normalize("1.1.7"))
    }

    @Test
    fun compareOrdersSegmentsNumerically() {
        assertTrue(UpdateVersion.compare("1.9.9", "1.10.0") < 0)
        assertTrue(UpdateVersion.compare("1.10.0", "1.9.9") > 0)
        assertTrue(UpdateVersion.compare("2.0.0", "1.99.99") > 0)
        assertEquals(0, UpdateVersion.compare("1.1.7", "1.1.7"))
    }

    @Test
    fun missingSegmentsCountAsZero() {
        assertEquals(0, UpdateVersion.compare("1.2", "1.2.0"))
        assertEquals(0, UpdateVersion.compare("1.2.0.0", "1.2"))
        assertTrue(UpdateVersion.compare("1.2.0", "1.2.1") < 0)
    }

    @Test
    fun prereleaseAndBuildSuffixesAreIgnored() {
        assertEquals(0, UpdateVersion.compare("1.2.3-beta", "1.2.3"))
        assertEquals(0, UpdateVersion.compare("1.2.3+5", "1.2.3"))
        assertEquals(0, UpdateVersion.compare("v1.2.3-rc.1", "1.2.3"))
    }

    @Test
    fun isNewerOnlyForStrictlyNewerVersions() {
        assertTrue(UpdateVersion.isNewer("1.1.7", "v1.1.8"))
        assertFalse(UpdateVersion.isNewer("1.1.7", "1.1.7"))
        assertFalse(UpdateVersion.isNewer("1.1.7", "1.1.6"))
        assertFalse(UpdateVersion.isNewer("v1.1.7", "V1.1.7"))
    }

    @Test
    fun unparsableVersionsCompareEqualInsteadOfNewer() {
        assertEquals(0, UpdateVersion.compare("", "1.2.3"))
        assertEquals(0, UpdateVersion.compare("nightly", "1.2.3"))
        assertFalse(UpdateVersion.isNewer("1.1.7", ""))
        assertFalse(UpdateVersion.isNewer("", "1.1.7"))
    }
}

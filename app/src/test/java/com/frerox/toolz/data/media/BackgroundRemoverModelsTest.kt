/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lineup integrity (revamp 2026): ids unique, migration total, pins well-formed.
 * No Android framework needed — pure Kotlin.
 */
class BackgroundModelTest {

    @Test
    fun idsAreUnique() {
        val ids = BackgroundModel.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun defaultIsProDetailOnnx() {
        val d = BackgroundModel.default()
        assertEquals("pro_detail", d.id)
        assertTrue(d.isRecommended)
    }

    @Test
    fun legacyIdsMigrateToLiveModels() {
        val legacy = listOf(
            "selfie_portrait", "selfie_landscape",
            "selfie_multiclass", "modnet_hd", "deeplabv3_objects",
            "instant_selfie",
        )
        for (old in legacy) {
            assertNull("retired id must not resolve: $old", BackgroundModel.fromId(old))
            val target = BackgroundModel.migrateLegacyId(old)
            assertNotNull(BackgroundModel.fromId(target.id))
        }
    }

    @Test
    fun unknownIdFallsBackToDefault() {
        assertEquals(BackgroundModel.default(), BackgroundModel.migrateLegacyId("nope_not_a_model"))
    }

    @Test
    fun shaPinsAreWellFormed() {
        for (m in BackgroundModel.entries) {
            m.expectedSha256?.let { sha ->
                assertEquals("${m.id} sha256 length", 64, sha.length)
                assertTrue("${m.id} sha256 hex", sha.all { it in '0'..'9' || it in 'a'..'f' })
            }
        }
    }

    @Test
    fun onnxModelsHavePinnedHashes() {
        // Unpinned model = silent-substitution risk. The ONNX-only lineup pins every entry.
        for (m in BackgroundModel.entries) {
            assertNotNull("${m.id} must pin expectedSha256", m.expectedSha256)
        }
    }

    @Test
    fun legacyFilesDoNotCollideWithLiveFiles() {
        val live = BackgroundModel.entries.map { it.fileName }.toSet()
        for (legacy in BackgroundModel.LEGACY_FILE_NAMES) {
            assertTrue("legacy file must not shadow a live model: $legacy", legacy !in live)
        }
    }

    @Test
    fun licensesPresent() {
        for (m in BackgroundModel.entries) {
            assertTrue("${m.id} license", m.licenseName.isNotBlank())
            assertTrue("${m.id} license url", m.licenseUrl.startsWith("http"))
        }
    }

    @Test
    fun ultraBirefnetIsPinnedGatedAndLogitBased() {
        val ultra = BackgroundModel.fromId("ultra_birefnet")
        assertNotNull(ultra)
        ultra!!
        assertEquals(1024, ultra.inputSize)
        // Quality-first: no downscaling — tiling preserves the full 1024.
        assertEquals(1024, ultra.inferenceInputSize)
        assertTrue("ultra needs sigmoid first", ultra.onnxPostSigmoid)
        assertTrue("ultra is wifi-gated", ultra.gatedOnWifi)
        assertEquals(224005088L, ultra.expectedSizeBytes)
        assertEquals(
            "5600024376f572a557870a5eb0afb1e5961636bef4e1e22132025467d0f03333",
            ultra.expectedSha256,
        )
    }
}

/**
 * MaskDecoder contracts — the paths the ONNX-only lineup actually exercises.
 */
class MaskDecoderTest {

    @Test
    fun minMaxStretchesWeakResponseToFullRange() {
        // rembg parity: a weak-but-correct response must survive the matting thresholds.
        val out = MaskDecoder.minMaxNormalize(floatArrayOf(0.10f, 0.12f, 0.20f, 0.14f))
        assertEquals(0f, out[0], 1e-6f)
        assertEquals(1f, out[2], 1e-6f)
        assertEquals((0.12f - 0.10f) / 0.10f, out[1], 1e-5f)
        assertEquals((0.14f - 0.10f) / 0.10f, out[3], 1e-5f)
    }

    @Test
    fun minMaxDegenerateInputReturnsZerosNotNaN() {
        // Flat response (e.g. no subject found): honest empty, never NaN explosion.
        val out = MaskDecoder.minMaxNormalize(floatArrayOf(0.3f, 0.3f, 0.3f))
        assertEquals(3, out.size)
        for (v in out) assertEquals(0f, v, 0f)
        assertEquals(0, MaskDecoder.minMaxNormalize(floatArrayOf()).size)
    }

    @Test
    fun sigmoidArrayMatchesScalarSigmoid() {
        val out = MaskDecoder.sigmoidArray(floatArrayOf(0f, 10f, -10f))
        assertEquals(0.5f, out[0], 1e-6f)
        assertTrue(out[1] > 0.9999f)
        assertTrue(out[2] < 0.0001f)
    }
}

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

package com.frerox.toolz.data.media

/**
 * Curated lineup for Background Removal (2026 revamp).
 *
 * All models are downloaded on-demand through the Model Hub — nothing ships in the APK.
 * See MODELS.md (repo root) for the full audit trail: source, license, SHA-256.
 *
 * Tiers:
 * - FAST    — U²-Net+ ONNX: general subjects, small download, CPU-friendly.
 * - PORTRAIT— Robust Video Matting (MobileNetV3, fp32 ONNX): people/pets, hair-level
 *             alpha, single-pass still mode with zero recurrent states.
 * - PRO     — ISNet general-use ONNX: best general quality, large + slow, Wi-Fi gated.
 * - INSTANT — MediaPipe selfie TFLite: rough person blob, fully offline fallback.
 */
enum class InferenceRuntime { LITERT, ONNX }

enum class BackgroundModel(
    val id: String,
    val displayName: String,
    val shortName: String,
    val description: String,
    val sizeLabel: String,
    val downloadUrl: String,
    val fileName: String,
    val runtime: InferenceRuntime,
    /** Square model input. RVM ([PORTRAIT_RVM]) uses this as a long-edge cap (aspect kept). */
    val inputSize: Int,
    val features: List<String>,
    /** SHA-256 pin — enforced at download, marker-cached afterwards. Null = legacy (size sniff only). */
    val expectedSha256: String? = null,
    val expectedSizeBytes: Long = -1L,
    /** True → refuses metered connections unless the user explicitly allows them. */
    val gatedOnWifi: Boolean = false,
    val licenseName: String,
    val licenseUrl: String,
    val isRecommended: Boolean = false,
) {
    FAST_GENERAL(
        id = "fast_general",
        displayName = "Fast • General",
        shortName = "Fast",
        description = "General subjects — people, pets, products. Small, quick, offline after download.",
        sizeLabel = "4.4 MB",
        downloadUrl = "https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx",
        fileName = "u2netp.onnx",
        runtime = InferenceRuntime.ONNX,
        inputSize = 320,
        features = listOf("General subjects", "Tiny download", "Fast on CPU"),
        expectedSha256 = "309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8",
        expectedSizeBytes = 4574861L,
        licenseName = "Apache-2.0 (U-2-Net)",
        licenseUrl = "https://github.com/xuebinqin/U-2-Net/blob/master/LICENSE",
        isRecommended = true,
    ),

    PORTRAIT_RVM(
        id = "portrait_rvm",
        displayName = "Portrait • Detail",
        shortName = "Portrait",
        description = "People and pets with hair-level alpha. Slightly slower, worth it for portraits.",
        sizeLabel = "14.3 MB",
        downloadUrl = "https://github.com/PeterL1n/RobustVideoMatting/releases/download/v1.0.0/rvm_mobilenetv3_fp32.onnx",
        fileName = "rvm_mobilenetv3_fp32.onnx",
        runtime = InferenceRuntime.ONNX,
        inputSize = 512,
        features = listOf("Hair-level alpha", "People & pets", "Studio portraits"),
        expectedSha256 = "88d4531297118f595bf2fd60f6f566aec2e559393802d1f436c380f0cbbd2828",
        expectedSizeBytes = 14975696L,
        licenseName = "GPL-3.0 (RVM)",
        licenseUrl = "https://github.com/PeterL1n/RobustVideoMatting/blob/master/LICENSE.txt",
    ),

    PRO_DETAIL(
        id = "pro_detail",
        displayName = "Pro • Max detail",
        shortName = "Pro",
        description = "Best general quality for tricky edges. Big download, slower — use on Wi-Fi.",
        sizeLabel = "178 MB",
        downloadUrl = "https://github.com/danielgatis/rembg/releases/download/v0.0.0/isnet-general-use.onnx",
        fileName = "isnet-general-use.onnx",
        runtime = InferenceRuntime.ONNX,
        inputSize = 1024,
        features = listOf("Max detail", "Tricky edges", "Wi-Fi download"),
        expectedSha256 = "60920e99c45464f2ba57bee2ad08c919a52bbf852739e96947fbb4358c0d964a",
        expectedSizeBytes = 178648008L,
        gatedOnWifi = true,
        licenseName = "See MODELS.md (ISNet via rembg)",
        licenseUrl = "https://github.com/danielgatis/rembg",
    ),

    INSTANT_SELFIE(
        id = "instant_selfie",
        displayName = "Instant • Rough",
        shortName = "Instant",
        description = "Rough person cutout in a blink. Offline fallback when nothing else fits.",
        sizeLabel = "250 KB",
        downloadUrl = "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite",
        fileName = "selfie_segmenter.tflite",
        runtime = InferenceRuntime.LITERT,
        inputSize = 256,
        features = listOf("Instant", "Tiny", "Offline fallback"),
        expectedSha256 = null,
        expectedSizeBytes = 249537L,
        licenseName = "Apache-2.0 (MediaPipe)",
        licenseUrl = "https://github.com/google-ai-edge/mediapipe/blob/master/LICENSE",
    );

    companion object {
        fun fromId(id: String): BackgroundModel? = entries.find { it.id == id }
        fun default(): BackgroundModel = FAST_GENERAL

        /** Retired pre-revamp ids → their migration target (null = default). */
        fun migrateLegacyId(oldId: String): BackgroundModel = when (oldId) {
            "selfie_portrait", "selfie_landscape" -> INSTANT_SELFIE
            "selfie_multiclass", "modnet_hd" -> PORTRAIT_RVM
            "deeplabv3_objects" -> FAST_GENERAL
            else -> default()
        }

        fun isLegacyId(id: String): Boolean = fromId(id) == null

        /** Stale files from the retired lineup, reclaimed once on upgrade. */
        val LEGACY_FILE_NAMES: List<String> = listOf(
            "selfie_segmenter_landscape.tflite",
            "selfie_multiclass_256x256.tflite",
            "deeplabv3.tflite",
            "modnet.tflite",
        )
    }
}

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
 * Pro Intelligence Lineup for Background Removal.
 * All models are downloaded on-demand through the Model Hub to maintain a compact APK footprint.
 *
 * Revamp 2026: fixed broken tfhub URL, pinned HuggingFace commit, added integrity fields.
 * Keep GCS MediaPipe urls on versioned `latest` (stable) but expose etag for verification.
 */
enum class BackgroundModel(
    val id: String,
    val displayName: String,
    val description: String,
    val sizeLabel: String,
    val resolution: Int,
    val downloadUrl: String,
    val fileName: String,
    val features: List<String>,
    /** Optional expected ETag / size for integrity check (no hard-fail if null) */
    val expectedEtag: String? = null,
    val expectedSizeBytes: Long = -1L,
    /** Human-readable recommendation badge */
    val isRecommended: Boolean = false,
) {
    SELFIE_PORTRAIT(
        id = "selfie_portrait",
        displayName = "Selfie Portrait • Fast",
        description = "Ultra-fast portrait segmentation. Best for selfies, headshots, and single-person portraits.",
        sizeLabel = "250 KB",
        resolution = 256,
        downloadUrl = "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite",
        fileName = "selfie_segmenter.tflite",
        features = listOf("Studio Portrait", "Instant Speed", "Low Memory"),
        expectedEtag = "\"3b2e3e1cfc7d31538caf00ff4e0fba8c\"",
        expectedSizeBytes = 249537L,
        isRecommended = true,
    ),

    SELFIE_LANDSCAPE(
        id = "selfie_landscape",
        displayName = "Selfie Landscape • Group",
        description = "Optimized for widescreen, full-body, and multi-person group shots.",
        sizeLabel = "250 KB",
        resolution = 256,
        downloadUrl = "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter_landscape/float16/latest/selfie_segmenter_landscape.tflite",
        fileName = "selfie_segmenter_landscape.tflite",
        features = listOf("Full-Body & Group", "Widescreen Native", "Fast Speed"),
        expectedEtag = "\"880c84cde97f80aa20d1b09d09c20113\"",
        expectedSizeBytes = 250177L,
    ),

    SELFIE_MULTICLASS(
        id = "selfie_multiclass",
        displayName = "Anatomical Matte • 6-Channel",
        description = "6-channel segmentation isolating body, hair, clothing, and accessories. Best detail.",
        sizeLabel = "15.6 MB",
        resolution = 256,
        downloadUrl = "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_multiclass_256x256/float32/latest/selfie_multiclass_256x256.tflite",
        fileName = "selfie_multiclass_256x256.tflite",
        features = listOf("Anatomical 6-Chan", "Hair & Clothing", "Studio Quality"),
        expectedEtag = "\"6ca6a40d84bcb910420a1a43a295100a\"",
        expectedSizeBytes = 16371837L,
    ),

    DEEPLABV3_OBJECTS(
        id = "deeplabv3_objects",
        displayName = "Universal Objects • DeepLabV3",
        description = "General object matting for pets, products, vehicles, plants and 20+ PASCAL classes.",
        sizeLabel = "2.7 MB",
        resolution = 257,
        // FIX 2026-08: tfhub.dev deprecated (404). Use stable GCS mirror — same model, GPU variant.
        downloadUrl = "https://storage.googleapis.com/download.tensorflow.org/models/tflite/gpu/deeplabv3_257_mv_gpu.tflite",
        fileName = "deeplabv3.tflite",
        features = listOf("Pets & Animals", "Products & Vehicles", "20+ Classes"),
        expectedEtag = "\"4a24db5a5fb05c47586a1197765e8548\"",
        expectedSizeBytes = 2779264L,
    ),

    MODNET_HD(
        id = "modnet_hd",
        displayName = "Portrait HD • Detail",
        description = "Hair & clothing detail via 6-channel matte — best for fine strands. Stable GCS.",
        sizeLabel = "15.6 MB",
        resolution = 256,
        // FIX 2026-08 v2: HF MODNet 24.9 MB proved unstable (redirects/rate-limit). Switch to GCS multiclass (same hair-detail quality, stable).
        downloadUrl = "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_multiclass_256x256/float32/latest/selfie_multiclass_256x256.tflite",
        fileName = "modnet.tflite",
        features = listOf("Hair & Clothing Detail", "6-Channel Matte", "Stable GCS"),
        expectedEtag = "\"6ca6a40d84bcb910420a1a43a295100a\"",
        expectedSizeBytes = 16371837L,
    );

    companion object {
        fun fromId(id: String): BackgroundModel? = entries.find { it.id == id }
        fun default(): BackgroundModel = SELFIE_PORTRAIT
    }
}

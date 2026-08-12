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
 */
enum class BackgroundModel(
    val id: String,
    val displayName: String,
    val description: String,
    val sizeLabel: String,
    val resolution: Int,
    val downloadUrl: String,
    val fileName: String,
    val features: List<String>
) {
    SELFIE_PORTRAIT(
        id = "selfie_portrait",
        displayName = "MediaPipe Selfie Portrait",
        description = "Ultra-fast SOTA portrait segmentation engine. Instant response and ultra-low battery usage.",
        sizeLabel = "250 KB",
        resolution = 256,
        downloadUrl = "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite",
        fileName = "selfie_segmenter.tflite",
        features = listOf("Studio Portrait", "Instant Speed", "Low Memory")
    ),

    SELFIE_LANDSCAPE(
        id = "selfie_landscape",
        displayName = "MediaPipe Selfie Landscape",
        description = "Optimized for widescreen photos, full-body portraits, and multi-person group shots.",
        sizeLabel = "250 KB",
        resolution = 256,
        downloadUrl = "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter_landscape/float16/latest/selfie_segmenter_landscape.tflite",
        fileName = "selfie_segmenter_landscape.tflite",
        features = listOf("Full-Body & Group", "Widescreen Native", "Fast Speed")
    ),

    SELFIE_MULTICLASS(
        id = "selfie_multiclass",
        displayName = "MediaPipe Studio Anatomical Matte",
        description = "Anatomical 6-channel segmentation engine isolating body, hair, clothing, and accessories.",
        sizeLabel = "15.6 MB",
        resolution = 256,
        downloadUrl = "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_multiclass_256x256/float32/latest/selfie_multiclass_256x256.tflite",
        fileName = "selfie_multiclass_256x256.tflite",
        features = listOf("Anatomical 6-Chan", "Hair & Clothing", "Studio Quality")
    ),

    DEEPLABV3_OBJECTS(
        id = "deeplabv3_objects",
        displayName = "DeepLabV3 Universal Object Engine",
        description = "Isolates pets, animals, vehicles, furniture, products, plants, and 20+ object classes.",
        sizeLabel = "2.65 MB",
        resolution = 257,
        downloadUrl = "https://tfhub.dev/tensorflow/lite-model/deeplabv3/1/metadata/2?lite-format=tflite",
        fileName = "deeplabv3.tflite",
        features = listOf("Pets & Animals", "Vehicles & Products", "20+ Object Classes")
    ),

    MODNET_HD(
        id = "modnet_hd",
        displayName = "MODNet LiteRT High-Res Matting",
        description = "Dedicated 512p photographic portrait matting engine for continuous alpha details.",
        sizeLabel = "24.9 MB",
        resolution = 512,
        downloadUrl = "https://huggingface.co/litert-community/MODNet-LiteRT/resolve/main/modnet.tflite",
        fileName = "modnet.tflite",
        features = listOf("512p Native", "Continuous Alpha", "Fine Hair Strands")
    );
}

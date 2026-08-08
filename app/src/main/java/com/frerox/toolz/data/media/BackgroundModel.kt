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
 * Metadata for all supported background removal models.
 * All models are downloaded on-demand to maintain a compact APK size.
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
    BIREFNET_LITE(
        id = "birefnet_lite",
        displayName = "BiRefNet Lite (2026 SOTA)",
        description = "The absolute best for extreme detail. Preserves hair strands, translucent fabrics, and complex boundaries.",
        sizeLabel = "18 MB",
        resolution = 512,
        downloadUrl = "https://huggingface.co/ZhengPeng7/BiRefNet_lite-matting/resolve/main/birefnet_lite_matting.tflite",
        fileName = "birefnet_lite_512.tflite",
        features = listOf("Studio Quality", "Hair & Glass", "SOTA")
    ),

    RMBG_2(
        id = "rmbg_2",
        displayName = "BRIA RMBG 2.0",
        description = "Industry standard for e-commerce and objects. Handles complex backgrounds and product shadows effortlessly.",
        sizeLabel = "22 MB",
        resolution = 1024,
        downloadUrl = "https://huggingface.co/litert-community/bria-rmbg-2.0/resolve/main/bria-rmbg-2.0.tflite",
        fileName = "rmbg_2_1024_int8.tflite",
        features = listOf("E-Commerce", "1024p Native", "Shadow Aware")
    ),

    MODNET_HD(
        id = "modnet_hd",
        displayName = "MODNet HD Matting",
        description = "Dedicated portrait matting engine. Produces a true 8-bit continuous alpha map specifically for people.",
        sizeLabel = "13 MB",
        resolution = 512,
        downloadUrl = "https://github.com/shubham0204/Portrait_Segmentation_TFLite/raw/main/app/src/main/assets/modnet_photographic_portrait_matting.tflite",
        fileName = "modnet_hd.tflite",
        features = listOf("Portrait Matte", "Continuous Alpha")
    ),

    INSPYRENET_MOBILE(
        id = "inspyrenet_mobile",
        displayName = "InSPyReNet Mobile",
        description = "Uses image pyramids to separate subjects from cluttered or low-contrast backgrounds where other models fail.",
        sizeLabel = "15 MB",
        resolution = 384,
        downloadUrl = "https://huggingface.co/signature-ai/InSPyReNet/resolve/main/inspyrenet_mobile.tflite",
        fileName = "inspyrenet_384.tflite",
        features = listOf("Cluttered BG", "High Contrast Mask")
    ),

    U2NET_FULL(
        id = "u2net_full",
        displayName = "U2-Net Full (Unpruned)",
        description = "The uncompressed 176MB original U2-Net. High compute cost, but vastly superior boundary accuracy over U2Net-P.",
        sizeLabel = "176 MB",
        resolution = 320,
        downloadUrl = "https://github.com/shubham0204/BackgroundRemoval-Android/raw/master/app/src/main/assets/u2net.tflite",
        fileName = "u2net_full.tflite",
        features = listOf("Universal", "Zero Compression Loss")
    );
}

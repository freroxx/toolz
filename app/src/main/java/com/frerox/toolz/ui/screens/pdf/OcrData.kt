package com.frerox.toolz.ui.screens.pdf

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OcrPageData(
    val pageIndex: Int,
    val blocks: List<OcrBlockData>
)

@JsonClass(generateAdapter = true)
data class OcrBlockData(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float
)

@JsonClass(generateAdapter = true)
data class OcrDocumentData(
    val pages: List<OcrPageData>
)

data class FormulaOcrResult(
    val plainText: String,
    val latex: String,
    val confidence: Float
)

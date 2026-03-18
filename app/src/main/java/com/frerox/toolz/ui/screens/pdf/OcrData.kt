package com.frerox.toolz.ui.screens.pdf

import com.squareup.moshi.JsonClass

// ─────────────────────────────────────────────────────────────
//  Language options
// ─────────────────────────────────────────────────────────────

enum class OcrLanguage(val displayName: String) {
    LATIN      ("English / Latin / European"),
    CHINESE    ("Chinese (Simplified / Traditional)"),
    JAPANESE   ("Japanese"),
    KOREAN     ("Korean"),
    DEVANAGARI ("Devanagari — Hindi / Sanskrit"),
}

// ─────────────────────────────────────────────────────────────
//  Block type classification
// ─────────────────────────────────────────────────────────────

enum class OcrBlockType {
    PARAGRAPH, FORMULA, TABLE_CELL, HEADER, LIST_ITEM, OTHER,
}

// ─────────────────────────────────────────────────────────────
//  Domain model — returned by FormulaOcrProcessor
//  Coordinates are in pixel-space of the processed bitmap.
// ─────────────────────────────────────────────────────────────

data class OcrBlock(
    val text      : String,
    val left      : Int,
    val top       : Int,
    val right     : Int,
    val bottom    : Int,
    val confidence: Float,
    val type      : OcrBlockType = OcrBlockType.PARAGRAPH,
)

// ─────────────────────────────────────────────────────────────
//  Persistence models — Moshi-serialised to Room
// ─────────────────────────────────────────────────────────────

/**
 * OCR block stored in the database.  Float coordinates allow the
 * [OcrOverlay] composable to scale with `scaleX = viewWidth / bitmapWidth`.
 */
@JsonClass(generateAdapter = true)
data class OcrBlockData(
    val text      : String,
    val left      : Float,
    val top       : Float,
    val right     : Float,
    val bottom    : Float,
    val confidence: Float        = -1f,
    val type      : OcrBlockType = OcrBlockType.PARAGRAPH,
)

@JsonClass(generateAdapter = true)
data class OcrPageData(
    val pageIndex: Int,
    val blocks   : List<OcrBlockData>,
    val fullText : String? = null,
)

@JsonClass(generateAdapter = true)
data class OcrDocumentData(
    val pages: List<OcrPageData>,
)

// ─────────────────────────────────────────────────────────────
//  Full OCR pass result
// ─────────────────────────────────────────────────────────────

data class FormulaOcrResult(
    val rawText    : String,
    val latexText  : String,
    val confidence : Float,
    val blocks     : List<OcrBlock> = emptyList(),
    val language   : OcrLanguage    = OcrLanguage.LATIN,
    val aiEnhanced : Boolean        = false,
    val pageNumber : Int            = 1,
) {
    val hasContent: Boolean get() = rawText.isNotBlank()
    val wordCount : Int     get() = rawText.split(Regex("\\s+")).count { it.isNotBlank() }

    companion object {
        fun empty(language: OcrLanguage = OcrLanguage.LATIN) =
            FormulaOcrResult(rawText = "", latexText = "", confidence = 0f, language = language)
    }
}

// ─────────────────────────────────────────────────────────────
//  Processing options
// ─────────────────────────────────────────────────────────────

data class OcrOptions(
    val language       : OcrLanguage = OcrLanguage.LATIN,
    val enableAiCleaner: Boolean     = false,
    val targetDpi      : Int         = 200,
    val binarise       : Boolean     = true,
)
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

package com.frerox.toolz.ui.screens.pdf

import com.squareup.moshi.JsonClass

// ─────────────────────────────────────────────────────────────
//  Language options
// ─────────────────────────────────────────────────────────────

enum class OcrLanguage(val displayName: String, val tessCode: String) {
    LATIN      ("All Supported (EN / FR / PT / ES)", "eng+fra+por+spa"),
    ENGLISH    ("English", "eng"),
    FRENCH     ("Français", "fra"),
    PORTUGUESE ("Português (Brasil)", "por"),
    SPANISH    ("Español", "spa"),
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
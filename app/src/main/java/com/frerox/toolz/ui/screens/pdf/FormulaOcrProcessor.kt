package com.frerox.toolz.ui.screens.pdf

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormulaOcrProcessor @Inject constructor() {

    fun fromRecognizedText(rawText: String): FormulaOcrResult {
        val normalized = rawText
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("√", "\\sqrt")
            .replace(Regex("\\s+"), " ")
            .trim()

        val latex = normalized
            .replace(Regex("([a-zA-Z0-9])\\^([a-zA-Z0-9]+)"), "$1^{$2}")
            .replace(Regex("([a-zA-Z0-9]+)/([a-zA-Z0-9]+)"), "\\\\frac{$1}{$2}")
            .replace(Regex("\\bsqrt\\s*\\(([^)]+)\\)"), "\\\\sqrt{$1}")

        val confidence = estimateConfidence(normalized)
        return FormulaOcrResult(normalized, latex, confidence)
    }

    private fun estimateConfidence(text: String): Float {
        if (text.isBlank()) return 0f
        val mathCharCount = text.count { it.isDigit() || it in "+-*/=^(){}[]\\" }
        val ratio = mathCharCount.toFloat() / text.length.toFloat()
        return (0.45f + ratio).coerceIn(0f, 0.99f)
    }
}

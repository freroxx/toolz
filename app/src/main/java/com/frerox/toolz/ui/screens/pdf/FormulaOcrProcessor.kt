package com.frerox.toolz.ui.screens.pdf

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormulaOcrProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImage(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val result = recognizer.process(image).await()
            result.text
        } catch (e: Exception) {
            ""
        }
    }

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

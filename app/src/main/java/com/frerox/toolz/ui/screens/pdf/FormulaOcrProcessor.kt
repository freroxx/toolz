package com.frerox.toolz.ui.screens.pdf

import android.content.Context
import android.graphics.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class OcrLanguage(val displayName: String) {
    LATIN("English/Latin"), // Latin handles English, French, Spanish, German, etc.
    CHINESE("Chinese"),
    JAPANESE("Japanese"),
    KOREAN("Korean"),
    DEVANAGARI("Devanagari")
}

@Singleton
class FormulaOcrProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recognizers = mutableMapOf<OcrLanguage, TextRecognizer>()

    private fun getRecognizer(language: OcrLanguage): TextRecognizer {
        return recognizers.getOrPut(language) {
            val options = when (language) {
                OcrLanguage.LATIN -> TextRecognizerOptions.DEFAULT_OPTIONS
                OcrLanguage.CHINESE -> ChineseTextRecognizerOptions.Builder().build()
                OcrLanguage.JAPANESE -> JapaneseTextRecognizerOptions.Builder().build()
                OcrLanguage.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
                OcrLanguage.DEVANAGARI -> DevanagariTextRecognizerOptions.Builder().build()
            }
            TextRecognition.getClient(options)
        }
    }

    suspend fun processImage(bitmap: Bitmap, language: OcrLanguage = OcrLanguage.LATIN, region: Rect? = null): Text = withContext(Dispatchers.Default) {
        val targetBitmap = if (region != null) {
            // Ensure region is within bounds
            val safeRegion = Rect(
                region.left.coerceIn(0, bitmap.width),
                region.top.coerceIn(0, bitmap.height),
                region.right.coerceIn(0, bitmap.width),
                region.bottom.coerceIn(0, bitmap.height)
            )
            if (safeRegion.width() > 0 && safeRegion.height() > 0) {
                Bitmap.createBitmap(bitmap, safeRegion.left, safeRegion.top, safeRegion.width(), safeRegion.height())
            } else {
                bitmap
            }
        } else {
            bitmap
        }

        val processedBitmap = preprocessForOcr(targetBitmap)
        val image = InputImage.fromBitmap(processedBitmap, 0)
        
        try {
            getRecognizer(language).process(image).await()
        } catch (e: Exception) {
            throw e
        } finally {
            if (targetBitmap != bitmap) targetBitmap.recycle()
            processedBitmap.recycle()
        }
    }

    private fun preprocessForOcr(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        
        // ML Kit works best with high contrast
        val cm = ColorMatrix(floatArrayOf(
            1.8f, 0f, 0f, 0f, -50f,
            0f, 1.8f, 0f, 0f, -50f,
            0f, 0f, 1.8f, 0f, -50f,
            0f, 0f, 0f, 1f, 0f
        ))
        
        val grayscale = ColorMatrix()
        grayscale.setSaturation(0f)
        cm.postConcat(grayscale)
        
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    fun getStructuredText(result: Text): String {
        // Sort blocks by top coordinate, then left (handles multi-column better)
        val blocks = result.textBlocks.sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
        
        val sb = StringBuilder()
        blocks.forEach { block ->
            if (block.text.isNotBlank()) {
                sb.append(block.text).append("\n\n")
            }
        }
        return sb.toString().trim()
    }

    fun fromRecognizedText(rawText: String): FormulaOcrResult {
        val normalized = rawText
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("—", "-")
            .replace("√", "\\sqrt")
            .replace("π", "pi")
            .replace("∑", "sum")
            .replace("∫", "int")
            .replace("∞", "inf")
            .replace("≈", "approx")
            .replace(Regex("\\s+"), " ")
            .trim()

        var latex = normalized
            .replace(Regex("([a-zA-Z0-9])\\^([a-zA-Z0-9]+)"), "$1^{$2}")
            .replace(Regex("([a-zA-Z0-9]+)/([a-zA-Z0-9]+)"), "\\\\frac{$1}{$2}")
            
        val symbolMap = mapOf("alpha" to "\\alpha", "beta" to "\\beta", "pi" to "\\pi", "sum" to "\\sum", "int" to "\\int")
        symbolMap.forEach { (key, value) -> latex = latex.replace(key, value, ignoreCase = true) }

        return FormulaOcrResult(normalized, latex, 0.9f)
    }
}

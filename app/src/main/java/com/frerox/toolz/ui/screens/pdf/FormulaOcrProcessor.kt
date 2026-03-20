package com.frerox.toolz.ui.screens.pdf

import android.content.Context
import android.graphics.*
import android.util.Log
import com.frerox.toolz.data.ai.AiSettingsManager
import com.frerox.toolz.data.ai.MessageContent
import com.frerox.toolz.data.ai.OpenAiMessage
import com.frerox.toolz.data.ai.OpenAiRequest
import com.frerox.toolz.data.ai.OpenAiService
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
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val TAG         = "FormulaOcrProcessor"
private const val GROQ_URL    = "https://api.groq.com/openai/v1/chat/completions"
private const val GROQ_MODEL  = "llama-3.3-70b-versatile"
private const val MIN_SIDE_PX = 1000
private const val MAX_SIDE_PX = 4000

@Singleton
class FormulaOcrProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openAiService    : OpenAiService,
    private val aiSettingsManager: AiSettingsManager,
) {

    private val recognizers = mutableMapOf<OcrLanguage, TextRecognizer>()

    private fun getRecognizer(language: OcrLanguage): TextRecognizer =
        recognizers.getOrPut(language) {
            TextRecognition.getClient(when (language) {
                OcrLanguage.LATIN      -> TextRecognizerOptions.DEFAULT_OPTIONS
                OcrLanguage.CHINESE    -> ChineseTextRecognizerOptions.Builder().build()
                OcrLanguage.JAPANESE   -> JapaneseTextRecognizerOptions.Builder().build()
                OcrLanguage.KOREAN     -> KoreanTextRecognizerOptions.Builder().build()
                OcrLanguage.DEVANAGARI -> DevanagariTextRecognizerOptions.Builder().build()
            })
        }

    fun close() {
        recognizers.values.forEach { it.close() }
        recognizers.clear()
    }

    suspend fun processImage(
        bitmap : Bitmap,
        options: OcrOptions = OcrOptions(),
        region : Rect?      = null,
    ): FormulaOcrResult = withContext(Dispatchers.Default) {

        val cropped     = safeCrop(bitmap, region)
        val ownsCropped = cropped !== bitmap

        try {
            val scaled     = scaleForOcr(cropped)
            val ownsScaled = scaled !== cropped

            try {
                val pre     = if (options.binarise) preprocessAdaptive(scaled) else scaled
                val ownsPre = pre !== scaled

                val pass1 = try {
                    runOcr(pre, options.language)
                } catch (e: Exception) {
                    Log.w(TAG, "Pass 1 failed: ${e.message}")
                    null
                } finally {
                    if (ownsPre) pre.recycle()
                }

                val p1conf  = pass1?.let { computeConfidence(it) } ?: 0f
                val chosen  = if (p1conf < 0.60f) {
                    Log.d(TAG, "Pass 1 conf $p1conf — trying colour pass")
                    val pass2 = try { runOcr(scaled, options.language) }
                    catch (e: Exception) { null }
                    val p2conf = pass2?.let { computeConfidence(it) } ?: 0f
                    when {
                        pass2 == null              -> pass1
                        pass1 == null              -> pass2
                        p2conf > p1conf + 0.05f    -> pass2
                        else                       -> pass1
                    }
                } else pass1

                if (chosen == null) {
                    Log.e(TAG, "All passes failed")
                    return@withContext FormulaOcrResult.empty(options.language)
                }

                val confidence = computeConfidence(chosen)
                val blocks     = buildOcrBlocks(chosen)
                val rawText    = buildStructuredText(chosen, scaled.width)
                val latex      = normaliseToLatex(rawText)

                val base = FormulaOcrResult(
                    rawText    = rawText,
                    latexText  = latex,
                    confidence = confidence,
                    blocks     = blocks,
                    language   = options.language,
                )

                if (options.enableAiCleaner && rawText.isNotBlank()) {
                    val cleaned = runAiCleaner(rawText, options.language)
                    if (cleaned != null) {
                        return@withContext base.copy(
                            rawText    = cleaned,
                            latexText  = normaliseToLatex(cleaned),
                            aiEnhanced = true,
                        )
                    }
                }

                base

            } finally {
                if (ownsScaled) scaled.recycle()
            }
        } finally {
            if (ownsCropped) cropped.recycle()
        }
    }

    private fun safeCrop(bitmap: Bitmap, region: Rect?): Bitmap {
        if (region == null) return bitmap
        val safe = Rect(
            region.left.coerceIn(0, bitmap.width),
            region.top.coerceIn(0, bitmap.height),
            region.right.coerceIn(0, bitmap.width),
            region.bottom.coerceIn(0, bitmap.height),
        )
        if (safe.width() <= 0 || safe.height() <= 0) return bitmap
        return Bitmap.createBitmap(bitmap, safe.left, safe.top, safe.width(), safe.height())
    }

    private fun scaleForOcr(bitmap: Bitmap): Bitmap {
        val minSide = minOf(bitmap.width, bitmap.height)
        if (minSide >= MIN_SIDE_PX) return bitmap
        val scale   = (MIN_SIDE_PX.toFloat() / minSide).coerceIn(1.5f, 3.0f)
        val newW    = (bitmap.width  * scale).toInt().coerceAtMost(MAX_SIDE_PX)
        val newH    = (bitmap.height * scale).toInt().coerceAtMost(MAX_SIDE_PX)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun preprocessAdaptive(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var minL = 255; var maxL = 0; var sumL = 0L; var n = 0
        for (i in pixels.indices step 4) {
            val c = pixels[i]
            val l = (0.299f * ((c shr 16) and 0xFF) +
                    0.587f * ((c shr 8)  and 0xFF) +
                    0.114f * ( c         and 0xFF)).toInt()
            if (l < minL) minL = l; if (l > maxL) maxL = l; sumL += l; n++
        }
        val avg   = if (n > 0) (sumL / n).toInt() else 128
        val range = (maxL - minL).coerceAtLeast(1)

        val contrast = when {
            range > 200 -> 1.15f
            range > 120 -> 1.5f
            range > 60  -> 2.0f
            else        -> 2.5f
        }
        val offset = (128f - avg * contrast).coerceIn(-50f, 30f)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cm  = ColorMatrix().apply { setSaturation(0f) }
        cm.postConcat(ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, offset,
            0f, contrast, 0f, 0f, offset,
            0f, 0f, contrast, 0f, offset,
            0f, 0f, 0f,      1f, 0f,
        )))
        Canvas(out).drawBitmap(bitmap, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    private suspend fun runOcr(bitmap: Bitmap, language: OcrLanguage): Text =
        getRecognizer(language).process(InputImage.fromBitmap(bitmap, 0)).await()

    private fun computeConfidence(result: Text): Float {
        if (result.textBlocks.isEmpty()) return 0f
        var total = 0f; var n = 0
        for (block in result.textBlocks)
            for (line in block.lines)
                for (el in line.elements) {
                    val c = el.confidence
                    if (c != null && c >= 0f) { total += c; n++ }
                }
        return if (n > 0) total / n else 0.5f
    }

    private fun buildOcrBlocks(result: Text): List<OcrBlock> =
        result.textBlocks.map { block ->
            val box = block.boundingBox
            OcrBlock(
                text       = block.text,
                left       = box?.left   ?: 0,
                top        = box?.top    ?: 0,
                right      = box?.right  ?: 0,
                bottom     = box?.bottom ?: 0,
                confidence = computeBlockConf(block),
                type       = classifyBlock(block.text, box),
            )
        }

    private fun computeBlockConf(block: Text.TextBlock): Float {
        var t = 0f; var n = 0
        for (l in block.lines) for (el in l.elements) {
            val c = el.confidence
            if (c != null && c >= 0f) { t += c; n++ }
        }
        return if (n > 0) t / n else -1f
    }

    private fun classifyBlock(text: String, box: Rect?): OcrBlockType {
        val t = text.trim()
        if (t.contains(Regex("[+\\-*/=^√∑∫πΣΩαβγδ]")))  return OcrBlockType.FORMULA
        if (t.length < 80 && t == t.uppercase() && t.length > 3) return OcrBlockType.HEADER
        if (t.startsWith("•") || t.startsWith("-") || t.matches(Regex("^\\d+[.)].*")))
            return OcrBlockType.LIST_ITEM
        return OcrBlockType.PARAGRAPH
    }

    /**
     * Enhanced structured text extraction.
     * Groups lines by vertical proximity and preserves basic horizontal layout (indents, columns).
     */
    private fun buildStructuredText(result: Text, imageWidth: Int): String {
        if (result.textBlocks.isEmpty()) return ""
        
        // Flatten all lines across blocks for better global ordering
        val allLines = result.textBlocks.flatMap { it.lines }
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))

        if (allLines.isEmpty()) return ""

        val sb = StringBuilder()
        var lastLine: Text.Line? = null
        val columnThreshold = imageWidth * 0.15f // Distance to consider as a significant horizontal gap

        allLines.forEach { line ->
            val box = line.boundingBox ?: return@forEach
            val lastBox = lastLine?.boundingBox

            if (lastBox != null) {
                val verticalGap = box.top - lastBox.bottom
                val horizontalGap = box.left - lastBox.right
                val lineHeights = (box.height() + lastBox.height()) / 2
                
                // New paragraph if vertical gap is large
                if (verticalGap > lineHeights * 1.2) {
                    sb.append("\n\n")
                } else if (verticalGap > lineHeights * 0.3) {
                    sb.append("\n")
                } else if (horizontalGap > columnThreshold) {
                    sb.append("    ") // Column-like spacing
                } else {
                    sb.append(" ") // Continuation on same semantic line
                }
            }
            
            val trimmed = line.text.trim()
            if (trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.matches(Regex("^\\d+[.)].*"))) {
                if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                sb.append(trimmed)
            } else {
                sb.append(trimmed)
            }
            
            lastLine = line
        }

        return sb.toString().trim()
    }

    fun normaliseToLatex(rawText: String): String {
        var text = rawText
            .replace("×","\\times ").replace("·","\\cdot ").replace("÷","\\div ")
            .replace("−"," - ")     .replace("—"," - ")   .replace("√","\\sqrt")
            .replace("π","\\pi ")   .replace("∑","\\sum ").replace("∫","\\int ")
            .replace("∞","\\infty ").replace("≈","\\approx ").replace("≤","\\leq ")
            .replace("≥","\\geq ")  .replace("≠","\\neq ").replace("±","\\pm ")
            .replace("∏","\\prod ").replace("∂","\\partial ").replace("∇","\\nabla ")
            .replace("∈","\\in ")   .replace("→","\\to ") .replace("⇒","\\Rightarrow ")
            .replace("α","\\alpha ").replace("β","\\beta ").replace("γ","\\gamma ")
            .replace("δ","\\delta ").replace("ε","\\epsilon ").replace("θ","\\theta ")
            .replace("λ","\\lambda ").replace("μ","\\mu ").replace("σ","\\sigma ")
            .replace("φ","\\phi ").replace("ψ","\\psi ").replace("ω","\\omega ")
            .replace(Regex("\\s+"), " ").trim()

        text = text.replace(Regex("([a-zA-Z0-9])\\^([a-zA-Z0-9]{2,})"), "$1^{$2}")
        text = text.replace(Regex("([a-zA-Z0-9])_([a-zA-Z0-9]{2,})"), "$1_{$2}")
        text = text.replace(Regex("(?<![a-zA-Z])([a-zA-Z0-9]+)/([a-zA-Z0-9]+)(?![a-zA-Z])"), "\\\\frac{$1}{$2}")

        return text
    }

    suspend fun runAiCleaner(rawText: String, language: OcrLanguage): String? {
        val key = aiSettingsManager.resolveApiKeyWithRemoteSync("Groq").value
        if (key.isBlank()) return null
        val truncated = if (rawText.length > 10_000) rawText.take(10_000) + "…" else rawText
        return try {
            withContext(Dispatchers.IO) {
                runGroqRequest(key) { requestKey ->
                    openAiService.getChatCompletion(
                        url        = GROQ_URL,
                        authHeader = "Bearer $requestKey",
                        request    = OpenAiRequest(
                        model     = GROQ_MODEL,
                        messages  = listOf(
                            OpenAiMessage("system", MessageContent.Text(
                                "You are an expert OCR corrector. Fix typos and structure the text professionally.\n" +
                                "1. Fix broken words and common OCR errors (I/l/1, 0/O).\n" +
                                "2. Use Markdown: # Titles, ## Sections, **Bold** for key terms, *Italic* for emphasis.\n" +
                                "3. Use bullet points for lists and code blocks for formulas/code.\n" +
                                "4. Preserve all original data and meaning.\n" +
                                "Return the ENHANCED MARKDOWN text only."
                            )),
                            OpenAiMessage("user", MessageContent.Text(truncated)),
                        ),
                        maxTokens = 4_096,
                        ),
                    ).choices.firstOrNull()?.message?.content?.trim()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AI OCR cleaner failed: ${e.message}")
            null
        }
    }

    suspend fun summarizePdf(text: String): String? {
        val key = aiSettingsManager.resolveApiKeyWithRemoteSync("Groq").value
        if (key.isBlank()) return null
        val truncated = if (text.length > 12_000) text.take(12_000) + "\n…[truncated]" else text
        return try {
            withContext(Dispatchers.IO) {
                runGroqRequest(key) { requestKey ->
                    openAiService.getChatCompletion(
                        url        = GROQ_URL,
                        authHeader = "Bearer $requestKey",
                        request    = OpenAiRequest(
                        model     = GROQ_MODEL,
                        messages  = listOf(
                            OpenAiMessage("system", MessageContent.Text(
                                "Summarise the PDF text clearly using Markdown.\n" +
                                "Structure:\n" +
                                "# Overview\nOne sentence summary.\n\n" +
                                "## Key Insights\n• Bullet points highlighting major facts.\n\n" +
                                "## Conclusion\nFinal takeaway.\n\n" +
                                "Use **bold** for emphasis. Keep it concise."
                            )),
                            OpenAiMessage("user", MessageContent.Text(
                                "Summarise this document:\n\n$truncated"
                            )),
                        ),
                        maxTokens = 1_536,
                        ),
                    ).choices.firstOrNull()?.message?.content?.trim()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PDF summarisation failed: ${e.message}")
            null
        }
    }

    private suspend fun <T> runGroqRequest(
        initialKey: String,
        requestBlock: suspend (String) -> T,
    ): T {
        try {
            return requestBlock(initialKey)
        } catch (e: HttpException) {
            if (e.code() == 401 && !aiSettingsManager.hasUserApiKey("Groq")) {
                val refreshed = aiSettingsManager.refreshRemoteKeyAfterAuthFailure("Groq", initialKey)
                if (refreshed.source == com.frerox.toolz.data.ai.ApiKeySource.REMOTE &&
                    refreshed.value.isNotBlank() &&
                    refreshed.value != initialKey
                ) {
                    return requestBlock(refreshed.value)
                }
                throw IllegalStateException(
                    "The Toolz default key for Groq is unavailable. Refresh keys or add your own key in AI settings."
                )
            }
            throw e
        }
    }
}

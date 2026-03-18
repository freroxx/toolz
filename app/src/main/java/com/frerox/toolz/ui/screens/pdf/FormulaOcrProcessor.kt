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
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG         = "FormulaOcrProcessor"
private const val GROQ_URL    = "https://api.groq.com/openai/v1/chat/completions"
private const val GROQ_MODEL  = "llama-3.3-70b-versatile"
/** Upscale bitmaps with shorter side below this threshold before recognition. */
private const val MIN_SIDE_PX = 900
/** Cap after upscaling to prevent OOM on large pages. */
private const val MAX_SIDE_PX = 3_500

@Singleton
class FormulaOcrProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openAiService    : OpenAiService,
    private val aiSettingsManager: AiSettingsManager,
) {

    // ── Recogniser pool ───────────────────────────────────────────────────

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

    // ── Main entry point ──────────────────────────────────────────────────

    /**
     * Full pipeline:
     * 1. Crop to [region] if provided
     * 2. Upscale if the image is too small for reliable recognition
     * 3. Pass 1 — OCR on the (optionally binarised) image
     * 4. Pass 2 — OCR on the colour image if Pass 1 confidence < 0.55
     * 5. Build [FormulaOcrResult] with pixel-space bounding boxes
     * 6. Groq cleanup if [OcrOptions.enableAiCleaner] is set
     */
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
                // Pass 1 — adaptive preprocessing
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

                // Pass 2 — raw colour if pass 1 is weak
                val p1conf  = pass1?.let { computeConfidence(it) } ?: 0f
                val chosen  = if (p1conf < 0.55f) {
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

    // ── Bitmap helpers ─────────────────────────────────────────────────────

    private fun safeCrop(bitmap: Bitmap, region: Rect?): Bitmap {
        if (region == null) return bitmap
        val safe = Rect(
            region.left.coerceIn(0, bitmap.width),
            region.top.coerceIn(0, bitmap.height),
            region.right.coerceIn(0, bitmap.width),
            region.bottom.coerceIn(0, bitmap.height),
        )
        if (safe.width() <= 0 || safe.height() <= 0) return bitmap
        if (safe.left == 0 && safe.top == 0 &&
            safe.right == bitmap.width && safe.bottom == bitmap.height) return bitmap
        return Bitmap.createBitmap(bitmap, safe.left, safe.top, safe.width(), safe.height())
    }

    /**
     * Upscales images with shorter side < [MIN_SIDE_PX] so ML Kit character
     * height is at least ~32 px, which is required for reliable recognition.
     * A 72-DPI A4 page has chars ~8 px tall — below that threshold.
     */
    private fun scaleForOcr(bitmap: Bitmap): Bitmap {
        if (minOf(bitmap.width, bitmap.height) >= MIN_SIDE_PX) return bitmap
        val scale   = 2.0f
        val newW    = (bitmap.width  * scale).toInt().coerceAtMost(MAX_SIDE_PX)
        val newH    = (bitmap.height * scale).toInt().coerceAtMost(MAX_SIDE_PX)
        Log.d(TAG, "Scaling ${bitmap.width}×${bitmap.height} → ${newW}×${newH}")
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    /**
     * Adaptive preprocessing.
     *
     * The original fixed ColorMatrix (contrast=2.0, offset=-80) burned any
     * non-pure-white background to solid black, making ML Kit see black
     * rectangles instead of text.  This version measures the actual
     * luminance range of the image and adapts the contrast and offset
     * accordingly — so yellowed paper, coloured headers, and low-contrast
     * scans all produce usable output.
     */
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
            range > 200 -> 1.1f
            range > 120 -> 1.4f
            range > 60  -> 1.8f
            else        -> 2.2f
        }
        val offset = (128f - avg * contrast).coerceIn(-60f, 40f)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cm  = ColorMatrix().apply { setSaturation(0f) }   // greyscale
        cm.postConcat(ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, offset,
            0f, contrast, 0f, 0f, offset,
            0f, 0f, contrast, 0f, offset,
            0f, 0f, 0f,      1f, 0f,
        )))
        Canvas(out).drawBitmap(bitmap, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    // ── OCR execution ──────────────────────────────────────────────────────

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
        return if (n > 0) total / n
        else {
            val nonBlank = result.textBlocks.count { it.text.isNotBlank() }
            (nonBlank.toFloat() / result.textBlocks.size.coerceAtLeast(1)).coerceIn(0f, 1f)
        }
    }

    // ── Block building — pixel-space coordinates ───────────────────────────

    /**
     * Converts ML Kit [Text] blocks to [OcrBlock] list.
     * Coordinates are in **bitmap pixel space** of the image passed to
     * [runOcr].  The [OcrOverlay] composable scales them to view space with
     * `scaleX = viewWidth.px / bitmapWidth`.
     */
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

    // ── Structured text ────────────────────────────────────────────────────

    private fun buildStructuredText(result: Text, imageWidth: Int): String {
        if (result.textBlocks.isEmpty()) return ""
        val indentThresh = imageWidth * 0.08f
        val sorted = result.textBlocks.sortedWith(
            compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 })
        )
        val sb = StringBuilder()
        var prevBottom = -1
        sorted.forEach { block ->
            if (block.text.isBlank()) return@forEach
            val top    = block.boundingBox?.top    ?: 0
            val bottom = block.boundingBox?.bottom ?: top
            val left   = block.boundingBox?.left   ?: 0
            if (prevBottom >= 0 && top - prevBottom > 40) sb.append("\n")
            if (left > indentThresh) sb.append("  ")
            val trimmed = block.text.trimStart()
            if (trimmed.startsWith("•") || trimmed.startsWith("-") ||
                trimmed.matches(Regex("^\\d+[.)].*"))) {
                sb.append("• ").append(trimmed.trimStart('•', '-', ' '))
            } else sb.append(block.text)
            sb.append("\n")
            prevBottom = bottom
        }
        return sb.toString().trim()
    }

    // ── LaTeX normalisation ────────────────────────────────────────────────

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

        mapOf("alpha" to "\\alpha","beta" to "\\beta","gamma" to "\\gamma",
            "delta" to "\\delta","theta" to "\\theta","lambda" to "\\lambda",
            "pi" to "\\pi","sigma" to "\\sigma","omega" to "\\omega",
            "sqrt" to "\\sqrt","sum" to "\\sum","int" to "\\int")
            .forEach { (k, v) -> text = text.replace(Regex("\\b$k\\b", RegexOption.IGNORE_CASE), v) }

        return text
    }

    // ── AI OCR cleanup ─────────────────────────────────────────────────────

    suspend fun runAiCleaner(rawText: String, language: OcrLanguage): String? {
        val key = aiSettingsManager.getApiKey("Groq")
        if (key.isBlank()) return null
        val truncated = if (rawText.length > 8_000) rawText.take(8_000) + "…" else rawText
        return try {
            withContext(Dispatchers.IO) {
                openAiService.getChatCompletion(
                    url        = GROQ_URL,
                    authHeader = "Bearer $key",
                    request    = OpenAiRequest(
                        model     = GROQ_MODEL,
                        messages  = listOf(
                            OpenAiMessage("system", MessageContent.Text(
                                "You are an expert OCR corrector and editor. Your task is to clean up OCR text, fix typos, and structure the content professionally.\n" +
                                        "1. Fix common OCR mistakes (e.g., l/1/I, O/0, broken words, broken line-end hyphens, etc.).\n" +
                                        "2. Use Markdown to structure the text: use # for main titles, ## for sections, and bold (**word**) or italic (*word*) for emphasis and key terms.\n" +
                                        "3. Organize into clear paragraphs and lists where appropriate.\n" +
                                        "4. Correct text color and font-style context through appropriate emphasis.\n" +
                                        "5. Preserve all mathematical formulas and technical data.\n" +
                                        "Return the structured and corrected text ONLY, without any preamble."
                            )),
                            OpenAiMessage("user", MessageContent.Text(truncated)),
                        ),
                        maxTokens = 4_096,
                    ),
                ).choices.firstOrNull()?.message?.content?.trim()
            }
        } catch (e: Exception) {
            Log.w(TAG, "AI OCR cleaner failed: ${e.message}")
            null
        }
    }

    /**
     * Summarises [text] (extracted PDF content) using Groq.
     * Returns the summary or null if the key isn't configured or the call fails.
     */
    suspend fun summarizePdf(text: String): String? {
        val key = aiSettingsManager.getApiKey("Groq")
        if (key.isBlank()) {
            Log.d(TAG, "Groq key not configured — skipping PDF summarisation")
            return null
        }
        // Limit to ~8 000 chars
        val truncated = if (text.length > 8_000) text.take(8_000) + "\n…[truncated]" else text
        return try {
            withContext(Dispatchers.IO) {
                openAiService.getChatCompletion(
                    url        = GROQ_URL,
                    authHeader = "Bearer $key",
                    request    = OpenAiRequest(
                        model     = GROQ_MODEL,
                        messages  = listOf(
                            OpenAiMessage("system", MessageContent.Text(
                                "You are an expert document analyst. " +
                                        "Summarise the provided PDF text concisely and clearly. " +
                                        "Structure your response as:\n" +
                                        "**Topic:** one-sentence overview\n" +
                                        "**Key Points:**\n• bullet 1\n• bullet 2\n• ...\n" +
                                        "**Conclusion:** one sentence.\n" +
                                        "Do not add content not present in the document."
                            )),
                            OpenAiMessage("user", MessageContent.Text(
                                "Summarise this PDF document:\n\n$truncated"
                            )),
                        ),
                        maxTokens = 1_024,
                    ),
                ).choices.firstOrNull()?.message?.content?.trim()
            }
        } catch (e: Exception) {
            Log.w(TAG, "PDF summarisation failed: ${e.message}")
            null
        }
    }

    // ── Legacy compatibility ───────────────────────────────────────────────

    /** Legacy signature kept for existing callers. Prefer the [OcrOptions] overload. */
    suspend fun processImage(bitmap: Bitmap, language: OcrLanguage = OcrLanguage.LATIN, region: Rect? = null): Text {
        val scaled = scaleForOcr(safeCrop(bitmap, region))
        return runOcr(scaled, language)
    }

    fun getStructuredText(result: Text): String = buildStructuredText(result, 1_000)

    fun fromRecognizedText(rawText: String) = FormulaOcrResult(
        rawText = rawText, latexText = normaliseToLatex(rawText), confidence = 0.9f,
    )
}
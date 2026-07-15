package com.frerox.toolz.util

import android.graphics.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap
import kotlin.math.abs
import kotlin.math.pow

object QREngine {

    enum class DotShape {
        SQUARE, ROUND, DIAMOND, LIQUID, HEART, STAR
    }

    enum class EyeShape {
        SQUARE, ROUND, DIAMOND, SQUIRCLE, LEAF
    }

    sealed class QrColor {
        data class Solid(val color: Int) : QrColor()
        data class LinearGradient(
            val colors: List<Int>,
            val orientation: GradientOrientation = GradientOrientation.TOP_LEFT_TO_BOTTOM_RIGHT
        ) : QrColor()

        fun getPrimaryColor(): Int = when (this) {
            is Solid -> color
            is LinearGradient -> colors.firstOrNull() ?: Color.BLACK
        }
    }

    enum class GradientOrientation {
        VERTICAL, HORIZONTAL, TOP_LEFT_TO_BOTTOM_RIGHT, TOP_RIGHT_TO_BOTTOM_LEFT
    }

    /**
     * Generates a stylized QR Code Bitmap.
     * Runs locally and offline.
     */
    fun generate(
        text: String,
        size: Int = 512,
        foregroundColor: QrColor = QrColor.Solid(Color.BLACK),
        backgroundColor: QrColor = QrColor.Solid(Color.WHITE),
        dotShape: DotShape = DotShape.SQUARE,
        eyeShape: EyeShape = EyeShape.SQUARE,
        logoBitmap: Bitmap? = null,
        quietZone: Int = 1,
        logoClearance: Float = 0.22f,
        noteText: String = "",
        noteSize: Float = 16f,
        notePosition: String = "BOTTOM",
        isNoteEnabled: Boolean = false
    ): Bitmap? {
        if (text.isEmpty()) return null

        return try {
            val writer = QRCodeWriter()
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.MARGIN, quietZone)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
            }

            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val moduleSize = size.toFloat() / width

            // Calculate extra height for note to avoid overlap
            val notePadding = if (isNoteEnabled && noteText.isNotEmpty()) {
                (noteSize * (size / 256f) * 1.5f).toInt()
            } else 0
            
            val totalHeight = size + notePadding
            val bitmap = Bitmap.createBitmap(size, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Draw Background
            drawQrColor(canvas, backgroundColor, size.toFloat(), totalHeight.toFloat())

            val qrOffsetY = if (isNoteEnabled && noteText.isNotEmpty() && notePosition == "TOP") {
                notePadding.toFloat()
            } else 0f

            // Setup Paints
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            applyQrColorToPaint(dotPaint, foregroundColor, size.toFloat(), size.toFloat())

            val eyeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = moduleSize
            }
            applyQrColorToPaint(eyeStrokePaint, foregroundColor, size.toFloat(), size.toFloat())

            val eyeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            applyQrColorToPaint(eyeFillPaint, foregroundColor, size.toFloat(), size.toFloat())

            // Determine logo clear zone
            val logoModuleSize = if (logoBitmap != null) {
                val base = (width * logoClearance).toInt()
                if (base % 2 == 0) base + 1 else base
            } else 0

            val centerCoord = width / 2
            val logoHalf = logoModuleSize / 2
            val logoRange = if (logoModuleSize > 0) {
                (centerCoord - logoHalf)..(centerCoord + logoHalf)
            } else null

            // Draw QR modules
            for (x in 0 until width) {
                for (y in 0 until height) {
                    if (isFinderPatternCell(x, y, width, height, quietZone)) continue
                    if (logoRange != null && x in logoRange && y in logoRange) continue

                    if (bitMatrix.get(x, y)) {
                        val left = x * moduleSize
                        val top = y * moduleSize + qrOffsetY
                        
                        if (dotShape == DotShape.LIQUID) {
                            drawLiquidModule(canvas, x, y, width, height, moduleSize, bitMatrix, dotPaint, logoRange, qrOffsetY)
                        } else {
                            drawSingleModule(canvas, left, top, moduleSize, dotShape, dotPaint)
                        }
                    }
                }
            }

            // Draw Eyes
            drawFinderPatterns(canvas, width, height, moduleSize, eyeShape, eyeStrokePaint, eyeFillPaint, quietZone, qrOffsetY)

            // Draw Logo
            if (logoBitmap != null && logoModuleSize > 0) {
                val bgInt = backgroundColor.getPrimaryColor()
                drawCenterLogo(canvas, size, logoModuleSize * moduleSize, logoBitmap, bgInt, qrOffsetY)
            }

            // Draw Note
            if (isNoteEnabled && noteText.isNotEmpty()) {
                val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    textSize = noteSize * (size / 256f) // Scale relative to 256px baseline
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                }
                applyQrColorToPaint(notePaint, foregroundColor, size.toFloat(), totalHeight.toFloat())
                
                val x = size / 2f
                val y = if (notePosition == "TOP") {
                    notePaint.textSize * 1.1f
                } else {
                    totalHeight - (notePaint.textSize * 0.3f)
                }
                
                canvas.drawText(noteText, x, y, notePaint)
            }

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isFinderPatternCell(x: Int, y: Int, width: Int, height: Int, quietZone: Int): Boolean {
        val q = quietZone
        // Top-Left Finder
        if (x in q until q + 7 && y in q until q + 7) return true
        // Top-Right Finder
        if (x in (width - 7 - q) until (width - q) && y in q until q + 7) return true
        // Bottom-Left Finder
        if (x in q until q + 7 && y in (height - 7 - q) until (height - q)) return true
        return false
    }

    private fun drawQrColor(canvas: Canvas, qrColor: QrColor, width: Float, height: Float) {
        when (qrColor) {
            is QrColor.Solid -> canvas.drawColor(qrColor.color)
            is QrColor.LinearGradient -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                applyQrColorToPaint(paint, qrColor, width, height)
                canvas.drawRect(0f, 0f, width, height, paint)
            }
        }
    }

    private fun applyQrColorToPaint(paint: Paint, qrColor: QrColor, width: Float, height: Float) {
        when (qrColor) {
            is QrColor.Solid -> {
                paint.color = qrColor.color
                paint.shader = null
            }
            is QrColor.LinearGradient -> {
                val (x0, y0, x1, y1) = when (qrColor.orientation) {
                    GradientOrientation.VERTICAL -> listOf(0f, 0f, 0f, height)
                    GradientOrientation.HORIZONTAL -> listOf(0f, 0f, width, 0f)
                    GradientOrientation.TOP_LEFT_TO_BOTTOM_RIGHT -> listOf(0f, 0f, width, height)
                    GradientOrientation.TOP_RIGHT_TO_BOTTOM_LEFT -> listOf(width, 0f, 0f, height)
                }
                paint.shader = LinearGradient(
                    x0, y0, x1, y1,
                    qrColor.colors.toIntArray(),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
        }
    }

    private fun drawSingleModule(canvas: Canvas, left: Float, top: Float, moduleSize: Float, shape: DotShape, paint: Paint) {
        val padding = moduleSize * 0.1f
        val rect = RectF(left + padding, top + padding, left + moduleSize - padding, top + moduleSize - padding)

        when (shape) {
            DotShape.SQUARE -> canvas.drawRect(rect, paint)
            DotShape.ROUND -> canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f, paint)
            DotShape.DIAMOND -> {
                val path = Path().apply {
                    moveTo(rect.centerX(), rect.top)
                    lineTo(rect.right, rect.centerY())
                    lineTo(rect.centerX(), rect.bottom)
                    lineTo(rect.left, rect.centerY())
                    close()
                }
                canvas.drawPath(path, paint)
            }
            DotShape.HEART -> {
                val path = Path().apply {
                    val width = rect.width()
                    val height = rect.height()
                    moveTo(rect.left + width / 2f, rect.top + height * 0.25f)
                    cubicTo(rect.left + width * 0.2f, rect.top - height * 0.1f,
                            rect.left - width * 0.1f, rect.top + height * 0.4f,
                            rect.left + width / 2f, rect.bottom)
                    cubicTo(rect.right + width * 0.1f, rect.top + height * 0.4f,
                            rect.right - width * 0.2f, rect.top - height * 0.1f,
                            rect.left + width / 2f, rect.top + height * 0.25f)
                }
                canvas.drawPath(path, paint)
            }
            DotShape.STAR -> {
                val path = Path()
                val cx = rect.centerX()
                val cy = rect.centerY()
                val outerRadius = rect.width() / 2f
                val innerRadius = outerRadius * 0.4f
                for (i in 0 until 10) {
                    val angle = Math.PI * i / 5.0 - Math.PI / 2.0
                    val radius = if (i % 2 == 0) outerRadius else innerRadius
                    val x = (cx + Math.cos(angle) * radius).toFloat()
                    val y = (cy + Math.sin(angle) * radius).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            DotShape.LIQUID -> {} // Handled separately
        }
    }

    private fun drawLiquidModule(
        canvas: Canvas,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        moduleSize: Float,
        bitMatrix: com.google.zxing.common.BitMatrix,
        paint: Paint,
        logoRange: IntRange?,
        offsetY: Float = 0f
    ) {
        val left = x * moduleSize
        val top = y * moduleSize + offsetY
        val right = left + moduleSize
        val bottom = top + moduleSize
        val radius = moduleSize * 0.48f // Slightly more rounded for liquid feel

        val hasTop = y > 0 && bitMatrix.get(x, y - 1) && (logoRange == null || (x !in logoRange || (y - 1) !in logoRange))
        val hasBottom = y < height - 1 && bitMatrix.get(x, y + 1) && (logoRange == null || (x !in logoRange || (y + 1) !in logoRange))
        val hasLeft = x > 0 && bitMatrix.get(x - 1, y) && (logoRange == null || ((x - 1) !in logoRange || y !in logoRange))
        val hasRight = x < width - 1 && bitMatrix.get(x + 1, y) && (logoRange == null || ((x + 1) !in logoRange || y !in logoRange))

        val path = Path()
        
        // Corners: Top-Left, Top-Right, Bottom-Right, Bottom-Left
        val tlR = if (hasTop || hasLeft) 0f else radius
        val trR = if (hasTop || hasRight) 0f else radius
        val brR = if (hasBottom || hasRight) 0f else radius
        val blR = if (hasBottom || hasLeft) 0f else radius

        val radii = floatArrayOf(tlR, tlR, trR, trR, brR, brR, blR, blR)
        path.addRoundRect(RectF(left, top, right, bottom), radii, Path.Direction.CW)
        
        canvas.drawPath(path, paint)
    }

    private fun drawFinderPatterns(
        canvas: Canvas,
        width: Int,
        height: Int,
        moduleSize: Float,
        shape: EyeShape,
        strokePaint: Paint,
        fillPaint: Paint,
        quietZone: Int,
        offsetY: Float = 0f
    ) {
        val q = quietZone
        val eyes = listOf(
            RectF(q * moduleSize, q * moduleSize + offsetY, (q + 7) * moduleSize, (q + 7) * moduleSize + offsetY),
            RectF((width - 7 - q) * moduleSize, q * moduleSize + offsetY, (width - q) * moduleSize, q * moduleSize + 7 * moduleSize + offsetY),
            RectF(q * moduleSize, (height - 7 - q) * moduleSize + offsetY, (q + 7) * moduleSize, (height - q) * moduleSize + offsetY)
        )

        for (eye in eyes) {
            val inset = moduleSize / 2f
            val frameRect = RectF(eye.left + inset, eye.top + inset, eye.right - inset, eye.bottom - inset)
            val ballRect = RectF(eye.left + 2 * moduleSize, eye.top + 2 * moduleSize, eye.right - 2 * moduleSize, eye.bottom - 2 * moduleSize)

            when (shape) {
                EyeShape.SQUARE -> {
                    canvas.drawRect(frameRect, strokePaint)
                    canvas.drawRect(ballRect, fillPaint)
                }
                EyeShape.ROUND -> {
                    val rFrame = moduleSize * 2f
                    canvas.drawRoundRect(frameRect, rFrame, rFrame, strokePaint)
                    canvas.drawCircle(ballRect.centerX(), ballRect.centerY(), ballRect.width() / 2f, fillPaint)
                }
                EyeShape.DIAMOND -> {
                    drawDiamond(canvas, frameRect, strokePaint)
                    drawDiamond(canvas, ballRect, fillPaint)
                }
                EyeShape.SQUIRCLE -> {
                    drawSquircle(canvas, frameRect, strokePaint)
                    drawSquircle(canvas, ballRect, fillPaint)
                }
                EyeShape.LEAF -> {
                    drawLeaf(canvas, frameRect, strokePaint)
                    drawLeaf(canvas, ballRect, fillPaint)
                }
            }
        }
    }

    private fun drawLeaf(canvas: Canvas, rect: RectF, paint: Paint) {
        val path = Path().apply {
            moveTo(rect.left, rect.top)
            quadTo(rect.right, rect.top, rect.right, rect.bottom)
            quadTo(rect.left, rect.bottom, rect.left, rect.top)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawDiamond(canvas: Canvas, rect: RectF, paint: Paint) {
        val path = Path().apply {
            moveTo(rect.centerX(), rect.top)
            lineTo(rect.right, rect.centerY())
            lineTo(rect.centerX(), rect.bottom)
            lineTo(rect.left, rect.centerY())
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawSquircle(canvas: Canvas, rect: RectF, paint: Paint) {
        val path = Path()
        val size = rect.width()
        val halfSize = size / 2f
        val cx = rect.centerX()
        val cy = rect.centerY()
        val n = 4.0 // Squircle exponent

        for (i in 0..360) {
            val angle = Math.toRadians(i.toDouble())
            val cosA = Math.cos(angle)
            val sinA = Math.sin(angle)
            
            val x = abs(cosA).pow(2.0 / n) * halfSize * Math.signum(cosA)
            val y = abs(sinA).pow(2.0 / n) * halfSize * Math.signum(sinA)
            
            if (i == 0) path.moveTo((cx + x).toFloat(), (cy + y).toFloat())
            else path.lineTo((cx + x).toFloat(), (cy + y).toFloat())
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawCenterLogo(canvas: Canvas, qrSize: Int, logoSize: Float, logo: Bitmap, bgColor: Int, offsetY: Float = 0f) {
        val cx = qrSize / 2f
        val cy = qrSize / 2f + offsetY
        val cardSize = logoSize * 1.2f
        val cardRect = RectF(cx - cardSize / 2f, cy - cardSize / 2f, cx + cardSize / 2f, cy + cardSize / 2f)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            setShadowLayer(10f, 0f, 5f, Color.argb(60, 0, 0, 0))
        }
        canvas.drawRoundRect(cardRect, cardSize * 0.3f, cardSize * 0.3f, cardPaint)

        val logoRect = RectF(cx - logoSize / 2f, cy - logoSize / 2f, cx + logoSize / 2f, cy + logoSize / 2f)
        val roundedLogo = getRoundedBitmap(logo, logoSize.toInt(), logoSize * 0.25f)
        canvas.drawBitmap(roundedLogo, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun getRoundedBitmap(bitmap: Bitmap, size: Int, radius: Float): Bitmap {
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        return output
    }

    fun generateSvg(
        text: String,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE,
        dotShape: DotShape = DotShape.SQUARE,
        eyeShape: EyeShape = EyeShape.SQUARE,
        quietZone: Int = 1
    ): String {
        val writer = QRCodeWriter()
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.MARGIN, quietZone)
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
        }
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val fgHex = String.format("#%06X", 0xFFFFFF and foregroundColor)
        val bgHex = String.format("#%06X", 0xFFFFFF and backgroundColor)

        val svg = StringBuilder()
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"512\" height=\"512\" viewBox=\"0 0 $width $height\">")
        svg.append("<rect width=\"$width\" height=\"$height\" fill=\"$bgHex\"/>")

        for (x in 0 until width) {
            for (y in 0 until height) {
                if (bitMatrix.get(x, y)) {
                    // Simplified SVG: just draw squares for now to ensure compatibility
                    // High-fidelity SVG paths would require complex path data generation
                    svg.append("<rect x=\"$x\" y=\"$y\" width=\"1\" height=\"1\" fill=\"$fgHex\"/>")
                }
            }
        }
        svg.append("</svg>")
        return svg.toString()
    }
}

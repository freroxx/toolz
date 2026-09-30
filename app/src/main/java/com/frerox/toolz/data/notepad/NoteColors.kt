package com.frerox.toolz.data.notepad

import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * Central note-color logic. Single source of truth for:
 * - safe AI hex parsing (never throws),
 * - transparent-black (0) legacy fallback,
 * - container/on-container contrast pairs.
 */
object NoteColors {
    const val FALLBACK_HEX = "#FFF9C4"
    private val HEX_RE = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")

    fun parseHexOrNull(raw: String?): Int? {
        val hex = raw?.trim() ?: return null
        if (!HEX_RE.matches(hex)) return null
        return try {
            val clean = hex.removePrefix("#")
            val argb = if (clean.length == 6) "FF$clean" else clean
            argb.toLong(16).toInt()
        } catch (_: Exception) {
            null
        }
    }

    fun parseHex(raw: String?, fallback: Int): Int = parseHexOrNull(raw) ?: fallback

    fun toHex(color: Int): String =
        String.format(Locale.US, "#%06X", 0xFFFFFF and color)

    /** 0 = legacy unset/transparent — never render transparent. */
    fun containerColor(color: Int, fallback: Color): Color =
        if (color == 0) fallback else Color(color)

    fun isDark(color: Color): Boolean {
        // Relative luminance (gamma-aware), alpha-agnostic.
        fun lin(c: Float): Float =
            if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        val l = 0.2126f * lin(color.red) + 0.7152f * lin(color.green) + 0.0722f * lin(color.blue)
        return l < 0.45f
    }

    fun contentColorFor(container: Color, dark: Color, light: Color): Color =
        if (isDark(container)) light else dark
}

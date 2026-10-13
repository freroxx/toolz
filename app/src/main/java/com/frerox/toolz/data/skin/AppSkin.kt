/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.skin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * App Skin + App Icon engine.
 *
 * Icon composition (from Toolz Icon PNGs, copied to drawable-nodpi):
 *  base         -> outer hexagon fill (background)
 *  second_layer -> inner hexagon coat / stroke
 *  circle       -> foreground disc (the white swoosh body)
 *  pill         -> carve shape (same color as background, creates swoosh cut)
 *
 * Rendering uses SrcIn tint so every layer is recolorable at runtime.
 * Launcher keeps a static default build; in-app surfaces are fully dynamic.
 */

enum class IconShape { HEXAGON, CIRCLE, PILL }

@Serializable
data class IconSpec(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("shape") val shape: String = "HEXAGON", // HEXAGON | CIRCLE | PILL
    @SerialName("background") val background: Long,     // ARGB as Long (0xFF...), fixed preset
    @SerialName("backgroundEnd") val backgroundEnd: Long? = null, // gradient end, null = solid
    @SerialName("stroke") val stroke: Long,             // second_layer color
    @SerialName("foreground") val foreground: Long,    // circle color
    @SerialName("carve") val carve: Long? = null,      // pill color, null = = background
    @SerialName("adaptToTheme") val adaptToTheme: Boolean = false,
    @SerialName("tag") val tag: String = "Vivid",      // grouping for catalog filters
) {
    val carveResolved: Long get() = carve ?: background
}

private fun argb(hex: Long) = hex

object IconCatalog {
    const val DEFAULT_ID = "grape-haze"

    val BUILT_IN: List<IconSpec> = listOf(
        // --- From your 5 example renders ---
        IconSpec("midnight", "Midnight", background = 0xFF000000, stroke = 0xFF4A4A4A, foreground = 0xFFFFFFFF, tag = "Mono"),
        IconSpec("mono-grey", "Mono Grey", background = 0xFF4A4A4A, stroke = 0xFFC9C9C9, foreground = 0xFFFFFFFF, tag = "Mono"),
        IconSpec("grape-haze", "Grape Haze", background = 0xFF7B2FBE, stroke = 0xFFD9B8FF, foreground = 0xFFFFFFFF, backgroundEnd = 0xFFB07FE8, tag = "Vivid"),
        IconSpec("grape-deep", "Grape Deep", background = 0xFF2B0052, stroke = 0xFFA88FBE, foreground = 0xFFD9D9D9, backgroundEnd = 0xFF12002B, tag = "Dark"),
        IconSpec("lagoon", "Lagoon", background = 0xFF0B3B3B, stroke = 0xFF5FBFB4, foreground = 0xFF7FDBD2, backgroundEnd = 0xFF083030, tag = "Dark"),
        // --- Legacy / brand riffs so the list is never empty ---
        IconSpec("legacy-citrus", "Legacy Citrus", background = 0xFFCC5A17, stroke = 0xFFFFDCC6, foreground = 0xFFFFFFFF, tag = "Legacy"),
        IconSpec("legacy-ocean", "Legacy Ocean", background = 0xFF0C7E78, stroke = 0xFFB7F1EA, foreground = 0xFFFFFFFF, tag = "Legacy"),
        IconSpec("legacy-night", "Legacy Night", background = 0xFF100D0B, stroke = 0xFFFFB785, foreground = 0xFFFFB785, tag = "Legacy"),
        IconSpec("paper-light", "Paper Light", background = 0xFFFCF9F6, stroke = 0xFFCC5A17, foreground = 0xFFCC5A17, tag = "Light"),
        IconSpec("paper-dark", "Paper Dark", background = 0xFF1B1B1F, stroke = 0xFFFBF8FF, foreground = 0xFFFBF8FF, tag = "Dark"),
        IconSpec("citrus-pop", "Citrus Pop", background = 0xFFFF6D00, stroke = 0xFFFFDCC6, foreground = 0xFFFFFFFF, backgroundEnd = 0xFFCC5A17, tag = "Vivid"),
        IconSpec("ocean-pop", "Ocean Pop", background = 0xFF00BFA5, stroke = 0xFF00201D, foreground = 0xFF00201D, backgroundEnd = 0xFF0C7E78, tag = "Vivid"),
        IconSpec("royal", "Royal", background = 0xFF2968C8, stroke = 0xFFD8E7FF, foreground = 0xFFFFFFFF, backgroundEnd = 0xFF001B3F, tag = "Vivid"),
        IconSpec("rose", "Rose", background = 0xFFC81E5B, stroke = 0xFFFFD9E2, foreground = 0xFFFFFFFF, backgroundEnd = 0xFF7A0F36, tag = "Vivid"),
        IconSpec("mint", "Mint", background = 0xFF0F5132, stroke = 0xFFB7F1EA, foreground = 0xFFB7F1EA, tag = "Dark"),
        IconSpec("sand", "Sand", background = 0xFFE8DCC8, stroke = 0xFF5C4A32, foreground = 0xFF5C4A32, tag = "Light"),
        IconSpec("slate", "Slate", background = 0xFF2B2F36, stroke = 0xFF9AA3B2, foreground = 0xFFEDF0F4, tag = "Dark"),
        IconSpec("snow", "Snow", background = 0xFFFFFFFF, stroke = 0xFF1B1B1F, foreground = 0xFF1B1B1F, tag = "Light"),
        IconSpec("carbon", "Carbon", background = 0xFF101014, stroke = 0xFF3A3A42, foreground = 0xFFEDEDF2, tag = "Mono"),
        IconSpec("cobalt", "Cobalt", background = 0xFF1A2340, stroke = 0xFF7F9CFF, foreground = 0xFFFFFFFF, backgroundEnd = 0xFF0B1030, tag = "Dark"),
        IconSpec("sunset", "Sunset", background = 0xFFFF8A3D, stroke = 0xFF7A1E3C, foreground = 0xFFFFFFFF, backgroundEnd = 0xFFC81E5B, tag = "Vivid"),
        IconSpec("forest", "Forest", background = 0xFF1B4332, stroke = 0xFF95D5B2, foreground = 0xFFD8F3DC, tag = "Dark"),
        IconSpec("lavender", "Lavender", background = 0xFFE6DEF7, stroke = 0xFF7B2FBE, foreground = 0xFF7B2FBE, tag = "Light"),
        IconSpec("peach", "Peach", background = 0xFFFFDCC6, stroke = 0xFFCC5A17, foreground = 0xFFCC5A17, tag = "Light"),
        // --- Adaptive (follow theme/dynamic at render time) ---
        IconSpec("system-auto", "System Auto", background = 0xFF7B2FBE, stroke = 0xFFD9B8FF, foreground = 0xFFFFFFFF, adaptToTheme = true, tag = "Adaptive"),
        IconSpec("dynamic-glow", "Dynamic Glow", background = 0xFF2962FF, stroke = 0xFFFFFFFF, foreground = 0xFFFFFFFF, adaptToTheme = true, tag = "Adaptive"),
    )

    fun find(id: String?): IconSpec =
        BUILT_IN.firstOrNull { it.id == id } ?: BUILT_IN.first { it.id == DEFAULT_ID }

    val tags = listOf("All", "Adaptive", "Vivid", "Dark", "Light", "Mono", "Legacy")
}

// ---------------------------------------------------------------------------
// Skins: theme + colors + icon bundled. Server-driven, community uploadable.
// ---------------------------------------------------------------------------

@Serializable
data class SkinDto(
    @SerialName("schemaVersion") val schemaVersion: Int = 1,
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("author") val author: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("themeMode") val themeMode: String = "SYSTEM", // SYSTEM | LIGHT | DARK
    @SerialName("dynamicColor") val dynamicColor: Boolean = true,
    @SerialName("primary") val primary: Long? = null,   // ARGB Long, null = default
    @SerialName("secondary") val secondary: Long? = null,
    @SerialName("gradient") val gradient: Boolean = true,
    @SerialName("iconPresetId") val iconPresetId: String = IconCatalog.DEFAULT_ID,
    @SerialName("iconAdaptTheme") val iconAdaptTheme: Boolean = true,
    @SerialName("iconFollowDynamic") val iconFollowDynamic: Boolean = true,
    @SerialName("iconShape") val iconShape: String = "HEXAGON",
    @SerialName("featured") val featured: Boolean = false,
    @SerialName("status") val status: String = "published",
    @SerialName("createdAt") val createdAt: String = "",
    @SerialName("updatedAt") val updatedAt: String = "",
) {
    fun validate(): String? {
        if (name.trim().length < 3) return "Name too short"
        if (name.trim().length > 32) return "Name too long"
        if (themeMode !in setOf("SYSTEM", "LIGHT", "DARK")) return "Bad themeMode"
        val shape = iconShape.uppercase()
        if (shape !in setOf("HEXAGON", "CIRCLE", "PILL")) return "Bad iconShape"
        if (IconCatalog.BUILT_IN.none { it.id == iconPresetId } && iconPresetId != "custom") return "Unknown icon"
        return null
    }
}

@Serializable
data class SkinFeedDto(
    @SerialName("version") val version: Int = 1,
    @SerialName("count") val count: Int = 0,
    @SerialName("skins") val skins: List<SkinDto> = emptyList(),
    @SerialName("removedIds") val removedIds: List<String> = emptyList(),
    @SerialName("v") val feedVersion: Int = -1,
    @SerialName("degraded") val degraded: Boolean = false,
)

object LegacySkins {
    val ALL: List<SkinDto> = listOf(
        SkinDto(id = "legacy-default", name = "Toolz Default", author = "Toolz", description = "Classic citrus + ocean with Grape Haze icon.", themeMode = "SYSTEM", dynamicColor = true, gradient = true, iconPresetId = "grape-haze", featured = true),
        SkinDto(id = "legacy-midnight", name = "Midnight Mono", author = "Toolz", description = "Pure black icon, system theme, dynamic on.", themeMode = "SYSTEM", dynamicColor = true, gradient = false, iconPresetId = "midnight", featured = true),
        SkinDto(id = "legacy-grape", name = "Grape Haze", author = "Toolz", description = "Signature purple, light or dark.", themeMode = "SYSTEM", dynamicColor = false, primary = 0xFF7B2FBE, secondary = 0xFFB07FE8, gradient = true, iconPresetId = "grape-haze"),
        SkinDto(id = "legacy-lagoon", name = "Lagoon Deep", author = "Toolz", description = "Teal dark theme lovers.", themeMode = "DARK", dynamicColor = false, primary = 0xFF0C7E78, secondary = 0xFF5FBFB4, gradient = true, iconPresetId = "lagoon"),
        SkinDto(id = "legacy-paper", name = "Paper", author = "Toolz", description = "Clean light appearance.", themeMode = "LIGHT", dynamicColor = false, primary = 0xFFCC5A17, secondary = 0xFF0C7E78, gradient = true, iconPresetId = "paper-light"),
        SkinDto(id = "legacy-carbon", name = "Carbon", author = "Toolz", description = "AMOLED dark, no gradient.", themeMode = "DARK", dynamicColor = false, primary = 0xFFEDEDF2, secondary = 0xFF9AA3B2, gradient = false, iconPresetId = "carbon"),
    )
}

/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.skin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * App Skin + App Icon catalog.
 *
 * An icon preset is four colours (see ToolzMarkPalette.kt for how they are drawn):
 *   rim        outer octagon ring, flat
 *   bodyStart  inner octagon gradient start; also the capsule fill
 *   bodyEnd    gradient end, null = flat body
 *   disc       the swoosh body
 *
 * Presets render exactly as listed. Only specs flagged [IconSpec.adaptToTheme] or
 * [IconSpec.followsScheme] are ever recoloured at runtime.
 * The launcher icon is a static vector built from DEFAULT_ID (res/drawable/ic_launcher_*.xml).
 */

enum class IconShape {
    OCTAGON, CIRCLE, PILL;

    companion object {
        /** "HEXAGON" is the legacy wire name for OCTAGON; skins on the server still use it. */
        fun parse(raw: String?): IconShape? = when (raw?.uppercase()) {
            "OCTAGON", "HEXAGON" -> OCTAGON
            "CIRCLE" -> CIRCLE
            "PILL" -> PILL
            else -> null
        }
    }
}

@Serializable
data class IconSpec(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("tag") val tag: String = "Vivid",          // catalog filter group
    @SerialName("rim") val rim: Long,                      // ARGB 0xFF......
    @SerialName("bodyStart") val bodyStart: Long,
    @SerialName("bodyEnd") val bodyEnd: Long? = null,      // null = flat body
    @SerialName("disc") val disc: Long,
    @SerialName("carve") val carve: Long? = null,          // null = bodyStart (matches source art)
    @SerialName("adaptToTheme") val adaptToTheme: Boolean = false, // dim body in dark theme
    @SerialName("followsScheme") val followsScheme: Boolean = false, // palette from Material scheme
) {
    @Deprecated("Renamed", ReplaceWith("bodyStart")) val background: Long get() = bodyStart
    @Deprecated("Renamed", ReplaceWith("bodyEnd")) val backgroundEnd: Long? get() = bodyEnd
    @Deprecated("Renamed", ReplaceWith("rim")) val stroke: Long get() = rim
    @Deprecated("Renamed", ReplaceWith("disc")) val foreground: Long get() = disc
}

object IconCatalog {
    const val DEFAULT_ID = "grape-haze"

    val BUILT_IN: List<IconSpec> = listOf(
        // --- Reference icons: colours sampled from the original renders ---
        IconSpec("grape-haze", "Grape Haze", tag = "Vivid", rim = 0xFFDFBCFF, bodyStart = 0xFF8534CE, bodyEnd = 0xFFD5B9EE, disc = 0xFFFFFFFF),
        IconSpec("grape-deep", "Grape Deep", tag = "Dark", rim = 0xFF9D8AAE, bodyStart = 0xFF370068, bodyEnd = 0xFF130024, disc = 0xFFBEBEBE),
        IconSpec("lagoon", "Lagoon", tag = "Dark", rim = 0xFF4CB3AE, bodyStart = 0xFF00393F, bodyEnd = 0xFF002925, disc = 0xFF74DFD5),
        IconSpec("mono-grey", "Mono Grey", tag = "Mono", rim = 0xFFBEBEBE, bodyStart = 0xFF444444, disc = 0xFFFFFFFF),
        // --- Brand riffs ---
        IconSpec("midnight", "Midnight", tag = "Mono", rim = 0xFF4A4A4A, bodyStart = 0xFF000000, disc = 0xFFFFFFFF),
        IconSpec("legacy-citrus", "Legacy Citrus", tag = "Legacy", rim = 0xFFFFDCC6, bodyStart = 0xFFCC5A17, disc = 0xFFFFFFFF),
        IconSpec("legacy-ocean", "Legacy Ocean", tag = "Legacy", rim = 0xFFB7F1EA, bodyStart = 0xFF0C7E78, disc = 0xFFFFFFFF),
        IconSpec("legacy-night", "Legacy Night", tag = "Legacy", rim = 0xFFFFB785, bodyStart = 0xFF100D0B, disc = 0xFFFFB785),
        IconSpec("paper-light", "Paper Light", tag = "Light", rim = 0xFFCC5A17, bodyStart = 0xFFFCF9F6, disc = 0xFFCC5A17),
        IconSpec("paper-dark", "Paper Dark", tag = "Dark", rim = 0xFFFBF8FF, bodyStart = 0xFF1B1B1F, disc = 0xFFFBF8FF),
        IconSpec("citrus-pop", "Citrus Pop", tag = "Vivid", rim = 0xFFFFDCC6, bodyStart = 0xFFFF6D00, bodyEnd = 0xFFCC5A17, disc = 0xFFFFFFFF),
        IconSpec("ocean-pop", "Ocean Pop", tag = "Vivid", rim = 0xFF00201D, bodyStart = 0xFF00BFA5, bodyEnd = 0xFF0C7E78, disc = 0xFF00201D),
        IconSpec("royal", "Royal", tag = "Vivid", rim = 0xFFD8E7FF, bodyStart = 0xFF2968C8, bodyEnd = 0xFF001B3F, disc = 0xFFFFFFFF),
        IconSpec("rose", "Rose", tag = "Vivid", rim = 0xFFFFD9E2, bodyStart = 0xFFC81E5B, bodyEnd = 0xFF7A0F36, disc = 0xFFFFFFFF),
        IconSpec("mint", "Mint", tag = "Dark", rim = 0xFFB7F1EA, bodyStart = 0xFF0F5132, disc = 0xFFB7F1EA),
        IconSpec("sand", "Sand", tag = "Light", rim = 0xFF5C4A32, bodyStart = 0xFFE8DCC8, disc = 0xFF5C4A32),
        IconSpec("slate", "Slate", tag = "Dark", rim = 0xFF9AA3B2, bodyStart = 0xFF2B2F36, disc = 0xFFEDF0F4),
        IconSpec("snow", "Snow", tag = "Light", rim = 0xFF1B1B1F, bodyStart = 0xFFFFFFFF, disc = 0xFF1B1B1F),
        IconSpec("carbon", "Carbon", tag = "Mono", rim = 0xFF3A3A42, bodyStart = 0xFF101014, disc = 0xFFEDEDF2),
        IconSpec("cobalt", "Cobalt", tag = "Dark", rim = 0xFF7F9CFF, bodyStart = 0xFF1A2340, bodyEnd = 0xFF0B1030, disc = 0xFFFFFFFF),
        IconSpec("sunset", "Sunset", tag = "Vivid", rim = 0xFF7A1E3C, bodyStart = 0xFFFF8A3D, bodyEnd = 0xFFC81E5B, disc = 0xFFFFFFFF),
        IconSpec("forest", "Forest", tag = "Dark", rim = 0xFF95D5B2, bodyStart = 0xFF1B4332, disc = 0xFFD8F3DC),
        IconSpec("lavender", "Lavender", tag = "Light", rim = 0xFF7B2FBE, bodyStart = 0xFFE6DEF7, disc = 0xFF7B2FBE),
        IconSpec("peach", "Peach", tag = "Light", rim = 0xFFCC5A17, bodyStart = 0xFFFFDCC6, disc = 0xFFCC5A17),
        // --- Adaptive ---
        // system-auto: Grape Haze, body dimmed in dark theme.
        IconSpec("system-auto", "System Auto", tag = "Adaptive", rim = 0xFFDFBCFF, bodyStart = 0xFF8534CE, bodyEnd = 0xFFD5B9EE, disc = 0xFFFFFFFF, adaptToTheme = true),
        // dynamic-glow: palette derived from the Material scheme; blue fallback without dynamic color.
        IconSpec("dynamic-glow", "Dynamic Glow", tag = "Adaptive", rim = 0xFFD8E2FF, bodyStart = 0xFF2962FF, bodyEnd = 0xFF1B3FA8, disc = 0xFFFFFFFF, adaptToTheme = true, followsScheme = true),
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
        if (IconShape.parse(iconShape) == null) return "Bad iconShape"
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

/** A look the user shared from the app. Stored locally to track review status. */
@Serializable
data class MySubmission(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("ts") val ts: Long = 0L,
)

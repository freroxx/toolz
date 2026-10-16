package com.frerox.toolz.data.skin

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AppSkinTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    @Test fun catalogHasManyPresets() {
        assertTrue(IconCatalog.BUILT_IN.size >= 40)
        assertNotNull(IconCatalog.BUILT_IN.firstOrNull { it.id == IconCatalog.DEFAULT_ID })
    }

    @Test fun fallbackFind() {
        assertEquals(IconCatalog.DEFAULT_ID, IconCatalog.find("nope").id)
        assertEquals("midnight", IconCatalog.find("midnight").id)
    }

    @Test fun carveDefaultsToBackground() {
        val spec = IconCatalog.find("midnight")
        assertEquals(spec.bodyStart, spec.carve ?: spec.bodyStart)
    }

    @Test fun skinValidation() {
        val ok = SkinDto(id = "x", name = "My Skin", themeMode = "DARK", dynamicColor = false, iconPresetId = "lagoon")
        assertNull(ok.validate())
        assertNotNull(ok.copy(name = "ab").validate())
        assertNotNull(ok.copy(themeMode = "BLUE").validate())
        assertNotNull(ok.copy(iconPresetId = "unknown-icon").validate())
        assertNotNull(ok.copy(iconShape = "TRIANGLE").validate())
    }

    @Test fun systemThemeValidates() {
        // Sharing a look must never fail just because the theme follows the system.
        val ok = SkinDto(id = "", name = "Midnight Grape", themeMode = "SYSTEM", dynamicColor = true, iconPresetId = "grape-haze")
        assertNull(ok.validate())
    }

    @Test fun pillMapsToCircle() {
        // PILL was removed (identical to CIRCLE); old values keep parsing.
        assertEquals(IconShape.CIRCLE, IconShape.parse("PILL"))
        assertEquals(IconShape.CIRCLE, IconShape.parse("circle"))
        assertEquals(IconShape.OCTAGON, IconShape.parse("HEXAGON"))
        assertNull(IconShape.parse("TRIANGLE"))
    }

    @Test fun launcherAliasesCoverShapes() {
        // Every preset ships an octagon tile and a circle tile; exactly one wins.
        assertEquals(IconCatalog.BUILT_IN.size * 2, LauncherIconManager.allAliasNames().distinct().size)
        assertEquals(".Icon_grape_haze", LauncherIconManager.aliasName("grape-haze", "OCTAGON"))
        assertEquals(".Icon_grape_haze", LauncherIconManager.aliasName("grape-haze", null))
        assertEquals(".Icon_grape_haze_circle", LauncherIconManager.aliasName("grape-haze", "CIRCLE"))
        assertEquals(".Icon_teal_pop_circle", LauncherIconManager.aliasName("teal-pop", "CIRCLE"))
        assertEquals(".Icon_grape_haze_circle", LauncherIconManager.aliasName("grape-haze", "PILL"))
        assertEquals("grape-haze", LauncherIconManager.presetIdForAlias(".Icon_grape_haze_circle"))
        assertEquals("teal-pop", LauncherIconManager.presetIdForAlias(".Icon_teal_pop"))
        assertEquals(IconCatalog.DEFAULT_ID, LauncherIconManager.presetIdForAlias(".Icon_nope"))
    }

    @Test fun everyPresetAdaptsToTheme() {
        // Adapt to theme is universal — no preset renders "exactly as designed" in dark mode anymore.
        IconCatalog.BUILT_IN.forEach { assertTrue("no adapt: ${it.id}", it.adaptToTheme) }
    }

    @Test fun everyPresetHasGradient() {
        // The in-app gradient toggle works on all presets, so none may be flat-only.
        IconCatalog.BUILT_IN.forEach { assertNotNull("no bodyEnd: ${it.id}", it.bodyEnd) }
    }

    @Test fun everyPresetHasPolishedTheme() {
        // Each icon ships tuned light/dark app accents that stay legible on the
        // fixed on-primary/on-secondary colors (white in light; deep brown/teal in dark).
        fun lum(argb: Long): Double {
            fun ch(v: Int): Double {
                val x = v / 255.0
                return if (x <= 0.04045) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * ch((argb shr 16).toInt() and 0xFF) +
                0.7152 * ch((argb shr 8).toInt() and 0xFF) +
                0.0722 * ch(argb.toInt() and 0xFF)
        }
        fun ratio(fg: Long, bg: Long): Double {
            val a = lum(fg); val b = lum(bg)
            return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
        }
        IconCatalog.BUILT_IN.forEach { spec ->
            assertTrue("light P==S: ${spec.id}", spec.themePrimaryLight != spec.themeSecondaryLight)
            assertTrue("dark P==S: ${spec.id}", spec.themePrimaryDark != spec.themeSecondaryDark)
            assertTrue("light primary contrast: ${spec.id}", ratio(spec.themePrimaryLight, 0xFFFFFFFF) >= 4.5)
            assertTrue("light secondary contrast: ${spec.id}", ratio(spec.themeSecondaryLight, 0xFFFFFFFF) >= 4.5)
            assertTrue("dark primary contrast: ${spec.id}", ratio(spec.themePrimaryDark, 0xFF512200) >= 4.5)
            assertTrue("dark secondary contrast: ${spec.id}", ratio(spec.themeSecondaryDark, 0xFF003734) >= 4.5)
            assertEquals(spec.themePrimaryLight, spec.themePrimary(false))
            assertEquals(spec.themePrimaryDark, spec.themePrimary(true))
            assertEquals(spec.themeSecondaryLight, spec.themeSecondary(false))
            assertEquals(spec.themeSecondaryDark, spec.themeSecondary(true))
        }
    }

    @Test fun fingerprintIgnoresIdentity() {
        val a = SkinDto(id = "1", name = "One", author = "x", themeMode = "DARK", dynamicColor = false, iconPresetId = "lagoon")
        val b = a.copy(id = "2", name = "Two", author = "y", createdAt = "t")
        assertEquals(a.fingerprint(), b.fingerprint())
        assertNotEquals(a.fingerprint(), b.copy(iconPresetId = "midnight").fingerprint())
        assertNotEquals(a.fingerprint(), b.copy(themeMode = "LIGHT").fingerprint())
        assertNotEquals(a.fingerprint(), b.copy(iconGradient = false).fingerprint())
        assertNotEquals(a.fingerprint(), b.copy(iconBg = 0xFF112233).fingerprint())
    }

    @Test fun skinV2FieldsRoundTrip() {
        val skin = SkinDto(id = "", name = "Full Look", themeMode = "SYSTEM", dynamicColor = true, iconPresetId = "dusk", iconGradient = false, iconBg = 0xFF112233, iconFg = 0xFF445566, iconStroke = 0xFF778899)
        assertNull(skin.validate())
        val raw = json.encodeToString(SkinDto.serializer(), skin)
        val back = json.decodeFromString(SkinDto.serializer(), raw)
        assertEquals(skin, back)
        assertEquals(skin.fingerprint(), back.fingerprint())
    }

    @Test fun legacyNonEmpty() {
        assertTrue(LegacySkins.ALL.size >= 6)
        LegacySkins.ALL.forEach { assertNull(it.validate()) }
    }

    @Test fun submitRequest_serializes() {
        val skin = SkinDto(id = "", name = "Midnight Grape", author = "frerox", themeMode = "DARK", dynamicColor = false, iconPresetId = "lagoon")
        val raw = json.encodeToString(SkinSubmitRequest.serializer(), SkinSubmitRequest(name = "Midnight Grape", skin = skin))
        val back = json.decodeFromString(SkinSubmitRequest.serializer(), raw)
        assertEquals("Midnight Grape", back.name)
        assertEquals("frerox", back.skin.author)
        assertEquals("lagoon", back.skin.iconPresetId)
    }

    @Test fun submitResponse_parses() {
        val ok = json.decodeFromString(SkinSubmitResponse.serializer(), """{"ok":true,"id":"abc123"}""")
        assertTrue(ok.ok)
        assertEquals("abc123", ok.id)
        val err = json.decodeFromString(SkinSubmitResponse.serializer(), """{"ok":false,"error":"Name too short"}""")
        assertFalse(err.ok)
        assertEquals("Name too short", err.error)
    }

    @Test fun statusFeed_parses() {
        val feed = json.decodeFromString(
            SkinStatusFeedDto.serializer(),
            """{"statuses":[{"id":"a","status":"published"},{"id":"b","status":"archived"}],"removed":["c"]}""",
        )
        assertEquals(2, feed.statuses.size)
        assertEquals(listOf("c"), feed.removed)
    }

    @Test fun mySubmission_roundTrip() {
        val list = listOf(MySubmission(id = "x1", name = "Grape", ts = 123L))
        val raw = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(MySubmission.serializer()), list)
        val back = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(MySubmission.serializer()), raw)
        assertEquals(list, back)
    }
}

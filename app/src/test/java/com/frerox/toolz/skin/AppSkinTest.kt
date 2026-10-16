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

    @Test fun everyPresetHasGradient() {
        // The in-app gradient toggle works on all presets, so none may be flat-only.
        IconCatalog.BUILT_IN.forEach { assertNotNull("no bodyEnd: ${it.id}", it.bodyEnd) }
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

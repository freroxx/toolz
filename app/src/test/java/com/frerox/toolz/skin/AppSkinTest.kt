package com.frerox.toolz.data.skin

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AppSkinTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    @Test fun catalogHasManyPresets() {
        assertTrue(IconCatalog.BUILT_IN.size >= 20)
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

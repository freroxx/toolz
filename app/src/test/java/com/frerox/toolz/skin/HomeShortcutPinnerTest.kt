/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.skin

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HomeShortcutPinnerTest {
    private val shapes = listOf<String?>(null, "CIRCLE")

    @Test fun shortcutIdsAreUniquePerPresetAndShape() {
        // Re-pinning must update the existing tile, so no two presets/shapes may collide.
        val ids = IconCatalog.BUILT_IN.flatMap { spec -> shapes.map { HomeShortcutPinner.shortcutId(spec.id, it) } }
        assertEquals(IconCatalog.BUILT_IN.size * shapes.size, ids.distinct().size)
        assertEquals("toolz_tile_grape_haze", HomeShortcutPinner.shortcutId("grape-haze", "OCTAGON"))
        assertEquals("toolz_tile_grape_haze_circle", HomeShortcutPinner.shortcutId("grape-haze", "CIRCLE"))
        assertEquals("toolz_tile_grape_haze_circle", HomeShortcutPinner.shortcutId("grape-haze", "PILL"))
    }

    @Test fun presetIdRoundTripsFromShortcutId() {
        for (spec in IconCatalog.BUILT_IN) {
            for (shape in shapes) {
                val id = HomeShortcutPinner.shortcutId(spec.id, shape)
                assertEquals(spec.id, HomeShortcutPinner.presetIdFromShortcut(id))
            }
        }
    }

    @Test fun refreshOnlyMatchesPresetPins() {
        // The custom-photo pin and unrelated shortcuts must survive a shape refresh.
        assertNull(HomeShortcutPinner.presetIdFromShortcut(HomeShortcutPinner.CUSTOM_PHOTO_ID))
        assertNull(HomeShortcutPinner.presetIdFromShortcut("toolz_tile_not_a_preset"))
        assertNull(HomeShortcutPinner.presetIdFromShortcut("toolz_tile_"))
        assertNull(HomeShortcutPinner.presetIdFromShortcut("com.example.other"))
        assertNull(HomeShortcutPinner.presetIdFromShortcut(null))
        assertFalse(HomeShortcutPinner.isPresetShortcut(HomeShortcutPinner.CUSTOM_PHOTO_ID))
        assertTrue(HomeShortcutPinner.isPresetShortcut(HomeShortcutPinner.shortcutId("midnight", null)))
    }

    @Test fun tileNamesFollowManifestArt() {
        assertEquals("ic_fg_grape_haze", PresetTileRenderer.fgName("grape-haze", null))
        assertEquals("ic_fg_grape_haze", PresetTileRenderer.fgName("grape-haze", "OCTAGON"))
        assertEquals("ic_fg_grape_haze_circle", PresetTileRenderer.fgName("grape-haze", "CIRCLE"))
        assertEquals("ic_fg_teal_pop_circle", PresetTileRenderer.fgName("teal-pop", "CIRCLE"))
        // Shape only changes the foreground; the body colour is per preset.
        assertEquals("ic_bg_grape_haze", PresetTileRenderer.bgName("grape-haze"))
        assertEquals("ic_fg_grape_haze", PresetTileRenderer.fgName("nope", null))
        assertEquals("ic_bg_grape_haze", PresetTileRenderer.bgName("nope"))
    }

    @Test fun everyPresetHasRenderableTileArt() {
        // getIdentifier fails silently at runtime; catch a catalog/art mismatch here.
        val res = listOf(File("src/main/res"), File("app/src/main/res")).firstOrNull { it.isDirectory }
            ?: error("Cannot locate res/ from ${File(".").absolutePath}")
        val backgrounds = File(res, "values/ic_icon_backgrounds.xml").readText()
        for (spec in IconCatalog.BUILT_IN) {
            val san = spec.id.replace('-', '_')
            assertTrue(
                "missing mipmap for ${spec.id}",
                File(res, "mipmap-anydpi-v26/ic_icon_$san.xml").isFile,
            )
            for (shape in listOf<String?>(null, "CIRCLE")) {
                assertTrue(
                    "missing ${PresetTileRenderer.fgName(spec.id, shape)}",
                    File(res, "drawable/${PresetTileRenderer.fgName(spec.id, shape)}.xml").isFile,
                )
            }
            assertTrue(
                "missing ${PresetTileRenderer.bgName(spec.id)}",
                backgrounds.contains("name=\"${PresetTileRenderer.bgName(spec.id)}\""),
            )
        }
    }
}

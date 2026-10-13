package com.frerox.toolz.data.skin

import org.junit.Assert.*
import org.junit.Test

class AppSkinTest {
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
        assertEquals(spec.background, spec.carveResolved)
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
}

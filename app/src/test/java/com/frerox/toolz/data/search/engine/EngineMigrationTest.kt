package com.frerox.toolz.data.search.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineMigrationTest {

    @Test
    fun `removed legacy engines migrate to META`() {
        val legacyEngines = listOf(
            "CUSTOM", "custom", "BRAVE", "GOOGLE", "STARTPAGE",
            "ECOSIA", "SWISSCOWS", "MOJEEK", "PRESEARCH",
            "brave", "google", "unknown_engine"
        )

        for (legacy in legacyEngines) {
            assertEquals(EngineId.META, EngineId.fromString(legacy))
        }
    }

    @Test
    fun `maintained engines parse correctly`() {
        assertEquals(EngineId.YAHOO, EngineId.fromString("YAHOO"))
        assertEquals(EngineId.YAHOO, EngineId.fromString("yahoo"))
        assertEquals(EngineId.QWANT, EngineId.fromString("QWANT"))
        assertEquals(EngineId.MARGINALIA, EngineId.fromString("MARGINALIA"))
        assertEquals(EngineId.BING, EngineId.fromString("BING"))
        // DUCKDUCKGO was retired once, but is a maintained engine again.
        assertEquals(EngineId.DUCKDUCKGO, EngineId.fromString("DUCKDUCKGO"))
        assertEquals(EngineId.DUCKDUCKGO, EngineId.fromString("duckduckgo"))
        assertEquals(EngineId.META, EngineId.fromString("META"))
    }
}

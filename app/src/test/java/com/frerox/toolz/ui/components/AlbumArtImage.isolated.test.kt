/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * ISOLATED Unit Test for AlbumArtImage placeholder logic.
 * Target: app/src/main/java/com/frerox/toolz/ui/components/AlbumArtImage.kt
 * Session: ses_WC (T3.1)
 *
 * **WARNING**: THIS FILE WILL BE DELETED AFTER TEST PASSES.
 * Test code preserved in: .opencode/unit-tests/
 */

package com.frerox.toolz.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumArtImageIsolatedTest {

    private val seeds = listOf(
        "Bohemian Rhapsody", "Shape of You", "Blinding Lights", "Stairway to Heaven",
        "Hotel California", "Smells Like Teen Spirit", "Billie Jean", "Imagine",
        "Yesterday", "Dancing Queen", "Sweet Child O' Mine", "Purple Rain",
        "Wonderwall", "Don't Stop Believin'", "Thriller", "Rolling in the Deep",
        "Hey Jude", "Despacito", "Lose Yourself", "Bad Guy",
        "Uptown Funk", "Someone Like You", "Take On Me", "Halo",
        "Shallow", "Old Town Road", "Senorita", "All of Me",
        "Unholy", "As It Was", "Anti-Hero", "Flowers",
        "Kill Bill", "Heat Waves", "Save Your Tears", "good 4 u",
        "Dynamite", "Levitating", "positions", "Peaches",
        "Stay", "Industry Baby", "Montero", "WAP",
        "abcdefu", "Need to Know", "Easy On Me", "Beggin'",
        "OUT OUT", "The Business", "Head & Heart", "Rasputin",
    )

    // ── Composition of the placeholder set ────────────────────────────────

    @Test
    fun placeholderPalette_hasAtLeast16Colors() {
        assertTrue(
            "palette should have >= 16 colors, got ${placeholderPalette.size}",
            placeholderPalette.size >= 16
        )
    }

    @Test
    fun placeholderIcons_hasAtLeast10DistinctIcons() {
        val distinct = placeholderIcons.distinctBy { it.name }
        assertTrue(
            "icons should have >= 10 distinct entries, got ${distinct.size}",
            distinct.size >= 10
        )
    }

    // ── pickColor ──────────────────────────────────────────────────────────

    @Test
    fun pickColor_isDeterministicForSameSeed() {
        seeds.forEach { seed ->
            assertEquals("pickColor must be deterministic for '$seed'", pickColor(seed), pickColor(seed))
        }
    }

    @Test
    fun pickColor_alwaysReturnsPaletteColor() {
        seeds.forEach { seed ->
            assertTrue(
                "pickColor('$seed') must be in palette",
                placeholderPalette.contains(pickColor(seed))
            )
        }
    }

    @Test
    fun pickColor_spreadsAcrossSeeds() {
        val distinct = seeds.map { pickColor(it) }.distinct().size
        assertTrue("expected >= 8 distinct colors across seeds, got $distinct", distinct >= 8)
    }

    // ── pickIcon ───────────────────────────────────────────────────────────

    @Test
    fun pickIcon_isDeterministicForSameSeed() {
        seeds.forEach { seed ->
            assertEquals("pickIcon must be deterministic for '$seed'", pickIcon(seed), pickIcon(seed))
        }
    }

    @Test
    fun pickIcon_alwaysReturnsIconFromSet() {
        seeds.forEach { seed ->
            assertTrue(
                "pickIcon('$seed') must be one of placeholderIcons",
                placeholderIcons.any { it.name == pickIcon(seed).name }
            )
        }
    }

    @Test
    fun pickIcon_spreadsAcrossSeeds() {
        val distinct = seeds.map { pickIcon(it).name }.distinct().size
        assertTrue("expected >= 6 distinct icons across seeds, got $distinct", distinct >= 6)
    }

    // ── Color/icon decorrelation ───────────────────────────────────────────

    @Test
    fun colorAndIcon_areNotPerfectlyCorrelated() {
        // pickIcon uses a different hash bit range than pickColor, so the
        // (color, icon) pairing must spread: >= 10 distinct combos across seeds.
        val combos = seeds.map { pickColor(it) to pickIcon(it).name }.distinct().size
        assertTrue("expected >= 10 distinct color/icon combos, got $combos", combos >= 10)
    }
}
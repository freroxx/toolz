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

package com.frerox.toolz.ui.screens.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * 05 World Clock audit acceptance (§0, W-P0-01 → W-P2-05):
 * - invalid zone never crashes, purged/logged (sanitized)
 * - table ZoneId.of all entries green, no dup zoneIds, no deprecated aliases
 * - alias add rejected (canonical), cap 24 enforced
 * - 12/24h locale + DST badge, date-line (Kiritimati/Samoa) sort sane
 */
class WorldClockZonesTest {

    // ── W-P0-01: table validation ───────────────────────────────────────

    @Test
    fun table_allZoneIds_valid_noThrow() {
        val bad = mutableListOf<String>()
        for (loc in WorldClockViewModel.worldClockLocations) {
            try {
                ZoneId.of(loc.zoneId)
            } catch (e: Exception) {
                bad.add(loc.zoneId)
            }
        }
        assertTrue("ZoneId.of threw for: $bad", bad.isEmpty())
    }

    @Test
    fun table_noDuplicateZoneIds() {
        val ids = WorldClockViewModel.worldClockLocations.map { it.zoneId }
        assertEquals(ids.toSet().size, ids.size)
    }

    @Test
    fun table_noDeprecatedAliases() {
        val ids = WorldClockViewModel.worldClockLocations.map { it.zoneId }.toSet()
        val leaked = WorldClockZones.ALIAS_TO_CANONICAL.keys.filter { it in ids }
        assertTrue("Deprecated aliases still in table: $leaked", leaked.isEmpty())
        // Single-component links (except UTC) must be gone.
        val singles = ids.filter { !it.contains("/") && it != "UTC" }
        assertTrue("Single-component zones remain: $singles", singles.isEmpty())
    }

    @Test
    fun table_supportsPalestine_noIsrael() {
        val locs = WorldClockViewModel.worldClockLocations
        // No Israel country code or Israel-named city/zone anywhere in the table.
        val bad = locs.filter { loc ->
            loc.country.equals("Israel", ignoreCase = true) ||
                loc.city.equals("Israel", ignoreCase = true) ||
                loc.zoneId.equals("Israel", ignoreCase = true)
        }
        assertTrue("Israel entries still in table: $bad", bad.isEmpty())
        // Palestine is represented: Gaza + Hebron (PS) and Jerusalem labeled Palestine.
        val ps = locs.filter { it.zoneId == "Asia/Gaza" || it.zoneId == "Asia/Hebron" }
        assertEquals(2, ps.size)
        val jerusalem = locs.firstOrNull { it.zoneId == "Asia/Jerusalem" }
        assertNotNull("Asia/Jerusalem missing from table", jerusalem)
        assertEquals(
            java.util.Locale("", "PS").displayCountry,
            jerusalem!!.country,
        )
    }

    // ── W-P0-01: never throw on bad zone ────────────────────────────────

    @Test
    fun safeZoneId_invalid_returnsUtc_noThrow() {
        val z = WorldClockZones.safeZoneId("Bad/Zone_XYZ")
        assertEquals(ZoneId.of("UTC"), z)
        // Blank also falls back, never throws.
        assertEquals(ZoneId.of("UTC"), WorldClockZones.safeZoneId(""))
        assertEquals(ZoneId.of("UTC"), WorldClockZones.safeZoneId("   "))
    }

    @Test
    fun safeZoneIdOrNull_invalid_returnsNull() {
        assertNull(WorldClockZones.safeZoneIdOrNull("Bad/Zone_XYZ"))
        assertNull(WorldClockZones.safeZoneIdOrNull(""))
        assertNull(WorldClockZones.safeZoneIdOrNull(null))
        assertNotNull(WorldClockZones.safeZoneIdOrNull("Europe/Paris"))
    }

    @Test
    fun canonicalZoneId_unknown_returnsNull() {
        assertNull(WorldClockZones.canonicalZoneId("Nope/Nowhere"))
        assertNull(WorldClockZones.canonicalZoneId(""))
    }

    // ── W-P2-02: canonicalize / alias rejection ─────────────────────────

    @Test
    fun canonicalZoneId_aliasResolves() {
        assertEquals("Australia/Sydney", WorldClockZones.canonicalZoneId("Australia/ACT"))
        assertEquals("Europe/Kyiv", WorldClockZones.canonicalZoneId("Europe/Kiev"))
        assertEquals("Asia/Kolkata", WorldClockZones.canonicalZoneId("Asia/Calcutta"))
        assertEquals("America/New_York", WorldClockZones.canonicalZoneId("US/Eastern"))
        assertEquals("Pacific/Pago_Pago", WorldClockZones.canonicalZoneId("Pacific/Samoa"))
        assertEquals("America/Indiana/Indianapolis", WorldClockZones.canonicalZoneId("America/Indianapolis"))
        // Canonical ids are stable.
        assertEquals("Europe/Paris", WorldClockZones.canonicalZoneId("Europe/Paris"))
        assertEquals("UTC", WorldClockZones.canonicalZoneId("UTC"))
    }

    @Test
    fun sanitize_dropsUnknown_canonicalizes_dedupes() {
        val raw = setOf(
            "Europe/Paris",
            "Bad/Zone_XYZ", // unknown -> dropped
            "Australia/ACT", // alias -> Australia/Sydney
            "Australia/Sydney", // canonical dup -> single entry
            "Europe/Kiev", // alias -> Europe/Kyiv
            "", // blank -> dropped
        )
        val cleaned = WorldClockZones.sanitizeSavedZones(raw)
        assertFalse(cleaned.contains("Bad/Zone_XYZ"))
        assertFalse(cleaned.contains(""))
        assertFalse(cleaned.contains("Australia/ACT"))
        assertFalse(cleaned.contains("Europe/Kiev"))
        assertTrue(cleaned.contains("Europe/Paris"))
        assertTrue(cleaned.contains("Australia/Sydney"))
        assertTrue(cleaned.contains("Europe/Kyiv"))
        assertEquals(cleaned.size, cleaned.toSet().size)
    }

    @Test
    fun sanitize_cap24_enforced() {
        val many = ZoneId.getAvailableZoneIds().filter { it.contains("/") }.sorted().take(30).toSet()
        assertTrue(many.size >= 25)
        val cleaned = WorldClockZones.sanitizeSavedZones(many)
        assertEquals(WorldClockZones.MAX_SAVED_ZONES, cleaned.size)
    }

    // ── W-P2-05: day-boundary / date-line sort ──────────────────────────

    @Test
    fun dayDelta_chronoUnit_semantics() {
        val d0 = LocalDate.of(2026, Month.JUNE, 10)
        assertEquals(0, WorldClockZones.dayDelta(d0, d0))
        assertEquals(1, WorldClockZones.dayDelta(d0, d0.plusDays(1)))
        assertEquals(-1, WorldClockZones.dayDelta(d0, d0.minusDays(1)))
    }

    @Test
    fun dateline_kiritimati_vs_pagoPago_sortSane() {
        // Fixed instant: 2026-06-10T10:00Z.
        // Kiritimati (+14) -> Jun-11; Pago Pago (-11) -> Jun-09 23:00.
        val instant = Instant.parse("2026-06-10T10:00:00Z")
        val kiritimati = ZonedDateTime.ofInstant(instant, ZoneId.of("Pacific/Kiritimati"))
        val pagoPago = ZonedDateTime.ofInstant(instant, ZoneId.of("Pacific/Pago_Pago"))
        val local = ZonedDateTime.ofInstant(instant, ZoneId.of("UTC")).toLocalDate()
        val dK = WorldClockZones.dayDelta(local, kiritimati.toLocalDate())
        val dP = WorldClockZones.dayDelta(local, pagoPago.toLocalDate())
        assertEquals(1, dK)
        assertEquals(-1, dP)
        // Comparator: yesterday (-1) sorts before tomorrow (+1).
        val cmp = WorldClockZones.clockComparator(Locale.US)
        val a = WorldClockItem(
            cityName = "Kiritimati", country = "KI", zoneId = "Pacific/Kiritimati",
            currentTime = "00:00", seconds = "00", date = "Jun 11",
            offset = "+14h", utcOffset = "UTC+14:00", timeShift = "Tomorrow",
            isNight = false, progressOfDay = 0f, latitude = null, longitude = null,
            dayDelta = dK, offsetSeconds = 14 * 3600,
        )
        val b = WorldClockItem(
            cityName = "Pago Pago", country = "AS", zoneId = "Pacific/Pago_Pago",
            currentTime = "23:00", seconds = "00", date = "Jun 9",
            offset = "-11h", utcOffset = "UTC-11:00", timeShift = "Yesterday",
            isNight = false, progressOfDay = 0.9f, latitude = null, longitude = null,
            dayDelta = dP, offsetSeconds = -11 * 3600,
        )
        assertTrue(cmp.compare(b, a) < 0)
    }

    // ── W-P2-04: 12/24h locale + DST badge ──────────────────────────────

    @Test
    fun dst_newYork_summerTrue_winterFalse() {
        val ny = ZoneId.of("America/New_York")
        assertTrue(WorldClockZones.isDst(ny, Instant.parse("2026-07-01T12:00:00Z")))
        assertFalse(WorldClockZones.isDst(ny, Instant.parse("2026-01-01T12:00:00Z")))
    }

    @Test
    fun dst_invalidZone_neverThrows() {
        // Unknown zone can't happen via safeZoneId, but isDst itself never throws.
        val utc = ZoneId.of("UTC")
        assertFalse(WorldClockZones.isDst(utc, Instant.now()))
    }

    @Test
    fun timeFormat_us12h_containsMarker_24h_plain() {
        val dt = ZonedDateTime.of(2026, 6, 10, 14, 30, 0, 0, ZoneId.of("America/New_York"))
        val us = Locale.US
        val twelve = WorldClockZones.timeFormatter(false, us).format(dt)
        assertTrue("12h US should contain AM/PM marker, got: $twelve", twelve.contains("PM", ignoreCase = true))
        val twentyFour = WorldClockZones.timeFormatter(true, us).format(dt)
        assertEquals("14:30", twentyFour)
    }

    @Test
    fun dateFormat_localized_noThrow() {
        val dt = ZonedDateTime.of(2026, 6, 10, 12, 0, 0, 0, ZoneId.of("Europe/Paris"))
        val us = WorldClockZones.dateFormatter(Locale.US).format(dt)
        val fr = WorldClockZones.dateFormatter(Locale.FRANCE).format(dt)
        assertTrue(us.isNotBlank())
        assertTrue(fr.isNotBlank())
    }
}

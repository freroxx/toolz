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

import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Central helpers for World Clock (§0 of 05-worldclock-audit).
 *
 * Guards:
 * - Never throw on bad zone: use [safeZoneId] / [safeZoneIdOrNull] (UTC fallback).
 * - Canonicalize zones via TZDB [ZoneId.normalized] + explicit allowlist
 *   [ALIAS_TO_CANONICAL] (never ad-hoc string-replace like Kiev->Kyiv).
 */
object WorldClockZones {

    /** Hard cap for persisted zones (W-P2-03). */
    const val MAX_SAVED_ZONES = 24

    /** Debounce for search (W-P2-01): 150-250ms. */
    const val SEARCH_DEBOUNCE_MS = 200L

    /** Max search results (filter -> take -> sort small N). */
    const val SEARCH_TAKE = 18

    val FALLBACK_ZONE_ID = "UTC"

    fun safeZoneIdOrNull(id: String?): ZoneId? {
        if (id.isNullOrBlank()) return null
        return runCatching { ZoneId.of(id.trim()) }.getOrNull()
    }

    /** Never throws. Returns UTC fallback for unknown/blank ids. */
    fun safeZoneId(id: String): ZoneId =
        safeZoneIdOrNull(id) ?: ZoneId.of(FALLBACK_ZONE_ID)

    /**
     * Canonical zone id, or null for unknown ids.
     * Resolution order: trim -> alias allowlist -> [ZoneId.normalized].
     */
    fun canonicalZoneId(id: String): String? {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) return null
        ALIAS_TO_CANONICAL[trimmed]?.let { return it }
        val zone = safeZoneIdOrNull(trimmed) ?: return null
        return zone.normalized().id
    }

    /**
     * Sanitize a persisted set: drop unknowns, canonicalize aliases,
     * dedupe (one entry per canonical id), cap at [MAX_SAVED_ZONES].
     * Order is not preserved (unordered stringSet); callers sort for display.
     * Never throws.
     */
    fun sanitizeSavedZones(raw: Set<String>): LinkedHashSet<String> {
        val out = LinkedHashSet<String>()
        for (id in raw) {
            val canon = runCatching { canonicalZoneId(id) }.getOrNull() ?: continue
            // Drop pure single-component links that slipped through (except UTC).
            if (canon != FALLBACK_ZONE_ID && !canon.contains("/")) continue
            if (!out.add(canon)) continue
            if (out.size >= MAX_SAVED_ZONES) break
        }
        return out
    }

    /** Single [Instant] sample per refresh — avoids straddling DST second. */
    fun nowInstant(): Instant = Instant.now()

    fun zoned(instant: Instant, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.ofInstant(instant, zone)

    /** DST check for badge. Never throws. */
    fun isDst(zone: ZoneId, instant: Instant): Boolean =
        runCatching { zone.rules.isDaylightSavings(instant) }.getOrDefault(false)

    /** Short zone display name (e.g. EDT, CEST). Never throws. */
    fun shortName(zone: ZoneId, instant: Instant, locale: Locale = Locale.getDefault()): String =
        runCatching { zone.getDisplayName(TextStyle.SHORT, locale) }.getOrDefault(zone.id)

    /**
     * Day-boundary delta in whole days (DST-safe: both midnights are 24h apart
     * is obscure — use ChronoUnit.DAYS between local dates).
     */
    fun dayDelta(localDate: LocalDate, targetDate: LocalDate): Long =
        ChronoUnit.DAYS.between(localDate, targetDate)

    /** Sort key: (dayDelta, utcOffsetSeconds, Collator.compare(city)). Local pinned first by caller. */
    fun clockComparator(locale: Locale = Locale.getDefault()): Comparator<WorldClockItem> {
        val collator = Collator.getInstance(locale)
        return compareBy<WorldClockItem> { it.dayDelta }
            .thenBy { it.offsetSeconds }
            .thenComparator { a, b -> collator.compare(a.cityName, b.cityName) }
    }

    /** Locale-aware time formatter honoring system 12/24h setting. */
    fun timeFormatter(is24Hour: Boolean, locale: Locale = Locale.getDefault()): DateTimeFormatter =
        if (is24Hour) {
            DateTimeFormatter.ofPattern("HH:mm", locale)
        } else {
            // SHORT localized time includes AM/PM marker (e.g. "h:mm a" in US).
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        }

    /** Locale-aware date formatter (fixes English-order "EEE, MMM d"). */
    fun dateFormatter(locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    val secondsFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("ss")

    /** Deprecated TZDB links -> canonical. Generated from table audit; keep in sync with table purge. */
    val ALIAS_TO_CANONICAL: Map<String, String> = mapOf(
        "Africa/Asmera" to "Africa/Asmara",
        "Africa/Timbuktu" to "Africa/Abidjan",
        "America/Argentina/ComodRivadavia" to "America/Argentina/Catamarca",
        "America/Atka" to "America/Adak",
        "America/Buenos_Aires" to "America/Argentina/Buenos_Aires",
        "America/Catamarca" to "America/Argentina/Catamarca",
        "America/Coral_Harbour" to "America/Atikokan",
        "America/Cordoba" to "America/Argentina/Cordoba",
        "America/Ensenada" to "America/Tijuana",
        "America/Fort_Wayne" to "America/Indiana/Indianapolis",
        "America/Godthab" to "America/Nuuk",
        "America/Indianapolis" to "America/Indiana/Indianapolis",
        "America/Jujuy" to "America/Argentina/Jujuy",
        "America/Knox_IN" to "America/Indiana/Knox",
        "America/Louisville" to "America/Kentucky/Louisville",
        "America/Mendoza" to "America/Argentina/Mendoza",
        "America/Montreal" to "America/Toronto",
        "America/Nipigon" to "America/Toronto",
        "America/Pangnirtung" to "America/Iqaluit",
        "America/Porto_Acre" to "America/Rio_Branco",
        "America/Rainy_River" to "America/Winnipeg",
        "America/Rosario" to "America/Argentina/Cordoba",
        "America/Santa_Isabel" to "America/Tijuana",
        "America/Shiprock" to "America/Denver",
        "America/Thunder_Bay" to "America/Toronto",
        "America/Virgin" to "America/Port_of_Spain",
        "America/Yellowknife" to "America/Edmonton",
        "Antarctica/South_Pole" to "Pacific/Auckland",
        "Asia/Ashkhabad" to "Asia/Ashgabat",
        "Asia/Calcutta" to "Asia/Kolkata",
        "Asia/Choibalsan" to "Asia/Ulaanbaatar",
        "Asia/Chongqing" to "Asia/Shanghai",
        "Asia/Chungking" to "Asia/Shanghai",
        "Asia/Dacca" to "Asia/Dhaka",
        "Asia/Harbin" to "Asia/Shanghai",
        "Asia/Istanbul" to "Europe/Istanbul",
        "Asia/Kashgar" to "Asia/Urumqi",
        "Asia/Katmandu" to "Asia/Kathmandu",
        "Asia/Macao" to "Asia/Macau",
        "Asia/Rangoon" to "Asia/Yangon",
        "Asia/Saigon" to "Asia/Ho_Chi_Minh",
        "Asia/Tel_Aviv" to "Asia/Jerusalem",
        "Asia/Thimbu" to "Asia/Thimphu",
        "Asia/Ujung_Pandang" to "Asia/Makassar",
        "Asia/Ulan_Bator" to "Asia/Ulaanbaatar",
        "Atlantic/Faeroe" to "Atlantic/Faroe",
        "Atlantic/Jan_Mayen" to "Europe/Berlin",
        "Australia/ACT" to "Australia/Sydney",
        "Australia/Canberra" to "Australia/Sydney",
        "Australia/Currie" to "Australia/Hobart",
        "Australia/LHI" to "Australia/Lord_Howe",
        "Australia/NSW" to "Australia/Sydney",
        "Australia/North" to "Australia/Darwin",
        "Australia/Queensland" to "Australia/Brisbane",
        "Australia/South" to "Australia/Adelaide",
        "Australia/Tasmania" to "Australia/Hobart",
        "Australia/Victoria" to "Australia/Melbourne",
        "Australia/West" to "Australia/Perth",
        "Australia/Yancowinna" to "Australia/Broken_Hill",
        "Brazil/Acre" to "America/Rio_Branco",
        "Brazil/DeNoronha" to "America/Noronha",
        "Brazil/East" to "America/Sao_Paulo",
        "Brazil/West" to "America/Manaus",
        "CET" to "Europe/Paris",
        "CST6CDT" to "America/Chicago",
        "Canada/Atlantic" to "America/Halifax",
        "Canada/Central" to "America/Winnipeg",
        "Canada/Eastern" to "America/Toronto",
        "Canada/Mountain" to "America/Edmonton",
        "Canada/Newfoundland" to "America/St_Johns",
        "Canada/Pacific" to "America/Vancouver",
        "Canada/Saskatchewan" to "America/Regina",
        "Canada/Yukon" to "America/Whitehorse",
        "Chile/Continental" to "America/Santiago",
        "Chile/EasterIsland" to "Pacific/Easter",
        "Cuba" to "America/Havana",
        "EET" to "Europe/Athens",
        "EST" to "America/New_York",
        "EST5EDT" to "America/New_York",
        "Egypt" to "Africa/Cairo",
        "Eire" to "Europe/Dublin",
        "Europe/Belfast" to "Europe/London",
        "Europe/Kiev" to "Europe/Kyiv",
        "Europe/Nicosia" to "Asia/Nicosia",
        "Europe/Tiraspol" to "Europe/Chisinau",
        "Europe/Uzhgorod" to "Europe/Kyiv",
        "Europe/Zaporozhye" to "Europe/Kyiv",
        "GB" to "Europe/London",
        "GB-Eire" to "Europe/London",
        "HST" to "Pacific/Honolulu",
        "Hongkong" to "Asia/Hong_Kong",
        "Iceland" to "Atlantic/Reykjavik",
        "Iran" to "Asia/Tehran",
        "Jamaica" to "America/Jamaica",
        "Japan" to "Asia/Tokyo",
        "Kwajalein" to "Pacific/Kwajalein",
        "Libya" to "Africa/Tripoli",
        "MET" to "Europe/Paris",
        "MST" to "America/Denver",
        "MST7MDT" to "America/Denver",
        "Mexico/BajaNorte" to "America/Tijuana",
        "Mexico/BajaSur" to "America/Mazatlan",
        "Mexico/General" to "America/Mexico_City",
        "NZ" to "Pacific/Auckland",
        "NZ-CHAT" to "Pacific/Chatham",
        "Navajo" to "America/Denver",
        "PRC" to "Asia/Shanghai",
        "PST8PDT" to "America/Los_Angeles",
        "Pacific/Enderbury" to "Pacific/Kanton",
        "Pacific/Johnston" to "Pacific/Honolulu",
        "Pacific/Ponape" to "Pacific/Pohnpei",
        "Pacific/Samoa" to "Pacific/Pago_Pago",
        "Pacific/Truk" to "Pacific/Chuuk",
        "Pacific/Yap" to "Pacific/Chuuk",
        "Poland" to "Europe/Warsaw",
        "Portugal" to "Europe/Lisbon",
        "ROC" to "Asia/Taipei",
        "ROK" to "Asia/Seoul",
        "Singapore" to "Asia/Singapore",
        "Turkey" to "Europe/Istanbul",
        "US/Alaska" to "America/Anchorage",
        "US/Aleutian" to "America/Adak",
        "US/Arizona" to "America/Phoenix",
        "US/Central" to "America/Chicago",
        "US/East-Indiana" to "America/Indiana/Indianapolis",
        "US/Eastern" to "America/New_York",
        "US/Hawaii" to "Pacific/Honolulu",
        "US/Indiana-Starke" to "America/Indiana/Knox",
        "US/Michigan" to "America/Detroit",
        "US/Mountain" to "America/Denver",
        "US/Pacific" to "America/Los_Angeles",
        "US/Samoa" to "Pacific/Pago_Pago",
        "W-SU" to "Europe/Moscow",
        "WET" to "Europe/Lisbon",
    )
}

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

package com.frerox.toolz.data.update

/**
 * Dotted version comparison used by both update sources.
 *
 * Accepts an optional leading `v`, ignores pre-release/build suffixes
 * (`1.2.3-beta`, `1.2.3+5`) and treats missing segments as zero, so
 * `1.2` equals `1.2.0`. Unparsable input compares equal instead of
 * pretending an update exists.
 *
 * Pure Kotlin on purpose — unit-tested without Android.
 */
object UpdateVersion {

    fun normalize(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V")

    /**
     * True when [candidate] is a strictly newer version than [current].
     *
     * All check/skip/restore decisions are phrased as
     * `isNewer(current, candidate)`.
     */
    fun isNewer(current: String, candidate: String): Boolean =
        compare(candidate, current) > 0

    /** `> 0` when [a] is newer than [b], `< 0` when older, `0` when equal. */
    fun compare(a: String, b: String): Int {
        val left = parse(a)
        val right = parse(b)
        if (left.isEmpty() || right.isEmpty()) return 0
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun parse(value: String): List<Int> {
        val core = normalize(value)
            .substringBefore('-')
            .substringBefore('+')
        return core.split('.')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
    }
}

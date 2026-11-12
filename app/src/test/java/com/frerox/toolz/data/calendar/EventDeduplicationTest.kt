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

package com.frerox.toolz.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EventDeduplicationTest {

    @Test
    fun testDeduplicationKeyMinuteBucket() {
        val t1 = 1700000000000L
        val t2 = t1 + 30_000L // 30 seconds later (same minute bucket)
        val t3 = t1 + 75_000L // 75 seconds later (different minute bucket)

        val key1 = EventRepository.deduplicationKey("Team Sync", t1)
        val key2 = EventRepository.deduplicationKey("  Team Sync  ", t2)
        val key3 = EventRepository.deduplicationKey("Team Sync", t3)

        assertEquals("Same minute and trimmed title should produce identical keys", key1, key2)
        assertNotEquals("Different minute buckets should produce distinct keys", key1, key3)
    }

    @Test
    fun testDeduplicationKeyCaseInsensitivity() {
        val t = 1700000000000L
        val keyLower = EventRepository.deduplicationKey("dentist appointment", t)
        val keyUpper = EventRepository.deduplicationKey("DENTIST APPOINTMENT", t)
        val keyMixed = EventRepository.deduplicationKey("Dentist Appointment", t)

        assertEquals(keyLower, keyUpper)
        assertEquals(keyLower, keyMixed)
    }

    @Test
    fun testDeduplicationKeyEventTypeHandling() {
        val t = 1700000000000L
        val keyGeneral = EventRepository.deduplicationKey("Review", t, "GENERAL")
        val keyBlank = EventRepository.deduplicationKey("Review", t, "")
        val keyWork = EventRepository.deduplicationKey("Review", t, "WORK")
        val keyExam = EventRepository.deduplicationKey("Review", t, "EXAM")

        assertEquals("GENERAL and blank type should produce identical base keys", keyGeneral, keyBlank)
        assertNotEquals("Specific types like WORK should differentiate the key", keyGeneral, keyWork)
        assertNotEquals("WORK and EXAM should produce distinct keys", keyWork, keyExam)
    }

    @Test
    fun testOccurrenceKeyFormat() {
        val event = EventEntry(
            id = 42,
            title = "Test",
            timestamp = 1700000000000L
        )
        val occurrenceKey = EventRepository.occurrenceKey(event)
        assertEquals("42@1700000000000", occurrenceKey)
    }
}

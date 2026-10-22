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
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsParserTest {

    @Test
    fun testParseSimpleIcs() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//EN
            BEGIN:VEVENT
            UID:event-12345@test
            DTSTART:20261022T100000Z
            DTEND:20261022T110000Z
            SUMMARY:Team Meeting
            DESCRIPTION:Discuss Q4 roadmap and architecture
            CATEGORIES:WORK
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")

        val events = IcsParser.parse(ics)
        assertEquals(1, events.size)

        val event = events.first()
        assertEquals("Team Meeting", event.title)
        assertEquals("Discuss Q4 roadmap and architecture", event.description)
        assertEquals("WORK", event.eventType)
        assertEquals(Recurrence.WEEKLY, event.recurringRule)
        assertEquals(60, event.durationMinutes)
    }

    @Test
    fun testExportAndReimportRoundTrip() {
        val originalEvent = EventEntry(
            id = 1,
            title = "Doctor Appointment",
            description = "Annual check-up with Dr. Smith, includes blood test.",
            timestamp = 1792663200000L,
            durationMinutes = 45,
            endTimestamp = 1792665900000L,
            eventType = "PERSONAL",
            recurringRule = Recurrence.MONTHLY,
            subjectColor = "#4CAF50"
        )

        val exportedIcs = IcsParser.serialize(listOf(originalEvent), calendarName = "MyTestCalendar")
        assertTrue(exportedIcs.contains("BEGIN:VCALENDAR"))
        assertTrue(exportedIcs.contains("SUMMARY:Doctor Appointment"))
        assertTrue(exportedIcs.contains("RRULE:FREQ=MONTHLY"))

        val reimported = IcsParser.parse(exportedIcs)
        assertEquals(1, reimported.size)

        val reEvent = reimported.first()
        assertEquals("Doctor Appointment", reEvent.title)
        assertEquals("Annual check-up with Dr. Smith, includes blood test.", reEvent.description)
        assertEquals(Recurrence.MONTHLY, reEvent.recurringRule)
        assertEquals("#4CAF50", reEvent.subjectColor)
        assertEquals(45, reEvent.durationMinutes)
    }

    @Test
    fun testLineFoldingAndUnfolding() {
        val veryLongDescription = "A".repeat(200)
        val event = EventEntry(
            id = 2,
            title = "Long Event",
            description = veryLongDescription,
            timestamp = 1792663200000L,
            durationMinutes = 30
        )

        val ics = IcsParser.serialize(listOf(event))
        // Verify line folding was applied (no single line exceeds 75 octets excluding CRLF)
        val lines = ics.split("\r\n")
        for (line in lines) {
            assertTrue("Line exceeds 75 octets: $line", line.toByteArray(Charsets.UTF_8).size <= 75)
        }

        // Verify unfolding properly reconstructs the full original description
        val parsed = IcsParser.parse(ics)
        assertEquals(1, parsed.size)
        assertEquals(veryLongDescription, parsed.first().description)
    }
}

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

package com.frerox.toolz.util.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OuiDatabaseTest {

    @Test
    fun `normalizes colon separated mac`() {
        assertEquals("B8:27:EB:11:22:33", OuiDatabase.normalize("b8:27:eb:11:22:33"))
    }

    @Test
    fun `normalizes dashless and dotted formats`() {
        assertEquals("DC:A6:32:AA:BB:CC", OuiDatabase.normalize("DCA632AABBCC"))
        assertEquals("E4:5F:01:AA:BB:CC", OuiDatabase.normalize("e45f-01aa-bbcc"))
    }

    @Test
    fun `rejects malformed macs`() {
        assertNull(OuiDatabase.normalize(null))
        assertNull(OuiDatabase.normalize(""))
        assertNull(OuiDatabase.normalize("not-a-mac"))
        assertNull(OuiDatabase.normalize("ZZ:27:EB:11:22:33"))
        assertNull(OuiDatabase.normalize("B8:27:EB"))
    }

    @Test
    fun `identifies known vendors`() {
        assertEquals("Raspberry Pi Trading", OuiDatabase.vendor("B8:27:EB:00:00:01"))
        assertEquals("Apple", OuiDatabase.vendor("AC:87:A3:12:34:56"))
        assertEquals("Espressif", OuiDatabase.vendor("5C:CF:7F:AA:BB:CC"))
        assertEquals("Google", OuiDatabase.vendor("F4:F5:D8:01:02:03"))
    }

    @Test
    fun `unknown mac yields Unknown`() {
        assertEquals("Unknown", OuiDatabase.vendor("00:00:00:00:00:00"))
        assertEquals("Unknown", OuiDatabase.vendor(null))
    }
}

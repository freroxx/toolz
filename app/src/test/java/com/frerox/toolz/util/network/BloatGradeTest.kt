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

import com.frerox.toolz.data.network.BloatGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BloatGradeTest {

    @Test
    fun `null inputs yield null grade`() {
        assertNull(BloatGrade.fromDelta(null))
        assertNull(SpeedTestEngine.grade(null, 40L))
        assertNull(SpeedTestEngine.grade(12L, null))
    }

    @Test
    fun `zero delta is A+`() {
        assertEquals(BloatGrade.A_PLUS, SpeedTestEngine.grade(10L, 10L))
        assertEquals(BloatGrade.A_PLUS, SpeedTestEngine.grade(20L, 24L))
    }

    @Test
    fun `grade boundaries are inclusive`() {
        assertEquals(BloatGrade.A_PLUS, SpeedTestEngine.grade(0L, 5L))   // delta 5 → still A+
        assertEquals(BloatGrade.A, SpeedTestEngine.grade(0L, 6L))
        assertEquals(BloatGrade.A, SpeedTestEngine.grade(0L, 15L))
        assertEquals(BloatGrade.B, SpeedTestEngine.grade(0L, 16L))
        assertEquals(BloatGrade.C, SpeedTestEngine.grade(0L, 40L))
        assertEquals(BloatGrade.D, SpeedTestEngine.grade(0L, 70L))
        assertEquals(BloatGrade.F, SpeedTestEngine.grade(0L, 71L))
        assertEquals(BloatGrade.F, SpeedTestEngine.grade(0L, Long.MAX_VALUE - 1))
    }
}

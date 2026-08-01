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

package com.frerox.toolz.service.step.dsp

/** Fixed-size long ring buffer for step interval history */
class LongRingBuffer(capacity: Int) {
    private val data = LongArray(capacity)
    private var head = 0
    private var count = 0
    
    fun push(value: Long) {
        data[head] = value
        head = (head + 1) % data.size
        if (count < data.size) count++
    }
    
    fun clear() { head = 0; count = 0 }
    
    fun isEmpty(): Boolean = count == 0
    
    fun toList(): List<Long> {
        if (count == 0) return emptyList()
        val result = mutableListOf<Long>()
        val start = if (count < data.size) 0 else head
        var idx = start
        repeat(count) {
            result.add(data[idx])
            idx = (idx + 1) % data.size
        }
        return result
    }
    
    fun last(): Long? = if (count > 0) data[(head - 1 + data.size) % data.size] else null
}

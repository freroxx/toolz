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

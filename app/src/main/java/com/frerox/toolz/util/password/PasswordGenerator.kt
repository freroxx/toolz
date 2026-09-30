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

package com.frerox.toolz.util.password

import java.security.SecureRandom

object PasswordGenerator {
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val NUMBERS = "0123456789"
    // Comma excluded on purpose: naive CSV round-trips split on ','.
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{}|;:.<>?"

    private val secureRandom = SecureRandom()

    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 128

    fun generate(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        require(length in 1..MAX_LENGTH) { "length must be in 1..$MAX_LENGTH" }
        val charPool = StringBuilder(LOWERCASE)
        if (includeUppercase) charPool.append(UPPERCASE)
        if (includeNumbers) charPool.append(NUMBERS)
        if (includeSymbols) charPool.append(SYMBOLS)
        require(charPool.isNotEmpty()) { "character pool is empty" }

        // Guarantee at least one char from each requested class.
        val required = mutableListOf<Char>()
        if (includeUppercase) required += UPPERCASE[secureRandom.nextInt(UPPERCASE.length)]
        if (includeNumbers) required += NUMBERS[secureRandom.nextInt(NUMBERS.length)]
        if (includeSymbols) required += SYMBOLS[secureRandom.nextInt(SYMBOLS.length)]
        // Always include a lowercase so mixed-case scoring is reachable.
        required += LOWERCASE[secureRandom.nextInt(LOWERCASE.length)]

        val pool = charPool.toString()
        val output = CharArray(length)
        for (i in output.indices) {
            output[i] = pool[secureRandom.nextInt(pool.length)]
        }
        // Overlay required chars at distinct random positions, then shuffle.
        val usedPositions = mutableSetOf<Int>()
        required.forEach { c ->
            if (usedPositions.size >= output.size) return@forEach
            var pos = secureRandom.nextInt(output.size)
            while (!usedPositions.add(pos)) {
                pos = secureRandom.nextInt(output.size)
            }
            output[pos] = c
        }
        // Fisher-Yates shuffle with SecureRandom.
        for (i in output.size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = output[i]
            output[i] = output[j]
            output[j] = tmp
        }
        return output.concatToString()
    }

    /**
     * Entropy-based strength on a 0-4 scale (all levels reachable).
     * pool = charset size actually used; entropy = length * log2(pool),
     * minus penalties for repeats, sequences and well-known weak passwords.
     * 0: <28 bits, 1: <40, 2: <60, 3: <80, 4: >=80.
     */
    fun calculateStrength(password: String): Int {
        if (password.isEmpty()) return 0
        if (password.length < 4) return 0
        val lower = password.any { it.isLowerCase() }
        val upper = password.any { it.isUpperCase() }
        val digit = password.any { it.isDigit() }
        val symbol = password.any { !it.isLetterOrDigit() }
        var pool = 0
        if (lower) pool += 26
        if (upper) pool += 26
        if (digit) pool += 10
        if (symbol) pool += 24
        if (pool == 0) pool = 26
        var entropy = password.length * (Math.log(pool.toDouble()) / Math.log(2.0))

        // Penalty: heavy character repetition.
        val distinct = password.toSet().size
        if (distinct < password.length / 2) entropy -= 15.0
        // Penalty: sequential runs (abc, 123, qwerty-adjacent).
        if (hasSequence(password)) entropy -= 12.0
        // Penalty: common weak passwords.
        val lowerPw = password.lowercase()
        if (WEAK_PASSWORDS.any { lowerPw.contains(it) }) entropy -= 20.0
        // Bonus: length >= 16.
        if (password.length >= 16) entropy += 8.0

        return when {
            entropy < 28 -> 0
            entropy < 40 -> 1
            entropy < 60 -> 2
            entropy < 80 -> 3
            else -> 4
        }
    }

    private fun hasSequence(password: String): Boolean {
        if (password.length < 4) return false
        val s = password.lowercase()
        var run = 1
        for (i in 1 until s.length) {
            if (s[i] == s[i - 1] + 1 || s[i] == s[i - 1] - 1) {
                run++
                if (run >= 4) return true
            } else {
                run = 1
            }
        }
        return false
    }

    private val WEAK_PASSWORDS = setOf(
        "password", "123456", "qwerty", "letmein", "welcome",
        "admin", "monkey", "dragon", "master", "abc123"
    )
}

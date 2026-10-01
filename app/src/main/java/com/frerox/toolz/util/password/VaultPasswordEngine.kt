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

/**
 * Single entropy engine for every password surface (vault sheet + random tool).
 * 5-tier taxonomy: CRITICAL / WEAK / MID / STRONG / ELITE, ordinal 0..4 so
 * stored `PasswordEntity.strength` values stay valid.
 */
object VaultPasswordEngine {
    const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val NUMBERS = "0123456789"
    // Comma excluded: CSV round-trips split on ','.
    const val SYMBOLS = "!@#\$%^&*()-_=+[]{}|;:.<>?"
    const val AMBIGUOUS = "Il1O0"

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 128

    enum class Tier { CRITICAL, WEAK, MID, STRONG, ELITE }

    data class PasswordSpec(
        val length: Int = 16,
        val includeLowercase: Boolean = true,
        val includeUppercase: Boolean = true,
        val includeNumbers: Boolean = true,
        val includeSymbols: Boolean = true,
        val customSymbols: String = "",
        val excludeAmbiguous: Boolean = false,
        val pinMode: Boolean = false
    )

    sealed interface SpecError {
        data object EmptyPool : SpecError
        data class BadLength(val length: Int) : SpecError
    }

    enum class Reason { TOO_SHORT, REPEATS, SEQUENCE, COMMON, GOOD_LENGTH, GOOD_MIX }

    data class StrengthReport(
        val tier: Tier,
        val tierIndex: Int,
        val bits: Double,
        val crackTimeLabel: String,
        val reasons: List<Reason>,
        val suggestions: List<String>
    )

    data class Preset(val name: String, val spec: PasswordSpec)

    val PRESETS = listOf(
        Preset("Login 16", PasswordSpec(length = 16)),
        Preset("Long 32", PasswordSpec(length = 32)),
        Preset("PIN 6", PasswordSpec(length = 6, includeLowercase = false, includeUppercase = false, includeSymbols = false, pinMode = true)),
        Preset("WiFi 20", PasswordSpec(length = 20))
    )

    private val secureRandom = SecureRandom()

    private val weakPasswords = setOf(
        "password", "123456", "qwerty", "letmein", "welcome",
        "admin", "monkey", "dragon", "master", "abc123"
    )

    private val memorableAdjectives = listOf(
        "Swift", "Silent", "Neon", "Crimson", "Azure", "Golden", "Shadow",
        "Crystal", "Electric", "Velvet", "Lunar", "Solar", "Cosmic", "Mystic"
    )
    private val memorableNouns = listOf(
        "Tiger", "Dragon", "Phoenix", "Wolf", "Eagle", "Falcon", "Panther",
        "Fox", "Bear", "Lion", "Shark", "Whale", "Hawk", "Cobra"
    )

    fun buildPool(spec: PasswordSpec): String {
        if (spec.pinMode) return NUMBERS
        val pool = StringBuilder()
        if (spec.includeLowercase) pool.append(LOWERCASE)
        if (spec.includeUppercase) pool.append(UPPERCASE)
        if (spec.includeNumbers) pool.append(NUMBERS)
        val symbols = spec.customSymbols.ifEmpty { if (spec.includeSymbols) SYMBOLS else "" }
        pool.append(symbols)
        var out = pool.toString()
        if (spec.excludeAmbiguous) out = out.filterNot { it in AMBIGUOUS }
        return out
    }

    fun generate(spec: PasswordSpec): Result<String> {
        if (spec.length !in 1..MAX_LENGTH) return Result.failure(
            IllegalArgumentException("length must be in 1..$MAX_LENGTH")
        )
        val pool = buildPool(spec)
        if (pool.isEmpty()) return Result.failure(IllegalStateException("empty pool"))

        // Per-class guarantee from the *filtered* classes.
        val classes = mutableListOf<String>()
        if (!spec.pinMode) {
            if (spec.includeLowercase) filterClass(LOWERCASE, spec)?.let { classes += it }
            if (spec.includeUppercase) filterClass(UPPERCASE, spec)?.let { classes += it }
            if (spec.includeNumbers) filterClass(NUMBERS, spec)?.let { classes += it }
            val symbols = spec.customSymbols.ifEmpty { if (spec.includeSymbols) SYMBOLS else "" }
            if (symbols.isNotEmpty()) filterClass(symbols, spec)?.let { classes += it }
        } else {
            classes += NUMBERS
        }

        val output = CharArray(spec.length) { pool[secureRandom.nextInt(pool.length)] }
        val used = mutableSetOf<Int>()
        val required = classes.map { cls -> cls[secureRandom.nextInt(cls.length)] }
        required.forEach { c ->
            if (used.size >= output.size) return@forEach
            var pos = secureRandom.nextInt(output.size)
            while (!used.add(pos)) pos = secureRandom.nextInt(output.size)
            output[pos] = c
        }
        for (i in output.size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = output[i]
            output[i] = output[j]
            output[j] = tmp
        }
        return Result.success(output.concatToString())
    }

    fun generateMemorable(wordCount: Int = 4, separator: String = "-"): String {
        val count = wordCount.coerceIn(2, 8)
        return List(count) {
            val a = memorableAdjectives[secureRandom.nextInt(memorableAdjectives.size)]
            val n = memorableNouns[secureRandom.nextInt(memorableNouns.size)]
            val num = secureRandom.nextInt(100)
            "$a$n$num"
        }.joinToString(separator)
    }

    fun assess(password: String): StrengthReport {
        if (password.isEmpty() || password.length < 4) {
            return StrengthReport(Tier.CRITICAL, 0, 0.0, "instant", listOf(Reason.TOO_SHORT), listOf("Use at least 12 characters"))
        }
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
        var bits = password.length * (Math.log(pool.toDouble()) / Math.log(2.0))
        val reasons = mutableListOf<Reason>()
        val suggestions = mutableListOf<String>()

        val distinct = password.toSet().size
        if (distinct < password.length / 2) {
            bits -= 15.0
            reasons += Reason.REPEATS
            suggestions += "Avoid repeating the same characters"
        }
        if (hasSequence(password)) {
            bits -= 12.0
            reasons += Reason.SEQUENCE
            suggestions += "Avoid sequences like abc or 123"
        }
        val lowerPw = password.lowercase()
        if (weakPasswords.any { lowerPw.contains(it) }) {
            bits -= 20.0
            reasons += Reason.COMMON
            suggestions += "Avoid common words and patterns"
        }
        if (password.length >= 16) {
            bits += 8.0
            reasons += Reason.GOOD_LENGTH
        }
        if (lower && upper && digit && symbol) reasons += Reason.GOOD_MIX
        if (password.length < 8) {
            reasons += Reason.TOO_SHORT
            suggestions += "Use at least 12 characters"
        }
        if (suggestions.isEmpty() && bits < 60) suggestions += "Add ${60 - bits.toInt()} more bits: lengthen or mix classes"

        val tier = when {
            bits < 28 -> Tier.CRITICAL
            bits < 40 -> Tier.WEAK
            bits < 60 -> Tier.MID
            bits < 80 -> Tier.STRONG
            else -> Tier.ELITE
        }
        return StrengthReport(
            tier = tier,
            tierIndex = tier.ordinal,
            bits = bits.coerceAtLeast(0.0),
            crackTimeLabel = crackTimeLabel(bits),
            reasons = reasons.distinct(),
            suggestions = suggestions.distinct().take(2)
        )
    }

    /** 0..4 tier index, compatible with stored `PasswordEntity.strength`. */
    fun tierOf(password: String): Int = assess(password).tierIndex

    fun crackTimeLabel(bits: Double): String = when {
        bits < 28 -> "instant"
        bits < 40 -> "minutes"
        bits < 60 -> "days"
        bits < 80 -> "years"
        else -> "centuries"
    }

    private fun filterClass(cls: String, spec: PasswordSpec): String? {
        val filtered = if (spec.excludeAmbiguous) cls.filterNot { it in AMBIGUOUS } else cls
        return filtered.takeIf { it.isNotEmpty() }
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
}

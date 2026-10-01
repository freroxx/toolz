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

/**
 * Backwards-compatible facade over [VaultPasswordEngine].
 * New code should use the engine directly (5-tier critical/weak/mid/strong/elite).
 */
object PasswordGenerator {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = VaultPasswordEngine.MAX_LENGTH

    fun generate(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        val spec = VaultPasswordEngine.PasswordSpec(
            length = length,
            includeLowercase = true,
            includeUppercase = includeUppercase,
            includeNumbers = includeNumbers,
            includeSymbols = includeSymbols
        )
        return VaultPasswordEngine.generate(spec).getOrThrow()
    }

    /** 0..4 tier index: 0 critical, 1 weak, 2 mid, 3 strong, 4 elite. */
    fun calculateStrength(password: String): Int =
        VaultPasswordEngine.tierOf(password)
}

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

import android.content.Context

/**
 * Persists the shared generator spec in plain SharedPreferences
 * (length and toggles only — never passwords).
 */
object GeneratorSpecStore {
    private const val PREFS = "generator_spec_prefs"
    private const val KEY_LENGTH = "length"
    private const val KEY_LOWER = "lower"
    private const val KEY_UPPER = "upper"
    private const val KEY_NUMBERS = "numbers"
    private const val KEY_SYMBOLS = "symbols"
    private const val KEY_CUSTOM = "custom"
    private const val KEY_AMBIGUOUS = "ambiguous"
    private const val KEY_PIN = "pin"

    fun load(context: Context): VaultPasswordEngine.PasswordSpec {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return VaultPasswordEngine.PasswordSpec(
            length = p.getInt(KEY_LENGTH, 16)
                .coerceIn(VaultPasswordEngine.MIN_LENGTH, VaultPasswordEngine.MAX_LENGTH),
            includeLowercase = p.getBoolean(KEY_LOWER, true),
            includeUppercase = p.getBoolean(KEY_UPPER, true),
            includeNumbers = p.getBoolean(KEY_NUMBERS, true),
            includeSymbols = p.getBoolean(KEY_SYMBOLS, true),
            customSymbols = p.getString(KEY_CUSTOM, "") ?: "",
            excludeAmbiguous = p.getBoolean(KEY_AMBIGUOUS, false),
            pinMode = p.getBoolean(KEY_PIN, false)
        )
    }

    fun save(context: Context, spec: VaultPasswordEngine.PasswordSpec) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_LENGTH, spec.length)
            .putBoolean(KEY_LOWER, spec.includeLowercase)
            .putBoolean(KEY_UPPER, spec.includeUppercase)
            .putBoolean(KEY_NUMBERS, spec.includeNumbers)
            .putBoolean(KEY_SYMBOLS, spec.includeSymbols)
            .putString(KEY_CUSTOM, spec.customSymbols)
            .putBoolean(KEY_AMBIGUOUS, spec.excludeAmbiguous)
            .putBoolean(KEY_PIN, spec.pinMode)
            .apply()
    }
}

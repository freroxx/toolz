package com.frerox.toolz.ui.screens.math

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class ConversionType {
    LENGTH, WEIGHT, TEMPERATURE, AREA, VOLUME, SPEED, TIME, DIGITAL_STORAGE, ENERGY, FORCE, PRESSURE, POWER, CURRENCY
}

/** A single "from -> to" pair the user has used before, per type. Used to power the quick-history chips. */
data class ConversionHistoryEntry(
    val type: ConversionType,
    val fromUnit: String,
    val toUnit: String
)

data class UnitConverterState(
    val type: ConversionType = ConversionType.LENGTH,
    val inputValue: String = "1",
    val outputValue: String = "",
    val fromUnit: String = "Meter",
    val toUnit: String = "Kilometer",
    val availableUnits: List<String> = emptyList(),
    // Pinned units per conversion type (shown first in the picker). Long-press a unit to toggle.
    val pinnedUnits: Map<ConversionType, Set<String>> = emptyMap(),
    // Most recent distinct from/to pairs, most recent first, capped at 3.
    val history: List<ConversionHistoryEntry> = emptyList(),
    // A short human-readable line describing the active multiplier, e.g. "x 0.3048" or "(C x 9/5) + 32".
    val formulaHint: String = "",
    // True only for CURRENCY, used to render the "approx." tag since rates are static/demo values.
    val isApproximate: Boolean = false,
    // 0f..1f, log-scaled magnitude of the from->to multiplier. Drives the Dial's filled arc so
    // the ring communicates how large the conversion's scale jump is (e.g. Byte -> Bit fills the
    // ring almost fully; Meter -> Yard barely fills it). Not used for any actual math.
    val dialSweep: Float = 0.5f,
    // Starred "from -> to" pairs a person wants pinned at the very top of the History row,
    // independent of recency. Keyed by type so favorites don't bleed across categories.
    val favorites: Map<ConversionType, List<ConversionHistoryEntry>> = emptyMap()
)

@HiltViewModel
class UnitConverterViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(UnitConverterState())
    val uiState: StateFlow<UnitConverterState> = _uiState.asStateFlow()

    private val unitsMap = mapOf(
        ConversionType.LENGTH to listOf("Millimeter", "Centimeter", "Meter", "Kilometer", "Inch", "Foot", "Yard", "Mile"),
        ConversionType.WEIGHT to listOf("Milligram", "Gram", "Kilogram", "Ounce", "Pound", "Stone", "Ton"),
        ConversionType.TEMPERATURE to listOf("Celsius", "Fahrenheit", "Kelvin"),
        ConversionType.AREA to listOf("Sq Meter", "Sq Kilometer", "Sq Foot", "Sq Mile", "Acre", "Hectare"),
        ConversionType.VOLUME to listOf("Milliliter", "Liter", "Cubic Meter", "Gallon", "Quart", "Pint", "Cup"),
        ConversionType.SPEED to listOf("Meters/sec", "Km/h", "Miles/h", "Knot", "Mach"),
        ConversionType.TIME to listOf("Second", "Minute", "Hour", "Day", "Week", "Month", "Year"),
        ConversionType.DIGITAL_STORAGE to listOf("Bit", "Byte", "Kilobyte", "Megabyte", "Gigabyte", "Terabyte", "Petabyte"),
        ConversionType.ENERGY to listOf("Joule", "Kilojoule", "Calorie", "Kilocalorie", "Watt-hour", "Kilowatt-hour", "Electronvolt"),
        ConversionType.FORCE to listOf("Newton", "Kilonewton", "Dyne", "Pound-force", "Gram-force", "Kilogram-force"),
        ConversionType.PRESSURE to listOf("Pascal", "Kilopascal", "Bar", "Millibar", "PSI", "Atmosphere", "Torr"),
        ConversionType.POWER to listOf("Watt", "Kilowatt", "Megawatt", "Horsepower", "Foot-pound/min", "BTU/hour"),
        ConversionType.CURRENCY to listOf("USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "CNY", "INR", "BRL", "RUB", "KRW", "SGD", "NZD", "MXN", "HKD", "IDR", "TRY", "SAR", "AED")
    )

    /** Returns the unit list for [type] with pinned units (if any) sorted to the front, order preserved otherwise. */
    fun unitsFor(type: ConversionType): List<String> {
        val base = unitsMap[type] ?: return emptyList()
        val pinned = _uiState.value.pinnedUnits[type].orEmpty()
        if (pinned.isEmpty()) return base
        return base.sortedBy { if (it in pinned) 0 else 1 }
    }

    init {
        val initialType = ConversionType.LENGTH
        val units = unitsMap[initialType]!!
        _uiState.update { it.copy(availableUnits = units, fromUnit = units[2], toUnit = units[3]) }
        convert()
    }

    fun onTypeChange(type: ConversionType) {
        val units = unitsFor(type)
        _uiState.update {
            it.copy(
                type = type,
                availableUnits = units,
                fromUnit = units[0],
                toUnit = if (units.size > 1) units[1] else units[0],
                isApproximate = type == ConversionType.CURRENCY
            )
        }
        convert()
    }

    fun onInputValueChange(value: String) {
        _uiState.update { it.copy(inputValue = value.filter { c -> c.isDigit() || c == '.' }) }
        convert()
    }

    fun onFromUnitChange(unit: String) {
        _uiState.update { it.copy(fromUnit = unit) }
        convert()
        recordHistory()
    }

    fun onToUnitChange(unit: String) {
        _uiState.update { it.copy(toUnit = unit) }
        convert()
        recordHistory()
    }

    fun swapUnits() {
        _uiState.update { it.copy(fromUnit = it.toUnit, toUnit = it.fromUnit) }
        convert()
        recordHistory()
    }

    /** Toggles whether [unit] is pinned to the top of its category's unit list. */
    fun togglePinned(type: ConversionType, unit: String) {
        _uiState.update { state ->
            val current = state.pinnedUnits[type].orEmpty()
            val updated = if (unit in current) current - unit else current + unit
            val newPinned = state.pinnedUnits + (type to updated)
            state.copy(
                pinnedUnits = newPinned,
                availableUnits = unitsFor(type).let { base ->
                    if (updated.isEmpty()) base else base.sortedBy { if (it in updated) 0 else 1 }
                }
            )
        }
    }

    /** Toggles whether the current from/to pair is starred as a favorite for its type. */
    fun toggleFavorite() {
        val s = _uiState.value
        val entry = ConversionHistoryEntry(s.type, s.fromUnit, s.toUnit)
        _uiState.update {
            val current = it.favorites[it.type].orEmpty()
            val exists = current.any { f -> f.fromUnit == entry.fromUnit && f.toUnit == entry.toUnit }
            val updated = if (exists) {
                current.filterNot { f -> f.fromUnit == entry.fromUnit && f.toUnit == entry.toUnit }
            } else {
                (current + entry).takeLast(6)
            }
            it.copy(favorites = it.favorites + (it.type to updated))
        }
    }

    fun isCurrentFavorite(): Boolean {
        val s = _uiState.value
        return s.favorites[s.type].orEmpty().any { it.fromUnit == s.fromUnit && it.toUnit == s.toUnit }
    }

    /** Restores a from/to pair from the quick-history row. */
    fun applyHistory(entry: ConversionHistoryEntry) {
        val units = unitsFor(entry.type)
        _uiState.update {
            it.copy(
                type = entry.type,
                availableUnits = units,
                fromUnit = entry.fromUnit,
                toUnit = entry.toUnit,
                isApproximate = entry.type == ConversionType.CURRENCY
            )
        }
        convert()
    }

    private fun recordHistory() {
        val s = _uiState.value
        val entry = ConversionHistoryEntry(s.type, s.fromUnit, s.toUnit)
        _uiState.update {
            val withoutDupe = it.history.filterNot { h -> h.type == entry.type && h.fromUnit == entry.fromUnit && h.toUnit == entry.toUnit }
            it.copy(history = (listOf(entry) + withoutDupe).take(3))
        }
    }

    private fun convert() {
        val input = _uiState.value.inputValue.toDoubleOrNull() ?: 0.0
        val from = _uiState.value.fromUnit
        val to = _uiState.value.toUnit
        val type = _uiState.value.type

        val result = when (type) {
            ConversionType.LENGTH -> convertLength(input, from, to)
            ConversionType.WEIGHT -> convertWeight(input, from, to)
            ConversionType.TEMPERATURE -> convertTemp(input, from, to)
            ConversionType.AREA -> convertArea(input, from, to)
            ConversionType.VOLUME -> convertVolume(input, from, to)
            ConversionType.SPEED -> convertSpeed(input, from, to)
            ConversionType.TIME -> convertTime(input, from, to)
            ConversionType.DIGITAL_STORAGE -> convertDigital(input, from, to)
            ConversionType.ENERGY -> convertEnergy(input, from, to)
            ConversionType.FORCE -> convertForce(input, from, to)
            ConversionType.PRESSURE -> convertPressure(input, from, to)
            ConversionType.POWER -> convertPower(input, from, to)
            ConversionType.CURRENCY -> convertCurrency(input, from, to)
        }

        val hint = formulaHint(type, from, to)
        val sweep = dialSweepFor(type, from, to)

        _uiState.update {
            it.copy(
                outputValue = if (result % 1.0 == 0.0) result.toLong().toString() else String.format("%.6f", result).trimEnd('0').trimEnd('.'),
                formulaHint = hint,
                dialSweep = sweep
            )
        }
    }

    /**
     * Log-scales the magnitude of the from->to multiplier into a 0.05f..1f fill fraction for
     * the Dial. A multiplier of 1 (or a same-unit no-op) reads as a small "resting" arc; very
     * large or very small multipliers (e.g. Byte -> Bit at 8x, or Kilometer -> Millimeter at
     * 1,000,000x) fill most or all of the ring. Purely cosmetic — the real multiplier used for
     * the displayed result always comes from the type-specific convert functions above.
     */
    private fun dialSweepFor(type: ConversionType, from: String, to: String): Float {
        if (from == to) return 0.08f
        if (type == ConversionType.TEMPERATURE) return 0.55f
        val multiplier = when (type) {
            ConversionType.LENGTH -> convertLength(1.0, from, to)
            ConversionType.WEIGHT -> convertWeight(1.0, from, to)
            ConversionType.AREA -> convertArea(1.0, from, to)
            ConversionType.VOLUME -> convertVolume(1.0, from, to)
            ConversionType.SPEED -> convertSpeed(1.0, from, to)
            ConversionType.TIME -> convertTime(1.0, from, to)
            ConversionType.DIGITAL_STORAGE -> convertDigital(1.0, from, to)
            ConversionType.ENERGY -> convertEnergy(1.0, from, to)
            ConversionType.FORCE -> convertForce(1.0, from, to)
            ConversionType.PRESSURE -> convertPressure(1.0, from, to)
            ConversionType.POWER -> convertPower(1.0, from, to)
            ConversionType.CURRENCY -> convertCurrency(1.0, from, to)
            ConversionType.TEMPERATURE -> 1.0
        }
        // log10 distance from 1.0, clamped to a reasonable visual range of +/- 6 orders of magnitude.
        val logDistance = kotlin.math.abs(kotlin.math.log10(multiplier.coerceIn(1e-12, 1e12)))
        val normalized = (logDistance / 6.0).coerceIn(0.0, 1.0)
        return (0.1f + normalized.toFloat() * 0.85f).coerceIn(0.05f, 1f)
    }

    /** Builds a short, human-readable description of the multiplier/formula in effect, shown under the ribbon. */
    private fun formulaHint(type: ConversionType, from: String, to: String): String {
        if (from == to) return "Same unit"
        if (type == ConversionType.TEMPERATURE) {
            return when {
                from == "Celsius" && to == "Fahrenheit" -> "(C x 9/5) + 32"
                from == "Fahrenheit" && to == "Celsius" -> "(F - 32) x 5/9"
                from == "Celsius" && to == "Kelvin" -> "C + 273.15"
                from == "Kelvin" && to == "Celsius" -> "K - 273.15"
                from == "Fahrenheit" && to == "Kelvin" -> "(F - 32) x 5/9 + 273.15"
                from == "Kelvin" && to == "Fahrenheit" -> "(K - 273.15) x 9/5 + 32"
                else -> ""
            }
        }
        // For linear/ratio-based categories, back out the effective multiplier by converting 1.0 unit.
        val multiplier = when (type) {
            ConversionType.LENGTH -> convertLength(1.0, from, to)
            ConversionType.WEIGHT -> convertWeight(1.0, from, to)
            ConversionType.AREA -> convertArea(1.0, from, to)
            ConversionType.VOLUME -> convertVolume(1.0, from, to)
            ConversionType.SPEED -> convertSpeed(1.0, from, to)
            ConversionType.TIME -> convertTime(1.0, from, to)
            ConversionType.DIGITAL_STORAGE -> convertDigital(1.0, from, to)
            ConversionType.ENERGY -> convertEnergy(1.0, from, to)
            ConversionType.FORCE -> convertForce(1.0, from, to)
            ConversionType.PRESSURE -> convertPressure(1.0, from, to)
            ConversionType.POWER -> convertPower(1.0, from, to)
            ConversionType.CURRENCY -> convertCurrency(1.0, from, to)
            ConversionType.TEMPERATURE -> 1.0
        }
        val formatted = if (multiplier >= 1000 || multiplier < 0.001) {
            String.format("%.4e", multiplier)
        } else {
            String.format("%.6f", multiplier).trimEnd('0').trimEnd('.')
        }
        return "x $formatted"
    }

    private fun convertLength(value: Double, from: String, to: String): Double {
        val toMeter = mapOf(
            "Millimeter" to 0.001, "Centimeter" to 0.01, "Meter" to 1.0, "Kilometer" to 1000.0,
            "Inch" to 0.0254, "Foot" to 0.3048, "Yard" to 0.9144, "Mile" to 1609.34
        )
        return value * (toMeter[from] ?: 1.0) / (toMeter[to] ?: 1.0)
    }

    private fun convertWeight(value: Double, from: String, to: String): Double {
        val toKg = mapOf(
            "Milligram" to 0.000001, "Gram" to 0.001, "Kilogram" to 1.0, "Ounce" to 0.0283495,
            "Pound" to 0.453592, "Stone" to 6.35029, "Ton" to 1000.0
        )
        return value * (toKg[from] ?: 1.0) / (toKg[to] ?: 1.0)
    }

    private fun convertTemp(value: Double, from: String, to: String): Double {
        val inCelsius = when (from) {
            "Celsius" -> value
            "Fahrenheit" -> (value - 32) * 5 / 9
            "Kelvin" -> value - 273.15
            else -> value
        }
        return when (to) {
            "Celsius" -> inCelsius
            "Fahrenheit" -> (inCelsius * 9 / 5) + 32
            "Kelvin" -> inCelsius + 273.15
            else -> inCelsius
        }
    }

    private fun convertArea(value: Double, from: String, to: String): Double {
        val toSqMeter = mapOf(
            "Sq Meter" to 1.0, "Sq Kilometer" to 1000000.0, "Sq Foot" to 0.092903,
            "Sq Mile" to 2589988.11, "Acre" to 4046.86, "Hectare" to 10000.0
        )
        return value * (toSqMeter[from] ?: 1.0) / (toSqMeter[to] ?: 1.0)
    }

    private fun convertVolume(value: Double, from: String, to: String): Double {
        val toLiter = mapOf(
            "Milliliter" to 0.001, "Liter" to 1.0, "Cubic Meter" to 1000.0,
            "Gallon" to 3.78541, "Quart" to 0.946353, "Pint" to 0.473176, "Cup" to 0.236588
        )
        return value * (toLiter[from] ?: 1.0) / (toLiter[to] ?: 1.0)
    }

    private fun convertSpeed(value: Double, from: String, to: String): Double {
        val toMs = mapOf(
            "Meters/sec" to 1.0, "Km/h" to 0.277778, "Miles/h" to 0.44704,
            "Knot" to 0.514444, "Mach" to 343.0
        )
        return value * (toMs[from] ?: 1.0) / (toMs[to] ?: 1.0)
    }

    private fun convertTime(value: Double, from: String, to: String): Double {
        val toSecond = mapOf(
            "Second" to 1.0, "Minute" to 60.0, "Hour" to 3600.0, "Day" to 86400.0,
            "Week" to 604800.0, "Month" to 2629800.0, "Year" to 31557600.0
        )
        return value * (toSecond[from] ?: 1.0) / (toSecond[to] ?: 1.0)
    }

    private fun convertDigital(value: Double, from: String, to: String): Double {
        val toBit = mapOf(
            "Bit" to 1.0, "Byte" to 8.0, "Kilobyte" to 8192.0, "Megabyte" to 8388608.0,
            "Gigabyte" to 8589934592.0, "Terabyte" to 8796093022208.0, "Petabyte" to 9007199254740992.0
        )
        return value * (toBit[from] ?: 1.0) / (toBit[to] ?: 1.0)
    }

    private fun convertEnergy(value: Double, from: String, to: String): Double {
        val toJoule = mapOf(
            "Joule" to 1.0, "Kilojoule" to 1000.0, "Calorie" to 4.184,
            "Kilocalorie" to 4184.0, "Watt-hour" to 3600.0, "Kilowatt-hour" to 3600000.0,
            "Electronvolt" to 1.602176634e-19
        )
        return value * (toJoule[from] ?: 1.0) / (toJoule[to] ?: 1.0)
    }

    private fun convertForce(value: Double, from: String, to: String): Double {
        val toNewton = mapOf(
            "Newton" to 1.0, "Kilonewton" to 1000.0, "Dyne" to 1e-5,
            "Pound-force" to 4.44822, "Gram-force" to 0.00980665, "Kilogram-force" to 9.80665
        )
        return value * (toNewton[from] ?: 1.0) / (toNewton[to] ?: 1.0)
    }

    private fun convertPressure(value: Double, from: String, to: String): Double {
        val toPascal = mapOf(
            "Pascal" to 1.0, "Kilopascal" to 1000.0, "Bar" to 100000.0,
            "Millibar" to 100.0, "PSI" to 6894.76, "Atmosphere" to 101325.0, "Torr" to 133.322
        )
        return value * (toPascal[from] ?: 1.0) / (toPascal[to] ?: 1.0)
    }

    private fun convertPower(value: Double, from: String, to: String): Double {
        val toWatt = mapOf(
            "Watt" to 1.0, "Kilowatt" to 1000.0, "Megawatt" to 1000000.0,
            "Horsepower" to 745.7, "Foot-pound/min" to 0.022597, "BTU/hour" to 0.293071
        )
        return value * (toWatt[from] ?: 1.0) / (toWatt[to] ?: 1.0)
    }

    private fun convertCurrency(value: Double, from: String, to: String): Double {
        // Exchange rates relative to 1 USD (static demo values — not live rates).
        val toUsd = mapOf(
            "USD" to 1.0, "EUR" to 0.92, "GBP" to 0.79, "JPY" to 150.0, "AUD" to 1.52,
            "CAD" to 1.35, "CHF" to 0.88, "CNY" to 7.19, "INR" to 82.90, "BRL" to 4.97,
            "RUB" to 92.50, "KRW" to 1330.0, "SGD" to 1.34, "NZD" to 1.63, "MXN" to 17.10,
            "HKD" to 7.82, "IDR" to 15600.0, "TRY" to 31.00, "SAR" to 3.75, "AED" to 3.67
        )
        val valueInUsd = value / (toUsd[from] ?: 1.0)
        return valueInUsd * (toUsd[to] ?: 1.0)
    }
}

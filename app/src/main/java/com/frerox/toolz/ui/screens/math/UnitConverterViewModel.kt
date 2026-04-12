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

data class UnitConverterState(
    val type: ConversionType = ConversionType.LENGTH,
    val inputValue: String = "1",
    val outputValue: String = "",
    val fromUnit: String = "Meter",
    val toUnit: String = "Kilometer",
    val availableUnits: List<String> = emptyList()
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

    init {
        val initialType = ConversionType.LENGTH
        val units = unitsMap[initialType]!!
        _uiState.update { it.copy(availableUnits = units, fromUnit = units[2], toUnit = units[3]) }
        convert()
    }

    fun onTypeChange(type: ConversionType) {
        val units = unitsMap[type]!!
        _uiState.update { it.copy(type = type, availableUnits = units, fromUnit = units[0], toUnit = if (units.size > 1) units[1] else units[0]) }
        convert()
    }

    fun onInputValueChange(value: String) {
        _uiState.update { it.copy(inputValue = value.filter { c -> c.isDigit() || c == '.' }) }
        convert()
    }

    fun onFromUnitChange(unit: String) {
        _uiState.update { it.copy(fromUnit = unit) }
        convert()
    }

    fun onToUnitChange(unit: String) {
        _uiState.update { it.copy(toUnit = unit) }
        convert()
    }

    fun swapUnits() {
        _uiState.update { it.copy(fromUnit = it.toUnit, toUnit = it.fromUnit) }
        convert()
    }

    private fun convert() {
        val input = _uiState.value.inputValue.toDoubleOrNull() ?: 0.0
        val result = when (_uiState.value.type) {
            ConversionType.LENGTH -> convertLength(input, _uiState.value.fromUnit, _uiState.value.toUnit)
            ConversionType.WEIGHT -> convertWeight(input, _uiState.value.fromUnit, _uiState.value.toUnit)
            ConversionType.TEMPERATURE -> convertTemp(input, _uiState.value.fromUnit, _uiState.value.toUnit)
            ConversionType.AREA -> convertArea(input, _uiState.value.fromUnit, _uiState.value.toUnit)
            ConversionType.VOLUME -> convertVolume(input, _uiState.value.fromUnit, _uiState.value.toUnit)
            ConversionType.SPEED -> convertSpeed(input, _uiState.value.fromUnit, _uiState.value.toUnit)
            ConversionType.TIME -> convertTime(input, _uiState.value.fromUnit, _uiState.value.toUnit)
            ConversionType.DIGITAL_STORAGE -> convertDigital(input, _uiState.value.fromUnit, _uiState.value.toUnit)
            ConversionType.ENERGY -> convertEnergy(input, _uiState.value.fromUnit, _uiState.value.toUnit)
            ConversionType.FORCE -> convertForce(input, _uiState.value.fromUnit, _uiState.value.toUnit)
            ConversionType.PRESSURE -> convertPressure(input, _uiState.value.fromUnit, _uiState.value.toUnit)
            ConversionType.POWER -> convertPower(input, _uiState.value.fromUnit, _uiState.value.toUnit)
            ConversionType.CURRENCY -> convertCurrency(input, _uiState.value.fromUnit, _uiState.value.toUnit)
        }
        _uiState.update { 
            it.copy(outputValue = if (result % 1.0 == 0.0) result.toLong().toString() else String.format("%.6f", result).trimEnd('0').trimEnd('.'))
        }
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
            "Fahrenheit" -> (value - 32) * 5/9
            "Kelvin" -> value - 273.15
            else -> value
        }
        return when (to) {
            "Celsius" -> inCelsius
            "Fahrenheit" -> (inCelsius * 9/5) + 32
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
        // Exchange rates relative to 1 USD (Approximate values for demonstration)
        val toUsd = mapOf(
            "USD" to 1.0,
            "EUR" to 0.92,
            "GBP" to 0.79,
            "JPY" to 150.0,
            "AUD" to 1.52,
            "CAD" to 1.35,
            "CHF" to 0.88,
            "CNY" to 7.19,
            "INR" to 82.90,
            "BRL" to 4.97,
            "RUB" to 92.50,
            "KRW" to 1330.0,
            "SGD" to 1.34,
            "NZD" to 1.63,
            "MXN" to 17.10,
            "HKD" to 7.82,
            "IDR" to 15600.0,
            "TRY" to 31.00,
            "SAR" to 3.75,
            "AED" to 3.67
        )
        
        // Value in USD = input / rate_of_from_currency_to_usd (Wait, toUsd means 1 USD = X EUR, so value * (1/X) converts EUR to USD)
        // If 1 USD = 0.92 EUR, then 1 EUR = 1/0.92 USD.
        // value_in_usd = value / (toUsd[from] ?: 1.0)
        // result = value_in_usd * (toUsd[to] ?: 1.0)
        
        val valueInUsd = value / (toUsd[from] ?: 1.0)
        return valueInUsd * (toUsd[to] ?: 1.0)
    }
}

package com.frerox.toolz.ui.screens.math

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class ConversionType {
    LENGTH, WEIGHT, TEMPERATURE
}

data class UnitConverterState(
    val type: ConversionType = ConversionType.LENGTH,
    val inputValue: String = "0",
    val outputValue: String = "0",
    val fromUnit: String = "Meter",
    val toUnit: String = "Foot"
)

@HiltViewModel
class UnitConverterViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(UnitConverterState())
    val uiState: StateFlow<UnitConverterState> = _uiState.asStateFlow()

    private val lengthUnits = listOf("Meter", "Foot", "Inch", "Kilometer", "Mile")
    private val weightUnits = listOf("Kilogram", "Pound", "Gram", "Ounce")
    private val tempUnits = listOf("Celsius", "Fahrenheit", "Kelvin")

    fun getUnits(): List<String> = when (_uiState.value.type) {
        ConversionType.LENGTH -> lengthUnits
        ConversionType.WEIGHT -> weightUnits
        ConversionType.TEMPERATURE -> tempUnits
    }

    fun onTypeChange(type: ConversionType) {
        val units = when (type) {
            ConversionType.LENGTH -> lengthUnits
            ConversionType.WEIGHT -> weightUnits
            ConversionType.TEMPERATURE -> tempUnits
        }
        _uiState.update { it.copy(type = type, fromUnit = units[0], toUnit = units[1], inputValue = "0", outputValue = "0") }
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

    private fun convert() {
        val input = _uiState.value.inputValue.toDoubleOrNull() ?: 0.0
        val result = when (_uiState.value.type) {
            ConversionType.LENGTH -> convertLength(input, _uiState.value.fromUnit, _uiState.value.toUnit)
            ConversionType.WEIGHT -> convertWeight(input, _uiState.value.fromUnit, _uiState.value.toUnit)
            ConversionType.TEMPERATURE -> convertTemp(input, _uiState.value.fromUnit, _uiState.value.toUnit)
        }
        _uiState.update { it.copy(outputValue = String.format("%.4f", result)) }
    }

    private fun convertLength(value: Double, from: String, to: String): Double {
        val inMeters = when (from) {
            "Meter" -> value
            "Foot" -> value * 0.3048
            "Inch" -> value * 0.0254
            "Kilometer" -> value * 1000.0
            "Mile" -> value * 1609.34
            else -> value
        }
        return when (to) {
            "Meter" -> inMeters
            "Foot" -> inMeters / 0.3048
            "Inch" -> inMeters / 0.0254
            "Kilometer" -> inMeters / 1000.0
            "Mile" -> inMeters / 1609.34
            else -> inMeters
        }
    }

    private fun convertWeight(value: Double, from: String, to: String): Double {
        val inKg = when (from) {
            "Kilogram" -> value
            "Pound" -> value * 0.453592
            "Gram" -> value / 1000.0
            "Ounce" -> value * 0.0283495
            else -> value
        }
        return when (to) {
            "Kilogram" -> inKg
            "Pound" -> inKg / 0.453592
            "Gram" -> inKg * 1000.0
            "Ounce" -> inKg / 0.0283495
            else -> inKg
        }
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
}

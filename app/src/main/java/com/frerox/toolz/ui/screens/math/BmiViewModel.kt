package com.frerox.toolz.ui.screens.math

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.pow

enum class Gender { MALE, FEMALE }

data class BmiState(
    val weight: String = "",
    val height: String = "",
    val age: String = "",
    val gender: Gender = Gender.MALE,
    val bmi: Float? = null,
    val category: String = "",
    val isCm: Boolean = true,
    val isKg: Boolean = true
)

@HiltViewModel
class BmiViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(BmiState())
    val uiState: StateFlow<BmiState> = _uiState.asStateFlow()

    fun onWeightChange(weight: String) {
        _uiState.update { it.copy(weight = weight.filter { c -> c.isDigit() || c == '.' }) }
        calculateBmi()
    }

    fun onHeightChange(height: String) {
        _uiState.update { it.copy(height = height.filter { c -> c.isDigit() || c == '.' || c == '\'' || c == '\"' || c == ' ' }) }
        calculateBmi()
    }

    fun onAgeChange(age: String) {
        _uiState.update { it.copy(age = age.filter { it.isDigit() }) }
        calculateBmi()
    }

    fun onGenderChange(gender: Gender) {
        _uiState.update { it.copy(gender = gender) }
        calculateBmi()
    }

    fun toggleUnit(isHeight: Boolean) {
        _uiState.update { 
            if (isHeight) it.copy(isCm = !it.isCm) else it.copy(isKg = !it.isKg)
        }
        calculateBmi()
    }

    private fun calculateBmi() {
        val state = _uiState.value
        val weightInKg = if (state.isKg) {
            state.weight.toFloatOrNull() ?: 0f
        } else {
            (state.weight.toFloatOrNull() ?: 0f) * 0.453592f
        }

        val heightInMeters = if (state.isCm) {
            (state.height.toFloatOrNull() ?: 0f) / 100f
        } else {
            parseImperialHeight(state.height) * 0.0254f
        }
        
        if (weightInKg > 0 && heightInMeters > 0) {
            val bmiValue = weightInKg / heightInMeters.pow(2)
            val category = getBmiCategory(bmiValue, state.age.toIntOrNull() ?: 20)
            _uiState.update { it.copy(bmi = bmiValue, category = category) }
        } else {
            _uiState.update { it.copy(bmi = null, category = "") }
        }
    }

    private fun getBmiCategory(bmi: Float, age: Int): String {
        return when {
            bmi < 18.5 -> "Underweight"
            bmi < 25 -> "Normal Weight"
            bmi < 30 -> "Overweight"
            bmi < 35 -> "Obese Class I"
            bmi < 40 -> "Obese Class II"
            else -> "Obese Class III"
        }
    }

    private fun parseImperialHeight(height: String): Float {
        return try {
            if (height.contains("'")) {
                val parts = height.split("'")
                val feet = parts[0].trim().toFloatOrNull() ?: 0f
                val inches = if (parts.size > 1) {
                    parts[1].replace("\"", "").trim().toFloatOrNull() ?: 0f
                } else 0f
                feet * 12 + inches
            } else {
                height.toFloatOrNull() ?: 0f
            }
        } catch (e: Exception) {
            0f
        }
    }
}

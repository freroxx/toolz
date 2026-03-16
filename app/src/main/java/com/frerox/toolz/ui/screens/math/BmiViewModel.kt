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
    val bmr: Float? = null,
    val tdee: Float? = null,
    val ibw: Float? = null,
    val bfp: Float? = null, // Body Fat Percentage
    val whr: Float? = null, // Waist-to-Height Ratio (placeholder if we add waist)
    val isCm: Boolean = true,
    val isKg: Boolean = true,
    val healthyRange: Pair<Float, Float> = 18.5f to 24.9f
)

@HiltViewModel
class BmiViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(BmiState())
    val uiState: StateFlow<BmiState> = _uiState.asStateFlow()

    fun onWeightChange(weight: String) {
        _uiState.update { it.copy(weight = weight.filter { c -> c.isDigit() || c == '.' }) }
        calculateMetrics()
    }

    fun onHeightChange(height: String) {
        _uiState.update { it.copy(height = height.filter { c -> c.isDigit() || c == '.' || c == '\'' || c == '\"' || c == ' ' }) }
        calculateMetrics()
    }

    fun onAgeChange(age: String) {
        _uiState.update { it.copy(age = age.filter { it.isDigit() }) }
        calculateMetrics()
    }

    fun onGenderChange(gender: Gender) {
        _uiState.update { it.copy(gender = gender) }
        calculateMetrics()
    }

    fun toggleUnit(isHeight: Boolean) {
        _uiState.update { 
            if (isHeight) it.copy(isCm = !it.isCm) else it.copy(isKg = !it.isKg)
        }
        calculateMetrics()
    }

    private fun calculateMetrics() {
        val state = _uiState.value
        val weightVal = state.weight.toFloatOrNull() ?: 0f
        val ageVal = state.age.toIntOrNull() ?: 0

        val weightInKg = if (state.isKg) weightVal else weightVal * 0.453592f
        val heightInCm = if (state.isCm) {
            state.height.toFloatOrNull() ?: 0f
        } else {
            parseImperialHeight(state.height) * 2.54f
        }
        val heightInMeters = heightInCm / 100f
        
        if (weightInKg > 0 && heightInMeters > 0) {
            // 1. BMI Calculation
            val bmiValue = weightInKg / heightInMeters.pow(2)
            
            // 2. Categories
            val (category, range) = getBmiCategoryAndRange(bmiValue, ageVal)
            
            // 3. BMR (Mifflin-St Jeor)
            val bmrValue = if (state.gender == Gender.MALE) {
                (10f * weightInKg) + (6.25f * heightInCm) - (5f * ageVal.toFloat()) + 5f
            } else {
                (10f * weightInKg) + (6.25f * heightInCm) - (5f * ageVal.toFloat()) - 161f
            }

            // 4. TDEE (Sedentary)
            val tdeeValue = bmrValue * 1.2f

            // 5. Ideal Body Weight (Devine)
            val heightInInches = heightInCm / 2.54f
            val inchesOver5Feet = (heightInInches - 60).coerceAtLeast(0f)
            val ibwValue = if (state.gender == Gender.MALE) {
                50f + (2.3f * inchesOver5Feet)
            } else {
                45.5f + (2.3f * inchesOver5Feet)
            }

            // 6. Body Fat Percentage (Deurenberg Formula)
            // Adult BFP = (1.20 × BMI) + (0.23 × Age) − (10.8 × sex) − 5.4
            // where sex is 1 for male, 0 for female
            val sexModifier = if (state.gender == Gender.MALE) 1 else 0
            val bfpValue = if (ageVal >= 18) {
                (1.20f * bmiValue) + (0.23f * ageVal.toFloat()) - (10.8f * sexModifier) - 5.4f
            } else {
                // Child/Teen formula
                (1.51f * bmiValue) - (0.70f * ageVal.toFloat()) - (3.6f * sexModifier) + 1.4f
            }

            _uiState.update { 
                it.copy(
                    bmi = bmiValue, 
                    category = category,
                    bmr = bmrValue,
                    tdee = tdeeValue,
                    ibw = ibwValue,
                    bfp = bfpValue,
                    healthyRange = range
                ) 
            }
        } else {
            _uiState.update { it.copy(bmi = null, category = "", bmr = null, tdee = null, ibw = null, bfp = null) }
        }
    }

    private fun getBmiCategoryAndRange(bmi: Float, age: Int): Pair<String, Pair<Float, Float>> {
        val range = if (age >= 65) {
            22.0f to 27.0f
        } else {
            18.5f to 24.9f
        }

        val category = when {
            bmi < range.first -> "Underweight"
            bmi <= range.second -> "Healthy"
            bmi < 30 -> "Overweight"
            bmi < 35 -> "Obese (Class I)"
            bmi < 40 -> "Obese (Class II)"
            else -> "Obese (Class III)"
        }
        
        return category to range
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

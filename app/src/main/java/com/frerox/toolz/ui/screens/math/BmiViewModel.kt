package com.frerox.toolz.ui.screens.math

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.pow

data class BmiState(
    val weight: String = "",
    val height: String = "",
    val bmi: Float? = null,
    val category: String = ""
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
        _uiState.update { it.copy(height = height.filter { c -> c.isDigit() || c == '.' }) }
        calculateBmi()
    }

    private fun calculateBmi() {
        val w = _uiState.value.weight.toFloatOrNull() ?: 0f
        val h = _uiState.value.height.toFloatOrNull() ?: 0f
        
        if (w > 0 && h > 0) {
            val hInMeters = h / 100f
            val bmiValue = w / hInMeters.pow(2)
            val category = when {
                bmiValue < 18.5 -> "Underweight"
                bmiValue < 25 -> "Normal"
                bmiValue < 30 -> "Overweight"
                else -> "Obese"
            }
            _uiState.update { it.copy(bmi = bmiValue, category = category) }
        } else {
            _uiState.update { it.copy(bmi = null, category = "") }
        }
    }
}

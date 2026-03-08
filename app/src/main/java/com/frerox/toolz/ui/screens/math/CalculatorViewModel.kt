package com.frerox.toolz.ui.screens.math

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.objecthunter.exp4j.ExpressionBuilder
import javax.inject.Inject

data class CalculatorState(
    val display: String = "0",
    val formula: String = "",
    val isScientific: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CalculatorViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorState())
    val uiState: StateFlow<CalculatorState> = _uiState.asStateFlow()

    private var isNewExpression = true

    fun onDigit(digit: String) {
        _uiState.update {
            val currentDisplay = if (isNewExpression) "" else it.display
            isNewExpression = false
            it.copy(display = if (currentDisplay == "0") digit else currentDisplay + digit, error = null)
        }
    }

    fun onOperator(op: String) {
        _uiState.update {
            isNewExpression = false
            it.copy(display = it.display + op, error = null)
        }
    }

    fun onClear() {
        _uiState.update { CalculatorState(isScientific = it.isScientific) }
        isNewExpression = true
    }

    fun onBackspace() {
        _uiState.update {
            if (it.display.length <= 1) {
                isNewExpression = true
                it.copy(display = "0")
            } else {
                it.copy(display = it.display.dropLast(1))
            }
        }
    }

    fun onToggleMode() {
        _uiState.update { it.copy(isScientific = !it.isScientific) }
    }

    fun onEquals() {
        _uiState.update { state ->
            try {
                val expressionStr = state.display
                    .replace("×", "*")
                    .replace("÷", "/")
                    .replace("π", "pi")
                    .replace("e", "e")
                
                val expression = ExpressionBuilder(expressionStr).build()
                val result = expression.evaluate()
                
                val formattedResult = if (result % 1.0 == 0.0) {
                    result.toLong().toString()
                } else {
                    String.format("%.8f", result).trimEnd('0').trimEnd('.')
                }
                
                isNewExpression = true
                state.copy(display = formattedResult, formula = expressionStr + " =", error = null)
            } catch (e: Exception) {
                state.copy(error = "Invalid Expression")
            }
        }
    }

    fun onFunction(func: String) {
        _uiState.update {
            val currentDisplay = if (isNewExpression) "" else it.display
            isNewExpression = false
            it.copy(display = currentDisplay + "$func(", error = null)
        }
    }
}

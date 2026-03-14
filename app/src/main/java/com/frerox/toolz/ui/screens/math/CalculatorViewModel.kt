package com.frerox.toolz.ui.screens.math

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.objecthunter.exp4j.ExpressionBuilder
import java.util.Locale
import javax.inject.Inject

data class CalculatorState(
    val display: String = "0",
    val formula: String = "",
    val isScientific: Boolean = false,
    val error: String? = null,
    val isDegreeMode: Boolean = true
)

@HiltViewModel
class CalculatorViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorState())
    val uiState: StateFlow<CalculatorState> = _uiState.asStateFlow()

    private var isNewExpression = true

    fun onDigit(digit: String) {
        _uiState.update {
            if (it.error != null) {
                isNewExpression = false
                return@update it.copy(display = digit, error = null)
            }
            val currentDisplay = if (isNewExpression) "" else it.display
            isNewExpression = false
            
            val newDisplay = when {
                currentDisplay == "0" && digit != "." -> digit
                currentDisplay == "" && digit == "." -> "0."
                digit == "." && currentDisplay.contains(".") -> {
                    // Check if the last number already has a decimal
                    val lastNumber = currentDisplay.split(Regex("[+×÷\\-^()]")).last()
                    if (lastNumber.contains(".")) currentDisplay else currentDisplay + digit
                }
                else -> currentDisplay + digit
            }
            it.copy(display = newDisplay)
        }
    }

    fun onOperator(op: String) {
        _uiState.update {
            if (it.error != null) return@update it
            isNewExpression = false
            val currentDisplay = it.display
            
            // Allow negative sign at start or after operator
            if (op == "-" && (currentDisplay == "0" || currentDisplay.isEmpty() || currentDisplay.last() in "+×÷(^")) {
                 return@update it.copy(display = if (currentDisplay == "0") "-" else currentDisplay + "-")
            }

            if (currentDisplay.isEmpty() || currentDisplay == "0") return@update it
            
            val lastChar = currentDisplay.last()
            val operators = listOf('+', '-', '×', '÷', '^')
            
            val newDisplay = if (lastChar in operators) {
                currentDisplay.dropLast(1) + op
            } else {
                currentDisplay + op
            }
            it.copy(display = newDisplay, error = null)
        }
    }

    fun onClear() {
        _uiState.update { CalculatorState(isScientific = it.isScientific, isDegreeMode = it.isDegreeMode) }
        isNewExpression = true
    }

    fun onBackspace() {
        _uiState.update {
            if (it.error != null) return@update it.copy(error = null)
            if (it.display.length <= 1 || (it.display.length == 2 && it.display.startsWith("-"))) {
                isNewExpression = true
                it.copy(display = "0")
            } else {
                // Check if we are deleting a function
                val functions = listOf("sin(", "cos(", "tan(", "log(", "ln(", "sqrt(", "abs(", "log10(", "exp(", "inv(", "acos(", "asin(", "atan(")
                var newDisplay = it.display
                for (func in functions) {
                    if (it.display.endsWith(func)) {
                        newDisplay = it.display.dropLast(func.length)
                        break
                    }
                }
                if (newDisplay == it.display) {
                    newDisplay = it.display.dropLast(1)
                }
                it.copy(display = if (newDisplay.isEmpty()) "0" else newDisplay)
            }
        }
    }

    fun onToggleMode() {
        _uiState.update { it.copy(isScientific = !it.isScientific) }
    }

    fun onToggleAngleMode() {
        _uiState.update { it.copy(isDegreeMode = !it.isDegreeMode) }
    }

    fun onEquals() {
        _uiState.update { state ->
            if (state.display == "0" && state.formula.isEmpty()) return@update state
            try {
                var expressionStr = state.display
                    .replace("×", "*")
                    .replace("÷", "/")
                    .replace("π", "pi")
                    .replace("ln(", "log(")
                    .replace("inv(", "1/(")
                
                if (state.isDegreeMode) {
                    expressionStr = transformTrig(expressionStr)
                }
                
                val expression = ExpressionBuilder(expressionStr).build()
                val result = expression.evaluate()
                
                val formattedResult = formatResult(result)
                
                isNewExpression = true
                state.copy(display = formattedResult, formula = state.display + " =", error = null)
            } catch (e: Exception) {
                state.copy(error = "Invalid Expression")
            }
        }
    }

    fun onFunction(func: String) {
        _uiState.update {
            val currentDisplay = if (isNewExpression || it.display == "0") "" else it.display
            isNewExpression = false
            it.copy(display = currentDisplay + "$func(", error = null)
        }
    }

    private fun formatResult(value: Double): String {
        return if (value.isInfinite()) "Infinity"
        else if (value.isNaN()) "NaN"
        else if (value % 1.0 == 0.0) value.toLong().toString()
        else {
            // Use scientific notation for very small or very large numbers
            if (Math.abs(value) < 1E-10 || Math.abs(value) > 1E10) {
                String.format(Locale.US, "%.6e", value)
            } else {
                String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
            }
        }
    }

    private fun transformTrig(expr: String): String {
        var res = expr
        val funcs = listOf("sin", "cos", "tan", "asin", "acos", "atan")
        funcs.forEach { f ->
            val pattern = Regex("$f\\(([^)]+)\\)")
            res = res.replace(pattern) { matchResult ->
                val inner = matchResult.groupValues[1]
                if (f.startsWith("a")) { // Inverse trig: result is in radians, convert to degrees
                    "($f($inner)*180/pi)"
                } else { // Normal trig: input is in degrees, convert to radians
                    "$f(($inner)*pi/180)"
                }
            }
        }
        return res
    }
}

package com.frerox.toolz.ui.screens.math

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val liveResult: String? = null,
    val isScientific: Boolean = false,
    val error: String? = null,
    val isDegreeMode: Boolean = true,
    val history: List<Pair<String, String>> = emptyList()
)

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

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
                    val lastNumber = currentDisplay.split(Regex("[+×÷\\-^()]")).last()
                    if (lastNumber.contains(".")) currentDisplay else currentDisplay + digit
                }
                else -> currentDisplay + digit
            }
            val nextState = it.copy(display = newDisplay)
            nextState.copy(liveResult = calculateLive(nextState))
        }
    }

    fun onOperator(op: String) {
        _uiState.update {
            if (it.error != null) return@update it
            isNewExpression = false
            val currentDisplay = it.display
            
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
            val nextState = it.copy(display = newDisplay, error = null)
            nextState.copy(liveResult = calculateLive(nextState))
        }
    }

    fun onClear() {
        _uiState.update { it.copy(display = "0", formula = "", liveResult = null, error = null) }
        isNewExpression = true
    }

    fun onBackspace() {
        _uiState.update {
            if (it.error != null) return@update it.copy(error = null)
            if (it.display.length <= 1 || (it.display.length == 2 && it.display.startsWith("-"))) {
                isNewExpression = true
                it.copy(display = "0")
            } else {
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
                val nextState = it.copy(display = if (newDisplay.isEmpty()) "0" else newDisplay)
                nextState.copy(liveResult = calculateLive(nextState))
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
                // Auto-close parentheses
                val openParentheses = state.display.count { it == '(' }
                val closeParentheses = state.display.count { it == ')' }
                val balancedDisplay = state.display + ")".repeat((openParentheses - closeParentheses).coerceAtLeast(0))

                var expressionStr = balancedDisplay
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
                
                val newHistory = (listOf(state.display to formattedResult) + state.history).take(20)
                
                isNewExpression = true
                state.copy(
                    display = formattedResult, 
                    formula = balancedDisplay + " =", 
                    liveResult = null,
                    error = null,
                    history = newHistory
                )
            } catch (e: Exception) {
                state.copy(error = "Invalid Expression")
            }
        }
    }

    fun onFunction(func: String) {
        _uiState.update {
            val currentDisplay = if (isNewExpression || it.display == "0") "" else it.display
            isNewExpression = false
            val functionCall = when(func) {
                "√" -> "sqrt("
                "log" -> "log10("
                else -> "$func("
            }
            val nextState = it.copy(display = currentDisplay + functionCall, error = null)
            nextState.copy(liveResult = calculateLive(nextState))
        }
    }

    fun onCopyResult() {
        val textToCopy = _uiState.value.display
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Calculator Result", textToCopy)
        cm.setPrimaryClip(clip)
    }

    fun clearHistory() {
        _uiState.update { it.copy(history = emptyList()) }
    }

    private fun formatResult(value: Double): String {
        return if (value.isInfinite()) "Infinity"
        else if (value.isNaN()) "NaN"
        else if (value % 1.0 == 0.0) value.toLong().toString()
        else {
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
                if (f.startsWith("a")) {
                    "($f($inner)*180/pi)"
                } else {
                    "$f(($inner)*pi/180)"
                }
            }
        }
        return res
    }

    private fun calculateLive(state: CalculatorState): String? {
        if (state.display == "0" || state.display.isEmpty()) return null
        
        // Don't calculate if ends with operator
        if (state.display.last() in "+×÷-^(") return null
        
        return try {
            val openParentheses = state.display.count { it == '(' }
            val closeParentheses = state.display.count { it == ')' }
            val balancedDisplay = state.display + ")".repeat((openParentheses - closeParentheses).coerceAtLeast(0))

            var expressionStr = balancedDisplay
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
            
            if (result.isNaN() || result.isInfinite()) null 
            else formatResult(result)
        } catch (e: Exception) {
            null
        }
    }
}

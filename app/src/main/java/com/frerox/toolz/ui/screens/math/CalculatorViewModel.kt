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

package com.frerox.toolz.ui.screens.math

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.math.MathHistory
import com.frerox.toolz.data.math.MathHistoryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.objecthunter.exp4j.ExpressionBuilder
import java.math.BigDecimal
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
    @ApplicationContext private val context: Context,
    private val mathHistoryDao: MathHistoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorState())
    val uiState: StateFlow<CalculatorState> = _uiState.asStateFlow()

    private var isNewExpression = true

    init {
        viewModelScope.launch {
            mathHistoryDao.getAllHistory().collect { historyList ->
                _uiState.update { state ->
                    state.copy(history = historyList.map { it.expression to it.result })
                }
            }
        }
    }

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
                val prepared = prepareExpression(state.display, state.isDegreeMode)
                val expression = ExpressionBuilder(prepared).build()
                val result = expression.evaluate()
                
                val formattedResult = formatResult(result)
                val sourceExpr = state.display
                
                viewModelScope.launch {
                    mathHistoryDao.insert(MathHistory(expression = sourceExpr, result = formattedResult))
                }
                
                val openCount = state.display.count { it == '(' }
                val closeCount = state.display.count { it == ')' }
                val balancedFormula = state.display + ")".repeat((openCount - closeCount).coerceAtLeast(0)) + " ="

                isNewExpression = true
                state.copy(
                    display = formattedResult, 
                    formula = balancedFormula, 
                    liveResult = null,
                    error = null,
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
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Calculator Result", textToCopy)
        cm?.setPrimaryClip(clip)
    }

    fun clearHistory() {
        viewModelScope.launch {
            mathHistoryDao.clearAll()
        }
    }

    private fun formatResult(value: Double): String {
        return when {
            value.isInfinite() -> "Infinity"
            value.isNaN() -> "NaN"
            else -> {
                val absVal = Math.abs(value)
                if (absVal == 0.0) {
                    "0"
                } else if (absVal >= 1e16 || (absVal < 1e-9 && absVal > 0.0)) {
                    // Scientific notation for very large or very small numbers
                    val formatted = String.format(Locale.US, "%.10e", value)
                    val parts = formatted.split("e", "E")
                    if (parts.size == 2) {
                        val mantissa = parts[0].trimEnd('0').trimEnd('.')
                        val exp = parts[1].replace("+", "").toIntOrNull() ?: parts[1]
                        "${mantissa}E$exp"
                    } else {
                        formatted
                    }
                } else {
                    // Clean decimal representation eliminating IEEE-754 precision artifacts (e.g. 0.1 + 0.2 = 0.30000000000000004)
                    val formatted = String.format(Locale.US, "%.12g", value).trim()
                    try {
                        val bd = BigDecimal(formatted)
                        bd.stripTrailingZeros().toPlainString()
                    } catch (_: Exception) {
                        if (value % 1.0 == 0.0 && absVal < Long.MAX_VALUE.toDouble()) {
                            value.toLong().toString()
                        } else {
                            String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
                        }
                    }
                }
            }
        }
    }

    private fun prepareExpression(rawDisplay: String, isDegreeMode: Boolean): String {
        // Auto-close open parentheses
        val openCount = rawDisplay.count { it == '(' }
        val closeCount = rawDisplay.count { it == ')' }
        val balanced = rawDisplay + ")".repeat((openCount - closeCount).coerceAtLeast(0))

        var s = balanced
            .replace("×", "*")
            .replace("÷", "/")
            .replace("π", "pi")
            .replace("ln(", "log(")
            .replace("inv(", "1/(")

        // Insert implicit multiplication:
        // 1) digit or constant or ')' followed by '('
        s = s.replace(Regex("(\\d|pi|e|\\))\\s*\\("), "$1*(")
        // 2) ')' followed by digit or constant or function
        s = s.replace(Regex("\\)\\s*(\\d|pi|e)"), ")*$1")
        s = s.replace(Regex("\\)\\s*([a-zA-Z]+)\\("), ")*$1(")
        // 3) digit followed by pi or e
        s = s.replace(Regex("(\\d)\\s*(pi|e)"), "$1*$2")
        // 4) digit followed by function (e.g. 5sqrt, 2sin)
        s = s.replace(Regex("(\\d)\\s*([a-zA-Z]+)\\("), "$1*$2(")
        // 5) percentage: e.g. 50% -> (50*0.01)
        s = s.replace(Regex("(\\d+(?:\\.\\d+)?)\\s*%"), "($1*0.01)")

        if (isDegreeMode) {
            s = transformTrig(s)
        }

        return s
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
            val prepared = prepareExpression(state.display, state.isDegreeMode)
            val expression = ExpressionBuilder(prepared).build()
            val result = expression.evaluate()
            
            if (result.isNaN() || result.isInfinite()) null 
            else formatResult(result)
        } catch (e: Exception) {
            null
        }
    }
}

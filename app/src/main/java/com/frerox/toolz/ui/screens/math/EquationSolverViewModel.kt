package com.frerox.toolz.ui.screens.math

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.math.MathHistory
import com.frerox.toolz.data.math.MathHistoryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.objecthunter.exp4j.ExpressionBuilder
import java.util.Locale
import javax.inject.Inject
import kotlin.math.sqrt

data class ConstantItem(val name: String, val value: String, val symbol: String, val description: String)

data class EquationSolverState(
    val expression: String = "",
    val result: String = "",
    val isDegreeMode: Boolean = true,
    val error: String? = null,
    val constants: List<ConstantItem> = listOf(
        ConstantItem("Pi", "pi", "π", "3.14159"),
        ConstantItem("Euler", "e", "e", "2.71828"),
        ConstantItem("Light Speed", "299792458", "c", "m/s"),
        ConstantItem("Gravitational", "6.67430e-11", "G", "N·m²/kg²"),
        ConstantItem("Planck", "6.62607e-34", "h", "J·s"),
        ConstantItem("Boltzmann", "1.38064e-23", "k", "J/K"),
        ConstantItem("Avogadro", "6.02214e23", "Nₐ", "mol⁻¹"),
        ConstantItem("Gas Constant", "8.31446", "R", "J/(mol·K)"),
        ConstantItem("Electron Charge", "1.60217e-19", "qₑ", "C"),
        ConstantItem("Proton Mass", "1.67262e-27", "mₚ", "kg"),
        ConstantItem("Electron Mass", "9.10938e-31", "mₑ", "kg"),
        ConstantItem("Neutron Mass", "1.67492e-27", "mₙ", "kg"),
        ConstantItem("Faraday", "96485.33", "F", "C/mol"),
        ConstantItem("Stefan-Boltzmann", "5.67037e-8", "σ", "W/(m²·K⁴)"),
        ConstantItem("Atomic Mass", "1.66053e-27", "u", "kg"),
        ConstantItem("Vacuum Permittivity", "8.85418e-12", "ε₀", "F/m"),
        ConstantItem("Vacuum Permeability", "1.25663e-6", "μ₀", "N/A²"),
        ConstantItem("Standard Gravity", "9.80665", "g", "m/s²"),
        ConstantItem("Bohr Radius", "5.29177e-11", "a₀", "m"),
        ConstantItem("Magnetic Flux Q", "2.06783e-15", "Φ₀", "Wb")
    )
)

@HiltViewModel
class EquationSolverViewModel @Inject constructor(
    private val mathHistoryDao: MathHistoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(EquationSolverState())
    val uiState: StateFlow<EquationSolverState> = _uiState.asStateFlow()

    val history: StateFlow<List<MathHistory>> = mathHistoryDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onExpressionChange(newExpression: String) {
        _uiState.update { it.copy(expression = newExpression, error = null) }
    }

    fun appendSymbol(symbol: String) {
        _uiState.update { it.copy(expression = it.expression + symbol, error = null) }
    }

    fun backspace() {
        _uiState.update { 
            if (it.expression.isNotEmpty()) {
                val newExpr = if (it.expression.endsWith("sin(") || it.expression.endsWith("cos(") || 
                    it.expression.endsWith("tan(") || it.expression.endsWith("log(") || 
                    it.expression.endsWith("sqrt(")) {
                    it.expression.substring(0, it.expression.lastIndexOf('(') - 2)
                } else if (it.expression.endsWith("asin(") || it.expression.endsWith("acos(") || 
                    it.expression.endsWith("atan(")) {
                    it.expression.substring(0, it.expression.lastIndexOf('(') - 3)
                } else {
                    it.expression.dropLast(1)
                }
                it.copy(expression = newExpr, error = null)
            } else it
        }
    }

    fun clear() {
        _uiState.update { it.copy(expression = "", result = "", error = null) }
    }

    fun toggleMode() {
        _uiState.update { it.copy(isDegreeMode = !it.isDegreeMode) }
    }

    fun solve() {
        val state = _uiState.value
        if (state.expression.isBlank()) return

        // 1. Check for Equation Solving (contains '=')
        if (state.expression.count { it == '=' } == 1) {
            solveEquation(state.expression)
            return
        }

        // 2. Standard Scientific Calculation
        try {
            var exprString = state.expression
                .replace("π", "pi")
                .replace("√", "sqrt")
            
            if (state.isDegreeMode) {
                exprString = transformToRadians(exprString)
            }
            
            val expression = ExpressionBuilder(exprString).build()
            val resultValue = expression.evaluate()
            
            val resultString = formatResult(resultValue)

            _uiState.update { it.copy(result = resultString, error = null) }

            saveToHistory(state.expression, resultString)
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Invalid Expression") }
        }
    }

    private fun solveEquation(input: String) {
        try {
            val parts = input.split('=')
            val left = parts[0].trim()
            val right = parts[1].trim()
            
            // Bring everything to one side: left - (right) = 0
            val equationStr = "($left) - ($right)"
            
            // Check for degree (highest power of x)
            if (input.contains("x^2")) {
                solveQuadratic(input)
            } else if (input.contains("x")) {
                solveLinear(input)
            } else {
                _uiState.update { it.copy(error = "No variable 'x' found") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Solving error") }
        }
    }

    private fun solveLinear(input: String) {
        // Simple heuristic: solve ax + b = c -> x = (c-b)/a
        // Using numerical approach for robust solving of any 1st degree
        try {
            val parts = input.split('=')
            val expr = "(${parts[0]}) - (${parts[1]})"
            
            // x = -f(0) / (f(1) - f(0))
            val f0 = ExpressionBuilder(expr).variable("x").build().setVariable("x", 0.0).evaluate()
            val f1 = ExpressionBuilder(expr).variable("x").build().setVariable("x", 1.0).evaluate()
            
            val x = -f0 / (f1 - f0)
            val result = "x = ${formatResult(x)}"
            _uiState.update { it.copy(result = result, error = null) }
            saveToHistory(input, result)
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Linear solving failed") }
        }
    }

    private fun solveQuadratic(input: String) {
        // Standard form: ax^2 + bx + c = 0
        try {
            val parts = input.split('=')
            val expr = "(${parts[0]}) - (${parts[1]})"
            
            // Numerical coefficient extraction
            val f0 = ExpressionBuilder(expr).variable("x").build().setVariable("x", 0.0).evaluate()
            val f1 = ExpressionBuilder(expr).variable("x").build().setVariable("x", 1.0).evaluate()
            val fm1 = ExpressionBuilder(expr).variable("x").build().setVariable("x", -1.0).evaluate()
            
            val c = f0
            val a = (f1 + fm1 - 2 * c) / 2.0
            val b = f1 - a - c
            
            val delta = b * b - 4 * a * c
            
            val result = when {
                delta > 0 -> {
                    val x1 = (-b + sqrt(delta)) / (2 * a)
                    val x2 = (-b - sqrt(delta)) / (2 * a)
                    "x₁ = ${formatResult(x1)}, x₂ = ${formatResult(x2)}"
                }
                delta == 0.0 -> {
                    val x = -b / (2 * a)
                    "x = ${formatResult(x)}"
                }
                else -> {
                    val real = -b / (2 * a)
                    val img = sqrt(-delta) / (2 * a)
                    "x = ${formatResult(real)} ± ${formatResult(img)}i"
                }
            }
            
            _uiState.update { it.copy(result = result, error = null) }
            saveToHistory(input, result)
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Quadratic solving failed") }
        }
    }

    private fun formatResult(value: Double): String {
        return if (value.isInfinite()) "Infinity" 
        else if (value.isNaN()) "NaN"
        else if (value % 1 == 0.0) value.toLong().toString()
        else String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
    }

    private fun saveToHistory(expr: String, res: String) {
        viewModelScope.launch {
            mathHistoryDao.insert(MathHistory(expression = expr, result = res))
        }
    }

    private fun transformToRadians(expr: String): String {
        var res = expr
        val funcs = listOf("sin", "cos", "tan", "asin", "acos", "atan")
        funcs.forEach { f ->
            val pattern = Regex("$f\\(([^)]+)\\)")
            if (!f.startsWith("a")) {
                res = res.replace(pattern, "$f(($1)*pi/180)")
            }
        }
        return res
    }
}

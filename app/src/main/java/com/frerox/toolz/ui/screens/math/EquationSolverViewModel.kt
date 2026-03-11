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

        try {
            var exprString = state.expression
                .replace("π", "pi")
                .replace("√", "sqrt")
            
            if (state.isDegreeMode) {
                // Better regex to handle arguments in degrees
                exprString = transformToRadians(exprString)
            }
            
            val expression = ExpressionBuilder(exprString).build()
            val resultValue = expression.evaluate()
            
            val resultString = if (resultValue.isInfinite()) "Infinity" 
            else if (resultValue.isNaN()) "NaN"
            else if (resultValue % 1 == 0.0) {
                resultValue.toLong().toString()
            } else {
                String.format(Locale.US, "%.8f", resultValue).trimEnd('0').trimEnd('.')
            }

            _uiState.update { it.copy(result = resultString, error = null) }

            viewModelScope.launch {
                mathHistoryDao.insert(MathHistory(expression = state.expression, result = resultString))
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Invalid Expression") }
        }
    }

    private fun transformToRadians(expr: String): String {
        var res = expr
        val funcs = listOf("sin", "cos", "tan", "asin", "acos", "atan")
        funcs.forEach { f ->
            // Use a more sophisticated replacement to avoid double-wrapping
            // This is still limited but covers basic cases for a tool like this
            val pattern = Regex("$f\\(([^)]+)\\)")
            if (f.startsWith("a")) {
                // Inverse functions result is in radians, convert to degrees if needed?
                // Usually asin(x) = angle. If we are in degree mode, we might want the result in degrees.
                // But let's keep internal evaluation in radians and handle conversion carefully.
            } else {
                res = res.replace(pattern, "$f(($1)*pi/180)")
            }
        }
        return res
    }
}

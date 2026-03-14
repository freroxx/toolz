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
import kotlin.math.abs
import kotlin.math.sqrt

enum class EquationType {
    LINEAR, QUADRATIC, CUBIC, SYSTEM2
}

data class ConstantItem(val name: String, val value: String, val symbol: String, val description: String)

data class SolverState(
    val selectedType: EquationType = EquationType.LINEAR,
    val coefficients: Map<String, String> = mapOf("a" to "", "b" to "", "c" to "", "d" to ""),
    val result: String = "",
    val steps: List<String> = emptyList(),
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
        ConstantItem("Electron Charge", "1.60217e-19", "qₑ", "C")
    )
)

@HiltViewModel
class EquationSolverViewModel @Inject constructor(
    private val mathHistoryDao: MathHistoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SolverState())
    val uiState: StateFlow<SolverState> = _uiState.asStateFlow()

    val history: StateFlow<List<MathHistory>> = mathHistoryDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onTypeChange(type: EquationType) {
        _uiState.update { it.copy(selectedType = type, coefficients = mapOf("a" to "", "b" to "", "c" to "", "d" to ""), result = "", steps = emptyList(), error = null) }
    }

    fun onCoefficientChange(key: String, value: String) {
        _uiState.update { state ->
            val newCoeffs = state.coefficients.toMutableMap()
            newCoeffs[key] = value
            state.copy(coefficients = newCoeffs, error = null)
        }
    }

    fun solve() {
        val state = _uiState.value
        try {
            when (state.selectedType) {
                EquationType.LINEAR -> solveLinear(state)
                EquationType.QUADRATIC -> solveQuadratic(state)
                EquationType.CUBIC -> solveCubic(state)
                EquationType.SYSTEM2 -> solveSystem2(state)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Calculation error. check inputs.") }
        }
    }

    private fun solveLinear(state: SolverState) {
        val a = parse(state.coefficients["a"] ?: "0")
        val b = parse(state.coefficients["b"] ?: "0")
        
        if (a == 0.0) {
            _uiState.update { it.copy(error = "Coefficient 'a' cannot be zero.") }
            return
        }

        val x = -b / a
        val res = "x = ${format(x)}"
        val steps = listOf(
            "Equation: ${format(a)}x + ${format(b)} = 0",
            "Subtract ${format(b)} from both sides: ${format(a)}x = ${format(-b)}",
            "Divide by ${format(a)}: x = ${format(-b)} / ${format(a)}",
            "Solution: $res"
        )
        _uiState.update { it.copy(result = res, steps = steps, error = null) }
        saveToHistory("${format(a)}x + ${format(b)} = 0", res)
    }

    private fun solveQuadratic(state: SolverState) {
        val a = parse(state.coefficients["a"] ?: "0")
        val b = parse(state.coefficients["b"] ?: "0")
        val c = parse(state.coefficients["c"] ?: "0")

        if (a == 0.0) {
            solveLinear(SolverState(coefficients = mapOf("a" to b.toString(), "b" to c.toString())))
            return
        }

        val delta = b * b - 4 * a * c
        val steps = mutableListOf(
            "Equation: ${format(a)}x² + ${format(b)}x + ${format(c)} = 0",
            "1. Identify coefficients: a=${format(a)}, b=${format(b)}, c=${format(c)}",
            "2. Calculate Discriminant (Δ): b² - 4ac",
            "Δ = (${format(b)})² - 4(${format(a)})(${format(c)}) = ${format(delta)}"
        )

        val res = when {
            delta > 0 -> {
                val x1 = (-b + sqrt(delta)) / (2 * a)
                val x2 = (-b - sqrt(delta)) / (2 * a)
                steps.add("3. Δ > 0, so there are two real roots:")
                steps.add("x = (-b ± √Δ) / 2a")
                steps.add("x₁ = (-${format(b)} + ${format(sqrt(delta))}) / ${format(2 * a)} = ${format(x1)}")
                steps.add("x₂ = (-${format(b)} - ${format(sqrt(delta))}) / ${format(2 * a)} = ${format(x2)}")
                "x₁ = ${format(x1)}, x₂ = ${format(x2)}"
            }
            delta == 0.0 -> {
                val x = -b / (2 * a)
                steps.add("3. Δ = 0, so there is one real double root:")
                steps.add("x = -b / 2a = -${format(b)} / ${format(2 * a)} = ${format(x)}")
                "x = ${format(x)}"
            }
            else -> {
                val real = -b / (2 * a)
                val img = sqrt(-delta) / (2 * a)
                steps.add("3. Δ < 0, so roots are complex:")
                steps.add("x = (-b ± i√|Δ|) / 2a")
                steps.add("x = ${format(real)} ± ${format(img)}i")
                "x = ${format(real)} ± ${format(img)}i"
            }
        }
        _uiState.update { it.copy(result = res, steps = steps, error = null) }
        saveToHistory("${format(a)}x² + ${format(b)}x + ${format(c)} = 0", res)
    }

    private fun solveCubic(state: SolverState) {
        val a = parse(state.coefficients["a"] ?: "0")
        val b = parse(state.coefficients["b"] ?: "0")
        val c = parse(state.coefficients["c"] ?: "0")
        val d = parse(state.coefficients["d"] ?: "0")

        if (a == 0.0) {
            solveQuadratic(state)
            return
        }

        // x³ + Ax² + Bx + C = 0
        val A = b / a
        val B = c / a
        val C = d / a

        val Q = (3 * B - A * A) / 9.0
        val R = (9 * A * B - 27 * C - 2 * A * A * A) / 54.0
        val D = Q * Q * Q + R * R

        val steps = mutableListOf(
            "Equation: ${format(a)}x³ + ${format(b)}x² + ${format(c)}x + ${format(d)} = 0",
            "Normalized: x³ + ${format(A)}x² + ${format(B)}x + ${format(C)} = 0",
            "Calculate Q and R parameters...",
            "Discriminant (D) = Q³ + R² = ${format(D)}"
        )

        val result = if (D >= 0) {
            val S = cubeRoot(R + sqrt(D))
            val T = cubeRoot(R - sqrt(D))
            val x1 = S + T - A / 3.0
            
            if (abs(D) < 1e-10) { // Three real roots, at least two equal
                val x2 = -(S + T) / 2.0 - A / 3.0
                steps.add("D ≈ 0: Three real roots, at least two are equal.")
                "x₁ = ${format(x1)}, x₂ = ${format(x2)}"
            } else { // One real root and two complex
                steps.add("D > 0: One real root and two complex conjugate roots.")
                "x₁ = ${format(x1)} (Real root)"
            }
        } else { // Three distinct real roots
            val theta = Math.acos(R / sqrt(-Q * Q * Q))
            val x1 = 2 * sqrt(-Q) * Math.cos(theta / 3.0) - A / 3.0
            val x2 = 2 * sqrt(-Q) * Math.cos((theta + 2 * Math.PI) / 3.0) - A / 3.0
            val x3 = 2 * sqrt(-Q) * Math.cos((theta + 4 * Math.PI) / 3.0) - A / 3.0
            steps.add("D < 0: Three distinct real roots using trigonometric method.")
            "x₁ = ${format(x1)}, x₂ = ${format(x2)}, x₃ = ${format(x3)}"
        }

        _uiState.update { it.copy(result = result, steps = steps, error = null) }
        saveToHistory("${format(a)}x³ + ${format(b)}x² + ${format(c)}x + ${format(d)} = 0", result)
    }

    private fun cubeRoot(x: Double): Double {
        return if (x >= 0) Math.pow(x, 1.0 / 3.0) else -Math.pow(-x, 1.0 / 3.0)
    }

    private fun solveSystem2(state: SolverState) {
        // a1x + b1y = c1
        // a2x + b2y = c2
        // We'll reuse coefficients map creatively or add more
        val a1 = parse(state.coefficients["a"] ?: "0")
        val b1 = parse(state.coefficients["b"] ?: "0")
        val c1 = parse(state.coefficients["c"] ?: "0")
        val a2 = parse(state.coefficients["d"] ?: "0")
        val b2 = parse(state.coefficients["e"] ?: "0")
        val c2 = parse(state.coefficients["f"] ?: "0")

        val det = a1 * b2 - a2 * b1
        if (det == 0.0) {
            _uiState.update { it.copy(error = "No unique solution (Determinant is 0)") }
            return
        }

        val x = (c1 * b2 - c2 * b1) / det
        val y = (a1 * c2 - a2 * c1) / det
        val res = "x = ${format(x)}, y = ${format(y)}"
        val steps = listOf(
            "System:",
            "1) ${format(a1)}x + ${format(b1)}y = ${format(c1)}",
            "2) ${format(a2)}x + ${format(b2)}y = ${format(c2)}",
            "Calculate Determinant (D) = a1*b2 - a2*b1 = ${format(det)}",
            "D_x = c1*b2 - c2*b1 = ${format(c1 * b2 - c2 * b1)}",
            "D_y = a1*c2 - a2*c1 = ${format(a1 * c2 - a2 * c1)}",
            "x = D_x / D = ${format(x)}",
            "y = D_y / D = ${format(y)}"
        )
        _uiState.update { it.copy(result = res, steps = steps, error = null) }
        saveToHistory("System ($a1,$b1,$c1),($a2,$b2,$c2)", res)
    }

    private fun parse(s: String): Double {
        if (s.isEmpty()) return 0.0
        return try {
            ExpressionBuilder(s.replace("π", "pi")).build().evaluate()
        } catch (e: Exception) {
            0.0
        }
    }

    private fun format(v: Double): String {
        return if (abs(v - Math.round(v)) < 1e-10) Math.round(v).toString()
        else String.format(Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
    }

    fun clear() {
        _uiState.update { SolverState(selectedType = it.selectedType) }
    }

    private fun saveToHistory(expr: String, res: String) {
        viewModelScope.launch {
            mathHistoryDao.insert(MathHistory(expression = expr, result = res))
        }
    }
}

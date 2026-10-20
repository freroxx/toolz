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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/* ───────────────────────────── UI models ───────────────────────────── */

/** A physical constant usable in any field. [insert] is the name the engine understands. */
data class ConstantItem(val name: String, val symbol: String, val insert: String, val unit: String)

val SolverConstants = listOf(
    ConstantItem("Light speed", "c", "c", "m/s"),
    ConstantItem("Gravitational", "G", "G", "N·m²/kg²"),
    ConstantItem("Planck", "h", "h", "J·s"),
    ConstantItem("Boltzmann", "k", "kB", "J/K"),
    ConstantItem("Avogadro", "Nₐ", "NA", "mol⁻¹"),
    ConstantItem("Gas constant", "R", "R", "J/(mol·K)"),
    ConstantItem("Elem. charge", "qₑ", "qe", "C")
)

/** Everything the user is editing. The engine only ever sees [toProblem]. */
data class SolverForm(
    val mode: SolverMode = SolverMode.POLYNOMIAL,
    val degree: Int = 2,
    val size: Int = 2,
    val inputs: Map<String, String> = emptyMap(),
    val active: String? = "p0",
    val searchRadius: Double? = null
) {
    val keys: List<String> get() = Slots.order(Problem(mode, degree, size))

    fun toProblem(): Problem {
        val k = keys.toSet()
        return Problem(mode, degree, size, inputs.filterKeys { it in k }.filterValues { it.isNotEmpty() }, searchRadius)
    }

    val isEmpty: Boolean get() = keys.all { inputs[it].isNullOrEmpty() }
}

/** A finished solve, paired with the problem it answers (so the UI can tell fresh from stale). */
class Computed(val problem: Problem, val report: SolveReport)

data class SolveRecord(
    val id: Long,
    val time: Long,
    val mode: SolverMode,
    val degree: Int,
    val size: Int,
    val radius: Double?,
    val inputs: Map<String, String>,
    val equation: String,
    val summary: String
)

data class SolverExample(
    val label: String,
    val mode: SolverMode,
    val degree: Int = 2,
    val size: Int = 2,
    val inputs: Map<String, String>
)

val SolverExamples: Map<SolverMode, List<SolverExample>> = mapOf(
    SolverMode.POLYNOMIAL to listOf(
        SolverExample("x² − 5x + 6", SolverMode.POLYNOMIAL, 2, inputs = mapOf("p0" to "1", "p1" to "-5", "p2" to "6")),
        SolverExample("x² + 2x + 5", SolverMode.POLYNOMIAL, 2, inputs = mapOf("p0" to "1", "p1" to "2", "p2" to "5")),
        SolverExample("x³ − 3x + 2", SolverMode.POLYNOMIAL, 3, inputs = mapOf("p0" to "1", "p1" to "0", "p2" to "-3", "p3" to "2")),
        SolverExample("x⁴ − 5x² + 4", SolverMode.POLYNOMIAL, 4, inputs = mapOf("p0" to "1", "p1" to "0", "p2" to "-5", "p3" to "0", "p4" to "4"))
    ),
    SolverMode.SYSTEM to listOf(
        SolverExample(
            "2x + y = 5, x − y = 1", SolverMode.SYSTEM, size = 2,
            inputs = mapOf("s00" to "2", "s01" to "1", "s02" to "5", "s10" to "1", "s11" to "-1", "s12" to "1")
        ),
        SolverExample(
            "3 × 3 system", SolverMode.SYSTEM, size = 3,
            inputs = mapOf(
                "s00" to "2", "s01" to "1", "s02" to "-1", "s03" to "8",
                "s10" to "-3", "s11" to "-1", "s12" to "2", "s13" to "-11",
                "s20" to "-2", "s21" to "1", "s22" to "2", "s23" to "-3"
            )
        )
    ),
    SolverMode.CUSTOM to listOf(
        SolverExample("sin x = 0.5", SolverMode.CUSTOM, inputs = mapOf("lhs" to "sin(x)", "rhs" to "0.5")),
        SolverExample("eˣ = 3x", SolverMode.CUSTOM, inputs = mapOf("lhs" to "exp(x)", "rhs" to "3*x")),
        SolverExample("cos x = x", SolverMode.CUSTOM, inputs = mapOf("lhs" to "cos(x)", "rhs" to "x")),
        SolverExample("x³ − 2x = 5", SolverMode.CUSTOM, inputs = mapOf("lhs" to "x^3-2*x", "rhs" to "5"))
    )
)

/* ───────────────────────────── Keypad logic ───────────────────────────── */

object SolverInput {
    private const val MAX_LEN = 48
    private val funcTail = Regex("[A-Za-z][A-Za-z0-9]*\\($")

    fun apply(cur: String, key: String): String = when (key) {
        "BS" -> if (cur.isEmpty()) cur else funcTail.find(cur)?.let { cur.dropLast(it.value.length) } ?: cur.dropLast(1)
        "C" -> ""
        "NEG" -> toggleNeg(cur)
        "." -> dot(cur)
        else -> if (cur.length + key.length > MAX_LEN) cur else cur + key
    }

    private fun toggleNeg(v: String): String = when {
        v.isEmpty() -> "-"
        v.startsWith("-(") && v.endsWith(")") -> v.substring(2, v.length - 1)
        v.startsWith("-") -> v.drop(1)
        v.any { it in "+-*/^()" } -> "-($v)"
        else -> "-$v"
    }

    private fun dot(cur: String): String {
        var i = cur.length
        while (i > 0 && (cur[i - 1].isDigit() || cur[i - 1] == '.')) i--
        val token = cur.substring(i)
        return when {
            token.contains('.') -> cur
            token.isEmpty() -> cur + "0."
            else -> "$cur."
        }
    }
}

/* ───────────────────────────── ViewModel ───────────────────────────── */

@HiltViewModel
class EquationSolverViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mathHistoryDao: MathHistoryDao
) : ViewModel() {

    private val prefs = context.getSharedPreferences("equation_solver", Context.MODE_PRIVATE)

    private val _form = MutableStateFlow(SolverForm())
    val form: StateFlow<SolverForm> = _form.asStateFlow()

    /** Live results: re-solved ~120 ms after the last edit, off the main thread, cancelling stale work. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val computed: StateFlow<Computed?> = _form
        .map { it.toProblem() }
        .distinctUntilChanged()
        .debounce(120)
        .mapLatest { p ->
            val job = currentCoroutineContext()[Job]
            Computed(p, withContext(Dispatchers.Default) { EquationEngine.solve(p) { job?.isActive == false } })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<SolveRecord>> = _history.asStateFlow()

    /** Bumps every time the user presses Solve, so the screen can scroll to / confirm the result. */
    private val _solveTick = MutableStateFlow(0)
    val solveTick: StateFlow<Int> = _solveTick.asStateFlow()

    /* ── form editing ── */

    fun setMode(mode: SolverMode) = _form.update { f ->
        val n = f.copy(mode = mode)
        n.copy(active = n.keys.first())
    }

    fun setDegree(degree: Int) = _form.update { f ->
        val n = f.copy(degree = degree.coerceIn(Slots.MIN_DEGREE, Slots.MAX_DEGREE))
        n.copy(active = n.active?.takeIf { it in n.keys } ?: n.keys.first())
    }

    fun setSize(size: Int) = _form.update { f ->
        val n = f.copy(size = size.coerceIn(2, 3))
        n.copy(active = n.active?.takeIf { it in n.keys } ?: n.keys.first())
    }

    fun setSearchRadius(radius: Double?) = _form.update { it.copy(searchRadius = radius) }

    fun select(key: String?) = _form.update { it.copy(active = key) }

    fun hideKeypad() = _form.update { it.copy(active = null) }

    fun onKey(key: String) = _form.update { f ->
        val k = f.active ?: return@update f
        val cur = f.inputs[k].orEmpty()
        val new = SolverInput.apply(cur, key)
        if (new == cur) f else f.copy(inputs = f.inputs + (k to new))
    }

    fun next() = _form.update { f ->
        val keys = f.keys
        val i = keys.indexOf(f.active)
        f.copy(active = if (i in 0 until keys.lastIndex) keys[i + 1] else null)
    }

    fun clearAll() = _form.update { f ->
        f.copy(inputs = f.inputs - f.keys.toSet(), active = f.keys.first())
    }

    fun loadExample(ex: SolverExample) = _form.update { f ->
        val n = f.copy(mode = ex.mode, degree = ex.degree, size = ex.size)
        n.copy(inputs = f.inputs - n.keys.toSet() + ex.inputs, active = null)
    }

    fun restore(r: SolveRecord) = _form.update { f ->
        val n = f.copy(mode = r.mode, degree = r.degree, size = r.size, searchRadius = r.radius)
        n.copy(inputs = f.inputs - n.keys.toSet() + r.inputs, active = null)
    }

    /* ── committing ── */

    fun solve() {
        val f = _form.value
        _form.update { it.copy(active = null) }
        val problem = f.toProblem()
        viewModelScope.launch {
            // Reuse the live result when it already matches; only compute on demand
            // when the user confirms before the debounce has caught up.
            val report = computed.value
                ?.takeIf { it.problem == problem }
                ?.report
                ?: withContext(Dispatchers.Default) { EquationEngine.solve(problem) }
            val solved = report.outcome !is Outcome.Incomplete && report.outcome !is Outcome.Invalid
            if (solved) {
                addRecord(f, report)
                try {
                    mathHistoryDao.insert(
                        MathHistory(expression = report.equation.replace("\n", "; "), result = report.summary)
                    )
                } catch (_: Exception) { /* shared history is best-effort */ }
            }
            _solveTick.update { it + 1 }
        }
    }

    /* ── history ── */

    fun deleteRecord(id: Long) {
        _history.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun clearHistory() {
        _history.value = emptyList()
        persist()
    }

    private fun addRecord(f: SolverForm, report: SolveReport) {
        val p = f.toProblem()
        val now = System.currentTimeMillis()
        val rec = SolveRecord(now, now, p.mode, p.degree, p.size, p.searchRadius, p.inputs, report.equation, report.summary)
        _history.update { list ->
            val deduped = list.filterNot { it.equation == rec.equation && it.summary == rec.summary }
            (listOf(rec) + deduped).take(MAX_HISTORY)
        }
        persist()
    }

    private fun persist() {
        val arr = JSONArray()
        _history.value.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id).put("t", r.time).put("m", r.mode.name)
                    .put("d", r.degree).put("n", r.size)
                    .put("r", r.radius ?: JSONObject.NULL)
                    .put("eq", r.equation).put("sum", r.summary)
                    .put("in", JSONObject(r.inputs as Map<*, *>))
            )
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    private fun loadHistory(): List<SolveRecord> = try {
        val arr = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val inObj = o.getJSONObject("in")
            val inputs = HashMap<String, String>()
            inObj.keys().forEach { k -> inputs[k] = inObj.getString(k) }
            SolveRecord(
                id = o.getLong("id"), time = o.getLong("t"),
                mode = SolverMode.valueOf(o.getString("m")),
                degree = o.getInt("d"), size = o.getInt("n"),
                radius = if (o.isNull("r")) null else o.getDouble("r"),
                inputs = inputs, equation = o.getString("eq"), summary = o.getString("sum")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private companion object {
        const val KEY_HISTORY = "history_v2"
        const val MAX_HISTORY = 40
    }
}
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

import net.objecthunter.exp4j.Expression
import net.objecthunter.exp4j.ExpressionBuilder
import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/* ───────────────────────────── Problem model ───────────────────────────── */

enum class SolverMode { POLYNOMIAL, SYSTEM, CUSTOM }

/** Everything the engine needs. Pure data → easy to debounce, compare and persist. */
data class Problem(
    val mode: SolverMode,
    val degree: Int = 2,
    val size: Int = 2,
    val inputs: Map<String, String> = emptyMap(),
    /** Custom mode only: null = auto (±10, ±100, ±1000). */
    val searchRadius: Double? = null
)

/** Slot keys: p0 = leading coefficient · s{row}{col} (last col = right-hand side) · lhs / rhs. */
object Slots {
    const val MIN_DEGREE = 1
    const val MAX_DEGREE = 6

    fun poly(degree: Int): List<String> = List(degree + 1) { "p$it" }
    fun system(size: Int): List<List<String>> = List(size) { r -> List(size + 1) { c -> "s$r$c" } }
    val custom: List<String> = listOf("lhs", "rhs")

    fun order(p: Problem): List<String> = when (p.mode) {
        SolverMode.POLYNOMIAL -> poly(p.degree)
        SolverMode.SYSTEM -> system(p.size).flatten()
        SolverMode.CUSTOM -> custom
    }

    fun polyLetter(i: Int): Char = 'a' + i
}

/* ───────────────────────────── Report model ───────────────────────────── */

data class Step(val title: String, val body: String)
data class RootRow(val label: String, val text: String, val mult: Int = 1, val isComplex: Boolean = false)
data class ValueRow(val label: String, val text: String)

sealed interface Outcome {
    data class Incomplete(val hint: String) : Outcome
    data class Invalid(val slot: String?, val message: String) : Outcome
    data class Roots(
        val headline: String,
        val rows: List<RootRow>,
        val exact: String? = null,
        val factored: String? = null,
        val note: String? = null
    ) : Outcome
    data class Values(val headline: String, val rows: List<ValueRow>) : Outcome
    data class Infinite(val headline: String, val lines: List<String>) : Outcome
    data class NoSolution(val headline: String, val detail: String) : Outcome
}

class GraphSeries(val label: String, val xs: DoubleArray, val ys: DoubleArray, val colorIndex: Int = 0)
class GraphMarker(val x: Double, val y: Double, val label: String, val kind: Int = 0) // kind 0 = solution, 1 = extremum
class GraphData(
    val xMin: Double, val xMax: Double, val yMin: Double, val yMax: Double,
    val series: List<GraphSeries>,
    val markers: List<GraphMarker>,
    val traceable: Boolean
)

class SolveReport(
    val outcome: Outcome,
    val equation: String = "",
    val summary: String = "",
    val copyText: String = "",
    val steps: List<Step> = emptyList(),
    val graph: GraphData? = null
)

/* ───────────────────────────── Numbers & text ───────────────────────────── */

data class Complex(val re: Double, val im: Double = 0.0) {
    operator fun plus(o: Complex) = Complex(re + o.re, im + o.im)
    operator fun minus(o: Complex) = Complex(re - o.re, im - o.im)
    operator fun times(o: Complex) = Complex(re * o.re - im * o.im, re * o.im + im * o.re)
    operator fun div(o: Complex): Complex {
        val d = o.re * o.re + o.im * o.im
        return Complex((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
    }
    fun abs(): Double = hypot(re, im)
}

object Fmt {
    private const val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    private const val SUB = "₀₁₂₃₄₅₆₇₈₉"

    fun sup(n: Int): String = n.toString().map { if (it == '-') '⁻' else SUP[it - '0'] }.joinToString("")
    fun sub(n: Int): String = n.toString().map { if (it == '-') '₋' else SUB[it - '0'] }.joinToString("")

    /** ASCII form (safe to copy/paste). 7 significant digits, integers snapped. */
    fun num(v: Double, sig: Int = 7): String {
        if (v.isNaN()) return "NaN"
        if (v.isInfinite()) return if (v > 0) "∞" else "-∞"
        if (abs(v) < 1e-12) return "0"
        val r = Math.rint(v)
        if (abs(v - r) <= 1e-9 * max(1.0, abs(v)) && abs(r) < 1e15) return r.toLong().toString()
        val a = abs(v)
        if (a >= 1e9 || a < 1e-4) {
            val s = String.format(Locale.US, "%.${sig - 1}e", v)
            val (mant, exp) = s.split('e')
            val m = if (mant.contains('.')) mant.trimEnd('0').trimEnd('.') else mant
            return m + "×10" + sup(exp.toInt())
        }
        return BigDecimal(v).round(MathContext(sig)).stripTrailingZeros().toPlainString()
    }

    /** Display form with a true minus sign. */
    fun shown(v: Double, sig: Int = 7): String = num(v, sig).replace('-', '−')

    fun complex(re: Double, im: Double, sig: Int = 7): String {
        val tiny = 1e-9 * max(1.0, hypot(re, im))
        if (abs(im) <= tiny) return shown(re, sig)
        val mag = num(abs(im), sig).let { if (it == "1") "" else it }
        val imPart = "${mag}i"
        if (abs(re) <= tiny) return (if (im < 0) "−" else "") + imPart
        return "${shown(re, sig)} ${if (im < 0) "−" else "+"} $imPart"
    }

    fun pair(re: Double, im: Double, sig: Int = 7): String {
        val tiny = 1e-9 * max(1.0, hypot(re, im))
        val mag = num(abs(im), sig).let { if (it == "1") "" else it } + "i"
        return if (abs(re) <= tiny) "±$mag" else "${shown(re, sig)} ± $mag"
    }
}

sealed interface Parsed {
    data class Ok(val v: Double) : Parsed
    data class Err(val msg: String) : Parsed
}

object Calc {
    /** Physical constants available as symbols in any field. */
    val constants: Map<String, Double> = mapOf(
        "c" to 299792458.0,
        "G" to 6.6743e-11,
        "h" to 6.62607015e-34,
        "kB" to 1.380649e-23,
        "NA" to 6.02214076e23,
        "R" to 8.314462618,
        "qe" to 1.602176634e-19
    )

    fun normalize(raw: String): String = raw
        .replace("π", "pi").replace("×", "*").replace("÷", "/")
        .replace("−", "-").replace("√", "sqrt")

    fun build(src: String, vararg vars: String): Expression {
        val names = HashSet<String>(constants.keys)
        names.addAll(vars)
        val e = ExpressionBuilder(normalize(src)).variables(names).build()
        if (!e.validate(false).isValid) throw IllegalArgumentException("invalid expression")
        e.setVariables(constants)
        return e
    }

    fun evalNumber(raw: String): Parsed {
        val s = raw.trim()
        if (s.isEmpty()) return Parsed.Err("empty")
        return try {
            val v = build(s).evaluate()
            if (v.isNaN() || v.isInfinite()) Parsed.Err("not a finite number") else Parsed.Ok(v)
        } catch (e: ArithmeticException) {
            Parsed.Err("division by zero")
        } catch (e: Exception) {
            Parsed.Err("not a valid number")
        }
    }
}

/* ───────────────────────────── Engine ───────────────────────────── */

object EquationEngine {

    fun solve(p: Problem, cancelled: () -> Boolean = { false }): SolveReport = try {
        when (p.mode) {
            SolverMode.POLYNOMIAL -> solvePolynomial(p, cancelled)
            SolverMode.SYSTEM -> solveSystem(p)
            SolverMode.CUSTOM -> solveCustom(p, cancelled)
        }
    } catch (e: Exception) {
        SolveReport(Outcome.Invalid(null, "Couldn't solve this one — check the inputs"))
    }

    private fun f(v: Double) = Fmt.shown(v)

    /* ───────────── Polynomials ───────────── */

    private class Cluster(val z: Complex, val mult: Int)
    private class RootSet(val clusters: List<Cluster>, val iterations: Int)

    private fun solvePolynomial(p: Problem, cancelled: () -> Boolean): SolveReport {
        val keys = Slots.poly(p.degree)
        if (p.inputs[keys[0]].isNullOrBlank()) {
            return SolveReport(Outcome.Incomplete("Enter the leading coefficient a. Empty fields count as 0."))
        }
        val raw = DoubleArray(keys.size)
        for ((i, k) in keys.withIndex()) {
            val s = p.inputs[k].orEmpty()
            if (s.isBlank()) continue
            when (val r = Calc.evalNumber(s)) {
                is Parsed.Ok -> raw[i] = r.v
                is Parsed.Err -> return SolveReport(Outcome.Invalid(k, "${Slots.polyLetter(i)}: ${r.msg}"))
            }
        }
        return solveCoefficients(raw, cancelled)
    }

    fun solveCoefficients(raw: DoubleArray, cancelled: () -> Boolean = { false }): SolveReport {
        var start = 0
        while (start < raw.size && raw[start] == 0.0) start++
        if (start == raw.size) {
            return SolveReport(
                Outcome.Infinite("Every x is a solution", listOf("0 = 0 is true for all x")),
                equation = "0 = 0", summary = "All x"
            )
        }
        val c = raw.copyOfRange(start, raw.size)
        val n = c.size - 1
        val eq = polyText(c) + " = 0"
        if (n == 0) {
            return SolveReport(
                Outcome.NoSolution("No solution", "${f(c[0])} = 0 is never true"),
                equation = eq, summary = "No solution"
            )
        }

        val rs = polyRoots(c, cancelled)
        val rows = buildRows(rs.clusters)
        val steps = ArrayList<Step>()
        steps += Step("Equation", eq)
        if (start > 0) steps += Step("Degree reduced", "The leading coefficient is 0, so this is really a degree-$n equation.")

        var exact: String? = null
        when (n) {
            1 -> linearSteps(c, steps)
            2 -> { quadraticSteps(c, steps); exact = exactQuadratic(c[0], c[1], c[2]) }
            3 -> cubicSteps(c, rs, steps)
            4 -> quarticSteps(c, rs, steps)
            else -> steps += Step(
                "Method",
                "Degree $n has no general closed-form formula (Abel–Ruffini), so the roots are found with Aberth iteration (${rs.iterations} rounds) and polished with Newton's method."
            )
        }
        val factored = if (n >= 2) factoredForm(c[0], rs.clusters) else null
        factored?.let { steps += Step("Factored form", it) }
        if (n >= 2) steps += Step("Check (Vieta)", vietaCheck(c, rs.clusters))

        val realCount = rows.filter { !it.isComplex }.sumOf { it.mult }
        val cxCount = rows.filter { it.isComplex }.sumOf { it.mult * 2 }
        val headline = when {
            realCount > 0 && cxCount > 0 -> "$realCount real + $cxCount complex roots"
            realCount > 0 -> if (realCount == 1) "1 real root" else "$realCount real roots"
            else -> "$cxCount complex roots"
        }
        val outcome = Outcome.Roots(headline, rows, exact, factored)
        val summary = rows.joinToString(", ") { "${it.label} = ${it.text}" + if (it.mult > 1) " (×${it.mult})" else "" }
        val copy = buildString {
            append(eq.replace('−', '-')).append('\n')
            rs.clusters.sortedWith(compareBy({ it.z.re }, { it.z.im })).forEachIndexed { i, cl ->
                append("x").append(i + 1).append(" = ").append(Fmt.complex(cl.z.re, cl.z.im, 12).replace('−', '-'))
                if (cl.mult > 1) append("  (multiplicity ${cl.mult})")
                append('\n')
            }
        }.trimEnd()
        return SolveReport(outcome, eq, summary, copy, steps, polyGraph(c, rs.clusters))
    }

    private fun letter(i: Int) = Slots.polyLetter(i)

    private fun polyText(c: DoubleArray, v: String = "x"): String {
        val n = c.size - 1
        val sb = StringBuilder()
        for (i in c.indices) {
            val co = c[i]
            if (co == 0.0) continue
            val pw = n - i
            val neg = co < 0
            val mag = abs(co)
            if (sb.isEmpty()) { if (neg) sb.append("−") } else sb.append(if (neg) " − " else " + ")
            if (!(mag == 1.0 && pw > 0)) sb.append(Fmt.num(mag))
            if (pw >= 1) sb.append(v)
            if (pw >= 2) sb.append(Fmt.sup(pw))
        }
        return if (sb.isEmpty()) "0" else sb.toString()
    }

    private fun hornerR(c: DoubleArray, x: Double): Double {
        var r = 0.0
        for (co in c) r = r * x + co
        return r
    }

    private fun hornerC(c: DoubleArray, z: Complex): Complex {
        var r = Complex(0.0)
        for (co in c) r = r * z + Complex(co)
        return r
    }

    private fun derivative(c: DoubleArray): DoubleArray {
        val n = c.size - 1
        return DoubleArray(n) { c[it] * (n - it) }
    }

    private fun polyRoots(c: DoubleArray, cancelled: () -> Boolean): RootSet {
        var cc = c
        var zeros = 0
        while (cc.size > 1 && cc.last() == 0.0) { cc = cc.copyOf(cc.size - 1); zeros++ }
        val out = ArrayList<Cluster>()
        var iters = 0
        when (cc.size - 1) {
            0 -> {}
            1 -> out += Cluster(Complex(-cc[1] / cc[0]), 1)
            2 -> out += quadClusters(cc[0], cc[1], cc[2])
            else -> {
                val (raw, it) = aberth(cc, cancelled)
                iters = it
                out += refineAndCluster(cc, raw)
            }
        }
        if (zeros > 0) out += Cluster(Complex(0.0), zeros)
        return RootSet(out, iters)
    }

    private fun quadClusters(a: Double, b: Double, c: Double): List<Cluster> {
        val d = b * b - 4 * a * c
        val scale = b * b + abs(4 * a * c)
        if (abs(d) <= 1e-14 * scale) return listOf(Cluster(Complex(-b / (2 * a)), 2))
        if (d > 0) {
            val sq = sqrt(d)
            val q = -0.5 * (b + (if (b >= 0) sq else -sq))
            return listOf(Cluster(Complex(q / a), 1), Cluster(Complex(c / q), 1))
        }
        val re = -b / (2 * a)
        val im = sqrt(-d) / (2 * abs(a))
        return listOf(Cluster(Complex(re, im), 1), Cluster(Complex(re, -im), 1))
    }

    /** Aberth–Ehrlich simultaneous iteration on the monic polynomial. */
    private fun aberth(c: DoubleArray, cancelled: () -> Boolean): Pair<List<Complex>, Int> {
        val n = c.size - 1
        val a = DoubleArray(n + 1) { c[it] / c[0] }
        val d = derivative(a)
        var r = 0.0
        for (k in 1..n) r = max(r, abs(a[k]).pow(1.0 / k))
        r = max(r, 1e-3)
        val z = Array(n) { k ->
            val th = 2 * Math.PI * k / n + 0.7
            Complex(r * cos(th), r * sin(th))
        }
        var iters = 0
        while (iters < 500) {
            iters++
            var maxRel = 0.0
            for (k in 0 until n) {
                val p = hornerC(a, z[k])
                if (p.re == 0.0 && p.im == 0.0) continue
                val dp = hornerC(d, z[k])
                val ratio = if (dp.abs() < 1e-300) Complex(1e-3, 1e-3) else p / dp
                var s = Complex(0.0)
                for (j in 0 until n) {
                    if (j == k) continue
                    val diff = z[k] - z[j]
                    if (diff.abs() > 1e-300) s += Complex(1.0) / diff
                }
                val denom = Complex(1.0) - ratio * s
                val w = if (denom.abs() < 1e-300) ratio else ratio / denom
                z[k] = z[k] - w
                maxRel = max(maxRel, w.abs() / (1 + z[k].abs()))
            }
            if (maxRel < 1e-15 || cancelled()) break
        }
        return z.toList() to iters
    }

    private fun scaleAt(c: DoubleArray, w: Complex): Double {
        val m = w.abs()
        var s = 0.0
        var pw = 1.0
        for (i in c.indices.reversed()) { s += abs(c[i]) * pw; pw *= m }
        return max(s, 1e-300)
    }

    private fun refineAndCluster(c: DoubleArray, raw: List<Complex>): List<Cluster> {
        val n = c.size - 1
        val ders = ArrayList<DoubleArray>()
        ders += c
        for (k in 1..n) ders += derivative(ders[k - 1])

        fun polish(z0: Complex): Complex {
            var z = z0
            repeat(4) {
                val p = hornerC(ders[0], z)
                val dp = hornerC(ders[1], z)
                if (dp.abs() < 1e-300) return z
                val nz = z - p / dp
                if (hornerC(ders[0], nz).abs() <= p.abs()) z = nz else return z
            }
            return z
        }

        fun snap(z: Complex): Complex =
            if (abs(z.im) <= 1e-9 * (1 + abs(z.re))) Complex(z.re, 0.0) else z

        val used = BooleanArray(raw.size)
        val out = ArrayList<Cluster>()
        for (i in raw.indices.sortedBy { raw[it].re }) {
            if (used[i]) continue
            used[i] = true
            val members = mutableListOf(i)
            for (j in raw.indices) {
                if (!used[j] && (raw[j] - raw[i]).abs() < 1e-3 * (1 + raw[i].abs())) { used[j] = true; members += j }
            }
            val m = members.size
            if (m >= 2) {
                val mean = Complex(members.sumOf { raw[it].re } / m, members.sumOf { raw[it].im } / m)
                var w = mean
                var ok = true
                repeat(40) {
                    val f0 = hornerC(ders[m - 1], w)
                    val f1 = hornerC(ders[m], w)
                    if (f1.abs() < 1e-300) { ok = false; return@repeat }
                    w -= f0 / f1
                }
                if (ok && (w - mean).abs() < 1e-2 * (1 + mean.abs())) {
                    for (j in 0 until m) {
                        if (hornerC(ders[j], w).abs() > 1e-8 * scaleAt(ders[j], w)) ok = false
                    }
                    if (ok) { out += Cluster(snap(w), m); continue }
                }
            }
            for (idx in members) out += Cluster(snap(polish(raw[idx])), 1)
        }
        return out
    }

    private fun buildRows(clusters: List<Cluster>): List<RootRow> {
        val real = clusters.filter { it.z.im == 0.0 }.sortedBy { it.z.re }
        val cx = clusters.filter { it.z.im > 0.0 }.sortedBy { it.z.re }
        val total = real.size + 2 * cx.size
        val rows = ArrayList<RootRow>()
        var idx = 1
        for (cl in real) {
            val label = if (total == 1) "x" else "x" + Fmt.sub(idx)
            rows += RootRow(label, Fmt.shown(cl.z.re), cl.mult, false)
            idx++
        }
        for (cl in cx) {
            val label = "x" + Fmt.sub(idx) + "," + Fmt.sub(idx + 1)
            rows += RootRow(label, Fmt.pair(cl.z.re, cl.z.im), cl.mult, true)
            idx += 2
        }
        return rows
    }

    private fun isInt(v: Double) = abs(v) <= 1e6 && abs(v - Math.rint(v)) < 1e-12

    private fun gcd(a: Long, b: Long): Long {
        var x = abs(a); var y = abs(b)
        while (y != 0L) { val t = x % y; x = y; y = t }
        return x
    }

    private fun frac(n: Long, d: Long): String {
        var nn = n; var dd = d
        val g = gcd(nn, dd)
        if (g > 1) { nn /= g; dd /= g }
        if (dd < 0) { nn = -nn; dd = -dd }
        return (if (dd == 1L) "$nn" else "$nn/$dd").replace('-', '−')
    }

    /** Surd / fraction form for integer-coefficient quadratics, or null when it adds nothing. */
    private fun exactQuadratic(a: Double, b: Double, c: Double): String? {
        if (!(isInt(a) && isInt(b) && isInt(c))) return null
        val A = a.toLong(); val B = b.toLong(); val C = c.toLong()
        val D = B * B - 4 * A * C
        if (D == 0L) return null
        var m = abs(D)
        var k = 1L
        var fct = 2L
        while (fct * fct <= m) {
            while (m % (fct * fct) == 0L) { m /= fct * fct; k *= fct }
            fct++
        }
        if (D > 0 && m == 1L) {
            val r1 = frac(-B + k, 2 * A)
            val r2 = frac(-B - k, 2 * A)
            if (!r1.contains('/') && !r2.contains('/')) return null
            return "x₁ = $r1,  x₂ = $r2"
        }
        val imaginary = D < 0
        var head = -B; var kk = k; var den = 2 * A
        val g = gcd(gcd(head, kk), den)
        if (g > 1) { head /= g; kk /= g; den /= g }
        if (den < 0) { den = -den; head = -head }
        val radical = buildString {
            if (kk != 1L) append(kk)
            if (imaginary) append("i")
            if (m != 1L) append("√$m")
        }
        val numer = if (head == 0L) "±$radical" else "$head ± $radical"
        val s = when {
            den == 1L -> numer
            head == 0L -> "$numer/$den"
            else -> "($numer)/$den"
        }
        return "x = ${s.replace('-', '−')}"
    }

    private fun factoredForm(a: Double, clusters: List<Cluster>): String? {
        val sb = StringBuilder()
        when {
            a == 1.0 -> {}
            a == -1.0 -> sb.append("−")
            else -> sb.append(f(a))
        }
        val real = clusters.filter { it.z.im == 0.0 }.sortedBy { it.z.re }
        val cx = clusters.filter { it.z.im > 0.0 }.sortedBy { it.z.re }
        if (real.size + cx.size == 0) return null
        val parts = ArrayList<String>()
        for (cl in real) {
            val r = cl.z.re
            val base = when {
                abs(r) < 1e-12 -> "x"
                r > 0 -> "(x − ${Fmt.num(r)})"
                else -> "(x + ${Fmt.num(-r)})"
            }
            parts += base + if (cl.mult > 1) Fmt.sup(cl.mult) else ""
        }
        for (cl in cx) {
            val b = -2 * cl.z.re
            val cc = cl.z.re * cl.z.re + cl.z.im * cl.z.im
            val bm = abs(b)
            val bx = if (bm == 1.0) "x" else "${Fmt.num(bm)}x"
            val mid = if (bm < 1e-12) "" else if (b > 0) " + $bx" else " − $bx"
            parts += "(x²$mid + ${Fmt.num(cc)})" + if (cl.mult > 1) Fmt.sup(cl.mult) else ""
        }
        val body = parts.joinToString("")
        return (sb.toString() + body).let { if (it.isEmpty()) null else "$it = 0" }
    }

    private fun vietaCheck(c: DoubleArray, clusters: List<Cluster>): String {
        val n = c.size - 1
        var sum = Complex(0.0)
        var prod = Complex(1.0)
        for (cl in clusters) repeat(cl.mult) { sum += cl.z; prod *= cl.z }
        val expSum = -c[1] / c[0]
        val expProd = (if (n % 2 == 0) 1.0 else -1.0) * c[n] / c[0]
        val ok1 = abs(sum.re - expSum) <= 1e-6 * (1 + abs(expSum))
        val ok2 = abs(prod.re - expProd) <= 1e-6 * (1 + abs(expProd))
        return "Sum of roots = ${f(sum.re)}   (expected −b/a = ${f(expSum)}) ${if (ok1) "✓" else "≈"}\n" +
            "Product of roots = ${f(prod.re)}   (expected ${if (n % 2 == 0) "" else "−"}last/a = ${f(expProd)}) ${if (ok2) "✓" else "≈"}"
    }

    private fun linearSteps(c: DoubleArray, steps: MutableList<Step>) {
        val a = c[0]; val b = c[1]
        steps += Step("Isolate the x term", "${f(a)}x = ${f(-b)}")
        steps += Step("Divide by ${f(a)}", "x = ${f(-b)} / ${f(a)} = ${f(-b / a)}")
    }

    private fun quadraticSteps(c: DoubleArray, steps: MutableList<Step>) {
        val a = c[0]; val b = c[1]; val cc = c[2]
        val d = b * b - 4 * a * cc
        steps += Step("Coefficients", "a = ${f(a)},  b = ${f(b)},  c = ${f(cc)}")
        steps += Step("Discriminant", "Δ = b² − 4ac\nΔ = (${f(b)})² − 4(${f(a)})(${f(cc)}) = ${f(d)}")
        val tiny = abs(d) <= 1e-14 * (b * b + abs(4 * a * cc))
        steps += Step(
            "Nature of the roots",
            when {
                tiny -> "Δ = 0 → one repeated real root"
                d > 0 -> "Δ > 0 → two distinct real roots"
                else -> "Δ < 0 → two complex conjugate roots"
            }
        )
        val formula = when {
            tiny -> "x = −b / 2a = ${f(-b / (2 * a))}"
            d > 0 -> {
                val s = sqrt(d)
                "x = (−b ± √Δ) / 2a\nx₁ = (${f(-b)} + ${f(s)}) / ${f(2 * a)} = ${f((-b + s) / (2 * a))}\nx₂ = (${f(-b)} − ${f(s)}) / ${f(2 * a)} = ${f((-b - s) / (2 * a))}"
            }
            else -> "x = (−b ± i√|Δ|) / 2a\nx = ${Fmt.pair(-b / (2 * a), sqrt(-d) / (2 * abs(a)))}"
        }
        steps += Step("Quadratic formula", formula)
        val vx = -b / (2 * a)
        val vy = a * vx * vx + b * vx + cc
        steps += Step("Vertex", "(${f(vx)}, ${f(vy)}) — the parabola opens ${if (a > 0) "upward" else "downward"}")
    }

    private fun cubicSteps(c: DoubleArray, rs: RootSet, steps: MutableList<Step>) {
        val a = c[0]
        val A = c[1] / a; val B = c[2] / a; val C = c[3] / a
        steps += Step("Normalize", "Divide by a = ${f(a)}:\nx³ + ${f(A)}x² + ${f(B)}x + ${f(C)} = 0".replace("+ −", "− "))
        val p = B - A * A / 3
        val q = 2 * A * A * A / 27 - A * B / 3 + C
        steps += Step("Depress the cubic", "Substitute x = t − ${f(A / 3)}:\nt³ + pt + q = 0\np = ${f(p)},  q = ${f(q)}")
        val disc = -(4 * p * p * p + 27 * q * q)
        val sc = 4 * abs(p * p * p) + 27 * q * q
        val kind = when {
            abs(disc) <= 1e-12 * max(sc, 1e-300) -> "Δ = 0 → a repeated root, all roots real"
            disc > 0 -> "Δ > 0 → three distinct real roots"
            else -> "Δ < 0 → one real root and two complex conjugates"
        }
        steps += Step("Discriminant", "Δ = −(4p³ + 27q²) = ${f(disc)}\n$kind")
        steps += Step("Solve", "Roots found with Aberth iteration (${rs.iterations} rounds), then polished with Newton's method.")
    }

    private fun quarticSteps(c: DoubleArray, rs: RootSet, steps: MutableList<Step>) {
        val a = c[0]
        if (c[1] == 0.0 && c[3] == 0.0) {
            steps += Step("Biquadratic form", "No x³ or x term, so let u = x²:\n${f(a)}u² + ${f(c[2])}u + ${f(c[4])} = 0")
            val us = quadClusters(a, c[2], c[4]).sortedBy { it.z.re }
            val lines = us.mapIndexed { i, u -> "u${Fmt.sub(i + 1)} = ${Fmt.complex(u.z.re, u.z.im)}" }
            steps += Step("Solve for u", lines.joinToString("\n") + "\nThen x = ±√u for each u.")
        } else {
            steps += Step("Method", "Roots found with Aberth iteration (${rs.iterations} rounds), then polished with Newton's method. Repeated roots are refined on the derivative for full precision.")
        }
    }

    private fun polyGraph(c: DoubleArray, clusters: List<Cluster>): GraphData? {
        val n = c.size - 1
        if (n < 1) return null
        val realRoots = clusters.filter { it.z.im == 0.0 }.map { it.z.re }.distinct()
        val crit = if (n >= 2) polyRoots(derivative(c)) { false }.clusters.filter { it.z.im == 0.0 }.map { it.z.re }.distinct() else emptyList()
        val pts = (realRoots + crit).sorted().ifEmpty { listOf(0.0) }
        var lo = pts.first(); var hi = pts.last()
        val span = hi - lo
        if (span < 1e-9) { lo -= 2.5; hi += 2.5 } else { val pad = max(span * 0.4, 1.0); lo -= pad; hi += pad }
        val base = pts.map { hornerR(c, it) } + 0.0
        val bMin = base.min(); val bMax = base.max()
        val bSpan = bMax - bMin
        val e1 = hornerR(c, lo); val e2 = hornerR(c, hi)
        var yMin: Double; var yMax: Double
        if (bSpan < 1e-9) {
            yMin = min(min(e1, e2), 0.0); yMax = max(max(e1, e2), 0.0)
        } else {
            val reach = 2.0 * max(bSpan, 1.0)
            yMin = min(bMin - 0.45 * bSpan, max(min(e1, e2), bMin - reach))
            yMax = max(bMax + 0.45 * bSpan, min(max(e1, e2), bMax + reach))
        }
        if (yMax - yMin < 1e-9) { yMin -= 1; yMax += 1 }
        val pad = (yMax - yMin) * 0.08
        yMin -= pad; yMax += pad
        val series = sampleSeries(polyText(c).let { "y = $it" }, { hornerR(c, it) }, lo, hi, yMin, yMax)
        val markers = ArrayList<GraphMarker>()
        for (r in realRoots) markers += GraphMarker(r, 0.0, Fmt.shown(r, 4), 0)
        for (x in crit) markers += GraphMarker(x, hornerR(c, x), "", 1)
        return GraphData(lo, hi, yMin, yMax, listOf(series), markers, traceable = true)
    }

    private fun sampleSeries(
        label: String, fn: (Double) -> Double,
        xMin: Double, xMax: Double, yMin: Double, yMax: Double,
        n: Int = 360, colorIndex: Int = 0
    ): GraphSeries {
        val xs = ArrayList<Double>(n + 8)
        val ys = ArrayList<Double>(n + 8)
        var prev = Double.NaN
        val span = yMax - yMin
        for (i in 0..n) {
            val x = xMin + (xMax - xMin) * i / n
            val y = fn(x)
            if (!y.isFinite()) {
                if (!prev.isNaN()) { xs += x; ys += Double.NaN }
                prev = Double.NaN
                continue
            }
            if (!prev.isNaN() && sign(prev) != sign(y) && abs(y - prev) > 4 * span) { xs += x; ys += Double.NaN }
            xs += x; ys += y; prev = y
        }
        return GraphSeries(label, xs.toDoubleArray(), ys.toDoubleArray(), colorIndex)
    }

    /* ───────────── Linear systems ───────────── */

    private val VARS = listOf("x", "y", "z")

    private fun termsText(co: List<Double>, names: List<String>): String {
        val sb = StringBuilder()
        for (i in co.indices) {
            val v = co[i]
            if (v == 0.0) continue
            val neg = v < 0
            val mag = abs(v)
            if (sb.isEmpty()) { if (neg) sb.append("−") } else sb.append(if (neg) " − " else " + ")
            if (mag != 1.0) sb.append(Fmt.num(mag))
            sb.append(names[i])
        }
        return if (sb.isEmpty()) "0" else sb.toString()
    }

    private fun matrixText(a: Array<DoubleArray>, n: Int): String {
        val cells = a.map { row -> row.map { Fmt.shown(if (abs(it) < 1e-12) 0.0 else it, 5) } }
        val w = cells.maxOf { r -> r.maxOf { it.length } }
        return cells.joinToString("\n") { r ->
            "[ " + r.take(n).joinToString("  ") { it.padStart(w) } + "  |  " + r[n].padStart(w) + " ]"
        }
    }

    private fun solveSystem(p: Problem): SolveReport {
        val n = p.size
        val rows = Slots.system(n)
        for (r in rows) for (k in r) {
            if (p.inputs[k].isNullOrBlank()) {
                return SolveReport(Outcome.Incomplete("Fill every field. Use 0 where a variable is missing."))
            }
        }
        val m = Array(n) { DoubleArray(n + 1) }
        for ((ri, r) in rows.withIndex()) for ((ci, k) in r.withIndex()) {
            when (val v = Calc.evalNumber(p.inputs[k].orEmpty())) {
                is Parsed.Ok -> m[ri][ci] = v.v
                is Parsed.Err -> return SolveReport(Outcome.Invalid(k, "Row ${ri + 1}: ${v.msg}"))
            }
        }
        val names = VARS.take(n)
        val eqLines = m.map { row -> termsText(row.take(n), names) + " = " + f(row[n]) }
        val equation = eqLines.joinToString("\n")
        val steps = ArrayList<Step>()
        steps += Step("System", eqLines.mapIndexed { i, s -> "(${i + 1})  $s" }.joinToString("\n"))
        steps += Step("Augmented matrix", matrixText(m, n))

        val det = if (n == 2) m[0][0] * m[1][1] - m[1][0] * m[0][1] else
            m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) -
                m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) +
                m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])
        val big = max(1.0, m.maxOf { r -> r.take(n).maxOf { abs(it) } })
        val detZero = abs(det) <= 1e-10 * big.pow(n)
        steps += Step(
            "Determinant",
            "det = ${f(if (detZero) 0.0 else det)}\n" + if (detZero) "= 0 → no unique solution" else "≠ 0 → exactly one solution"
        )

        val a = Array(n) { m[it].copyOf() }
        val tol = 1e-10 * max(1.0, m.maxOf { r -> r.maxOf { abs(it) } })
        var pr = 0
        val pivotCols = ArrayList<Int>()
        for (col in 0 until n) {
            if (pr >= n) break
            var best = pr
            for (r in pr until n) if (abs(a[r][col]) > abs(a[best][col])) best = r
            if (abs(a[best][col]) <= tol) continue
            val lines = ArrayList<String>()
            if (best != pr) { val t = a[best]; a[best] = a[pr]; a[pr] = t; lines += "R${pr + 1} ↔ R${best + 1}" }
            val pv = a[pr][col]
            if (abs(pv - 1.0) > 1e-12) {
                for (j in 0..n) a[pr][j] /= pv
                lines += "R${pr + 1} ← R${pr + 1} ÷ ${f(pv)}"
            }
            for (r in 0 until n) {
                if (r == pr) continue
                val fac = a[r][col]
                if (abs(fac) > 1e-14) {
                    for (j in 0..n) a[r][j] -= fac * a[pr][j]
                    a[r][col] = 0.0
                    lines += "R${r + 1} ← R${r + 1} − (${f(fac)})·R${pr + 1}"
                }
            }
            for (r in 0 until n) for (j in 0..n) if (abs(a[r][j]) < 1e-12) a[r][j] = 0.0
            steps += Step("Pivot on ${names[col]}", lines.joinToString("\n").let { if (it.isEmpty()) "" else "$it\n" } + matrixText(a, n))
            pivotCols += col
            pr++
        }
        val rank = pivotCols.size
        for (r in rank until n) {
            if (abs(a[r][n]) > tol) {
                val detail = if (n == 2) "The lines are parallel — they never meet." else "The planes have no point in common."
                steps += Step("Contradiction", "Row ${r + 1} reads 0 = ${f(a[r][n])}, which is impossible.")
                return SolveReport(
                    Outcome.NoSolution("No solution", detail), equation, "No solution",
                    equation, steps, if (n == 2) lineGraph(m, null) else null
                )
            }
        }
        if (rank < n) {
            val free = (0 until n).filter { it !in pivotCols }
            val pname = listOf("t", "s")
            val lines = ArrayList<String>()
            for ((i, pc) in pivotCols.withIndex()) {
                val parts = ArrayList<String>()
                if (abs(a[i][n]) > 1e-12) parts += f(a[i][n])
                for ((fi, fc) in free.withIndex()) {
                    val co = -a[i][fc]
                    if (abs(co) < 1e-12) continue
                    val mag = abs(co)
                    val term = (if (mag == 1.0) "" else Fmt.shown(mag)) + pname[fi]
                    parts += (if (co < 0) "− " else "+ ") + term
                }
                val txt = if (parts.isEmpty()) "0" else parts.joinToString(" ").let {
                    if (it.startsWith("+ ")) it.drop(2) else if (it.startsWith("− ")) "−" + it.drop(2) else it
                }
                lines += "${names[pc]} = $txt"
            }
            for ((fi, fc) in free.withIndex()) lines += "${names[fc]} = ${pname[fi]}  (any real number)"
            val ordered = lines.sortedBy { l -> names.indexOfFirst { l.startsWith(it + " =") } }
            val kind = if (free.size == 1) "a line of solutions" else "a plane of solutions"
            steps += Step("Free variables", "Rank $rank of $n → ${free.size} free parameter${if (free.size > 1) "s" else ""}.\nThe solution set is $kind.")
            return SolveReport(
                Outcome.Infinite("Infinitely many solutions", ordered), equation, "Infinitely many solutions",
                ordered.joinToString("\n"), steps, if (n == 2) lineGraph(m, null) else null
            )
        }
        val sol = DoubleArray(n) { a[it][n] }
        val check = m.mapIndexed { i, row ->
            val lhs = (0 until n).sumOf { row[it] * sol[it] }
            "(${i + 1})  ${f(lhs)} = ${f(row[n])} ${if (abs(lhs - row[n]) <= 1e-9 * max(1.0, abs(row[n]))) "✓" else "≈"}"
        }
        steps += Step("Check", "Substitute back:\n" + check.joinToString("\n"))
        val vals = sol.mapIndexed { i, v -> ValueRow(names[i], f(v)) }
        val summary = vals.joinToString(", ") { "${it.label} = ${it.text}" }
        val copy = sol.mapIndexed { i, v -> "${names[i]} = ${Fmt.num(v, 12)}" }.joinToString("\n")
        return SolveReport(
            Outcome.Values("Unique solution", vals), equation, summary, copy, steps,
            if (n == 2) lineGraph(m, Pair(sol[0], sol[1])) else null
        )
    }

    private fun lineGraph(m: Array<DoubleArray>, sol: Pair<Double, Double>?): GraphData? {
        for (r in 0 until 2) if (m[r][0] == 0.0 && m[r][1] == 0.0) return null
        val anchors = (0 until 2).map { r ->
            val d = m[r][0] * m[r][0] + m[r][1] * m[r][1]
            Pair(m[r][0] * m[r][2] / d, m[r][1] * m[r][2] / d)
        }
        val cx = sol?.first ?: ((anchors[0].first + anchors[1].first) / 2)
        val cy = sol?.second ?: ((anchors[0].second + anchors[1].second) / 2)
        val sep = hypot(anchors[0].first - anchors[1].first, anchors[0].second - anchors[1].second)
        val h = if (sol != null) 3.0 else max(3.0, sep * 0.9)
        val xMin = cx - h * 1.6; val xMax = cx + h * 1.6
        val yMin = cy - h; val yMax = cy + h
        val series = (0 until 2).map { r ->
            val a = m[r][0]; val b = m[r][1]; val c = m[r][2]
            val label = termsText(listOf(a, b), listOf("x", "y")) + " = " + f(c)
            if (abs(b) > 1e-12) {
                GraphSeries(label, doubleArrayOf(xMin, xMax), doubleArrayOf((c - a * xMin) / b, (c - a * xMax) / b), r)
            } else {
                val x0 = c / a
                GraphSeries(label, doubleArrayOf(x0, x0), doubleArrayOf(yMin, yMax), r)
            }
        }
        val markers = if (sol != null) listOf(GraphMarker(sol.first, sol.second, "(${Fmt.shown(sol.first, 4)}, ${Fmt.shown(sol.second, 4)})")) else emptyList()
        return GraphData(xMin, xMax, yMin, yMax, series, markers, traceable = false)
    }

    /* ───────────── Custom f(x) = g(x) ───────────── */

    private fun solveCustom(p: Problem, cancelled: () -> Boolean): SolveReport {
        val lhs = p.inputs["lhs"].orEmpty().trim()
        val rhs = p.inputs["rhs"].orEmpty().trim().ifEmpty { "0" }
        if (lhs.isEmpty()) return SolveReport(Outcome.Incomplete("Type an expression in x, like x² − 4 or sin(x)."))
        try { Calc.build(lhs, "x") } catch (e: Exception) { return SolveReport(Outcome.Invalid("lhs", "Left side isn't a valid expression")) }
        try { Calc.build(rhs, "x") } catch (e: Exception) { return SolveReport(Outcome.Invalid("rhs", "Right side isn't a valid expression")) }
        val expr = Calc.build("($lhs)-($rhs)", "x")
        val fn: (Double) -> Double = { x ->
            expr.setVariable("x", x)
            try { expr.evaluate() } catch (e: Exception) { Double.NaN }
        }
        val equation = "$lhs = $rhs".replace('-', '−').replace("*", "×").replace("/", "÷").replace("sqrt", "√")

        val probes = listOf(-3.7, 0.3, 1.9, 7.1).map(fn)
        if (probes.all { it.isFinite() } && probes.all { abs(it - probes[0]) < 1e-12 }) {
            return if (abs(probes[0]) < 1e-12) {
                SolveReport(Outcome.Infinite("Every x is a solution", listOf("Both sides are always equal")), equation, "All x")
            } else {
                SolveReport(Outcome.NoSolution("No solution", "Both sides differ by a constant, ${f(probes[0])}"), equation, "No solution")
            }
        }

        val radii = p.searchRadius?.let { listOf(it) } ?: listOf(10.0, 100.0, 1000.0)
        var used = radii.last()
        var roots: List<Double> = emptyList()
        var steps = 0
        for (r in radii) {
            used = r
            steps = 20000
            roots = findRoots(fn, -r, r, steps, cancelled)
            if (roots.isNotEmpty() || cancelled()) break
        }
        val capped = roots.size > 40
        val shown = roots.take(40)
        val rows = shown.mapIndexed { i, r -> RootRow(if (shown.size == 1) "x" else "x" + Fmt.sub(i + 1), f(r)) }
        val range = "−${Fmt.num(used)} … ${Fmt.num(used)}"
        val h = 2 * used / steps
        val stepsList = ArrayList<Step>()
        stepsList += Step("Rewrite as f(x) = 0", "f(x) = ($lhs) − ($rhs)".replace("*", "×").replace("/", "÷").replace('-', '−'))
        stepsList += Step("Scan for sign changes", "Sampled f at $steps points over [$range] (spacing ${Fmt.num(h, 3)}).")
        stepsList += Step("Refine", "Each sign change is narrowed by bisection to about 1e-13. Poles (like tan x) are rejected, and tangent roots where f only touches zero are caught by minimising |f|.")
        if (shown.isNotEmpty()) {
            stepsList += Step("Check", shown.take(6).joinToString("\n") { "f(${f(it)}) = ${Fmt.shown(fn(it), 3)}" })
        }
        val graph = customGraph(fn, shown, used)
        if (shown.isEmpty()) {
            return SolveReport(
                Outcome.NoSolution("No real solution in $range", "Try widening the search range, or the equation may have only complex solutions."),
                equation, "No real solution", "", stepsList, graph
            )
        }
        val headline = if (shown.size == 1) "1 solution in $range" else "${shown.size}${if (capped) "+" else ""} solutions in $range"
        val note = if (capped) "Showing the first 40 solutions." else "Solutions where the curve only grazes zero are included; roots closer than ${Fmt.num(h, 2)} apart can be missed."
        val summary = rows.joinToString(", ") { "${it.label} = ${it.text}" }
        val copy = equation.replace('−', '-') + "\n" + shown.mapIndexed { i, r -> "x${i + 1} = ${Fmt.num(r, 12)}" }.joinToString("\n")
        return SolveReport(Outcome.Roots(headline, rows, null, null, note), equation, summary, copy, stepsList, graph)
    }

    private fun findRoots(fn: (Double) -> Double, lo: Double, hi: Double, n: Int, cancelled: () -> Boolean): List<Double> {
        val h = (hi - lo) / n
        val xs = DoubleArray(n + 1) { lo + it * h }
        val ys = DoubleArray(n + 1)
        for (i in 0..n) {
            ys[i] = fn(xs[i])
            if (i % 2000 == 0 && cancelled()) return emptyList()
        }
        val found = ArrayList<Double>()
        fun add(x: Double) {
            if (found.none { abs(it - x) < 1e-7 * max(1.0, abs(x)) }) found += x
        }
        for (i in 0 until n) {
            val y0 = ys[i]; val y1 = ys[i + 1]
            if (y0.isNaN() || y1.isNaN()) continue
            if (y0 == 0.0) {
                val plateau = (i > 0 && ys[i - 1] == 0.0) || y1 == 0.0
                if (!plateau) add(xs[i])
                continue
            }
            if (y0 * y1 < 0) {
                var a = xs[i]; var b = xs[i + 1]; var fa = y0
                repeat(100) {
                    val mid = 0.5 * (a + b)
                    val fm = fn(mid)
                    if (fm.isNaN()) return@repeat
                    if (fm == 0.0) { a = mid; b = mid; return@repeat }
                    if (fa * fm < 0) b = mid else { a = mid; fa = fm }
                }
                val r = 0.5 * (a + b)
                if (abs(fn(r)) <= 1e-6 * (1 + min(abs(y0), abs(y1)))) add(r)
            }
        }
        for (i in 1 until n) {
            val ym = ys[i - 1]; val y = ys[i]; val yp = ys[i + 1]
            if (ym.isNaN() || y.isNaN() || yp.isNaN() || y == 0.0) continue
            if (ym * y <= 0 || y * yp <= 0) continue
            val ay = abs(y)
            if (ay <= abs(ym) && ay <= abs(yp) && !(ym == 0.0 || yp == 0.0)) {
                var a = xs[i - 1]; var b = xs[i + 1]
                repeat(100) {
                    val m1 = a + (b - a) / 3; val m2 = b - (b - a) / 3
                    if (abs(fn(m1)) < abs(fn(m2))) b = m2 else a = m1
                }
                val xm = 0.5 * (a + b)
                if (abs(fn(xm)) <= 1e-10) add(xm)
            }
        }
        return found.sorted()
    }

    private fun customGraph(fn: (Double) -> Double, roots: List<Double>, searched: Double): GraphData? {
        var lo: Double; var hi: Double
        when {
            roots.size >= 2 -> {
                val span = roots.last() - roots.first()
                val pad = max(span * 0.25, 1.0)
                lo = roots.first() - pad; hi = roots.last() + pad
            }
            roots.size == 1 -> { lo = roots[0] - 5; hi = roots[0] + 5 }
            else -> { lo = -min(searched, 10.0); hi = min(searched, 10.0) }
        }
        val sample = (0..400).map { fn(lo + (hi - lo) * it / 400) }.filter { it.isFinite() }.sorted()
        if (sample.size < 10) return null
        var yLo = min(sample[(sample.size * 0.05).toInt()], 0.0)
        var yHi = max(sample[(sample.size * 0.95).toInt().coerceAtMost(sample.size - 1)], 0.0)
        if (yHi - yLo < 1e-9) { yLo -= 1; yHi += 1 }
        val pad = (yHi - yLo) * 0.15
        yLo -= pad; yHi += pad
        val series = sampleSeries("f(x)", fn, lo, hi, yLo, yHi)
        return GraphData(lo, hi, yLo, yHi, listOf(series), roots.map { GraphMarker(it, 0.0, Fmt.shown(it, 4)) }, traceable = true)
    }
}

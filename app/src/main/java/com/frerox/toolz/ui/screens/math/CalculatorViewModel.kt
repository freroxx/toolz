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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.math.MathHistory
import com.frerox.toolz.data.math.MathHistoryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.objecthunter.exp4j.ExpressionBuilder
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import java.util.Random
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ═══════════════════════════════════════════════════════════════════════════════
// PUBLIC MODEL
// ═══════════════════════════════════════════════════════════════════════════════

enum class CalcMode(val label: String, val glyph: String, val description: String) {
    COMP("Calculate", "123", "Scientific calculator"),
    SOLVE("Solve", "x=?", "Solve any equation"),
    EQN("Equation", "ax²", "Polynomials & systems"),
    STAT("Statistics", "Σx", "1-var & regression"),
    TABLE("Table", "f(x)", "Value tables"),
    BASE("Base-N", "0x", "BIN · OCT · DEC · HEX"),
    CONVERT("Convert", "⇄", "Units"),
}

enum class AngleUnit(val label: String) { DEG("DEG"), RAD("RAD"), GRA("GRA") }

enum class FormatKind(val label: String) { NORM("Norm"), FIX("Fix"), SCI("Sci") }

enum class KeyLayer { NONE, SHIFT, ALPHA }

enum class KeyKind { DIGIT, OPERATOR, EQUALS, CLEAR, DELETE, FUNCTION, MODIFIER, NAV }

enum class CalcSheet { NONE, SETUP, CATALOG, CONST, STORE, RECALL }

enum class EqnType(val label: String, val fieldLabels: List<String>) {
    QUAD("ax² + bx + c", listOf("a", "b", "c")),
    CUBIC("ax³ + bx² + cx + d", listOf("a", "b", "c", "d")),
    LIN2("2 unknowns", listOf("a₁", "b₁", "c₁", "a₂", "b₂", "c₂")),
    LIN3("3 unknowns", listOf("a₁", "b₁", "c₁", "d₁", "a₂", "b₂", "c₂", "d₂", "a₃", "b₃", "c₃", "d₃")),
}

enum class StatType(val label: String) {
    ONE_VAR("1-Var"),
    LINEAR("A + BX"),
    QUAD("A + BX + CX²"),
    LOG("A + B·ln X"),
    EXP("A·e^(BX)"),
    AB("A·B^X"),
    POWER("A·X^B"),
    INV("A + B/X");

    val twoVar: Boolean get() = this != ONE_VAR
}

data class EditField(val text: String = "", val cursor: Int = 0)

data class OutLine(
    val label: String,
    val text: String,
    val num: String? = null,
    val den: String? = null,
    val negative: Boolean = false,
    val header: Boolean = false,
)

data class ResultDisplay(
    val plain: String,
    val num: String? = null,
    val den: String? = null,
    val negative: Boolean = false,
)

data class KeyDef(
    val id: String,
    val label: String,
    val kind: KeyKind,
    val action: String,
    val shiftLabel: String? = null,
    val shiftAction: String? = null,
    val alphaLabel: String? = null,
    val alphaAction: String? = null,
    val repeat: Boolean = false,
    val wide: Float = 1f,
)

data class CalculatorState(
    val mode: CalcMode = CalcMode.COMP,
    val scientific: Boolean = false,
    val display: String = "0",
    val formula: String = "",
    val liveResult: String? = null,
    val error: String? = null,
    val layer: KeyLayer = KeyLayer.NONE,
    val hyp: Boolean = false,
    val angle: AngleUnit = AngleUnit.DEG,
    val fmtKind: FormatKind = FormatKind.NORM,
    val fmtDigits: Int = 4,
    val showFraction: Boolean = true,
    val fields: List<EditField> = listOf(EditField()),
    val focus: Int = 0,
    val evaluated: Boolean = false,
    val note: String? = null,
    val outputs: List<OutLine> = emptyList(),
    val tableHeader: List<String> = emptyList(),
    val tableRows: List<List<String>> = emptyList(),
    val eqnType: EqnType = EqnType.QUAD,
    val statType: StatType = StatType.ONE_VAR,
    val baseRadix: Int = 10,
    val convCategory: Int = 0,
    val convFrom: Int = 0,
    val convTo: Int = 1,
    val variables: Map<String, String> = emptyMap(),
    val history: List<Pair<String, String>> = emptyList(),
    val sheet: CalcSheet = CalcSheet.NONE,
    val toast: String? = null,
) {
    val activeField: EditField get() = fields.getOrElse(focus) { EditField() }
}

// ═══════════════════════════════════════════════════════════════════════════════
// KEY TABLES  (single source of truth for labels AND actions)
//   action codes:  i:<text> insert   t:<fn> trig   ti:<fn> inverse trig   c:<cmd> command
// ═══════════════════════════════════════════════════════════════════════════════

object CalcKeys {
    private fun k(
        id: String,
        label: String,
        kind: KeyKind,
        action: String,
        shift: Pair<String, String>? = null,
        alpha: Pair<String, String>? = null,
        repeat: Boolean = false,
        wide: Float = 1f,
    ) = KeyDef(id, label, kind, action, shift?.first, shift?.second, alpha?.first, alpha?.second, repeat, wide)

    private fun dg(label: String, shift: Pair<String, String>? = null, wide: Float = 1f) =
        k("d$label", label, KeyKind.DIGIT, "i:$label", shift, wide = wide)

    private fun op(label: String, shift: Pair<String, String>? = null) =
        k("o$label", label, KeyKind.OPERATOR, "i:$label", shift)

    private val left = k("left", "◀", KeyKind.NAV, "c:left", repeat = true)
    private val right = k("right", "▶", KeyKind.NAV, "c:right", repeat = true)
    private val lp = k("lp", "(", KeyKind.FUNCTION, "i:(", alpha = "X" to "i:X")
    private val rp = k("rp", ")", KeyKind.FUNCTION, "i:)", alpha = "Y" to "i:Y")
    private val cat = k("cat", "CAT", KeyKind.FUNCTION, "c:cat", shift = "CONST" to "c:const")
    private val del = k("del", "DEL", KeyKind.DELETE, "c:del", repeat = true)
    private val ac = k("ac", "AC", KeyKind.CLEAR, "c:ac")
    private val eq = k("eq", "=", KeyKind.EQUALS, "c:equals", shift = "=" to "i:=")

    /** Casio-style function block, 6 columns. */
    val fullFunctions: List<List<KeyDef>> = listOf(
        listOf(
            k("shift", "SHIFT", KeyKind.MODIFIER, "c:shift"),
            k("alpha", "ALPHA", KeyKind.MODIFIER, "c:alpha"),
            left, right, cat,
            k("sd", "S⇔D", KeyKind.FUNCTION, "c:sd"),
        ),
        listOf(
            k("inv", "x⁻¹", KeyKind.FUNCTION, "i:⁻¹", shift = "x!" to "i:!"),
            k("sq", "x²", KeyKind.FUNCTION, "i:²", shift = "x³" to "i:³"),
            k("pow", "xⁿ", KeyKind.FUNCTION, "i:^", shift = "ˣ√" to "i:root("),
            k("sqrt", "√", KeyKind.FUNCTION, "i:√(", shift = "∛" to "i:∛("),
            k("log", "log", KeyKind.FUNCTION, "i:log(", shift = "10ˣ" to "i:10^("),
            k("ln", "ln", KeyKind.FUNCTION, "i:ln(", shift = "eˣ" to "i:e^("),
        ),
        listOf(
            k("neg", "(−)", KeyKind.FUNCTION, "c:neg", alpha = "A" to "i:A"),
            k("pi", "π", KeyKind.FUNCTION, "i:π", shift = "e" to "i:e", alpha = "B" to "i:B"),
            k("hyp", "hyp", KeyKind.MODIFIER, "c:hyp", shift = "abs" to "i:abs(", alpha = "C" to "i:C"),
            k("sin", "sin", KeyKind.FUNCTION, "t:sin", shift = "sin⁻¹" to "ti:sin", alpha = "D" to "i:D"),
            k("cos", "cos", KeyKind.FUNCTION, "t:cos", shift = "cos⁻¹" to "ti:cos", alpha = "E" to "i:E"),
            k("tan", "tan", KeyKind.FUNCTION, "t:tan", shift = "tan⁻¹" to "ti:tan", alpha = "F" to "i:F"),
        ),
        listOf(
            k("fact", "x!", KeyKind.FUNCTION, "i:!", shift = "STO" to "c:sto"),
            lp, rp,
            k("comma", ",", KeyKind.FUNCTION, "i:,"),
            k("abs", "abs(", KeyKind.FUNCTION, "i:abs(", shift = "RCL" to "c:rcl"),
            k("pct", "%", KeyKind.FUNCTION, "i:%"),
        ),
    )

    /** Digit block for the scientific pad, 5 columns. */
    val numbers: List<List<KeyDef>> = listOf(
        listOf(
            dg("7", "CONST" to "c:const"), dg("8", "SETUP" to "c:setup"), dg("9", "MCLR" to "c:clr"), del, ac,
        ),
        listOf(
            dg("4"), dg("5"), dg("6"),
            op("×", "nPr" to "i:nPr"), op("÷", "nCr" to "i:nCr"),
        ),
        listOf(
            dg("1"), dg("2"), dg("3"),
            op("+", "Pol" to "i:Pol("), op("−", "Rec" to "i:Rec("),
        ),
        listOf(
            dg("0", "Rnd" to "i:Rnd("),
            dg(".", "Ran#" to "i:Ran#"),
            k("exp", "×10ˣ", KeyKind.FUNCTION, "i:ᴇ", shift = "RanInt" to "i:RanInt("),
            k("ans", "Ans", KeyKind.FUNCTION, "i:Ans", shift = "DRG▶" to "c:drg"),
            eq,
        ),
    )

    /** Large 4-column pad for quick sums. */
    val basic: List<List<KeyDef>> = listOf(
        listOf(
            k("b_ac", "AC", KeyKind.CLEAR, "c:ac"),
            k("b_del", "DEL", KeyKind.DELETE, "c:del", repeat = true),
            k("b_pct", "%", KeyKind.OPERATOR, "i:%"),
            k("b_div", "÷", KeyKind.OPERATOR, "i:÷"),
        ),
        listOf(dg("7"), dg("8"), dg("9"), k("b_mul", "×", KeyKind.OPERATOR, "i:×")),
        listOf(dg("4"), dg("5"), dg("6"), k("b_sub", "−", KeyKind.OPERATOR, "i:−")),
        listOf(dg("1"), dg("2"), dg("3"), k("b_add", "+", KeyKind.OPERATOR, "i:+")),
        listOf(
            k("b_paren", "( )", KeyKind.FUNCTION, "c:paren"),
            dg("0"), dg("."),
            k("b_eq", "=", KeyKind.EQUALS, "c:equals"),
        ),
    )
}

class CatalogItem(val label: String, val insert: String, val hint: String = "")

object CalcCatalog {
    val sections: List<Pair<String, List<CatalogItem>>> = listOf(
        "Rounding & parts" to listOf(
            CatalogItem("abs", "abs(", "absolute value"),
            CatalogItem("int", "int(", "integer part"),
            CatalogItem("frac", "frac(", "fractional part"),
            CatalogItem("floor", "floor("),
            CatalogItem("ceil", "ceil("),
            CatalogItem("round", "round(", "round(x, digits)"),
            CatalogItem("sign", "sign("),
        ),
        "Number theory" to listOf(
            CatalogItem("gcd", "gcd(", "gcd(a, b)"),
            CatalogItem("lcm", "lcm(", "lcm(a, b)"),
            CatalogItem("mod", "mod(", "mod(a, b)"),
            CatalogItem("min", "min(", "min(a, b, …)"),
            CatalogItem("max", "max(", "max(a, b, …)"),
        ),
        "Powers & logs" to listOf(
            CatalogItem("ˣ√", "root(", "root(n, x) = n-th root"),
            CatalogItem("logₐb", "log(", "log(base, x)"),
            CatalogItem("10ˣ", "10^("),
            CatalogItem("eˣ", "e^("),
            CatalogItem("x!", "!"),
            CatalogItem("%", "%"),
        ),
        "Calculus" to listOf(
            CatalogItem("d/dx", "d/dx(", "d/dx(f(X), point)"),
            CatalogItem("∫", "∫(", "∫(f(X), from, to)"),
            CatalogItem("Σ", "Σ(", "Σ(f(X), from, to)"),
            CatalogItem("Π", "Π(", "Π(f(X), from, to)"),
        ),
        "Combinatorics & random" to listOf(
            CatalogItem("nPr", "nPr"),
            CatalogItem("nCr", "nCr"),
            CatalogItem("Ran#", "Ran#", "random 0–0.999"),
            CatalogItem("RanInt", "RanInt(", "RanInt(a, b)"),
            CatalogItem("Rnd", "Rnd(", "round to 10 digits"),
        ),
        "Coordinates" to listOf(
            CatalogItem("Pol", "Pol(", "Pol(x, y) → r, stores θ in Y"),
            CatalogItem("Rec", "Rec(", "Rec(r, θ) → x, stores y in Y"),
        ),
        "Hyperbolic" to listOf(
            CatalogItem("sinh", "sinh("),
            CatalogItem("cosh", "cosh("),
            CatalogItem("tanh", "tanh("),
            CatalogItem("sinh⁻¹", "sinh⁻¹("),
            CatalogItem("cosh⁻¹", "cosh⁻¹("),
            CatalogItem("tanh⁻¹", "tanh⁻¹("),
        ),
        "Symbols" to listOf(
            CatalogItem(",", ","),
            CatalogItem("X", "X"),
            CatalogItem("Y", "Y"),
            CatalogItem("M", "M"),
            CatalogItem("e", "e"),
        ),
    )
}

class ConstantDef(val symbol: String, val name: String, val value: String, val unit: String)

object CalcConstants {
    val all: List<ConstantDef> = listOf(
        ConstantDef("π", "Pi", "3.14159265358979", ""),
        ConstantDef("φ", "Golden ratio", "1.61803398874989", ""),
        ConstantDef("c₀", "Speed of light", "299792458", "m/s"),
        ConstantDef("h", "Planck constant", "6.62607015ᴇ−34", "J·s"),
        ConstantDef("ħ", "Reduced Planck constant", "1.054571817ᴇ−34", "J·s"),
        ConstantDef("G", "Gravitational constant", "6.6743ᴇ−11", "m³/(kg·s²)"),
        ConstantDef("g", "Standard gravity", "9.80665", "m/s²"),
        ConstantDef("e", "Elementary charge", "1.602176634ᴇ−19", "C"),
        ConstantDef("Nₐ", "Avogadro constant", "6.02214076ᴇ23", "1/mol"),
        ConstantDef("k", "Boltzmann constant", "1.380649ᴇ−23", "J/K"),
        ConstantDef("R", "Molar gas constant", "8.314462618", "J/(mol·K)"),
        ConstantDef("Vₘ", "Molar volume (273.15 K, 101.325 kPa)", "2.241396954ᴇ−2", "m³/mol"),
        ConstantDef("F", "Faraday constant", "96485.33212", "C/mol"),
        ConstantDef("σ", "Stefan–Boltzmann", "5.670374419ᴇ−8", "W/(m²·K⁴)"),
        ConstantDef("ε₀", "Electric constant", "8.8541878128ᴇ−12", "F/m"),
        ConstantDef("μ₀", "Magnetic constant", "1.25663706212ᴇ−6", "N/A²"),
        ConstantDef("Z₀", "Impedance of vacuum", "376.730313668", "Ω"),
        ConstantDef("Φ₀", "Magnetic flux quantum", "2.067833848ᴇ−15", "Wb"),
        ConstantDef("G₀", "Conductance quantum", "7.748091729ᴇ−5", "S"),
        ConstantDef("α", "Fine-structure constant", "7.2973525693ᴇ−3", ""),
        ConstantDef("R∞", "Rydberg constant", "10973731.56816", "1/m"),
        ConstantDef("a₀", "Bohr radius", "5.29177210903ᴇ−11", "m"),
        ConstantDef("rₑ", "Classical electron radius", "2.8179403262ᴇ−15", "m"),
        ConstantDef("λc", "Compton wavelength", "2.42631023867ᴇ−12", "m"),
        ConstantDef("λcp", "Proton Compton wavelength", "1.32140985539ᴇ−15", "m"),
        ConstantDef("λcn", "Neutron Compton wavelength", "1.31959090581ᴇ−15", "m"),
        ConstantDef("mₚ", "Proton mass", "1.67262192369ᴇ−27", "kg"),
        ConstantDef("mₙ", "Neutron mass", "1.67492749804ᴇ−27", "kg"),
        ConstantDef("mₑ", "Electron mass", "9.1093837015ᴇ−31", "kg"),
        ConstantDef("mμ", "Muon mass", "1.883531627ᴇ−28", "kg"),
        ConstantDef("u", "Atomic mass unit", "1.6605390666ᴇ−27", "kg"),
        ConstantDef("μB", "Bohr magneton", "9.2740100783ᴇ−24", "J/T"),
        ConstantDef("μN", "Nuclear magneton", "5.0507837461ᴇ−27", "J/T"),
        ConstantDef("μₚ", "Proton magnetic moment", "1.41060679736ᴇ−26", "J/T"),
        ConstantDef("μₑ", "Electron magnetic moment", "−9.2847647043ᴇ−24", "J/T"),
        ConstantDef("μₙ", "Neutron magnetic moment", "−9.6623651ᴇ−27", "J/T"),
        ConstantDef("μμ", "Muon magnetic moment", "−4.4904483ᴇ−26", "J/T"),
        ConstantDef("γₚ", "Proton gyromagnetic ratio", "2.6752218744ᴇ8", "1/(s·T)"),
        ConstantDef("c₁", "First radiation constant", "3.741771852ᴇ−16", "W·m²"),
        ConstantDef("c₂", "Second radiation constant", "1.438776877ᴇ−2", "m·K"),
        ConstantDef("atm", "Standard atmosphere", "101325", "Pa"),
        ConstantDef("t", "Celsius zero", "273.15", "K"),
    )
}

class UnitDef(val symbol: String, val factor: Double)

class UnitCategory(val name: String, val units: List<UnitDef>, val temperature: Boolean = false)

object CalcUnits {
    val categories: List<UnitCategory> = listOf(
        UnitCategory(
            "Length",
            listOf(
                UnitDef("mm", 1e-3), UnitDef("cm", 1e-2), UnitDef("m", 1.0), UnitDef("km", 1e3),
                UnitDef("in", 0.0254), UnitDef("ft", 0.3048), UnitDef("yd", 0.9144),
                UnitDef("mi", 1609.344), UnitDef("nmi", 1852.0),
            ),
        ),
        UnitCategory(
            "Mass",
            listOf(
                UnitDef("mg", 1e-6), UnitDef("g", 1e-3), UnitDef("kg", 1.0), UnitDef("t", 1e3),
                UnitDef("oz", 0.028349523125), UnitDef("lb", 0.45359237), UnitDef("st", 6.35029318),
            ),
        ),
        UnitCategory(
            "Temperature",
            listOf(UnitDef("°C", 1.0), UnitDef("°F", 1.0), UnitDef("K", 1.0)),
            temperature = true,
        ),
        UnitCategory(
            "Area",
            listOf(
                UnitDef("cm²", 1e-4), UnitDef("m²", 1.0), UnitDef("ha", 1e4), UnitDef("km²", 1e6),
                UnitDef("in²", 6.4516e-4), UnitDef("ft²", 0.09290304), UnitDef("ac", 4046.8564224),
                UnitDef("mi²", 2589988.110336),
            ),
        ),
        UnitCategory(
            "Volume",
            listOf(
                UnitDef("mL", 1e-6), UnitDef("L", 1e-3), UnitDef("m³", 1.0),
                UnitDef("tsp", 4.92892159375e-6), UnitDef("tbsp", 1.478676478125e-5),
                UnitDef("fl oz", 2.95735295625e-5), UnitDef("cup", 2.365882365e-4),
                UnitDef("pt", 4.73176473e-4), UnitDef("qt", 9.46352946e-4), UnitDef("gal", 3.785411784e-3),
            ),
        ),
        UnitCategory(
            "Time",
            listOf(
                UnitDef("ms", 1e-3), UnitDef("s", 1.0), UnitDef("min", 60.0), UnitDef("h", 3600.0),
                UnitDef("day", 86400.0), UnitDef("week", 604800.0), UnitDef("year", 31557600.0),
            ),
        ),
        UnitCategory(
            "Speed",
            listOf(
                UnitDef("m/s", 1.0), UnitDef("km/h", 1.0 / 3.6), UnitDef("mph", 0.44704),
                UnitDef("kn", 1852.0 / 3600.0), UnitDef("ft/s", 0.3048),
            ),
        ),
        UnitCategory(
            "Pressure",
            listOf(
                UnitDef("Pa", 1.0), UnitDef("kPa", 1e3), UnitDef("bar", 1e5), UnitDef("atm", 101325.0),
                UnitDef("psi", 6894.757293168), UnitDef("mmHg", 133.322387415),
            ),
        ),
        UnitCategory(
            "Energy",
            listOf(
                UnitDef("J", 1.0), UnitDef("kJ", 1e3), UnitDef("cal", 4.184), UnitDef("kcal", 4184.0),
                UnitDef("Wh", 3600.0), UnitDef("kWh", 3.6e6), UnitDef("eV", 1.602176634e-19),
                UnitDef("BTU", 1055.05585262),
            ),
        ),
        UnitCategory(
            "Power",
            listOf(UnitDef("W", 1.0), UnitDef("kW", 1e3), UnitDef("MW", 1e6), UnitDef("hp", 745.69987158227)),
        ),
        UnitCategory(
            "Force",
            listOf(UnitDef("N", 1.0), UnitDef("kN", 1e3), UnitDef("lbf", 4.4482216152605), UnitDef("kgf", 9.80665)),
        ),
        UnitCategory(
            "Data",
            listOf(
                UnitDef("B", 1.0), UnitDef("KB", 1e3), UnitDef("MB", 1e6), UnitDef("GB", 1e9), UnitDef("TB", 1e12),
                UnitDef("KiB", 1024.0), UnitDef("MiB", 1048576.0), UnitDef("GiB", 1073741824.0),
            ),
        ),
        UnitCategory(
            "Angle",
            listOf(UnitDef("deg", PI / 180.0), UnitDef("rad", 1.0), UnitDef("grad", PI / 200.0), UnitDef("turn", 2.0 * PI)),
        ),
    )

    fun convert(cat: UnitCategory, from: Int, to: Int, v: Double): Double {
        if (cat.temperature) {
            val kelvin = when (from) {
                0 -> v + 273.15
                1 -> (v - 32.0) * 5.0 / 9.0 + 273.15
                else -> v
            }
            return when (to) {
                0 -> kelvin - 273.15
                1 -> (kelvin - 273.15) * 9.0 / 5.0 + 32.0
                else -> kelvin
            }
        }
        return v * cat.units[from].factor / cat.units[to].factor
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ENGINE — exact rational arithmetic with a double fallback
// ═══════════════════════════════════════════════════════════════════════════════

internal class CalcError(message: String) : Exception(message)

internal sealed class Value {
    class Exact(val n: BigInteger, val d: BigInteger) : Value()
    class Real(val v: Double) : Value()
}

internal object Nm {
    private const val MAX_BITS = 640

    val ZERO: Value = Value.Exact(BigInteger.ZERO, BigInteger.ONE)
    val ONE: Value = Value.Exact(BigInteger.ONE, BigInteger.ONE)
    val HUNDRED: Value = Value.Exact(BigInteger.valueOf(100), BigInteger.ONE)

    fun int(n: Long): Value = Value.Exact(BigInteger.valueOf(n), BigInteger.ONE)

    fun big(n: BigInteger): Value = exact(n, BigInteger.ONE)

    fun exact(n: BigInteger, d: BigInteger): Value {
        if (d.signum() == 0) throw CalcError("Math ERROR")
        var nn = n
        var dd = d
        if (dd.signum() < 0) {
            nn = nn.negate()
            dd = dd.negate()
        }
        if (dd != BigInteger.ONE) {
            val g = nn.gcd(dd)
            if (g.signum() != 0 && g != BigInteger.ONE) {
                nn = nn.divide(g)
                dd = dd.divide(g)
            }
        }
        if (nn.bitLength() > MAX_BITS || dd.bitLength() > MAX_BITS) {
            return real(ratioToDouble(nn, dd))
        }
        return Value.Exact(nn, dd)
    }

    private fun ratioToDouble(n: BigInteger, d: BigInteger): Double =
        if (d == BigInteger.ONE) BigDecimal(n).toDouble()
        else BigDecimal(n).divide(BigDecimal(d), MathContext(30, RoundingMode.HALF_EVEN)).toDouble()

    fun real(x: Double): Value {
        if (x.isNaN() || x.isInfinite()) throw CalcError("Math ERROR")
        return Value.Real(x)
    }

    fun dbl(v: Value): Double = when (v) {
        is Value.Exact -> ratioToDouble(v.n, v.d)
        is Value.Real -> v.v
    }

    fun toBigDecimal(v: Value): BigDecimal = when (v) {
        is Value.Exact ->
            if (v.d == BigInteger.ONE) BigDecimal(v.n)
            else BigDecimal(v.n).divide(BigDecimal(v.d), MathContext(40, RoundingMode.HALF_EVEN))
        is Value.Real -> BigDecimal(v.v)
    }

    fun fromBigDecimal(bd: BigDecimal): Value {
        val unscaled = bd.unscaledValue()
        val scale = bd.scale()
        return if (scale >= 0) exact(unscaled, BigInteger.TEN.pow(scale))
        else exact(unscaled.multiply(BigInteger.TEN.pow(-scale)), BigInteger.ONE)
    }

    fun isZero(v: Value): Boolean = when (v) {
        is Value.Exact -> v.n.signum() == 0
        is Value.Real -> v.v == 0.0
    }

    fun signum(v: Value): Int = when (v) {
        is Value.Exact -> v.n.signum()
        is Value.Real -> if (v.v > 0) 1 else if (v.v < 0) -1 else 0
    }

    fun isInt(v: Value): Boolean = v is Value.Exact && v.d == BigInteger.ONE

    fun bigIntOf(v: Value): BigInteger {
        if (v is Value.Exact && v.d == BigInteger.ONE) return v.n
        throw CalcError("Math ERROR")
    }

    fun parseNumber(text: String): Value {
        var s = text
        if (s.startsWith(".")) s = "0$s"
        if (s.endsWith(".")) s += "0"
        val bd = try {
            BigDecimal(s)
        } catch (e: NumberFormatException) {
            throw CalcError("Syntax ERROR")
        }
        return fromBigDecimal(bd)
    }

    fun add(a: Value, b: Value): Value =
        if (a is Value.Exact && b is Value.Exact) exact(a.n * b.d + b.n * a.d, a.d * b.d)
        else real(dbl(a) + dbl(b))

    fun sub(a: Value, b: Value): Value =
        if (a is Value.Exact && b is Value.Exact) exact(a.n * b.d - b.n * a.d, a.d * b.d)
        else real(dbl(a) - dbl(b))

    fun mul(a: Value, b: Value): Value =
        if (a is Value.Exact && b is Value.Exact) exact(a.n * b.n, a.d * b.d)
        else real(dbl(a) * dbl(b))

    fun div(a: Value, b: Value): Value {
        if (isZero(b)) throw CalcError("Math ERROR")
        return if (a is Value.Exact && b is Value.Exact) exact(a.n * b.d, a.d * b.n)
        else real(dbl(a) / dbl(b))
    }

    fun neg(a: Value): Value = when (a) {
        is Value.Exact -> Value.Exact(a.n.negate(), a.d)
        is Value.Real -> Value.Real(-a.v)
    }

    fun abs(a: Value): Value = if (signum(a) < 0) neg(a) else a

    private fun iroot(x: BigInteger, k: Int): BigInteger? {
        if (x.signum() == 0 || x == BigInteger.ONE) return x
        if (x.bitLength() > 1000) return null
        val guess = Math.round(Math.pow(x.toDouble(), 1.0 / k))
        for (delta in -1L..1L) {
            val cand = guess + delta
            if (cand < 0) continue
            val c = BigInteger.valueOf(cand)
            if (c.pow(k) == x) return c
        }
        return null
    }

    private fun exactRoot(a: Value.Exact, k: Int): Value? {
        if (k < 2 || k > 12) return null
        val negative = a.n.signum() < 0
        if (negative && k % 2 == 0) return null
        val rn = iroot(a.n.abs(), k) ?: return null
        val rd = iroot(a.d, k) ?: return null
        return exact(if (negative) rn.negate() else rn, rd)
    }

    fun pow(a: Value, b: Value): Value {
        if (a is Value.Exact && b is Value.Exact) {
            if (b.d == BigInteger.ONE) {
                if (b.n.bitLength() < 15) {
                    val e = b.n.toInt()
                    if (e == 0) {
                        if (a.n.signum() == 0) throw CalcError("Math ERROR")
                        return ONE
                    }
                    val mag = if (e < 0) -e else e
                    val bits = max(a.n.bitLength(), a.d.bitLength()).coerceAtLeast(1).toLong() * mag
                    if (bits <= 6000L) {
                        return if (e > 0) {
                            exact(a.n.pow(mag), a.d.pow(mag))
                        } else {
                            if (a.n.signum() == 0) throw CalcError("Math ERROR")
                            exact(a.d.pow(mag), a.n.pow(mag))
                        }
                    }
                }
            } else if (b.d.bitLength() < 8 && b.n.bitLength() < 15) {
                val q = b.d.toInt()
                val p = b.n.toInt()
                val root = exactRoot(a, q)
                if (root != null) return pow(root, int(p.toLong()))
            }
        }
        return real(Math.pow(dbl(a), dbl(b)))
    }

    fun sqrt(a: Value): Value {
        if (signum(a) < 0) throw CalcError("Math ERROR")
        if (a is Value.Exact) {
            val r = exactRoot(a, 2)
            if (r != null) return r
        }
        return real(Math.sqrt(dbl(a)))
    }

    fun cbrt(a: Value): Value {
        if (a is Value.Exact) {
            val r = exactRoot(a, 3)
            if (r != null) return r
        }
        return real(Math.cbrt(dbl(a)))
    }

    /** Snap values that are an integer up to floating-point noise. */
    fun snap(x: Double, tol: Double = 1e-11): Value {
        if (x.isNaN() || x.isInfinite()) throw CalcError("Math ERROR")
        val r = Math.rint(x)
        if (r == 0.0) return if (x == 0.0) ZERO else Value.Real(x)
        return if (abs(x - r) <= tol * max(1.0, abs(x)) && abs(r) < 1e15) int(r.toLong()) else Value.Real(x)
    }

    fun roundInt(v: Value, mode: RoundingMode): Value {
        if (v is Value.Exact) {
            val qr = v.n.divideAndRemainder(v.d)
            var q = qr[0]
            if (qr[1].signum() != 0) {
                if (mode == RoundingMode.FLOOR && v.n.signum() < 0) q = q - BigInteger.ONE
                if (mode == RoundingMode.CEILING && v.n.signum() > 0) q = q + BigInteger.ONE
            }
            return big(q)
        }
        return fromBigDecimal(BigDecimal(dbl(v)).setScale(0, mode))
    }

    fun factorial(a: Value): Value {
        val n = bigIntOf(a)
        if (n.signum() < 0 || n.bitLength() > 10) throw CalcError("Math ERROR")
        val k = n.toInt()
        if (k > 170) throw CalcError("Math ERROR")
        var r = BigInteger.ONE
        for (i in 2..k) r = r * BigInteger.valueOf(i.toLong())
        return big(r)
    }

    fun perm(a: Value, b: Value): Value {
        val n = bigIntOf(a)
        val r = bigIntOf(b)
        if (n.signum() < 0 || r.signum() < 0 || r > n || n.bitLength() > 20) throw CalcError("Math ERROR")
        val nl = n.toLong()
        val rl = r.toLong()
        if (rl > 20000L) throw CalcError("Math ERROR")
        var res = BigInteger.ONE
        for (i in 0 until rl) res = res * BigInteger.valueOf(nl - i)
        return big(res)
    }

    fun comb(a: Value, b: Value): Value {
        val n = bigIntOf(a)
        val r = bigIntOf(b)
        if (n.signum() < 0 || r.signum() < 0 || r > n || n.bitLength() > 20) throw CalcError("Math ERROR")
        val nl = n.toLong()
        val kl = min(r.toLong(), nl - r.toLong())
        if (kl > 20000L) throw CalcError("Math ERROR")
        var res = BigInteger.ONE
        for (i in 0 until kl) {
            res = res * BigInteger.valueOf(nl - i) / BigInteger.valueOf(i + 1)
        }
        return big(res)
    }

    /** Gauss–Jordan on an augmented n×(n+1) matrix. Returns null when singular. */
    fun solveLinear(aug: Array<Array<Value>>): List<Value>? {
        val n = aug.size
        var scale = 0.0
        for (row in aug) for (cell in row) scale = max(scale, abs(dbl(cell)))
        if (scale == 0.0) return null
        for (col in 0 until n) {
            var piv = -1
            var best = 0.0
            for (r in col until n) {
                val m = abs(dbl(aug[r][col]))
                if (!isZero(aug[r][col]) && m > best) {
                    best = m
                    piv = r
                }
            }
            if (piv < 0 || best < 1e-12 * scale) return null
            if (piv != col) {
                val t = aug[piv]
                aug[piv] = aug[col]
                aug[col] = t
            }
            for (r in 0 until n) {
                if (r == col || isZero(aug[r][col])) continue
                val factor = div(aug[r][col], aug[col][col])
                for (c in col..n) aug[r][c] = sub(aug[r][c], mul(factor, aug[col][c]))
            }
        }
        return List(n) { div(aug[it][n], aug[it][it]) }
    }
}

// ─── Lexer ───────────────────────────────────────────────────────────────────

internal enum class TT { NUM, OP, LP, RP, COMMA, FUNC, IDENT, EQ, END }

internal class Tok(val t: TT, val s: String)

internal object Lexer {
    private val FUNC_NAMES = listOf(
        "sinh⁻¹", "cosh⁻¹", "tanh⁻¹", "sin⁻¹", "cos⁻¹", "tan⁻¹",
        "sinh", "cosh", "tanh", "sin", "cos", "tan",
        "log", "ln", "abs", "int", "frac", "floor", "ceil", "round", "sign",
        "gcd", "lcm", "mod", "min", "max", "root",
        "Rnd", "RanInt", "Pol", "Rec", "d/dx", "∫", "Σ", "Π",
    )
    private val FUNC_SET = FUNC_NAMES.toSet()
    private val IDENT_NAMES = listOf(
        "Ans", "Ran#", "nPr", "nCr", "π", "e", "A", "B", "C", "D", "E", "F", "X", "Y", "M",
    )
    private val KEYWORDS: List<String> = (FUNC_NAMES + IDENT_NAMES).sortedByDescending { it.length }

    fun tokenize(src: String): List<Tok> {
        val out = ArrayList<Tok>()
        val n = src.length
        var i = 0
        while (i < n) {
            val c = src[i]
            if (c == ' ') {
                i++
                continue
            }
            if (c in '0'..'9' || c == '.') {
                val st = i
                var dots = 0
                while (i < n && (src[i] in '0'..'9' || src[i] == '.')) {
                    if (src[i] == '.') dots++
                    i++
                }
                val txt = src.substring(st, i)
                if (dots > 1 || txt == ".") throw CalcError("Syntax ERROR")
                out.add(Tok(TT.NUM, txt))
                continue
            }
            var matched = false
            for (kw in KEYWORDS) {
                if (!src.startsWith(kw, i)) continue
                if (kw in FUNC_SET) {
                    var j = i + kw.length
                    while (j < n && src[j] == ' ') j++
                    if (j < n && src[j] == '(') {
                        out.add(Tok(TT.FUNC, kw))
                        i = j + 1
                        matched = true
                    }
                } else {
                    out.add(Tok(TT.IDENT, kw))
                    i += kw.length
                    matched = true
                }
                if (matched) break
            }
            if (matched) continue
            when (c) {
                '+' -> out.add(Tok(TT.OP, "+"))
                '-', '−' -> out.add(Tok(TT.OP, "-"))
                '×', '*' -> out.add(Tok(TT.OP, "*"))
                '÷', '/' -> out.add(Tok(TT.OP, "/"))
                '^' -> out.add(Tok(TT.OP, "^"))
                '!' -> out.add(Tok(TT.OP, "!"))
                '%' -> out.add(Tok(TT.OP, "%"))
                '²' -> out.add(Tok(TT.OP, "²"))
                '³' -> out.add(Tok(TT.OP, "³"))
                'ᴇ' -> out.add(Tok(TT.OP, "E"))
                '√' -> out.add(Tok(TT.OP, "√"))
                '∛' -> out.add(Tok(TT.OP, "∛"))
                '(' -> out.add(Tok(TT.LP, "("))
                ')' -> out.add(Tok(TT.RP, ")"))
                ',' -> out.add(Tok(TT.COMMA, ","))
                '=' -> out.add(Tok(TT.EQ, "="))
                '⁻' -> {
                    if (i + 1 < n && src[i + 1] == '¹') {
                        out.add(Tok(TT.OP, "⁻¹"))
                        i++
                    } else {
                        throw CalcError("Syntax ERROR")
                    }
                }
                else -> throw CalcError("Syntax ERROR")
            }
            i++
        }
        out.add(Tok(TT.END, ""))
        return out
    }
}

// ─── AST + Parser ────────────────────────────────────────────────────────────

internal sealed class Node {
    class Num(val v: Value) : Node()
    class Const(val v: Value) : Node()
    class Var(val name: String) : Node()
    class Neg(val x: Node) : Node()
    class Bin(val op: Char, val l: Node, val r: Node) : Node()
    class Post(val op: String, val x: Node) : Node()
    class Call(val name: String, val args: List<Node>) : Node()
}

internal class Parser(private val toks: List<Tok>) {
    private var p = 0

    private fun peek(): Tok = toks[p]

    private fun next(): Tok = toks[p++]

    private fun isOp(t: Tok, s: String) = t.t == TT.OP && t.s == s

    fun parseAll(): Node {
        if (toks.size == 1) throw CalcError("Syntax ERROR")
        val n = sum()
        if (peek().t != TT.END) throw CalcError("Syntax ERROR")
        return n
    }

    private fun sum(): Node {
        var l = prod()
        while (true) {
            val t = peek()
            if (isOp(t, "+") || isOp(t, "-")) {
                next()
                l = Node.Bin(t.s[0], l, prod())
            } else break
        }
        return l
    }

    private fun prod(): Node {
        var l = perm()
        while (true) {
            val t = peek()
            if (isOp(t, "*") || isOp(t, "/")) {
                next()
                l = Node.Bin(t.s[0], l, perm())
            } else break
        }
        return l
    }

    private fun perm(): Node {
        var l = unary()
        while (true) {
            val t = peek()
            if (t.t == TT.IDENT && (t.s == "nPr" || t.s == "nCr")) {
                next()
                l = Node.Bin(if (t.s == "nPr") 'P' else 'C', l, unary())
            } else break
        }
        return l
    }

    private fun unary(): Node {
        val t = peek()
        if (isOp(t, "-")) {
            next()
            return Node.Neg(unary())
        }
        if (isOp(t, "+")) {
            next()
            return unary()
        }
        return implicit()
    }

    private fun startsPrimary(t: Tok): Boolean = when (t.t) {
        TT.NUM, TT.FUNC, TT.LP -> true
        TT.IDENT -> t.s != "nPr" && t.s != "nCr"
        TT.OP -> t.s == "√" || t.s == "∛"
        else -> false
    }

    private fun implicit(): Node {
        var l = power()
        while (startsPrimary(peek())) {
            l = Node.Bin('*', l, power())
        }
        return l
    }

    private fun power(): Node {
        val base = postfix()
        if (isOp(peek(), "^")) {
            next()
            return Node.Bin('^', base, powerOperand())
        }
        return base
    }

    private fun powerOperand(): Node {
        val t = peek()
        if (isOp(t, "-")) {
            next()
            return Node.Neg(powerOperand())
        }
        if (isOp(t, "+")) {
            next()
            return powerOperand()
        }
        return power()
    }

    private fun postfix(): Node {
        var n = primary()
        while (true) {
            val t = peek()
            if (t.t == TT.OP && (t.s == "!" || t.s == "%" || t.s == "²" || t.s == "³" || t.s == "⁻¹")) {
                next()
                n = Node.Post(t.s, n)
            } else if (isOp(t, "E")) {
                next()
                var negative = false
                if (isOp(peek(), "-") || isOp(peek(), "+")) {
                    negative = peek().s == "-"
                    next()
                }
                val e = peek()
                if (e.t != TT.NUM || e.s.contains('.')) throw CalcError("Syntax ERROR")
                next()
                val ev = Nm.parseNumber(e.s)
                n = Node.Bin('E', n, Node.Num(if (negative) Nm.neg(ev) else ev))
            } else break
        }
        return n
    }

    private fun closeParen() {
        if (peek().t == TT.RP) next()
        else if (peek().t != TT.END) throw CalcError("Syntax ERROR")
    }

    private fun primary(): Node {
        val t = next()
        when (t.t) {
            TT.NUM -> return Node.Num(Nm.parseNumber(t.s))
            TT.LP -> {
                val e = sum()
                closeParen()
                return e
            }
            TT.FUNC -> {
                val args = ArrayList<Node>()
                while (true) {
                    args.add(sum())
                    if (peek().t == TT.COMMA) {
                        next()
                    } else break
                }
                closeParen()
                return Node.Call(t.s, args)
            }
            TT.IDENT -> {
                return when (t.s) {
                    "π" -> Node.Const(Value.Real(PI))
                    "e" -> Node.Const(Value.Real(Math.E))
                    "Ran#" -> Node.Call("Ran#", emptyList())
                    "nPr", "nCr" -> throw CalcError("Syntax ERROR")
                    else -> Node.Var(t.s)
                }
            }
            TT.OP -> {
                if (t.s == "√") return Node.Call("sqrt", listOf(primary()))
                if (t.s == "∛") return Node.Call("cbrt", listOf(primary()))
                throw CalcError("Syntax ERROR")
            }
            else -> throw CalcError("Syntax ERROR")
        }
    }
}

// ─── Evaluator ───────────────────────────────────────────────────────────────

internal class Evaluator(
    private val angle: AngleUnit,
    private val vars: MutableMap<String, Value>,
) {
    private var steps = 0
    private val rnd = Random()

    fun reset() {
        steps = 0
    }

    fun eval(n: Node): Value {
        if (++steps > 3_000_000) throw CalcError("Time ERROR")
        return when (n) {
            is Node.Num -> n.v
            is Node.Const -> n.v
            is Node.Var -> vars[n.name] ?: Nm.ZERO
            is Node.Neg -> Nm.neg(eval(n.x))
            is Node.Post -> post(n)
            is Node.Bin -> bin(n)
            is Node.Call -> call(n)
        }
    }

    private fun post(n: Node.Post): Value {
        val v = eval(n.x)
        return when (n.op) {
            "!" -> Nm.factorial(v)
            "%" -> Nm.div(v, Nm.HUNDRED)
            "²" -> Nm.pow(v, Nm.int(2))
            "³" -> Nm.pow(v, Nm.int(3))
            else -> Nm.div(Nm.ONE, v)
        }
    }

    private fun bin(n: Node.Bin): Value {
        when (n.op) {
            '+', '-' -> {
                val l = eval(n.l)
                val r = n.r
                // "100 + 10%" means 100 + 10 % of 100
                if (r is Node.Post && r.op == "%") {
                    val p = eval(r.x)
                    val delta = Nm.div(Nm.mul(l, p), Nm.HUNDRED)
                    return if (n.op == '+') Nm.add(l, delta) else Nm.sub(l, delta)
                }
                val rv = eval(r)
                return if (n.op == '+') Nm.add(l, rv) else Nm.sub(l, rv)
            }
            '*' -> return Nm.mul(eval(n.l), eval(n.r))
            '/' -> return Nm.div(eval(n.l), eval(n.r))
            '^' -> return Nm.pow(eval(n.l), eval(n.r))
            'P' -> return Nm.perm(eval(n.l), eval(n.r))
            'C' -> return Nm.comb(eval(n.l), eval(n.r))
            'E' -> return Nm.mul(eval(n.l), Nm.pow(Nm.int(10), eval(n.r)))
        }
        throw CalcError("Syntax ERROR")
    }

    // ── angles ──
    private fun toRad(x: Double): Double = when (angle) {
        AngleUnit.DEG -> Math.toRadians(x)
        AngleUnit.RAD -> x
        AngleUnit.GRA -> x * PI / 200.0
    }

    private fun fromRad(x: Double): Double = when (angle) {
        AngleUnit.DEG -> Math.toDegrees(x)
        AngleUnit.RAD -> x
        AngleUnit.GRA -> x * 200.0 / PI
    }

    private fun quarterTurns(x: Double): Double = when (angle) {
        AngleUnit.DEG -> x / 90.0
        AngleUnit.RAD -> x / (PI / 2.0)
        AngleUnit.GRA -> x / 100.0
    }

    private fun quadrant(k: Double): Int {
        val m = (k.toLong() % 4L).toInt()
        return if (m < 0) m + 4 else m
    }

    private val half: Value = Value.Exact(BigInteger.ONE, BigInteger.valueOf(2))
    private val negHalf: Value = Value.Exact(BigInteger.ONE.negate(), BigInteger.valueOf(2))

    private fun degNorm(x: Double, mod: Double): Double = ((x % mod) + mod) % mod

    private fun exactQuarter(x: Double): Double? {
        val q = quarterTurns(x)
        val k = Math.rint(q)
        return if (abs(q - k) < 1e-11 * max(1.0, abs(k)) && abs(k) < 1e12) k else null
    }

    private fun sinV(v: Value): Value {
        val x = Nm.dbl(v)
        val k = exactQuarter(x)
        if (k != null) {
            val t = when (quadrant(k)) { 1 -> 1L; 3 -> -1L; else -> 0L }
            return Nm.int(t)
        }
        if (angle == AngleUnit.DEG) {
            val r = degNorm(x, 360.0)
            if (r == 30.0 || r == 150.0) return half
            if (r == 210.0 || r == 330.0) return negHalf
        }
        return Nm.real(Math.sin(toRad(x)))
    }

    private fun cosV(v: Value): Value {
        val x = Nm.dbl(v)
        val k = exactQuarter(x)
        if (k != null) {
            val t = when (quadrant(k)) { 0 -> 1L; 2 -> -1L; else -> 0L }
            return Nm.int(t)
        }
        if (angle == AngleUnit.DEG) {
            val r = degNorm(x, 360.0)
            if (r == 60.0 || r == 300.0) return half
            if (r == 120.0 || r == 240.0) return negHalf
        }
        return Nm.real(Math.cos(toRad(x)))
    }

    private fun tanV(v: Value): Value {
        val x = Nm.dbl(v)
        val k = exactQuarter(x)
        if (k != null) {
            if (quadrant(k) % 2 == 0) return Nm.ZERO
            throw CalcError("Math ERROR")
        }
        if (angle == AngleUnit.DEG) {
            val r = degNorm(x, 180.0)
            if (r == 45.0) return Nm.ONE
            if (r == 135.0) return Nm.int(-1)
        }
        return Nm.real(Math.tan(toRad(x)))
    }

    private fun asinh(x: Double): Double =
        if (x < 0) -asinh(-x) else ln(x + sqrt(x * x + 1.0))

    // ── calls ──
    private fun need(a: List<Node>, k: Int) {
        if (a.size != k) throw CalcError("Syntax ERROR")
    }

    private fun one(v: List<Value>): Value {
        if (v.size != 1) throw CalcError("Syntax ERROR")
        return v[0]
    }

    private fun two(v: List<Value>) {
        if (v.size != 2) throw CalcError("Syntax ERROR")
    }

    private fun call(n: Node.Call): Value {
        val a = n.args
        when (n.name) {
            "d/dx" -> {
                need(a, 2)
                return derivative(a[0], Nm.dbl(eval(a[1])))
            }
            "∫" -> {
                need(a, 3)
                return integral(a[0], Nm.dbl(eval(a[1])), Nm.dbl(eval(a[2])))
            }
            "Σ" -> {
                need(a, 3)
                return series(a[0], eval(a[1]), eval(a[2]), true)
            }
            "Π" -> {
                need(a, 3)
                return series(a[0], eval(a[1]), eval(a[2]), false)
            }
            "Ran#" -> return Nm.exact(BigInteger.valueOf(rnd.nextInt(1000).toLong()), BigInteger.valueOf(1000))
        }
        return fn(n.name, a.map { eval(it) })
    }

    private fun fn(name: String, v: List<Value>): Value {
        when (name) {
            "sin" -> return sinV(one(v))
            "cos" -> return cosV(one(v))
            "tan" -> return tanV(one(v))
            "sin⁻¹" -> {
                val x = Nm.dbl(one(v))
                if (abs(x) > 1.0 + 1e-12) throw CalcError("Math ERROR")
                return Nm.snap(fromRad(Math.asin(x.coerceIn(-1.0, 1.0))))
            }
            "cos⁻¹" -> {
                val x = Nm.dbl(one(v))
                if (abs(x) > 1.0 + 1e-12) throw CalcError("Math ERROR")
                return Nm.snap(fromRad(Math.acos(x.coerceIn(-1.0, 1.0))))
            }
            "tan⁻¹" -> return Nm.snap(fromRad(Math.atan(Nm.dbl(one(v)))))
            "sinh" -> return Nm.real(Math.sinh(Nm.dbl(one(v))))
            "cosh" -> return Nm.real(Math.cosh(Nm.dbl(one(v))))
            "tanh" -> return Nm.real(Math.tanh(Nm.dbl(one(v))))
            "sinh⁻¹" -> return Nm.snap(asinh(Nm.dbl(one(v))))
            "cosh⁻¹" -> {
                val x = Nm.dbl(one(v))
                if (x < 1.0) throw CalcError("Math ERROR")
                return Nm.snap(ln(x + sqrt(x * x - 1.0)))
            }
            "tanh⁻¹" -> {
                val x = Nm.dbl(one(v))
                if (abs(x) >= 1.0) throw CalcError("Math ERROR")
                return Nm.snap(0.5 * ln((1.0 + x) / (1.0 - x)))
            }
            "log" -> {
                if (v.size == 1) {
                    val x = Nm.dbl(v[0])
                    if (x <= 0.0) throw CalcError("Math ERROR")
                    return Nm.snap(Math.log10(x))
                }
                two(v)
                val base = Nm.dbl(v[0])
                val x = Nm.dbl(v[1])
                if (base <= 0.0 || base == 1.0 || x <= 0.0) throw CalcError("Math ERROR")
                return Nm.snap(Math.log(x) / Math.log(base))
            }
            "ln" -> {
                val x = Nm.dbl(one(v))
                if (x <= 0.0) throw CalcError("Math ERROR")
                return Nm.snap(Math.log(x))
            }
            "sqrt" -> return Nm.sqrt(one(v))
            "cbrt" -> return Nm.cbrt(one(v))
            "abs" -> return Nm.abs(one(v))
            "int" -> return Nm.roundInt(one(v), RoundingMode.DOWN)
            "frac" -> {
                val x = one(v)
                return Nm.sub(x, Nm.roundInt(x, RoundingMode.DOWN))
            }
            "floor" -> return Nm.roundInt(one(v), RoundingMode.FLOOR)
            "ceil" -> return Nm.roundInt(one(v), RoundingMode.CEILING)
            "sign" -> return Nm.int(Nm.signum(one(v)).toLong())
            "round" -> {
                if (v.isEmpty() || v.size > 2) throw CalcError("Syntax ERROR")
                val digits = if (v.size == 2) Nm.bigIntOf(v[1]).toInt() else 0
                if (digits < 0 || digits > 40) throw CalcError("Math ERROR")
                return Nm.fromBigDecimal(Nm.toBigDecimal(v[0]).setScale(digits, RoundingMode.HALF_UP))
            }
            "Rnd" -> {
                val bd = Nm.toBigDecimal(one(v))
                return Nm.fromBigDecimal(bd.round(MathContext(10, RoundingMode.HALF_UP)))
            }
            "gcd" -> {
                two(v)
                return Nm.big(Nm.bigIntOf(v[0]).gcd(Nm.bigIntOf(v[1])))
            }
            "lcm" -> {
                two(v)
                val x = Nm.bigIntOf(v[0])
                val y = Nm.bigIntOf(v[1])
                if (x.signum() == 0 || y.signum() == 0) return Nm.ZERO
                return Nm.big((x * y).abs().divide(x.gcd(y)))
            }
            "mod" -> {
                two(v)
                if (Nm.isZero(v[1])) throw CalcError("Math ERROR")
                if (Nm.isInt(v[0]) && Nm.isInt(v[1])) {
                    val x = Nm.bigIntOf(v[0])
                    val y = Nm.bigIntOf(v[1])
                    var r = x.mod(y.abs())
                    if (y.signum() < 0 && r.signum() != 0) r = r + y
                    return Nm.big(r)
                }
                val x = Nm.dbl(v[0])
                val y = Nm.dbl(v[1])
                return Nm.real(x - y * floor(x / y))
            }
            "min", "max" -> {
                if (v.isEmpty()) throw CalcError("Syntax ERROR")
                var best = v[0]
                for (item in v) {
                    val better = if (name == "min") Nm.dbl(item) < Nm.dbl(best) else Nm.dbl(item) > Nm.dbl(best)
                    if (better) best = item
                }
                return best
            }
            "root" -> {
                two(v)
                val k = v[0]
                val x = v[1]
                if (Nm.isZero(k)) throw CalcError("Math ERROR")
                val kd = Nm.dbl(k)
                if (Nm.signum(x) < 0 && Nm.isInt(k) && Nm.bigIntOf(k).testBit(0)) {
                    return Nm.neg(Nm.pow(Nm.neg(x), Nm.div(Nm.ONE, k)))
                }
                if (kd == 0.0) throw CalcError("Math ERROR")
                return Nm.pow(x, Nm.div(Nm.ONE, k))
            }
            "RanInt" -> {
                two(v)
                val lo = Nm.bigIntOf(v[0]).toLong()
                val hi = Nm.bigIntOf(v[1]).toLong()
                if (hi < lo || hi - lo > 1_000_000_000L) throw CalcError("Math ERROR")
                val span = hi - lo + 1
                return Nm.int(lo + (rnd.nextDouble() * span).toLong())
            }
            "Pol" -> {
                two(v)
                val x = Nm.dbl(v[0])
                val y = Nm.dbl(v[1])
                val r = Nm.snap(Math.hypot(x, y))
                val theta = Nm.snap(fromRad(Math.atan2(y, x)))
                vars["X"] = r
                vars["Y"] = theta
                return r
            }
            "Rec" -> {
                two(v)
                val r = v[0]
                val theta = v[1]
                val x = Nm.mul(r, cosV(theta))
                val y = Nm.mul(r, sinV(theta))
                vars["X"] = x
                vars["Y"] = y
                return x
            }
        }
        throw CalcError("Syntax ERROR")
    }

    // ── calculus ──
    private fun evalAt(node: Node, x: Value): Value {
        val old = vars["X"]
        vars["X"] = x
        try {
            return eval(node)
        } finally {
            if (old == null) vars.remove("X") else vars["X"] = old
        }
    }

    private fun fAt(node: Node, x: Double): Double = Nm.dbl(evalAt(node, Nm.real(x)))

    private fun derivative(f: Node, x0: Double): Value {
        val h = 1e-3 * max(1.0, abs(x0))
        val d = (-fAt(f, x0 + 2 * h) + 8 * fAt(f, x0 + h) - 8 * fAt(f, x0 - h) + fAt(f, x0 - 2 * h)) / (12 * h)
        return Nm.snap(d, 1e-9)
    }

    private fun simpson(a: Double, b: Double, fa: Double, fm: Double, fb: Double): Double =
        (b - a) / 6.0 * (fa + 4.0 * fm + fb)

    private fun adaptive(
        f: Node, a: Double, b: Double, fa: Double, fm: Double, fb: Double,
        whole: Double, eps: Double, depth: Int,
    ): Double {
        val m = (a + b) / 2.0
        val lm = (a + m) / 2.0
        val rm = (m + b) / 2.0
        val flm = fAt(f, lm)
        val frm = fAt(f, rm)
        val left = simpson(a, m, fa, flm, fm)
        val right = simpson(m, b, fm, frm, fb)
        val delta = left + right - whole
        if (depth <= 0 || abs(delta) <= 15.0 * eps) return left + right + delta / 15.0
        return adaptive(f, a, m, fa, flm, fm, left, eps / 2.0, depth - 1) +
                adaptive(f, m, b, fm, frm, fb, right, eps / 2.0, depth - 1)
    }

    private fun integral(f: Node, a: Double, b: Double): Value {
        if (a == b) return Nm.ZERO
        val parts = 16
        val width = (b - a) / parts
        var total = 0.0
        for (i in 0 until parts) {
            val lo = a + width * i
            val hi = if (i == parts - 1) b else lo + width
            val mid = (lo + hi) / 2.0
            val fl = fAt(f, lo)
            val fm = fAt(f, mid)
            val fh = fAt(f, hi)
            total += adaptive(f, lo, hi, fl, fm, fh, simpson(lo, hi, fl, fm, fh), 1e-11 / parts, 14)
        }
        return Nm.snap(total, 1e-9)
    }

    private fun series(f: Node, from: Value, to: Value, sum: Boolean): Value {
        val lo = Nm.bigIntOf(from)
        val hi = Nm.bigIntOf(to)
        if (hi < lo) return if (sum) Nm.ZERO else Nm.ONE
        if (lo.bitLength() > 40 || hi.bitLength() > 40) throw CalcError("Math ERROR")
        val count = hi.toLong() - lo.toLong() + 1
        if (count > 1_000_000L) throw CalcError("Math ERROR")
        var acc = if (sum) Nm.ZERO else Nm.ONE
        var i = lo.toLong()
        val end = hi.toLong()
        while (i <= end) {
            val term = evalAt(f, Nm.int(i))
            acc = if (sum) Nm.add(acc, term) else Nm.mul(acc, term)
            i++
        }
        return acc
    }
}

internal object Engine {
    fun parse(text: String): Node = Parser(Lexer.tokenize(text)).parseAll()

    fun evaluate(text: String, angle: AngleUnit, vars: MutableMap<String, Value>): Value {
        try {
            return Evaluator(angle, vars).eval(parse(text))
        } catch (e: StackOverflowError) {
            throw CalcError("Math ERROR")
        }
    }
}

// ─── Number formatting ───────────────────────────────────────────────────────

internal object CalcFmt {
    private fun sciString(r: BigDecimal, exp10: Int): String {
        val digits = r.stripTrailingZeros().unscaledValue().abs().toString()
        val mant = if (digits.length == 1) digits else "${digits[0]}.${digits.substring(1)}"
        val sign = if (r.signum() < 0) "-" else ""
        return "$sign${mant}ᴇ$exp10"
    }

    private fun norm(bd: BigDecimal): String {
        val r = bd.round(MathContext(12, RoundingMode.HALF_UP))
        if (r.signum() == 0) return "0"
        val exp10 = r.precision() - r.scale() - 1
        return if (exp10 >= 12 || exp10 < -9) sciString(r, exp10)
        else r.stripTrailingZeros().toPlainString()
    }

    private fun fix(bd: BigDecimal, n: Int): String = bd.setScale(n, RoundingMode.HALF_UP).toPlainString()

    private fun sci(bd: BigDecimal, n: Int): String {
        if (bd.signum() == 0) return (if (n > 0) "0." + "0".repeat(n) else "0") + "ᴇ0"
        val r = bd.round(MathContext(n + 1, RoundingMode.HALF_UP))
        val exp10 = r.precision() - r.scale() - 1
        val mant = r.movePointLeft(exp10).setScale(n, RoundingMode.HALF_UP).toPlainString()
        return "${mant}ᴇ$exp10"
    }

    fun decimal(bd: BigDecimal, kind: FormatKind, digits: Int): String {
        val s = when (kind) {
            FormatKind.NORM -> norm(bd)
            FormatKind.FIX -> fix(bd, digits)
            FormatKind.SCI -> sci(bd, digits)
        }
        return s.replace('-', '−')
    }

    fun display(v: Value, kind: FormatKind, digits: Int, showFraction: Boolean): ResultDisplay {
        if (v is Value.Exact && v.d != BigInteger.ONE && showFraction && kind == FormatKind.NORM) {
            val ns = v.n.abs().toString()
            val ds = v.d.toString()
            if (ns.length + ds.length <= 14) {
                val neg = v.n.signum() < 0
                return ResultDisplay((if (neg) "−" else "") + "$ns/$ds", ns, ds, neg)
            }
        }
        return ResultDisplay(decimal(Nm.toBigDecimal(v), kind, digits))
    }

    fun toClipboard(s: String): String = s.replace('−', '-').replace('ᴇ', 'E')
}

// ─── Base-N (32-bit two's complement) ────────────────────────────────────────

internal object BaseCalc {
    private class P(val s: String, val radix: Int) {
        var i = 0

        fun ws() {
            while (i < s.length && s[i] == ' ') i++
        }

        fun starts(kw: String): Boolean {
            ws()
            return s.startsWith(kw, i)
        }

        fun check(v: Long): Long {
            if (v < Int.MIN_VALUE.toLong() || v > Int.MAX_VALUE.toLong()) throw CalcError("Math ERROR")
            return v
        }

        fun orExpr(): Long {
            var l = andExpr()
            while (true) {
                if (starts("XNOR")) {
                    i += 4
                    l = (l xor andExpr()).inv()
                } else if (starts("XOR")) {
                    i += 3
                    l = l xor andExpr()
                } else if (starts("OR")) {
                    i += 2
                    l = l or andExpr()
                } else break
            }
            return l
        }

        fun andExpr(): Long {
            var l = addExpr()
            while (starts("AND")) {
                i += 3
                l = l and addExpr()
            }
            return l
        }

        fun addExpr(): Long {
            var l = mulExpr()
            while (true) {
                ws()
                if (i < s.length && s[i] == '+') {
                    i++
                    l = check(l + mulExpr())
                } else if (i < s.length && (s[i] == '-' || s[i] == '−')) {
                    i++
                    l = check(l - mulExpr())
                } else break
            }
            return l
        }

        fun mulExpr(): Long {
            var l = unary()
            while (true) {
                ws()
                if (i < s.length && (s[i] == '×' || s[i] == '*')) {
                    i++
                    l = check(l * unary())
                } else if (i < s.length && (s[i] == '÷' || s[i] == '/')) {
                    i++
                    val r = unary()
                    if (r == 0L) throw CalcError("Math ERROR")
                    l /= r
                } else break
            }
            return l
        }

        fun closeParen() {
            ws()
            if (i < s.length && s[i] == ')') i++
            else if (i < s.length) throw CalcError("Syntax ERROR")
        }

        fun unary(): Long {
            ws()
            if (i >= s.length) throw CalcError("Syntax ERROR")
            val c = s[i]
            if (c == '-' || c == '−') {
                i++
                return check(-unary())
            }
            if (s.startsWith("NOT(", i)) {
                i += 4
                val v = orExpr()
                closeParen()
                return v.inv()
            }
            if (s.startsWith("NEG(", i)) {
                i += 4
                val v = orExpr()
                closeParen()
                return check(-v)
            }
            if (c == '(') {
                i++
                val v = orExpr()
                closeParen()
                return v
            }
            val st = i
            while (i < s.length && Character.digit(s[i], radix) >= 0) i++
            if (st == i) throw CalcError("Syntax ERROR")
            val raw = try {
                java.lang.Long.parseLong(s.substring(st, i), radix)
            } catch (e: NumberFormatException) {
                throw CalcError("Math ERROR")
            }
            if (radix == 10) return check(raw)
            if (raw > 0xFFFFFFFFL) throw CalcError("Math ERROR")
            return if (raw > Int.MAX_VALUE.toLong()) raw - 0x100000000L else raw
        }
    }

    fun eval(src: String, radix: Int): Long {
        val p = P(src, radix)
        val v = p.orExpr()
        p.ws()
        if (p.i != src.length) throw CalcError("Syntax ERROR")
        return v
    }

    fun render(v: Long, radix: Int, group: Boolean): String {
        if (radix == 10) return v.toString().replace('-', '−')
        val u = v and 0xFFFFFFFFL
        var s = java.lang.Long.toString(u, radix).uppercase()
        if (radix == 2) {
            val padTo = if (v < 0) 32 else ((s.length + 3) / 4) * 4
            s = s.padStart(padTo, '0')
            if (group) s = s.chunked(4).joinToString(" ")
        }
        return s
    }
}

// ─── Root finder for SOLVE ───────────────────────────────────────────────────

internal object Solver {
    private fun addRoot(roots: MutableList<Double>, r: Double) {
        if (roots.none { abs(it - r) < 1e-9 * max(1.0, abs(r)) }) roots.add(r)
    }

    private fun bisect(f: (Double) -> Double, a0: Double, b0: Double, fa0: Double, fb0: Double): Double? {
        var a = a0
        var b = b0
        var fa = fa0
        repeat(120) {
            val m = (a + b) / 2.0
            val fm = f(m)
            if (fm.isNaN()) return null
            if (fm == 0.0) return m
            if (fa * fm < 0) {
                b = m
            } else {
                a = m
                fa = fm
            }
        }
        val mid = (a + b) / 2.0
        val fmid = f(mid)
        val tol = 1e-7 * max(1.0, max(abs(fa0), abs(fb0)))
        return if (!fmid.isNaN() && abs(fmid) <= tol) mid else null
    }

    private fun scan(f: (Double) -> Double, lo: Double, hi: Double, n: Int): List<Double> {
        val roots = ArrayList<Double>()
        val step = (hi - lo) / n
        var x0 = lo
        var f0 = f(x0)
        for (i in 1..n) {
            val x1 = lo + step * i
            val f1 = f(x1)
            if (!f0.isNaN()) {
                if (f0 == 0.0) addRoot(roots, x0)
                else if (!f1.isNaN() && f0 * f1 < 0) {
                    val r = bisect(f, x0, x1, f0, f1)
                    if (r != null) addRoot(roots, r)
                }
            }
            x0 = x1
            f0 = f1
        }
        if (!f0.isNaN() && f0 == 0.0) addRoot(roots, x0)
        return roots
    }

    private fun newton(f: (Double) -> Double, start: Double): Double? {
        var x = start
        repeat(200) {
            val fx = f(x)
            if (fx.isNaN()) return null
            if (abs(fx) < 1e-12) return x
            val h = 1e-6 * max(1.0, abs(x))
            val d = (f(x + h) - f(x - h)) / (2 * h)
            if (d == 0.0 || d.isNaN()) return null
            val nx = x - fx / d
            if (nx.isNaN() || nx.isInfinite()) return null
            if (abs(nx - x) < 1e-14 * max(1.0, abs(x))) {
                return if (abs(f(nx)) < 1e-8 * max(1.0, abs(nx))) nx else null
            }
            x = nx
        }
        return null
    }

    fun findRoots(f: (Double) -> Double, guess: Double): List<Double> {
        for (radius in doubleArrayOf(10.0, 100.0, 1000.0, 10000.0)) {
            val roots = scan(f, guess - radius, guess + radius, 4000)
            if (roots.isNotEmpty()) return roots.sortedBy { abs(it - guess) }.take(6)
        }
        val nr = newton(f, guess)
        return if (nr != null) listOf(nr) else emptyList()
    }
}

// ─── Statistics ──────────────────────────────────────────────────────────────

internal class Reg(
    val coefs: List<Pair<String, Double>>,
    val r: Double?,
    val yOf: (Double) -> Double,
    val xOf: (Double) -> List<Double>,
)

internal object Stats {
    fun valueAt(sx: DoubleArray, cf: LongArray, idx: Long): Double {
        var cum = 0L
        for (i in sx.indices) {
            cum += cf[i]
            if (idx < cum) return sx[i]
        }
        return sx[sx.size - 1]
    }

    fun medianRange(sx: DoubleArray, cf: LongArray, from: Long, to: Long): Double {
        val cnt = to - from + 1
        if (cnt <= 0) return Double.NaN
        val mid = from + cnt / 2
        return if (cnt % 2L == 1L) valueAt(sx, cf, mid)
        else (valueAt(sx, cf, mid - 1) + valueAt(sx, cf, mid)) / 2.0
    }

    fun linear(u: DoubleArray, v: DoubleArray): Triple<Double, Double, Double> {
        val n = u.size.toDouble()
        var su = 0.0
        var sv = 0.0
        var suu = 0.0
        var suv = 0.0
        var svv = 0.0
        for (i in u.indices) {
            su += u[i]
            sv += v[i]
            suu += u[i] * u[i]
            suv += u[i] * v[i]
            svv += v[i] * v[i]
        }
        val dx = n * suu - su * su
        val dy = n * svv - sv * sv
        if (dx == 0.0) throw CalcError("Math ERROR")
        val b = (n * suv - su * sv) / dx
        val a = (sv - b * su) / n
        val r = if (dy == 0.0) Double.NaN else (n * suv - su * sv) / sqrt(dx * dy)
        return Triple(a, b, r)
    }

    fun fit(type: StatType, xs: DoubleArray, ys: DoubleArray): Reg {
        val n = xs.size
        if (type == StatType.QUAD) {
            if (n < 3) throw CalcError("Need at least 3 points")
            var s1 = 0.0; var s2 = 0.0; var s3 = 0.0; var s4 = 0.0
            var t0 = 0.0; var t1 = 0.0; var t2 = 0.0
            for (i in 0 until n) {
                val x = xs[i]
                val y = ys[i]
                s1 += x; s2 += x * x; s3 += x * x * x; s4 += x * x * x * x
                t0 += y; t1 += x * y; t2 += x * x * y
            }
            val m: Array<Array<Value>> = arrayOf(
                arrayOf(Nm.real(n.toDouble()), Nm.real(s1), Nm.real(s2), Nm.real(t0)),
                arrayOf(Nm.real(s1), Nm.real(s2), Nm.real(s3), Nm.real(t1)),
                arrayOf(Nm.real(s2), Nm.real(s3), Nm.real(s4), Nm.real(t2)),
            )
            val sol = Nm.solveLinear(m) ?: throw CalcError("Math ERROR")
            val a = Nm.dbl(sol[0])
            val b = Nm.dbl(sol[1])
            val c = Nm.dbl(sol[2])
            return Reg(
                listOf("A" to a, "B" to b, "C" to c), null,
                { x -> a + b * x + c * x * x },
                { y ->
                    val disc = b * b - 4.0 * c * (a - y)
                    if (c == 0.0 || disc < 0) emptyList()
                    else if (disc == 0.0) listOf(-b / (2.0 * c))
                    else listOf((-b + sqrt(disc)) / (2.0 * c), (-b - sqrt(disc)) / (2.0 * c))
                },
            )
        }
        if (n < 2) throw CalcError("Need at least 2 points")
        val u = DoubleArray(n)
        val v = DoubleArray(n)
        for (i in 0 until n) {
            val x = xs[i]
            val y = ys[i]
            when (type) {
                StatType.LINEAR -> { u[i] = x; v[i] = y }
                StatType.LOG -> {
                    if (x <= 0.0) throw CalcError("Math ERROR")
                    u[i] = ln(x); v[i] = y
                }
                StatType.EXP, StatType.AB -> {
                    if (y <= 0.0) throw CalcError("Math ERROR")
                    u[i] = x; v[i] = ln(y)
                }
                StatType.POWER -> {
                    if (x <= 0.0 || y <= 0.0) throw CalcError("Math ERROR")
                    u[i] = ln(x); v[i] = ln(y)
                }
                StatType.INV -> {
                    if (x == 0.0) throw CalcError("Math ERROR")
                    u[i] = 1.0 / x; v[i] = y
                }
                else -> {}
            }
        }
        val (a, b, r) = linear(u, v)
        return when (type) {
            StatType.LOG -> Reg(
                listOf("A" to a, "B" to b), r,
                { x -> a + b * ln(x) }, { y -> listOf(Math.exp((y - a) / b)) },
            )
            StatType.EXP -> {
                val ca = Math.exp(a)
                Reg(
                    listOf("A" to ca, "B" to b), r,
                    { x -> ca * Math.exp(b * x) }, { y -> listOf(ln(y / ca) / b) },
                )
            }
            StatType.AB -> {
                val ca = Math.exp(a)
                val cb = Math.exp(b)
                Reg(
                    listOf("A" to ca, "B" to cb), r,
                    { x -> ca * Math.pow(cb, x) }, { y -> listOf(ln(y / ca) / ln(cb)) },
                )
            }
            StatType.POWER -> {
                val ca = Math.exp(a)
                Reg(
                    listOf("A" to ca, "B" to b), r,
                    { x -> ca * Math.pow(x, b) }, { y -> listOf(Math.pow(y / ca, 1.0 / b)) },
                )
            }
            StatType.INV -> Reg(
                listOf("A" to a, "B" to b), r,
                { x -> a + b / x }, { y -> listOf(b / (y - a)) },
            )
            else -> Reg(
                listOf("A" to a, "B" to b), r,
                { x -> a + b * x }, { y -> listOf((y - a) / b) },
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// VIEW MODEL
// ═══════════════════════════════════════════════════════════════════════════════

private val EDIT_TOKENS: List<String> = listOf(
    "sinh⁻¹(", "cosh⁻¹(", "tanh⁻¹(", "sin⁻¹(", "cos⁻¹(", "tan⁻¹(",
    "sinh(", "cosh(", "tanh(", "sin(", "cos(", "tan(",
    "log(", "ln(", "abs(", "int(", "frac(", "floor(", "ceil(", "round(", "sign(",
    "gcd(", "lcm(", "mod(", "min(", "max(", "root(", "Rnd(", "RanInt(", "Pol(", "Rec(",
    "d/dx(", "∫(", "Σ(", "Π(", "√(", "∛(", "10^(", "e^(",
    "Ans", "Ran#", "nPr", "nCr", "⁻¹",
    " XNOR ", " XOR ", " AND ", " OR ", "NOT(", "NEG(",
).sortedByDescending { it.length }

private val BIN_OPS = setOf("+", "×", "÷", "^", "nPr", "nCr")
private val POSTFIX_OPS = setOf("²", "³", "⁻¹", "!", "%", "ᴇ")
private val VAR_NAMES = listOf("A", "B", "C", "D", "E", "F", "X", "Y", "M")

private fun tokenLenBefore(text: String, cur: Int): Int {
    for (t in EDIT_TOKENS) {
        if (cur >= t.length && text.startsWith(t, cur - t.length)) return t.length
    }
    return 1
}

private fun tokenLenAfter(text: String, cur: Int): Int {
    for (t in EDIT_TOKENS) {
        if (text.startsWith(t, cur)) return t.length
    }
    return 1
}

private fun snapCursor(text: String, pos: Int): Int {
    var p = 0
    while (p < text.length) {
        val len = tokenLenAfter(text, p)
        if (pos < p + len) return if (pos - p <= p + len - pos) p else p + len
        p += len
    }
    return text.length
}

private fun balance(text: String): String {
    val open = text.count { it == '(' }
    val close = text.count { it == ')' }
    return text + ")".repeat(max(0, open - close))
}

private fun startsInfix(text: String): Boolean =
    text in BIN_OPS || text in POSTFIX_OPS || text == "−"

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mathHistoryDao: MathHistoryDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorState())
    val uiState: StateFlow<CalculatorState> = _uiState.asStateFlow()

    private val vars = HashMap<String, Value>()
    private var lastValue: Value? = null
    private var toastJob: Job? = null
    private var isNewExpression = true

    fun onDigit(digit: String) {
        _uiState.update {
            if (it.error != null) {
                isNewExpression = false
                val nextState = it.copy(display = digit, error = null)
                return@update nextState.copy(liveResult = calculateLive(nextState))
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
                val nextState = it.copy(display = if (currentDisplay == "0") "-" else currentDisplay + "-")
                return@update nextState.copy(liveResult = calculateLive(nextState))
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
                it.copy(display = "0", liveResult = null)
            } else {
                val nextState = it.copy(display = it.display.dropLast(1))
                nextState.copy(liveResult = calculateLive(nextState))
            }
        }
    }

    fun onEquals() {
        val state = _uiState.value
        if (state.display.isEmpty() || state.error != null) return

        try {
            val prepared = prepareExpression(state.display, state.angle == AngleUnit.DEG)
            val expression = ExpressionBuilder(prepared).build()
            val result = expression.evaluate()

            if (result.isNaN() || result.isInfinite()) {
                _uiState.update { it.copy(error = "Invalid Operation") }
            } else {
                val formattedResult = formatResult(result)
                val currentExpr = state.display
                _uiState.update {
                    it.copy(
                        display = formattedResult,
                        formula = "$currentExpr =",
                        liveResult = null,
                        error = null,
                        evaluated = true,
                    )
                }
                isNewExpression = true

                viewModelScope.launch {
                    mathHistoryDao.insert(MathHistory(expression = currentExpr, result = formattedResult))
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Syntax Error") }
        }
    }

    fun onFunction(func: String) {
        _uiState.update {
            if (it.error != null) return@update it
            isNewExpression = false
            val currentDisplay = if (it.display == "0") "" else it.display
            val functionCall = when (func) {
                "√" -> "sqrt("
                "sin" -> "sin("
                "cos" -> "cos("
                "tan" -> "tan("
                "ln" -> "ln("
                "log" -> "log10("
                "1/x" -> "inv("
                "x!" -> "!"
                else -> "$func("
            }
            val nextState = it.copy(display = currentDisplay + functionCall, error = null)
            nextState.copy(liveResult = calculateLive(nextState))
        }
    }

    fun onUseHistory(item: Pair<String, String>) {
        _uiState.update {
            it.copy(
                formula = item.first + " =",
                display = item.second,
                liveResult = null,
                error = null,
            )
        }
        isNewExpression = true
    }

    private fun formatResult(value: Double): String {
        return when {
            value.isInfinite() -> "Infinity"
            value.isNaN() -> "NaN"
            else -> {
                val absVal = abs(value)
                if (absVal == 0.0) {
                    "0"
                } else if (absVal >= 1e16 || (absVal < 1e-9 && absVal > 0.0)) {
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
        val openCount = rawDisplay.count { it == '(' }
        val closeCount = rawDisplay.count { it == ')' }
        val balanced = rawDisplay + ")".repeat((openCount - closeCount).coerceAtLeast(0))

        var s = balanced
            .replace("×", "*")
            .replace("÷", "/")
            .replace("π", "pi")
            .replace("ln(", "log(")
            .replace("inv(", "1/(")

        s = s.replace(Regex("(\\d|pi|e|\\))\\s*\\("), "$1*(")
        s = s.replace(Regex("\\)\\s*(\\d|pi|e)"), ")*$1")
        s = s.replace(Regex("\\)\\s*([a-zA-Z]+)\\("), ")*$1(")
        s = s.replace(Regex("(\\d)\\s*(pi|e)"), "$1*$2")
        s = s.replace(Regex("(\\d)\\s*([a-zA-Z]+)\\("), "$1*$2(")
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
        if (state.display.last() in "+×÷-^(") return null

        return try {
            val prepared = prepareExpression(state.display, state.angle == AngleUnit.DEG)
            val expression = ExpressionBuilder(prepared).build()
            val result = expression.evaluate()

            if (result.isNaN() || result.isInfinite()) null
            else formatResult(result)
        } catch (e: Exception) {
            null
        }
    }

    init {
        refreshVariables()
        viewModelScope.launch {
            mathHistoryDao.getAllHistory().collect { list ->
                _uiState.update { state ->
                    state.copy(history = list.map { it.expression to it.result })
                }
            }
        }
    }

    // ── small helpers ────────────────────────────────────────────────────────

    private inline fun mutate(block: (CalculatorState) -> CalculatorState) {
        _uiState.value = block(_uiState.value)
    }

    private fun CalculatorState.withField(f: EditField, index: Int = focus): CalculatorState {
        val list = fields.toMutableList()
        if (index in list.indices) list[index] = f
        return copy(fields = list)
    }

    private fun flash(message: String) {
        toastJob?.cancel()
        mutate { it.copy(toast = message) }
        toastJob = viewModelScope.launch {
            delay(1400)
            mutate { it.copy(toast = null) }
        }
    }

    private fun refreshVariables() {
        val s = _uiState.value
        val map = LinkedHashMap<String, String>()
        for (name in VAR_NAMES) {
            val v = vars[name]
            map[name] = if (v == null) "0" else CalcFmt.display(v, s.fmtKind, s.fmtDigits, s.showFraction).plain
        }
        _uiState.value = s.copy(variables = map)
    }

    private fun line(label: String, v: Value, s: CalculatorState): OutLine {
        val d = CalcFmt.display(v, s.fmtKind, s.fmtDigits, s.showFraction)
        return OutLine(label, d.plain, d.num, d.den, d.negative)
    }

    private fun plainLine(label: String, v: Double, s: CalculatorState): OutLine =
        line(label, Nm.snap(v, 1e-12), s)

    // ── key dispatch ─────────────────────────────────────────────────────────

    fun onKey(def: KeyDef) {
        val s = _uiState.value
        val action = when (s.layer) {
            KeyLayer.SHIFT -> def.shiftAction ?: def.action
            KeyLayer.ALPHA -> def.alphaAction ?: def.action
            KeyLayer.NONE -> def.action
        }
        val keepFlags = action == "c:shift" || action == "c:alpha" || action == "c:hyp"
        val hypNow = s.hyp
        _uiState.value = if (keepFlags) s else s.copy(layer = KeyLayer.NONE, hyp = false, error = null)
        when {
            action.startsWith("i:") -> insert(action.substring(2))
            action.startsWith("ti:") -> insert(trigName(action.substring(3), hypNow, true))
            action.startsWith("t:") -> insert(trigName(action.substring(2), hypNow, false))
            action.startsWith("c:") -> command(action.substring(2))
        }
    }

    private fun trigName(base: String, hyp: Boolean, inverse: Boolean): String =
        base + (if (hyp) "h" else "") + (if (inverse) "⁻¹" else "") + "("

    private fun command(name: String) {
        when (name) {
            "shift" -> mutate { it.copy(layer = if (it.layer == KeyLayer.SHIFT) KeyLayer.NONE else KeyLayer.SHIFT) }
            "alpha" -> mutate { it.copy(layer = if (it.layer == KeyLayer.ALPHA) KeyLayer.NONE else KeyLayer.ALPHA) }
            "hyp" -> mutate { it.copy(hyp = !it.hyp) }
            "left" -> moveCursor(-1)
            "right" -> moveCursor(1)
            "del" -> backspace()
            "ac" -> clearEntry()
            "equals" -> equals()
            "sd" -> toggleFraction()
            "neg" -> insert("−", raw = true)
            "paren" -> smartParen()
            "cat" -> mutate { it.copy(sheet = CalcSheet.CATALOG) }
            "const" -> mutate { it.copy(sheet = CalcSheet.CONST) }
            "setup" -> mutate { it.copy(sheet = CalcSheet.SETUP) }
            "clr" -> clearMemory()
            "drg" -> {
                val next = when (_uiState.value.angle) {
                    AngleUnit.DEG -> AngleUnit.RAD
                    AngleUnit.RAD -> AngleUnit.GRA
                    AngleUnit.GRA -> AngleUnit.DEG
                }
                onSetAngle(next)
                flash(next.label)
            }
            "sto" -> mutate { it.copy(sheet = CalcSheet.STORE) }
            "rcl" -> mutate { it.copy(sheet = CalcSheet.RECALL) }
        }
    }

    // ── text editing ─────────────────────────────────────────────────────────

    fun onInsertText(text: String) {
        insert(text)
        mutate { it.copy(sheet = CalcSheet.NONE) }
    }

    private fun insert(text: String, raw: Boolean = false) {
        var s = _uiState.value
        if (s.mode == CalcMode.COMP && s.evaluated) {
            val base = if (!raw && startsInfix(text)) "Ans" else ""
            s = s.copy(
                fields = listOf(EditField(base, base.length)),
                focus = 0,
                evaluated = false,
                outputs = emptyList(),
            )
        }
        val f = s.activeField
        var t = f.text
        var cur = f.cursor.coerceIn(0, t.length)
        var ins = text

        if (!raw && s.mode != CalcMode.BASE) {
            val prev: Char? = if (cur > 0) t[cur - 1] else null
            when {
                ins == "." -> {
                    var i = cur - 1
                    while (i >= 0 && (t[i] in '0'..'9' || t[i] == '.')) i--
                    val run = t.substring(i + 1, cur)
                    if (run.contains('.')) return
                    if (run.isEmpty()) ins = "0."
                }
                ins == "ᴇ" -> {
                    if (prev == '.') return
                    if (prev == null || !(prev in '0'..'9' || prev == ')')) ins = "1ᴇ"
                }
                ins in BIN_OPS -> {
                    if (t.isEmpty() && s.mode == CalcMode.COMP) {
                        ins = "Ans$ins"
                    } else if (prev != null && prev in "+−×÷^") {
                        t = t.removeRange(cur - 1, cur)
                        cur--
                    }
                }
                ins in POSTFIX_OPS && ins != "ᴇ" -> {
                    if (t.isEmpty() && s.mode == CalcMode.COMP) ins = "Ans$ins"
                }
                ins == "−" -> {
                    if (prev == '+') {
                        t = t.removeRange(cur - 1, cur)
                        cur--
                    } else if (prev == '−') return
                }
                ins == "=" -> {
                    if (t.contains('=')) return
                }
            }
        }

        val newText = t.substring(0, cur) + ins + t.substring(cur)
        if (newText.length > 240) return
        s = s.withField(EditField(newText, cur + ins.length)).copy(error = null)
        _uiState.value = recompute(s)
    }

    private fun smartParen() {
        val s = _uiState.value
        val f = s.activeField
        val before = f.text.substring(0, f.cursor.coerceIn(0, f.text.length))
        val open = before.count { it == '(' }
        val close = before.count { it == ')' }
        val prev = before.lastOrNull()
        val closing = open > close && prev != null && prev !in "+−×÷^(,"
        insert(if (closing) ")" else "(")
    }

    private fun moveCursor(dir: Int) {
        var s = _uiState.value
        if (s.mode == CalcMode.COMP && s.evaluated) s = s.copy(evaluated = false)
        val f = s.activeField
        val cur = f.cursor.coerceIn(0, f.text.length)
        val next = if (dir < 0) cur - (if (cur > 0) tokenLenBefore(f.text, cur) else 0)
        else cur + (if (cur < f.text.length) tokenLenAfter(f.text, cur) else 0)
        s = s.withField(EditField(f.text, next.coerceIn(0, f.text.length)))
        _uiState.value = recompute(s)
    }

    private fun backspace() {
        var s = _uiState.value
        if (s.mode == CalcMode.COMP && s.evaluated) s = s.copy(evaluated = false)
        val f = s.activeField
        val cur = f.cursor.coerceIn(0, f.text.length)
        if (cur == 0) {
            _uiState.value = recompute(s)
            return
        }
        val len = tokenLenBefore(f.text, cur)
        val nt = f.text.removeRange(cur - len, cur)
        s = s.withField(EditField(nt, cur - len)).copy(error = null)
        _uiState.value = recompute(s)
    }

    private fun clearEntry() {
        val s = _uiState.value
        if (s.mode == CalcMode.COMP || s.mode == CalcMode.BASE) {
            _uiState.value = recompute(
                s.copy(
                    fields = listOf(EditField()), focus = 0, evaluated = false,
                    error = null, outputs = emptyList(),
                ),
            )
        } else {
            _uiState.value = recompute(s.withField(EditField()).copy(error = null))
        }
    }

    fun onCursor(field: Int, pos: Int) {
        var s = _uiState.value
        if (field !in s.fields.indices) return
        if (s.mode == CalcMode.COMP && s.evaluated) s = s.copy(evaluated = false)
        val f = s.fields[field]
        s = s.copy(focus = field).withField(EditField(f.text, snapCursor(f.text, pos)), field)
        _uiState.value = recompute(s)
    }

    fun onFocus(field: Int) {
        val s = _uiState.value
        if (field !in s.fields.indices) return
        val f = s.fields[field]
        _uiState.value = s.copy(focus = field).withField(EditField(f.text, f.text.length), field)
    }

    // ── equals / enter ───────────────────────────────────────────────────────

    private fun equals() {
        val s = _uiState.value
        when (s.mode) {
            CalcMode.COMP -> evaluateComp(s)
            CalcMode.BASE -> {
                val t = s.activeField.text
                if (t.isBlank()) return
                try {
                    val v = BaseCalc.eval(balance(t), s.baseRadix)
                    val out = BaseCalc.render(v, s.baseRadix, false)
                    _uiState.value = recompute(s.withField(EditField(out, out.length)).copy(error = null))
                } catch (e: CalcError) {
                    _uiState.value = s.copy(error = e.message ?: "Math ERROR")
                }
            }
            CalcMode.STAT -> {
                val dataCount = s.fields.size - 2
                if (s.focus == dataCount - 1) {
                    val list = s.fields.toMutableList()
                    list.add(dataCount, EditField())
                    list.add(dataCount + 1, EditField())
                    _uiState.value = recompute(s.copy(fields = list, focus = dataCount))
                } else {
                    onFocus((s.focus + 1) % s.fields.size)
                }
            }
            else -> onFocus((s.focus + 1) % s.fields.size)
        }
    }

    private fun evaluateComp(s: CalculatorState) {
        val text = s.activeField.text
        if (text.isBlank()) return
        val balanced = balance(text)
        val local = HashMap(vars)
        try {
            val v = Engine.evaluate(balanced, s.angle, local)
            vars.putAll(local)
            vars["Ans"] = v
            lastValue = v
            val d = CalcFmt.display(v, s.fmtKind, s.fmtDigits, s.showFraction)
            viewModelScope.launch {
                mathHistoryDao.insert(MathHistory(expression = balanced, result = d.plain))
            }
            _uiState.value = s.copy(
                fields = listOf(EditField(balanced, balanced.length)),
                focus = 0,
                evaluated = true,
                error = null,
                outputs = listOf(OutLine("", d.plain, d.num, d.den, d.negative)),
            )
            refreshVariables()
        } catch (e: CalcError) {
            _uiState.value = s.copy(error = e.message ?: "Math ERROR", outputs = emptyList())
        }
    }

    // ── memory / variables ───────────────────────────────────────────────────

    private fun currentValue(s: CalculatorState): Value? {
        if (s.mode == CalcMode.BASE) return null
        if (s.mode == CalcMode.COMP && s.evaluated) return lastValue ?: vars["Ans"]
        val t = s.activeField.text
        if (t.isBlank()) return vars["Ans"]
        return try {
            Engine.evaluate(balance(t), s.angle, HashMap(vars))
        } catch (e: CalcError) {
            null
        }
    }

    fun onStoreVar(name: String) {
        val s = _uiState.value
        val v = currentValue(s)
        mutate { it.copy(sheet = CalcSheet.NONE) }
        if (v == null) {
            flash("Nothing to store")
            return
        }
        vars[name] = v
        refreshVariables()
        flash("Stored in $name")
    }

    fun onRecallVar(name: String) {
        insert(name)
        mutate { it.copy(sheet = CalcSheet.NONE) }
    }

    private fun clearMemory() {
        vars.clear()
        lastValue = null
        refreshVariables()
        flash("Memory cleared")
    }

    // ── settings ─────────────────────────────────────────────────────────────

    fun onSetAngle(angle: AngleUnit) {
        _uiState.value = recompute(_uiState.value.copy(angle = angle))
    }

    fun onCycleAngle() {
        val next = when (_uiState.value.angle) {
            AngleUnit.DEG -> AngleUnit.RAD
            AngleUnit.RAD -> AngleUnit.GRA
            AngleUnit.GRA -> AngleUnit.DEG
        }
        onSetAngle(next)
    }

    fun onSetFormat(kind: FormatKind, digits: Int) {
        _uiState.value = recompute(_uiState.value.copy(fmtKind = kind, fmtDigits = digits.coerceIn(0, 9)))
        refreshVariables()
    }

    fun onSetShowFraction(show: Boolean) {
        _uiState.value = recompute(_uiState.value.copy(showFraction = show))
        refreshVariables()
    }

    private fun toggleFraction() {
        onSetShowFraction(!_uiState.value.showFraction)
    }

    fun onToggleScientific() {
        mutate { it.copy(scientific = !it.scientific, layer = KeyLayer.NONE, hyp = false) }
    }

    fun onSheet(sheet: CalcSheet) {
        mutate { it.copy(sheet = sheet) }
    }

    fun onDismissSheet() {
        mutate { it.copy(sheet = CalcSheet.NONE) }
    }

    // ── history ──────────────────────────────────────────────────────────────

    fun onUseHistoryExpression(expression: String) {
        val s = _uiState.value
        _uiState.value = recompute(
            s.copy(
                fields = listOf(EditField(expression, expression.length)),
                focus = 0, evaluated = false, error = null,
            ),
        )
    }

    fun onUseHistoryResult(result: String) {
        insert(result)
    }

    fun clearHistory() {
        viewModelScope.launch { mathHistoryDao.clearAll() }
    }

    // ── clipboard ────────────────────────────────────────────────────────────

    fun onCopy(text: String) {
        if (text.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("Calculator", CalcFmt.toClipboard(text)))
        flash("Copied")
    }

    fun onCopyResult() {
        val s = _uiState.value
        val text = s.outputs.firstOrNull { !it.header }?.text ?: s.display
        if (text.isBlank()) return
        onCopy(text)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // RECOMPUTE — derives the outputs of the current mode from its fields
    // ═════════════════════════════════════════════════════════════════════════

    private fun recompute(s: CalculatorState): CalculatorState = when (s.mode) {
        CalcMode.COMP -> recomputeComp(s)
        CalcMode.SOLVE -> recomputeSolve(s)
        CalcMode.EQN -> recomputeEqn(s)
        CalcMode.STAT -> recomputeStat(s)
        CalcMode.TABLE -> recomputeTable(s)
        CalcMode.BASE -> recomputeBase(s)
        CalcMode.CONVERT -> recomputeConvert(s)
    }

    private fun recomputeComp(s: CalculatorState): CalculatorState {
        if (s.evaluated) {
            val v = lastValue ?: return s
            return s.copy(outputs = listOf(line("", v, s)))
        }
        val text = s.activeField.text
        if (text.isBlank() || text.contains("Ran")) return s.copy(outputs = emptyList())
        return try {
            val v = Engine.evaluate(balance(text), s.angle, HashMap(vars))
            val d = CalcFmt.display(v, s.fmtKind, s.fmtDigits, s.showFraction)
            if (d.plain == text.trim()) s.copy(outputs = emptyList())
            else s.copy(outputs = listOf(OutLine("", d.plain, d.num, d.den, d.negative)))
        } catch (e: CalcError) {
            s.copy(outputs = emptyList())
        }
    }

    // ── SOLVE ────────────────────────────────────────────────────────────────

    private fun recomputeSolve(s: CalculatorState): CalculatorState {
        val eq = s.fields[0].text
        if (eq.isBlank()) {
            return s.copy(outputs = emptyList(), note = "Type an equation such as X²−4=0, or just an expression.")
        }
        return try {
            val parts = eq.split("=")
            if (parts.size > 2 || (parts.size == 2 && parts[1].isBlank())) throw CalcError("Syntax ERROR")
            val left = Engine.parse(parts[0])
            val right = if (parts.size == 2) Engine.parse(parts[1]) else null
            val guessText = s.fields[1].text
            val local = HashMap(vars)
            val guess = if (guessText.isBlank()) 0.0 else Nm.dbl(Engine.evaluate(guessText, s.angle, local))
            val ev = Evaluator(s.angle, local)
            val f: (Double) -> Double = { x ->
                try {
                    local["X"] = Value.Real(x)
                    ev.reset()
                    val l = Nm.dbl(ev.eval(left))
                    val r = if (right != null) Nm.dbl(ev.eval(right)) else 0.0
                    l - r
                } catch (e: CalcError) {
                    Double.NaN
                }
            }
            val roots = Solver.findRoots(f, guess)
            if (roots.isEmpty()) {
                s.copy(outputs = emptyList(), note = "No solution found near X = ${s.fields[1].text.ifBlank { "0" }}")
            } else {
                val out = ArrayList<OutLine>()
                roots.forEachIndexed { i, r ->
                    val label = if (roots.size == 1) "X" else "X${"₁₂₃₄₅₆"[i]}"
                    out.add(line(label, Nm.snap(r, 1e-11), s))
                }
                val residual = f(roots[0])
                val resText = if (abs(residual) < 1e-10 || residual.isNaN()) Nm.ZERO else Nm.real(residual)
                out.add(line("L − R", resText, s))
                s.copy(outputs = out, note = null)
            }
        } catch (e: CalcError) {
            s.copy(outputs = emptyList(), note = e.message)
        }
    }

    // ── TABLE ────────────────────────────────────────────────────────────────

    private fun recomputeTable(s: CalculatorState): CalculatorState {
        val fText = s.fields[0].text
        val gText = s.fields[1].text
        if (fText.isBlank()) {
            return s.copy(tableHeader = emptyList(), tableRows = emptyList(), note = "Enter f(X) to build a table.")
        }
        return try {
            val local = HashMap(vars)
            val f = Engine.parse(fText)
            val g = if (gText.isBlank()) null else Engine.parse(gText)
            if (s.fields[2].text.isBlank() || s.fields[3].text.isBlank() || s.fields[4].text.isBlank()) {
                throw CalcError("Start, End and Step are required")
            }
            val start = Engine.evaluate(s.fields[2].text, s.angle, local)
            val end = Engine.evaluate(s.fields[3].text, s.angle, local)
            val step = Engine.evaluate(s.fields[4].text, s.angle, local)
            if (Nm.isZero(step)) throw CalcError("Step must not be 0")
            val countD = floor((Nm.dbl(end) - Nm.dbl(start)) / Nm.dbl(step) + 1e-9) + 1.0
            if (countD < 1.0) throw CalcError("Step has the wrong direction")
            if (countD > 100.0) throw CalcError("Too many rows (max 100)")
            val ev = Evaluator(s.angle, local)
            val header = ArrayList<String>()
            header.add("X")
            header.add("f(X)")
            if (g != null) header.add("g(X)")
            val rows = ArrayList<List<String>>()
            for (i in 0 until countD.toInt()) {
                val x = Nm.add(start, Nm.mul(step, Nm.int(i.toLong())))
                local["X"] = x
                val row = ArrayList<String>()
                row.add(CalcFmt.display(x, s.fmtKind, s.fmtDigits, s.showFraction).plain)
                for (node in listOfNotNull(f, g)) {
                    row.add(
                        try {
                            ev.reset()
                            CalcFmt.display(ev.eval(node), s.fmtKind, s.fmtDigits, s.showFraction).plain
                        } catch (e: CalcError) {
                            "ERROR"
                        },
                    )
                }
                rows.add(row)
            }
            s.copy(tableHeader = header, tableRows = rows, note = null)
        } catch (e: CalcError) {
            s.copy(tableHeader = emptyList(), tableRows = emptyList(), note = e.message)
        }
    }

    // ── EQUATIONS ────────────────────────────────────────────────────────────

    private fun complexText(re: Value, im: Value, plus: Boolean, s: CalculatorState): String {
        val r = CalcFmt.display(re, s.fmtKind, s.fmtDigits, s.showFraction).plain
        val i = CalcFmt.display(im, s.fmtKind, s.fmtDigits, s.showFraction).plain
        val imStr = if (i == "1") "i" else if (i.contains('/')) "($i)i" else "${i}i"
        if (Nm.isZero(re)) return if (plus) imStr else "−$imStr"
        return "$r ${if (plus) "+" else "−"} $imStr"
    }

    private fun solveQuad(a: Value, b: Value, c: Value, s: CalculatorState, out: MutableList<OutLine>) {
        if (Nm.isZero(a)) {
            if (Nm.isZero(b)) throw CalcError(if (Nm.isZero(c)) "Infinitely many solutions" else "No solution")
            out.add(line("X", Nm.div(Nm.neg(c), b), s))
            return
        }
        val disc = Nm.sub(Nm.mul(b, b), Nm.mul(Nm.int(4), Nm.mul(a, c)))
        val twoA = Nm.mul(Nm.int(2), a)
        out.add(line("Δ", disc, s))
        if (Nm.signum(disc) >= 0) {
            val sq = Nm.sqrt(disc)
            out.add(line("X₁", Nm.div(Nm.add(Nm.neg(b), sq), twoA), s))
            out.add(line("X₂", Nm.div(Nm.sub(Nm.neg(b), sq), twoA), s))
        } else {
            val re = Nm.div(Nm.neg(b), twoA)
            val im = Nm.div(Nm.sqrt(Nm.neg(disc)), Nm.abs(twoA))
            out.add(OutLine("X₁", complexText(re, im, true, s)))
            out.add(OutLine("X₂", complexText(re, im, false, s)))
        }
        val h = Nm.div(Nm.neg(b), twoA)
        val k = Nm.sub(c, Nm.div(Nm.mul(b, b), Nm.mul(Nm.int(4), a)))
        val hs = CalcFmt.display(h, s.fmtKind, s.fmtDigits, s.showFraction).plain
        val ks = CalcFmt.display(k, s.fmtKind, s.fmtDigits, s.showFraction).plain
        out.add(OutLine("Vertex", "($hs, $ks)"))
    }

    private fun solveCubic(c: List<Double>, s: CalculatorState, out: MutableList<OutLine>) {
        val a = c[0]
        if (a == 0.0) throw CalcError("a must not be 0")
        val b1 = c[1] / a
        val c1 = c[2] / a
        val d1 = c[3] / a
        val p = c1 - b1 * b1 / 3.0
        val q = 2.0 * b1 * b1 * b1 / 27.0 - b1 * c1 / 3.0 + d1
        val shift = b1 / 3.0
        val disc = q * q / 4.0 + p * p * p / 27.0
        val scale = max(1.0, max(abs(q * q / 4.0), abs(p * p * p / 27.0)))
        val roots = ArrayList<Double>()
        var hasComplex = false
        var complexRe = 0.0
        var complexIm = 0.0
        if (abs(disc) <= 1e-12 * scale) {
            if (abs(p) < 1e-14 && abs(q) < 1e-14) {
                roots.add(-shift); roots.add(-shift); roots.add(-shift)
            } else {
                val t1 = 3.0 * q / p
                val t2 = -3.0 * q / (2.0 * p)
                roots.add(t1 - shift); roots.add(t2 - shift); roots.add(t2 - shift)
            }
        } else if (disc > 0) {
            val sq = sqrt(disc)
            val u = Math.cbrt(-q / 2.0 + sq)
            val w = Math.cbrt(-q / 2.0 - sq)
            roots.add(u + w - shift)
            complexRe = -(u + w) / 2.0 - shift
            complexIm = abs(sqrt(3.0) / 2.0 * (u - w))
            hasComplex = true
        } else {
            val r = 2.0 * sqrt(-p / 3.0)
            val arg = (3.0 * q / (2.0 * p)) * sqrt(-3.0 / p)
            val phi = acos(arg.coerceIn(-1.0, 1.0))
            for (kk in 0..2) roots.add(r * cos((phi - 2.0 * PI * kk) / 3.0) - shift)
        }
        val refined = roots.map { r0 ->
            var r = r0
            repeat(3) {
                val dp = (3.0 * a * r + 2.0 * c[1]) * r + c[2]
                if (abs(dp) > 1e-9) {
                    val pv = ((a * r + c[1]) * r + c[2]) * r + c[3]
                    r -= pv / dp
                }
            }
            r
        }.sorted()
        val names = listOf("X₁", "X₂", "X₃")
        refined.forEachIndexed { i, r -> out.add(line(names[i], Nm.snap(r, 1e-9), s)) }
        if (hasComplex) {
            val re = Nm.real(complexRe)
            val im = Nm.real(complexIm)
            out.add(OutLine("X₂", complexText(re, im, true, s)))
            out.add(OutLine("X₃", complexText(re, im, false, s)))
        }
    }

    private fun solveSystem(
        c: List<Value>, n: Int, names: List<String>, s: CalculatorState, out: MutableList<OutLine>,
    ) {
        val aug = Array(n) { i -> Array<Value>(n + 1) { j -> c[i * (n + 1) + j] } }
        val sol = Nm.solveLinear(aug) ?: throw CalcError("No unique solution")
        for (i in 0 until n) out.add(line(names[i], sol[i], s))
    }

    private fun recomputeEqn(s: CalculatorState): CalculatorState {
        if (s.fields.all { it.text.isBlank() }) {
            return s.copy(outputs = emptyList(), note = "Enter the coefficients.")
        }
        return try {
            val local = HashMap(vars)
            val c = s.fields.map {
                if (it.text.isBlank()) Nm.ZERO else Engine.evaluate(it.text, s.angle, local)
            }
            val out = ArrayList<OutLine>()
            when (s.eqnType) {
                EqnType.QUAD -> solveQuad(c[0], c[1], c[2], s, out)
                EqnType.CUBIC -> solveCubic(c.map { Nm.dbl(it) }, s, out)
                EqnType.LIN2 -> solveSystem(c, 2, listOf("X", "Y"), s, out)
                EqnType.LIN3 -> solveSystem(c, 3, listOf("X", "Y", "Z"), s, out)
            }
            s.copy(outputs = out, note = null)
        } catch (e: CalcError) {
            s.copy(outputs = emptyList(), note = e.message)
        }
    }

    // ── STATISTICS ───────────────────────────────────────────────────────────

    private fun recomputeStat(s: CalculatorState): CalculatorState {
        val dataCount = s.fields.size - 2
        val rows = dataCount / 2
        if ((0 until dataCount).all { s.fields[it].text.isBlank() }) {
            return s.copy(outputs = emptyList(), note = "Enter data, then press NEXT to move between cells.")
        }
        return try {
            val local = HashMap(vars)
            fun num(text: String): Double = Nm.dbl(Engine.evaluate(text, s.angle, local))
            val xs = ArrayList<Double>()
            val bs = ArrayList<Double>()
            for (r in 0 until rows) {
                val xt = s.fields[2 * r].text
                val bt = s.fields[2 * r + 1].text
                if (xt.isBlank() && bt.isBlank()) continue
                if (xt.isBlank()) throw CalcError("Row ${r + 1}: X is missing")
                if (s.statType.twoVar && bt.isBlank()) throw CalcError("Row ${r + 1}: Y is missing")
                xs.add(num(xt))
                bs.add(if (bt.isBlank()) 1.0 else num(bt))
            }
            if (xs.isEmpty()) throw CalcError("Enter at least one value")
            val out = if (s.statType == StatType.ONE_VAR) statOneVar(s, xs, bs) else statTwoVar(s, xs, bs, ::num)
            s.copy(outputs = out, note = null)
        } catch (e: CalcError) {
            s.copy(outputs = emptyList(), note = e.message)
        }
    }

    private fun statOneVar(s: CalculatorState, xs: List<Double>, fs: List<Double>): List<OutLine> {
        for (f in fs) {
            if (f < 0 || f != Math.rint(f) || f > 1e9) throw CalcError("Frequencies must be whole numbers")
        }
        var n = 0.0
        var sum = 0.0
        var sum2 = 0.0
        for (i in xs.indices) {
            n += fs[i]
            sum += xs[i] * fs[i]
            sum2 += xs[i] * xs[i] * fs[i]
        }
        if (n < 1) throw CalcError("Total frequency is 0")
        val mean = sum / n
        var ss = 0.0
        for (i in xs.indices) ss += fs[i] * (xs[i] - mean) * (xs[i] - mean)
        val order = xs.indices.sortedBy { xs[it] }
        val sx = DoubleArray(order.size) { xs[order[it]] }
        val cf = LongArray(order.size) { fs[order[it]].toLong() }
        val total = n.toLong()
        val med = Stats.medianRange(sx, cf, 0, total - 1)
        var q1 = Stats.medianRange(sx, cf, 0, total / 2 - 1)
        var q3 = Stats.medianRange(sx, cf, total - total / 2, total - 1)
        if (q1.isNaN()) q1 = med
        if (q3.isNaN()) q3 = med
        val out = ArrayList<OutLine>()
        out.add(plainLine("n", n, s))
        out.add(plainLine("x̄", mean, s))
        out.add(plainLine("Σx", sum, s))
        out.add(plainLine("Σx²", sum2, s))
        out.add(plainLine("σx", sqrt(ss / n), s))
        if (n > 1) out.add(plainLine("sx", sqrt(ss / (n - 1.0)), s))
        out.add(OutLine("Five-number summary", "", header = true))
        out.add(plainLine("min", sx[0], s))
        out.add(plainLine("Q1", q1, s))
        out.add(plainLine("Med", med, s))
        out.add(plainLine("Q3", q3, s))
        out.add(plainLine("max", sx[sx.size - 1], s))
        return out
    }

    private fun statTwoVar(
        s: CalculatorState, xl: List<Double>, yl: List<Double>, num: (String) -> Double,
    ): List<OutLine> {
        val xs = xl.toDoubleArray()
        val ys = yl.toDoubleArray()
        val n = xs.size.toDouble()
        val reg = Stats.fit(s.statType, xs, ys)
        val out = ArrayList<OutLine>()
        out.add(OutLine("Regression · ${s.statType.label}", "", header = true))
        for ((name, v) in reg.coefs) out.add(plainLine(name, v, s))
        val r = reg.r
        if (r != null && !r.isNaN()) out.add(plainLine("r", r, s))

        val dataCount = s.fields.size - 2
        val xhatText = s.fields[dataCount].text
        val yhatText = s.fields[dataCount + 1].text
        if (xhatText.isNotBlank() || yhatText.isNotBlank()) {
            out.add(OutLine("Prediction", "", header = true))
            if (xhatText.isNotBlank()) {
                val v = try {
                    plainLine("ŷ", reg.yOf(num(xhatText)), s)
                } catch (e: CalcError) {
                    OutLine("ŷ", e.message ?: "Math ERROR")
                }
                out.add(v)
            }
            if (yhatText.isNotBlank()) {
                try {
                    val sols = reg.xOf(num(yhatText))
                    if (sols.isEmpty()) out.add(OutLine("x̂", "No real solution"))
                    sols.forEachIndexed { i, x ->
                        out.add(plainLine(if (sols.size == 1) "x̂" else "x̂${"₁₂"[i]}", x, s))
                    }
                } catch (e: CalcError) {
                    out.add(OutLine("x̂", e.message ?: "Math ERROR"))
                }
            }
        }

        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in xs.indices) {
            sx += xs[i]; sy += ys[i]
            sxx += xs[i] * xs[i]; syy += ys[i] * ys[i]; sxy += xs[i] * ys[i]
        }
        val mx = sx / n
        val my = sy / n
        var ssx = 0.0
        var ssy = 0.0
        for (i in xs.indices) {
            ssx += (xs[i] - mx) * (xs[i] - mx)
            ssy += (ys[i] - my) * (ys[i] - my)
        }
        out.add(OutLine("Summary", "", header = true))
        out.add(plainLine("n", n, s))
        out.add(plainLine("x̄", mx, s))
        out.add(plainLine("ȳ", my, s))
        out.add(plainLine("σx", sqrt(ssx / n), s))
        out.add(plainLine("σy", sqrt(ssy / n), s))
        if (n > 1) {
            out.add(plainLine("sx", sqrt(ssx / (n - 1.0)), s))
            out.add(plainLine("sy", sqrt(ssy / (n - 1.0)), s))
        }
        out.add(plainLine("Σx", sx, s))
        out.add(plainLine("Σy", sy, s))
        out.add(plainLine("Σx²", sxx, s))
        out.add(plainLine("Σy²", syy, s))
        out.add(plainLine("Σxy", sxy, s))
        return out
    }

    // ── BASE-N ───────────────────────────────────────────────────────────────

    private fun recomputeBase(s: CalculatorState): CalculatorState {
        val text = s.activeField.text
        if (text.isBlank()) return s.copy(outputs = emptyList(), note = null)
        return try {
            val v = BaseCalc.eval(balance(text), s.baseRadix)
            s.copy(
                outputs = listOf(
                    OutLine("HEX", BaseCalc.render(v, 16, true)),
                    OutLine("DEC", BaseCalc.render(v, 10, true)),
                    OutLine("OCT", BaseCalc.render(v, 8, true)),
                    OutLine("BIN", BaseCalc.render(v, 2, true)),
                ),
                note = null,
            )
        } catch (e: CalcError) {
            s.copy(outputs = emptyList(), note = if (e.message == "Syntax ERROR") null else e.message)
        }
    }

    // ── CONVERT ──────────────────────────────────────────────────────────────

    private fun recomputeConvert(s: CalculatorState): CalculatorState {
        val text = s.activeField.text
        if (text.isBlank()) return s.copy(outputs = emptyList(), note = null)
        val cat = CalcUnits.categories.getOrNull(s.convCategory) ?: return s
        return try {
            val x = Nm.dbl(Engine.evaluate(balance(text), s.angle, HashMap(vars)))
            val out = ArrayList<OutLine>()
            val main = CalcUnits.convert(cat, s.convFrom, s.convTo, x)
            out.add(line(cat.units[s.convTo].symbol, Nm.snap(main, 1e-12), s))
            out.add(OutLine("All ${cat.name.lowercase()} units", "", header = true))
            cat.units.forEachIndexed { i, u ->
                if (i != s.convFrom) {
                    out.add(line(u.symbol, Nm.snap(CalcUnits.convert(cat, s.convFrom, i, x), 1e-12), s))
                }
            }
            s.copy(outputs = out, note = null)
        } catch (e: CalcError) {
            s.copy(outputs = emptyList(), note = if (e.message == "Syntax ERROR") null else e.message)
        }
    }
}
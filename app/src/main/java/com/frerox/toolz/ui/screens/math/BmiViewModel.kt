package com.frerox.toolz.ui.screens.math

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.pow

// ─────────────────────────────────────────────────────────────
//  Domain enums
// ─────────────────────────────────────────────────────────────

enum class Gender { MALE, FEMALE }

enum class ActivityLevel(
    val multiplier: Float,
    val label: String,
    val shortLabel: String,
) {
    SEDENTARY (1.20f, "Sedentary",   "SED"),
    LIGHT     (1.375f,"Light Active", "LGT"),
    MODERATE  (1.55f, "Moderate",    "MOD"),
    ACTIVE    (1.725f,"Very Active",  "ACT"),
    EXTREME   (1.90f, "Extreme",     "EXT"),
}

// ─────────────────────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────────────────────

data class BmiState(
    // ── Inputs ─────────────────────────────────────────────────
    val weight    : String        = "",
    val height    : String        = "",
    val age       : String        = "",
    val gender    : Gender        = Gender.MALE,
    val isCm      : Boolean       = true,
    val isKg      : Boolean       = true,
    val activity  : ActivityLevel = ActivityLevel.SEDENTARY,

    // ── Computed (all nullable — null means "not enough input") ──
    val bmi               : Float? = null,
    val category          : String = "",
    /** Age-adjusted healthy BMI range. */
    val healthyRange      : Pair<Float, Float> = 18.5f to 24.9f,
    /** Basal Metabolic Rate (kcal/day). */
    val bmr               : Float? = null,
    /** Total Daily Energy Expenditure (kcal/day). */
    val tdee              : Float? = null,
    /**
     * Ideal Body Weight in the currently selected unit
     * (kg when [isKg] = true, lb when false).
     */
    val ibw               : Float? = null,
    /** Body Fat Percentage (Deurenberg estimate). */
    val bfp               : Float? = null,
    /**
     * How far the user's current weight is from IBW,
     * in the currently selected unit.
     * Positive = need to lose, negative = need to gain.
     */
    val weightDifference  : Float? = null,
)

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class BmiViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(BmiState())
    val uiState: StateFlow<BmiState> = _uiState.asStateFlow()

    // ── Input handlers ─────────────────────────────────────────

    fun onWeightChange(weight: String) {
        val cleaned = weight.filter { it.isDigit() || it == '.' }
        _uiState.update { recalculate(it.copy(weight = cleaned)) }
    }

    fun onHeightChange(height: String) {
        val cleaned = height.filter { it.isDigit() || it == '.' || it == '\'' || it == '"' || it == ' ' }
        _uiState.update { recalculate(it.copy(height = cleaned)) }
    }

    fun onAgeChange(age: String) {
        val cleaned = age.filter { it.isDigit() }
        _uiState.update { recalculate(it.copy(age = cleaned)) }
    }

    fun onGenderChange(gender: Gender) {
        _uiState.update { recalculate(it.copy(gender = gender)) }
    }

    fun toggleUnit(isHeight: Boolean) {
        _uiState.update { state ->
            val next = if (isHeight) state.copy(isCm = !state.isCm)
            else          state.copy(isKg = !state.isKg)
            recalculate(next)
        }
    }

    fun onActivityChange(level: ActivityLevel) {
        _uiState.update { recalculate(it.copy(activity = level)) }
    }

    // ── Calculation pipeline ───────────────────────────────────

    /**
     * Pure function: given an input [state], compute all derived metrics
     * and return a fully populated [BmiState].
     *
     * Taking the state as a parameter (rather than reading [_uiState.value]
     * inside the function) makes this deterministic and testable, and
     * prevents stale-read bugs when called from inside [_uiState.update].
     */
    private fun recalculate(state: BmiState): BmiState {
        val weightVal = state.weight.toFloatOrNull() ?: 0f
        val ageVal    = state.age.toIntOrNull() ?: 0

        val weightInKg  = if (state.isKg) weightVal else weightVal * LB_TO_KG
        val heightInCm  = if (state.isCm) state.height.toFloatOrNull() ?: 0f
        else            parseImperialHeight(state.height) * IN_TO_CM
        val heightInM   = heightInCm / 100f

        // Guard: need plausible inputs
        if (weightInKg < 1f || heightInM < 0.3f || ageVal < 2) {
            return state.copy(
                bmi = null, category = "", bmr = null,
                tdee = null, ibw = null, bfp = null,
                weightDifference = null,
            )
        }

        // 1. BMI ─────────────────────────────────────────────────
        val bmi = weightInKg / heightInM.pow(2)

        // 2. Age-adjusted range & category ──────────────────────
        val range = if (ageVal >= 65) 22.0f to 27.0f else 18.5f to 24.9f
        val category = when {
            bmi < range.first  -> "Underweight"
            bmi <= range.second-> "Healthy"
            bmi < 30f          -> "Overweight"
            bmi < 35f          -> "Obese Class I"
            bmi < 40f          -> "Obese Class II"
            else               -> "Obese Class III"
        }

        // 3. BMR — Mifflin-St Jeor equation ─────────────────────
        //    Men:   10W + 6.25H - 5A + 5
        //    Women: 10W + 6.25H - 5A - 161
        val bmr = (10f * weightInKg) + (6.25f * heightInCm) -
                (5f  * ageVal.toFloat()) +
                if (state.gender == Gender.MALE) 5f else -161f

        // 4. TDEE — BMR × activity multiplier ───────────────────
        val tdee = bmr * state.activity.multiplier

        // 5. Ideal Body Weight — Devine formula ─────────────────
        //    Men:   50   + 2.3 × (height_in_inches - 60)
        //    Women: 45.5 + 2.3 × (height_in_inches - 60)
        val heightInInches  = heightInCm / IN_TO_CM
        val inchesOver5Feet = (heightInInches - 60f).coerceAtLeast(0f)
        val ibwKg = (if (state.gender == Gender.MALE) 50f else 45.5f) +
                (2.3f * inchesOver5Feet)
        // Convert to the user's selected unit for display
        val ibwDisplay = if (state.isKg) ibwKg else ibwKg * KG_TO_LB

        // 6. Body Fat % — Deurenberg formula ────────────────────
        //    (1.20 × BMI) + (0.23 × age) − (10.8 × sex) − 5.4
        //    sex = 1 for male, 0 for female
        val sexFactor = if (state.gender == Gender.MALE) 1f else 0f
        val bfpRaw = (1.20f * bmi) + (0.23f * ageVal) - (10.8f * sexFactor) - 5.4f
        // Clamp to physiologically plausible range
        val bfp = bfpRaw.coerceIn(3f, 60f)

        // 7. Weight difference from ideal (in selected unit) ────
        val weightDiff = if (state.isKg) weightInKg - ibwKg
        else            (weightInKg - ibwKg) * KG_TO_LB

        return state.copy(
            bmi              = bmi,
            category         = category,
            healthyRange     = range,
            bmr              = bmr,
            tdee             = tdee,
            ibw              = ibwDisplay,
            bfp              = bfp,
            weightDifference = weightDiff,
        )
    }

    // ── Helpers ────────────────────────────────────────────────

    /**
     * Parses imperial height strings of the form `5' 11"`, `5'11"`, `5.9`, or plain
     * feet-as-decimal. Returns the value in **inches**.
     */
    private fun parseImperialHeight(height: String): Float = try {
        when {
            height.contains('\'') -> {
                val parts   = height.split('\'')
                val feet    = parts[0].trim().toFloatOrNull() ?: 0f
                val inches  = parts.getOrNull(1)
                    ?.replace("\"", "")?.trim()?.toFloatOrNull() ?: 0f
                feet * 12f + inches
            }
            else -> height.toFloatOrNull() ?: 0f
        }
    } catch (_: Exception) { 0f }

    companion object {
        private const val LB_TO_KG  = 0.453592f
        private const val KG_TO_LB  = 2.204623f
        private const val IN_TO_CM  = 2.54f
    }
}
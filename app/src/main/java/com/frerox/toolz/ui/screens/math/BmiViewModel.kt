package com.frerox.toolz.ui.screens.math

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt

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

    // ── Computed ──
    val bmi               : Float? = null,
    val oxfordBmi         : Float? = null,
    val ponderalIndex     : Float? = null,
    val category          : String = "",
    /** Age-adjusted healthy BMI range. */
    val healthyRange      : Pair<Float, Float> = 18.5f to 24.9f,
    /** Healthy weight range for this height in current unit. */
    val weightRange       : Pair<Float, Float>? = null,
    /** Basal Metabolic Rate (kcal/day). */
    val bmr               : Float? = null,
    /** Total Daily Energy Expenditure (kcal/day). */
    val tdee              : Float? = null,
    /** Ideal Body Weight in the currently selected unit. */
    val ibw               : Float? = null,
    /** Body Fat Percentage (Deurenberg estimate). */
    val bfp               : Float? = null,
    /** Lean Body Mass (Boer formula) */
    val lbm               : Float? = null,
    /** Body Surface Area (Mosteller formula) */
    val bsa               : Float? = null,
    /** Water intake recommendation (liters/day) */
    val waterIntake       : Float? = null,
    /** Macronutrients (Grams) */
    val protein           : Float? = null,
    val carbs             : Float? = null,
    val fats              : Float? = null,
    /** How far the user's current weight is from IBW. */
    val weightDifference  : Float? = null,
)

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class BmiViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(BmiState())
    val uiState: StateFlow<BmiState> = _uiState.asStateFlow()

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

    private fun recalculate(state: BmiState): BmiState {
        val weightVal = state.weight.toFloatOrNull() ?: 0f
        val ageVal    = state.age.toIntOrNull() ?: 0

        val weightInKg  = if (state.isKg) weightVal else weightVal * LB_TO_KG
        val heightInCm  = if (state.isCm) state.height.toFloatOrNull() ?: 0f
        else            parseImperialHeight(state.height) * IN_TO_CM
        val heightInM   = heightInCm / 100f

        if (weightInKg < 1f || heightInM < 0.3f || ageVal < 2) {
            return state.copy(
                bmi = null, oxfordBmi = null, ponderalIndex = null,
                category = "", bmr = null, tdee = null, ibw = null,
                bfp = null, lbm = null, bsa = null, waterIntake = null,
                protein = null, carbs = null, fats = null,
                weightRange = null, weightDifference = null,
            )
        }

        // 1. BMI
        val bmi = weightInKg / heightInM.pow(2)
        val oxfordBmi = 1.3f * weightInKg / heightInM.pow(2.5f)
        val ponderalIndex = weightInKg / heightInM.pow(3)

        // 2. Age-adjusted range & category
        // Granular age-based ranges for better feedback
        val range = when {
            ageVal >= 65 -> 23.0f to 29.0f
            ageVal >= 55 -> 22.5f to 28.5f
            ageVal >= 45 -> 22.0f to 28.0f
            ageVal >= 35 -> 21.0f to 27.0f
            ageVal >= 25 -> 20.0f to 26.0f
            else         -> 18.5f to 24.9f
        }
        
        val category = when {
            bmi < range.first  -> "Underweight"
            bmi <= range.second-> "Healthy"
            bmi < 30f          -> "Overweight"
            bmi < 35f          -> "Obese Class I"
            bmi < 40f          -> "Obese Class II"
            else               -> "Obese Class III"
        }

        // Weight range for height
        val minWeightKg = range.first * heightInM.pow(2)
        val maxWeightKg = range.second * heightInM.pow(2)
        val weightRange = if (state.isKg) minWeightKg to maxWeightKg 
                         else (minWeightKg * KG_TO_LB) to (maxWeightKg * KG_TO_LB)

        // 3. BMR (Mifflin-St Jeor)
        val bmr = (10f * weightInKg) + (6.25f * heightInCm) -
                (5f  * ageVal.toFloat()) +
                if (state.gender == Gender.MALE) 5f else -161f

        // 4. TDEE
        val tdee = bmr * state.activity.multiplier
        
        // Macros (30% protein, 40% carbs, 30% fats)
        val protein = (tdee * 0.30f) / 4f
        val carbs   = (tdee * 0.40f) / 4f
        val fats    = (tdee * 0.30f) / 9f

        // 5. Ideal Body Weight (Devine)
        val heightInInches  = heightInCm / IN_TO_CM
        val inchesOver5Feet = (heightInInches - 60f).coerceAtLeast(0f)
        val ibwKg = (if (state.gender == Gender.MALE) 50f else 45.5f) +
                (2.3f * inchesOver5Feet)
        val ibwDisplay = if (state.isKg) ibwKg else ibwKg * KG_TO_LB

        // 6. Body Fat % (Deurenberg formula)
        // sex = 1 for male, 0 for female
        val sexFactor = if (state.gender == Gender.MALE) 1f else 0f
        val bfpRaw = (1.20f * bmi) + (0.23f * ageVal.toFloat()) - (10.8f * sexFactor) - 5.4f
        val bfp = bfpRaw.coerceIn(3f, 60f)
        
        // 7. Lean Body Mass (Boer formula)
        val lbm = if (state.gender == Gender.MALE) {
            (0.407f * weightInKg) + (0.267f * heightInCm) - 19.2f
        } else {
            (0.252f * weightInKg) + (0.473f * heightInCm) - 48.3f
        }
        val lbmDisplay = if (state.isKg) lbm else lbm * KG_TO_LB

        // 8. Body Surface Area (Mosteller)
        val bsa = sqrt((heightInCm * weightInKg) / 3600f)
        
        // 9. Water Intake (Approx 33ml per kg)
        val waterIntake = weightInKg * 0.033f

        // 10. Weight difference from ideal
        val weightDiff = if (state.isKg) weightInKg - ibwKg
        else            (weightInKg - ibwKg) * KG_TO_LB

        return state.copy(
            bmi              = bmi,
            oxfordBmi        = oxfordBmi,
            ponderalIndex    = ponderalIndex,
            category         = category,
            healthyRange     = range,
            weightRange      = weightRange,
            bmr              = bmr,
            tdee             = tdee,
            ibw              = ibwDisplay,
            bfp              = bfp,
            lbm              = lbmDisplay,
            bsa              = bsa,
            waterIntake       = waterIntake,
            protein          = protein,
            carbs            = carbs,
            fats             = fats,
            weightDifference = weightDiff,
        )
    }

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
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val description: String,
) {
    SEDENTARY (1.20f, "Sedentary",   "SED", "Desk job, little to no exercise"),
    LIGHT     (1.375f,"Lightly Active", "LGT", "Light exercise 1-3 days/week"),
    MODERATE  (1.55f, "Moderately Active", "MOD", "Moderate exercise 3-5 days/week"),
    ACTIVE    (1.725f,"Very Active",  "ACT", "Hard exercise 6-7 days/week"),
    EXTREME   (1.90f, "Extra Active",     "EXT", "Physical job or training 2x/day"),
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
    /** Personalized insight text. */
    val insight           : String = "",
    /** Quiz visibility. */
    val showQuiz          : Boolean = false,
    /** Calorie goals. */
    val lossCalories      : Float? = null,
    val gainCalories      : Float? = null,
    /** Bio-Impact offsets for transparency. */
    val genderBmrOffset   : Float = 0f,
    val genderBfpOffset   : Float = 0f,
)

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class BmiViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BmiState())
    val uiState: StateFlow<BmiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val h = repository.bmiHeight.first()
            val w = repository.bmiWeight.first()
            val a = repository.bmiAge.first()
            val g = repository.bmiGender.first()
            val act = repository.bmiActivity.first()
            val isKg = repository.bmiIsKg.first()
            val isCm = repository.bmiIsCm.first()

            val initialState = BmiState(
                height = h,
                weight = w,
                age = a,
                gender = Gender.valueOf(g),
                activity = ActivityLevel.valueOf(act),
                isKg = isKg,
                isCm = isCm
            )
            _uiState.update { recalculate(initialState) }
        }
    }

    fun onWeightChange(weight: String) {
        val cleaned = weight.filter { it.isDigit() || it == '.' }
        _uiState.update { recalculate(it.copy(weight = cleaned)) }
        viewModelScope.launch { repository.setBmiWeight(cleaned) }
    }

    fun onHeightChange(height: String) {
        val cleaned = height.filter { it.isDigit() || it == '.' || it == '\'' || it == '"' || it == ' ' }
        _uiState.update { recalculate(it.copy(height = cleaned)) }
        viewModelScope.launch { repository.setBmiHeight(cleaned) }
    }

    fun onHeightSliderChange(inchesOrCm: Float) {
        _uiState.update { state ->
            val heightStr = if (state.isCm) {
                "%.0f".format(inchesOrCm)
            } else {
                val feet = (inchesOrCm / 12).toInt()
                val inches = (inchesOrCm % 12).toInt()
                "$feet' $inches\""
            }
            val newState = recalculate(state.copy(height = heightStr))
            viewModelScope.launch { repository.setBmiHeight(heightStr) }
            newState
        }
    }

    fun onAgeChange(age: String) {
        val cleaned = age.filter { it.isDigit() }
        _uiState.update { recalculate(it.copy(age = cleaned)) }
        viewModelScope.launch { repository.setBmiAge(cleaned) }
    }

    fun onGenderChange(gender: Gender) {
        _uiState.update { recalculate(it.copy(gender = gender)) }
        viewModelScope.launch { repository.setBmiGender(gender.name) }
    }

    fun toggleUnit(isHeight: Boolean) {
        _uiState.update { state ->
            val next = if (isHeight) state.copy(isCm = !state.isCm)
            else          state.copy(isKg = !state.isKg)
            viewModelScope.launch {
                if (isHeight) repository.setBmiIsCm(next.isCm)
                else          repository.setBmiIsKg(next.isKg)
            }
            recalculate(next)
        }
    }

    fun onActivityChange(level: ActivityLevel) {
        _uiState.update { recalculate(it.copy(activity = level)) }
        viewModelScope.launch { repository.setBmiActivity(level.name) }
    }

    fun toggleQuiz(show: Boolean) {
        _uiState.update { it.copy(showQuiz = show) }
    }

    fun applyQuizResult(totalScore: Int) {
        // Score range: 0 to 12+
        val level = when {
            totalScore <= 2 -> ActivityLevel.SEDENTARY
            totalScore <= 5 -> ActivityLevel.LIGHT
            totalScore <= 8 -> ActivityLevel.MODERATE
            totalScore <= 11 -> ActivityLevel.ACTIVE
            else -> ActivityLevel.EXTREME
        }
        _uiState.update { recalculate(it.copy(activity = level, showQuiz = false)) }
        viewModelScope.launch { repository.setBmiActivity(level.name) }
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
        // Modern geriatric standards suggest 23-30 is healthy for 65+
        val range = when {
            ageVal >= 65 -> 23.0f to 29.9f
            ageVal >= 55 -> 22.0f to 28.5f
            ageVal >= 45 -> 21.0f to 27.5f
            ageVal >= 35 -> 20.0f to 26.5f
            ageVal >= 25 -> 19.5f to 25.5f
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
        
        val lossCalories = (tdee - 500f).coerceAtLeast(1200f)
        val gainCalories = tdee + 500f
        
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

        // 11. Insight
        val insight = generateInsight(category, bmi, ageVal, weightDiff, state.isKg)

        // 12. Bio-Impact exposure
        val bmrOffset = if (state.gender == Gender.MALE) 5f else -161f
        val bfpOffset = if (state.gender == Gender.MALE) -10.8f else 0f

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
            insight          = insight,
            lossCalories     = lossCalories,
            gainCalories     = gainCalories,
            genderBmrOffset  = bmrOffset,
            genderBfpOffset  = bfpOffset,
        )
    }

    private fun generateInsight(category: String, bmi: Float, age: Int, diff: Float, isKg: Boolean): String {
        val unit = if (isKg) "kg" else "lb"
        val absDiff = kotlin.math.abs(diff)
        
        return when {
            bmi < 16 -> "Your BMI is significantly low. Please consult a healthcare provider for personalized guidance."
            bmi < 18.5 -> "You are in the underweight range. Focusing on nutrient-dense foods may help you reach a healthier weight."
            bmi <= 24.9 -> "Great job! You are in the healthy weight range. Maintaining a balanced diet and active lifestyle is key."
            bmi < 30 -> {
                if (absDiff < 2f) "You're very close to your ideal weight range. Small adjustments could make a big difference!"
                else "You're in the overweight range. Consider increasing activity levels or adjusting your caloric intake."
            }
            else -> "Your BMI is in the obese range. Aiming for a gradual weight loss of 5-10% can significantly improve your health markers."
        }
    }


    companion object {
        const val LB_TO_KG  = 0.453592f
        const val KG_TO_LB  = 2.204623f
        const val IN_TO_CM  = 2.54f

        fun parseImperialHeight(height: String): Float = try {
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
    }
}
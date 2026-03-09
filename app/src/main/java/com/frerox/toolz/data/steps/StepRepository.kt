package com.frerox.toolz.data.steps

import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepRepository @Inject constructor(
    private val stepDao: StepDao
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val todayStr: String get() = dateFormat.format(Date())

    val currentSteps: Flow<Int> = stepDao.getStepsForDate(todayStr)
        .map { it?.steps ?: 0 }

    val weeklySteps: Flow<List<StepEntry>> = stepDao.getRecentSteps()

    suspend fun updateSteps(steps: Int) {
        stepDao.insertOrUpdateSteps(StepEntry(todayStr, steps))
    }
}

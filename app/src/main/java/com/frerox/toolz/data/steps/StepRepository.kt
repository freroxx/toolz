package com.frerox.toolz.data.steps

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepRepository @Inject constructor(
    private val stepDao: StepDao
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // ---------------------------------------------------------------------------
    // Midnight-safe date flow
    // Emits today's date string and re-emits every time the date changes.
    // This fixes the critical bug where `todayStr` was captured at init time
    // and never refreshed across midnight.
    // ---------------------------------------------------------------------------
    private val _todayDate: Flow<String> = flow {
        while (true) {
            val now = Calendar.getInstance()
            val today = dateFormat.format(now.time)
            emit(today)

            // Sleep until just after midnight (+ 1 second buffer)
            val midnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 1)
                set(Calendar.MILLISECOND, 0)
            }
            val msUntilMidnight = midnight.timeInMillis - System.currentTimeMillis()
            delay(msUntilMidnight.coerceAtLeast(1_000L))
        }
    }.distinctUntilChanged()

    /** Today's step count — automatically refreshes at midnight. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSteps: Flow<Int> = _todayDate.flatMapLatest { date ->
        stepDao.getStepsForDate(date).map { it?.steps ?: 0 }
    }

    val weeklySteps: Flow<List<StepEntry>> = stepDao.getRecentSteps()

    fun getStepsForLastNDays(days: Int): Flow<List<StepEntry>> {
        val calendar = Calendar.getInstance()
        val endDate = dateFormat.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -(days - 1))
        val startDate = dateFormat.format(calendar.time)
        return stepDao.getStepsInRange(startDate, endDate)
    }

    fun getStepsInRange(startDate: String, endDate: String): Flow<List<StepEntry>> =
        stepDao.getStepsInRange(startDate, endDate)

    /** Called by the UI / ViewModel to update today's step count. */
    suspend fun updateSteps(steps: Int) {
        val today = dateFormat.format(Date())
        val exists = stepDao.countForDate(today) > 0
        if (exists) {
            stepDao.atomicUpdateSteps(today, steps, steps)
        } else {
            stepDao.insertOrUpdateSteps(StepEntry(today, steps, steps))
        }
    }

    /**
     * Called by [StepCounterService] sensor handler with the raw cumulative
     * sensor value and the already-computed step delta for today.
     * Uses the atomic UPDATE path when the row already exists.
     */
    suspend fun updateStepsFromSensor(date: String, newStepCount: Int, rawSensorValue: Int) {
        val exists = stepDao.countForDate(date) > 0
        if (exists) {
            stepDao.atomicUpdateSteps(date, newStepCount, rawSensorValue)
        } else {
            stepDao.insertOrUpdateSteps(StepEntry(date, newStepCount, rawSensorValue))
        }
    }

    /**
     * Schedules a background cleanup pass.  Called internally; the ViewModel
     * should not call this directly.
     */
    suspend fun cleanupOldSteps(retentionDays: Int) {
        if (retentionDays <= 0) return // 0 or less means "Forever"
        withContext(Dispatchers.IO) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -retentionDays)
            val cutoffDate = dateFormat.format(calendar.time)
            stepDao.deleteStepsBeforeDate(cutoffDate)
        }
    }

    /** Convenience — the today date string used by calling code. */
    val todayString: String get() = dateFormat.format(Date())
}

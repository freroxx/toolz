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

package com.frerox.toolz.data.steps

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepRepository @Inject constructor(
    private val stepDao: StepDao
) {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // ---------------------------------------------------------------------------
    // Midnight-safe date flow
    // Emits today's date string and re-emits every time the date changes.
    // This fixes the critical bug where `todayStr` was captured at init time
    // and never refreshed across midnight.
    // ---------------------------------------------------------------------------
    private val _todayDate: Flow<String> = flow {
        while (true) {
            emit(LocalDate.now().format(dateFormatter))

            // Sleep until just after midnight (+ 1 second buffer)
            val nowMs = System.currentTimeMillis()
            val tomorrowMidnightMs = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 1)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            delay((tomorrowMidnightMs - nowMs).coerceAtLeast(1_000L))
        }
    }.distinctUntilChanged()

    /** Today's step count — automatically refreshes at midnight. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSteps: Flow<Int> = _todayDate.flatMapLatest { date ->
        stepDao.getStepsForDate(date).map { it?.steps ?: 0 }
    }

    val weeklySteps: Flow<List<StepEntry>> = stepDao.getRecentSteps()

    fun getStepsForLastNDays(days: Int): Flow<List<StepEntry>> {
        val end = LocalDate.now()
        val start = end.minusDays((days - 1).toLong())
        return stepDao.getStepsInRange(start.format(dateFormatter), end.format(dateFormatter))
    }

    fun getStepsInRange(startDate: String, endDate: String): Flow<List<StepEntry>> =
        stepDao.getStepsInRange(startDate, endDate)

    /**
     * Hot-path delta insert. Uses true SQL atomic increment, safe under
     * concurrent emissions. Creates the row if missing.
     */
    suspend fun addStepsDelta(date: String, delta: Int, rawSensorValue: Int) {
        if (delta <= 0) return
        val updated = stepDao.incrementSteps(date, delta, rawSensorValue)
        if (updated == 0) {
            // No row yet — insert. If a concurrent insert wins, fall back to increment.
            try {
                stepDao.insertOrUpdateSteps(StepEntry(date, delta, rawSensorValue))
            } catch (_: Exception) {
                stepDao.incrementSteps(date, delta, rawSensorValue)
            }
        }
    }

    /** Convenience overload that resolves today's date with a thread-safe formatter. */
    suspend fun addStepsDeltaToday(delta: Int, rawSensorValue: Int) {
        addStepsDelta(LocalDate.now().format(dateFormatter), delta, rawSensorValue)
    }

    /** Called by the UI / ViewModel to update today's step count. */
    suspend fun updateSteps(steps: Int) {
        val today = LocalDate.now().format(dateFormatter)
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
            val cutoffDate = LocalDate.now().minusDays(retentionDays.toLong()).format(dateFormatter)
            stepDao.deleteStepsBeforeDate(cutoffDate)
        }
    }

    /** Convenience — the today date string used by calling code. */
    val todayString: String get() = LocalDate.now().format(dateFormatter)
}

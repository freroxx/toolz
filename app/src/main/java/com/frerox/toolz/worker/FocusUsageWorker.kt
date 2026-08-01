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

package com.frerox.toolz.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.focus.AppUsageInfo
import com.frerox.toolz.data.focus.UsageStatsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

@HiltWorker
class FocusUsageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val usageRepository: UsageStatsRepository
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "FocusUsageWorker"
        private const val PREFS_USAGE_CACHE = "focus_daily_usage_cache"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (!usageRepository.hasUsageStatsPermission()) {
                Log.w(TAG, "No usage stats permission, skipping.")
                return@withContext Result.failure()
            }

            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val todayKey = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

            // Get start of today
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startTime = cal.timeInMillis
            val endTime = System.currentTimeMillis()

            val usageList = usageRepository.queryDailyByEvents(startTime, endTime)
            
            if (usageList.isNotEmpty()) {
                saveUsageToPrefs(todayKey, usageList)
                Log.d(TAG, "Snapshot saved for $todayKey with ${usageList.size} apps")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error snapshotting usage", e)
            Result.retry()
        }
    }

    private fun saveUsageToPrefs(dateKey: String, usageList: List<AppUsageInfo>) {
        try {
            val prefs = applicationContext.getSharedPreferences(PREFS_USAGE_CACHE, Context.MODE_PRIVATE)
            
            // Optimization: check if data changed significantly (optional but good for SSD wear)
            val jsonArray = JSONArray()
            usageList.forEach { info ->
                val obj = JSONObject()
                obj.put("pkg", info.packageName)
                obj.put("name", info.appName)
                obj.put("time", info.usageTimeMillis)
                jsonArray.put(obj)
            }
            
            val newJson = jsonArray.toString()
            val oldJson = prefs.getString(dateKey, null)
            
            if (newJson != oldJson) {
                prefs.edit().putString(dateKey, newJson).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save daily usage locally", e)
        }
    }
}

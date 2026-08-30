package com.frerox.toolz.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.purgeshot.PurgeShotRepository
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.PurgeShotDetector
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class PurgeShotDetectWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PurgeShotRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            // Delegate to single source of truth — unified heuristic + freshness + dedup + handler routing.
            // Previously this worker duplicated query/heuristic with different sort/age and could miss
            // screenshots that the live observer caught, or pick a different "latest" image.
            val handled = PurgeShotDetector.detectAndHandle(
                context = applicationContext,
                repository = repository,
                settingsRepository = settingsRepository,
                awaitSettle = false,
                isPoll = true
            )
            Log.d("PurgeShotDetect", "poll handled=$handled")
            // Keep JobScheduler alive — re-arm content trigger each poll if still enabled
            if (settingsRepository.purgeShotEnabled.first()) {
                try { com.frerox.toolz.service.PurgeShotObserverJobService.schedule(applicationContext) } catch (_: Exception) {}
            }
            Result.success()
        } catch (e: Exception) {
            Log.w("PurgeShotDetect", "poll failed", e)
            Result.retry()
        }
    }
}

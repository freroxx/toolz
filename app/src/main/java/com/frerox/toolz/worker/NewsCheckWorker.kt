package com.frerox.toolz.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.news.NewsNotifier
import com.frerox.toolz.data.news.NewsRepository
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class NewsCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val newsRepository: NewsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val ok = newsRepository.syncIfStale()
            if (!ok) return Result.success()
            val notifOn = settingsRepository.newsNotificationsEnabled.first()
            val notified = settingsRepository.newsNotifiedIds.first()
            // Notify up to 3 newly-eligible items so stacked drops aren't lost;
            // each id notifies exactly once (notified_ids guard).
            for (candidate in newsRepository.notificationCandidates(limit = 3)) {
                if (candidate.id in notified) continue
                if (!notifOn && candidate.priority != "critical") continue
                NewsNotifier.post(applicationContext, candidate)
                settingsRepository.addNewsNotified(candidate.id)
            }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("NewsCheck", "News check failed", e)
            Result.success()
        }
    }
}

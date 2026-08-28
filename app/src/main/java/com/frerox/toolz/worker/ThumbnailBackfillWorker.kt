/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.music.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// P1-13 BackfillWorker: batches thumbs lazily so scanDevice inserts stay <800ms
@HiltWorker
class ThumbnailBackfillWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val musicRepository: MusicRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG_THUMB_BACKFILL = "thumbnail_backfill"
        private const val BATCH_SIZE = 50
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // use public fixAll which already batches isThumbnailValid==false or catalog download
            // but to avoid blocking, we chunk with semaphore
            val all = musicRepository.getAllTracksSyncForBackfill()
            val needing = all.filter { track ->
                val thumb = track.thumbnailUri
                thumb.isNullOrBlank() || thumb.startsWith("content://media/external/audio/albumart") || thumb == track.uri
            }.take(300) // cap per run to avoid battery drain; WorkManager periodic will catch rest

            if (needing.isEmpty()) return@withContext Result.success()

            val sem = Semaphore(4)
            coroutineScope {
                needing.chunked(BATCH_SIZE).forEach { chunk ->
                    chunk.map { track ->
                        async {
                            sem.withPermit {
                                musicRepository.fixThumbnailForTrack(track)
                            }
                        }
                    }.awaitAll()
                }
            }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("ThumbnailBackfill", "backfill failed", e)
            Result.failure()
        }
    }
}

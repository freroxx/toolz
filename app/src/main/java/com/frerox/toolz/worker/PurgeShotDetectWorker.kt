package com.frerox.toolz.worker

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.purgeshot.PurgeShotRepository
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.PurgeShotService
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
            if (!settingsRepository.purgeShotEnabled.first()) return Result.success()
            // Poll latest screenshot — catches missed events when observer was dead (outside Toolz on OEMs)
            val candidate = queryLatest(applicationContext.contentResolver) ?: return Result.success()
            val age = System.currentTimeMillis() - candidate.dateAddedMs
            if (age > 60_000 || age < -2000) return Result.success() // only very recent
            val stored = settingsRepository.purgeShotLastScreenshotUri.first()
            if (candidate.uri.toString() == stored) return Result.success()
            Log.d("PurgeShotDetect", "Poll found ${candidate.displayName} age $age")
            com.frerox.toolz.data.purgeshot.PurgeShotHandler.handleNewScreenshot(applicationContext, repository, settingsRepository, candidate)
            Result.success()
        } catch (e: Exception) {
            Log.w("PurgeShotDetect", "poll failed", e)
            Result.retry()
        }
    }

    private fun queryLatest(cr: ContentResolver): PurgeShotService.ScreenshotCandidate? {
        val proj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        return try {
            cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null, sort)?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val name = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "screenshot.jpg"
                    val taken = c.getLong(c.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN))
                    val added = c.getLong(c.getColumnIndex(MediaStore.Images.Media.DATE_ADDED))
                    val rel = c.getString(c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH))
                    val buck = c.getString(c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
                    val path = c.getString(c.getColumnIndex(MediaStore.Images.Media.DATA))
                    val ts = when {
                        taken > 0 -> taken
                        added > 0 -> added * 1000
                        else -> System.currentTimeMillis()
                    }
                    PurgeShotService.ScreenshotCandidate(
                        uri = android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                        displayName = name,
                        dateAddedMs = ts,
                        relativePath = rel,
                        filePath = path,
                        bucketName = buck
                    )
                } else null
            }
        } catch (_: Exception) { null }
    }
}

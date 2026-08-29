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

package com.frerox.toolz.data.music

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao
) {

    val allTracks: Flow<List<MusicTrack>> = musicDao.getAllTracks()
    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists()
    val favoriteTracks: Flow<List<MusicTrack>> = musicDao.getFavoriteTracks()
    val recentlyPlayed: Flow<List<MusicTrack>> = musicDao.getRecentlyPlayed()
    val mostPlayed: Flow<List<MusicTrack>> = musicDao.getMostPlayed()

    private var liveObserver: ContentObserver? = null
    private var debounceJob: Job? = null
    private var lastThumbFixMs: Long = 0L

    fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())
        }
    }

    /**
     * Registers a MediaStore ContentObserver for real-time live refresh
     * whenever audio files are added, modified, moved, or deleted on the device.
     * P0-06 fix: paired with [stopLiveObserver] to avoid leaks on rotation/recreate.
     */
    fun startLiveObserver(scope: CoroutineScope) {
        if (liveObserver != null) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (!hasAudioPermission()) return
                debounceJob?.cancel()
                debounceJob = scope.launch(Dispatchers.IO) {
                    delay(1200) // Debounce rapid file events
                    runCatching { scanDeviceForMusic() }
                }
            }
        }
        liveObserver = observer
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            android.util.Log.w("MusicRepository", "registerContentObserver failed", e)
        }
    }

    /**
     * P0-06 fix: unregisters the observer and cancels pending debounce.
     * Must be called from ViewModel.onCleared / Service.onDestroy as appropriate.
     */
    fun stopLiveObserver() {
        liveObserver?.let { observer ->
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                android.util.Log.w("MusicRepository", "unregisterContentObserver failed", e)
            }
            liveObserver = null
        }
        debounceJob?.cancel()
        debounceJob = null
    }

    suspend fun getTrackByUri(uri: String): MusicTrack? = withContext(Dispatchers.IO) {
        musicDao.getTrackByUri(uri)
    }

    suspend fun getTrackBySourceUrl(sourceUrl: String): MusicTrack? = withContext(Dispatchers.IO) {
        musicDao.getTrackBySourceUrl(sourceUrl)
    }

    suspend fun getPlaylistById(id: Int): Playlist? = withContext(Dispatchers.IO) {
        musicDao.getPlaylistById(id)
    }

    private fun computeStableId(path: String?, sourceUrl: String?, uri: String, title: String, artist: String?): String {
        // P-Quality: reduce hashCode collision via SHA chunk (still cheap determinism)
        val base = path?.takeIf { it.isNotBlank() } ?: sourceUrl?.takeIf { it.isNotBlank() } ?: uri
        val raw = "$base|${title.trim()}|${artist?.trim() ?: ""}"
        // Use SHA-256 hex take 16 char + hash suffix for human uniqueness without full length
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val hex = md.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
            "${hex}_${raw.hashCode().toString(36)}"
        } catch (_: Exception) {
            "${base.hashCode()}_${title.hashCode()}_${artist.hashCode()}"
        }
    }

    suspend fun updateTrackAiData(
        uri: String,
        lyrics: String? = null,
        vitals: String? = null,
        meaning: String? = null,
        recommendationsJson: String? = null
    ) = withContext(Dispatchers.IO) {
        val track = musicDao.getTrackByUri(uri) ?: return@withContext
        musicDao.updateTrack(track.copy(
            aiLyrics = lyrics ?: track.aiLyrics,
            aiArtistVitals = vitals ?: track.aiArtistVitals,
            aiSongMeaning = meaning ?: track.aiSongMeaning,
            aiRecommendationsJson = recommendationsJson ?: track.aiRecommendationsJson,
            lastAiSync = System.currentTimeMillis()
        ))
    }

    private fun getArtworkStorageDir(): File {
        val dir = File(context.filesDir, "album_thumbs")
        if (!dir.exists() && !dir.mkdirs()) {
            android.util.Log.w("MusicRepository", "getArtworkStorageDir: failed to create $dir")
        }
        return dir
    }

    private fun isThumbnailValid(thumbUri: String?): Boolean {
        if (thumbUri.isNullOrBlank()) return false
        if (thumbUri.startsWith("content://media/external/audio/albumart")) return false
        if (thumbUri.startsWith("file://")) {
            val filePath = Uri.parse(thumbUri).path ?: return false
            val file = File(filePath)
            // P2-09 fix: use canonicalPath for cache check (encoded paths could bypass contains)
            val canonical = runCatching { file.canonicalPath }.getOrDefault(filePath)
            return file.exists() && file.length() > 0 && !canonical.contains("/cache/")
        }
        return true
    }

    private fun getEmbeddedArtworkUri(uri: Uri, path: String? = null, albumId: Long = -1, forceRefresh: Boolean = false): String? {
        val thumbsDir = getArtworkStorageDir()
        val uniqueKey = "${path ?: uri.toString()}_${albumId}".hashCode()
        val targetFile = File(thumbsDir, "thumb_${uniqueKey}.jpg")

        // Unless forced, serve the cached extraction. forceRefresh is used right after
        // a file rewrite so the cache can't serve stale pre-edit artwork.
        if (!forceRefresh && targetFile.exists() && targetFile.length() > 0) {
            return Uri.fromFile(targetFile).toString()
        }

        // 1. Try extracting embedded picture via MediaMetadataRetriever
        val retriever = MediaMetadataRetriever()
        try {
            if (path != null && File(path).exists()) {
                retriever.setDataSource(path)
            } else {
                retriever.setDataSource(context, uri)
            }
            val artwork = retriever.embeddedPicture
            if (artwork != null && artwork.isNotEmpty()) {
                FileOutputStream(targetFile).use { it.write(artwork) }
                return Uri.fromFile(targetFile).toString()
            }
        } catch (e: Exception) {
            android.util.Log.d("MusicRepository", "embeddedPicture failed for $uri: ${e.message}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        // P1 thumb ordering: albumArt before loadThumbnail (binder cheaper)
        // 2. Fallback to MediaStore album art URI if available
        if (albumId >= 0) {
            val albumArt = getAlbumArtUri(albumId)
            if (albumArt != null) {
                try {
                    context.contentResolver.openInputStream(albumArt)?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (targetFile.exists() && targetFile.length() > 0) {
                        return Uri.fromFile(targetFile).toString()
                    }
                } catch (e: Exception) {}
            }
        }

        // 3. Last resort: ContentResolver.loadThumbnail on Android 10+ (Q+) — binder heavy so last
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
            try {
                val bitmap = context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
                FileOutputStream(targetFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                return Uri.fromFile(targetFile).toString()
            } catch (e: Exception) {}
        }

        return null
    }

    private fun getAlbumArtUri(albumId: Long): Uri? {
        if (albumId < 0) return null
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
    }

    suspend fun scanDeviceForMusic(): List<MusicTrack> = withContext(Dispatchers.IO) {
        if (!hasAudioPermission()) return@withContext emptyList()

        val scannedUris = mutableSetOf<String>()
        val scannedPaths = mutableSetOf<String>()
        val newOrUpdatedTracks = mutableListOf<MusicTrack>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val albumId = cursor.getLong(albumIdColumn)
                    val duration = cursor.getLong(durationColumn)
                    val path = cursor.getString(dataColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val contentUriString = contentUri.toString()

                    scannedUris.add(contentUriString)
                    if (path != null) scannedPaths.add(path)

                    val existingTrack = musicDao.getTrackByUri(contentUriString)
                        ?: (path?.let { musicDao.getTrackByPath(it) })
                        ?: musicDao.findDuplicate(title, artist, album, duration, path)

                    if (existingTrack == null) {
                        val thumbnailUri = getEmbeddedArtworkUri(contentUri, path, albumId)
                        val newTrack = MusicTrack(
                            uri = contentUriString,
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId,
                            duration = duration,
                            thumbnailUri = thumbnailUri,
                            isFavorite = false,
                            lastPlayed = 0L,
                            playCount = 0,
                            path = path,
                            dateAdded = System.currentTimeMillis(),
                            stableId = computeStableId(path, null, contentUriString, title, artist)
                        )
                        musicDao.insertTrack(newTrack)
                        newOrUpdatedTracks.add(newTrack)
                    } else {
                        val currentThumb = existingTrack.thumbnailUri
                        val isThumbValid = isThumbnailValid(currentThumb) && currentThumb != existingTrack.uri && currentThumb != existingTrack.path

                        val newThumb = if (!isThumbValid) {
                            getEmbeddedArtworkUri(contentUri, path, albumId) ?: currentThumb
                        } else {
                            currentThumb
                        }

                        if (existingTrack.uri != contentUriString) {
                            // Primary key changed (file relocated or re-indexed) — atomic migration
                            val stable = existingTrack.stableId.takeIf { it.isNotBlank() }
                                ?: computeStableId(path ?: existingTrack.path, existingTrack.sourceUrl, contentUriString, title, artist)
                            val updatedTrack = existingTrack.copy(
                                uri = contentUriString,
                                title = if (title != "Unknown") title else existingTrack.title,
                                artist = if (artist != "Unknown Artist") artist else existingTrack.artist,
                                album = if (album != "Unknown Album") album else existingTrack.album,
                                albumId = albumId,
                                duration = if (duration > 0) duration else existingTrack.duration,
                                path = path,
                                thumbnailUri = newThumb,
                                stableId = stable
                            )
                            // P0-01 fix: single transaction guarantees playlist consistency on crash
                            musicDao.atomicMigrateUri(existingTrack.uri, updatedTrack)
                            newOrUpdatedTracks.add(updatedTrack)
                        } else {
                            // Same URI: update in place
                            val needsUpdate = existingTrack.path != path ||
                                    existingTrack.albumId != albumId ||
                                    (duration > 0 && existingTrack.duration <= 0) ||
                                    newThumb != currentThumb

                            if (needsUpdate || existingTrack.stableId.isBlank()) {
                                val stable = existingTrack.stableId.takeIf { it.isNotBlank() }
                                    ?: computeStableId(path, existingTrack.sourceUrl, existingTrack.uri, existingTrack.title, existingTrack.artist)
                                val updatedTrack = existingTrack.copy(
                                    path = path,
                                    albumId = albumId,
                                    duration = if (duration > 0) duration else existingTrack.duration,
                                    thumbnailUri = newThumb,
                                    stableId = stable
                                )
                                musicDao.updateTrack(updatedTrack)
                                newOrUpdatedTracks.add(updatedTrack)
                            }
                        }
                    }
                }
            }

            // Prune dead/stale tracks that no longer exist in MediaStore AND don't exist on disk
            val allExistingTracks = musicDao.getAllTracksSync()
            allExistingTracks.forEach { track ->
                // Do not delete remote online-only catalog tracks
                if (track.sourceUrl != null && track.path == null && !track.uri.startsWith("content://")) {
                    return@forEach
                }

                // If not found in current scan
                if (!scannedUris.contains(track.uri)) {
                    // P0-01: also check contentResolver for content:// URIs before pruning
                    // File.exists() alone can falsely prune when scoped-storage permission was lost.
                    val fileOnDiskExists = track.path?.let { File(it).exists() } == true
                    val contentExists = if (!fileOnDiskExists && track.uri.startsWith("content://")) {
                        runCatching {
                            context.contentResolver.openFileDescriptor(Uri.parse(track.uri), "r")?.use { true } ?: false
                        }.getOrDefault(false)
                    } else false
                    if (!fileOnDiskExists && !contentExists) {
                        // Dead track: remove atomically with playlist cleanup
                        musicDao.atomicDeleteTrackAndCleanPlaylists(track)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "scanDeviceForMusic failed", e)
        }

        // P2: throttle redundant thumbnail pass (scan already handled per-track thumbs)
        val now = System.currentTimeMillis()
        val shouldFix = newOrUpdatedTracks.isNotEmpty() || now - lastThumbFixMs > 300_000L // 5 min
        if (shouldFix) {
            // P1-13: prefer lazy backfill worker instead of inline heavy pass
            try {
                enqueueBackfillWorker()
            } catch (_: Exception) {
                fixAllThumbnails()
            }
            lastThumbFixMs = now
        }
        newOrUpdatedTracks
    }

    private fun enqueueBackfillWorker() {
        try {
            val wm = androidx.work.WorkManager.getInstance(context)
            val req = androidx.work.OneTimeWorkRequestBuilder<com.frerox.toolz.worker.ThumbnailBackfillWorker>()
                .addTag(com.frerox.toolz.worker.ThumbnailBackfillWorker.TAG_THUMB_BACKFILL)
                .build()
            wm.enqueueUniqueWork("thumb_backfill", androidx.work.ExistingWorkPolicy.KEEP, req)
        } catch (e: Exception) {
            android.util.Log.w("MusicRepository", "enqueueBackfill failed", e)
        }
    }

    suspend fun fixAllThumbnails() = withContext(Dispatchers.IO) {
        val allTracks = musicDao.getAllTracksSync()
        allTracks.forEach { track ->
            val currentThumb = track.thumbnailUri
            val isThumbValid = isThumbnailValid(currentThumb) && currentThumb != track.uri && currentThumb != track.path
            val isCatalogDownload = track.sourceUrl != null && track.path != null

            val needsFix = !isThumbValid || isCatalogDownload

            if (needsFix) {
                val fileUri = track.path?.let {
                    if (it.startsWith("content://") || it.startsWith("file://")) Uri.parse(it)
                    else Uri.fromFile(File(it))
                } ?: Uri.parse(track.uri)

                val newThumb = getEmbeddedArtworkUri(fileUri, track.path, track.albumId)
                if (newThumb != null && newThumb != currentThumb) {
                    musicDao.updateTrack(track.copy(thumbnailUri = newThumb))
                }
            }
        }
    }

    suspend fun scanCustomFolder(folderUri: Uri): List<MusicTrack> = withContext(Dispatchers.IO) {
        // persist permission for re-scan after reboot
        try {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(folderUri, takeFlags)
        } catch (_: Exception) {}
        val tracks = mutableListOf<MusicTrack>()
        val rootFolder = DocumentFile.fromTreeUri(context, folderUri)

        suspend fun scanRecursive(directory: DocumentFile) {
            directory.listFiles().forEach { file ->
                try {
                    if (file.isDirectory) {
                        scanRecursive(file)
                    } else if (isAudioFile(file.name ?: "")) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, file.uri)
                            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                                ?: file.name?.substringBeforeLast(".") ?: "Unknown"
                            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
                            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L

                            val fileUriString = file.uri.toString()
                            val existingTrack = musicDao.getTrackByUri(fileUriString)
                                ?: musicDao.findDuplicate(title, artist, album, duration, file.uri.path)

                            val thumb = getEmbeddedArtworkUri(file.uri, file.uri.path)

                            if (existingTrack == null) {
                                val track = MusicTrack(
                                    uri = fileUriString,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    duration = duration,
                                    thumbnailUri = thumb,
                                    isFavorite = false,
                                    lastPlayed = 0L,
                                    playCount = 0,
                                    path = file.uri.path,
                                    dateAdded = System.currentTimeMillis(),
                                    stableId = computeStableId(file.uri.path, null, fileUriString, title, artist)
                                )
                                musicDao.insertTrack(track)
                                tracks.add(track)
                            } else {
                                if (existingTrack.uri != fileUriString) {
                                    val stable = existingTrack.stableId.takeIf { it.isNotBlank() }
                                        ?: computeStableId(file.uri.path, existingTrack.sourceUrl, fileUriString, title, artist)
                                    val updated = existingTrack.copy(
                                        uri = fileUriString,
                                        title = title,
                                        artist = artist,
                                        album = album,
                                        duration = if (duration > 0) duration else existingTrack.duration,
                                        thumbnailUri = thumb ?: existingTrack.thumbnailUri,
                                        stableId = stable
                                    )
                                    // atomic migration via DAO
                                    musicDao.atomicMigrateUri(existingTrack.uri, updated)
                                    tracks.add(updated)
                                } else {
                                    val updated = existingTrack.copy(
                                        title = title,
                                        artist = artist,
                                        album = album,
                                        duration = if (duration > 0) duration else existingTrack.duration,
                                        thumbnailUri = thumb ?: existingTrack.thumbnailUri
                                    )
                                    musicDao.updateTrack(updated)
                                    tracks.add(updated)
                                }
                            }
                        } finally {
                            try { retriever.release() } catch (e: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        rootFolder?.let { scanRecursive(it) }
        tracks
    }

    private fun isAudioFile(name: String): Boolean {
        // P2: support more lossless/compressed formats seen via SAF / downloads
        val extensions = listOf(".mp3", ".wav", ".m4a", ".ogg", ".flac", ".opus", ".aac", ".wma", ".aiff", ".m4b", ".oga")
        return extensions.any { name.lowercase().endsWith(it) }
    }

    suspend fun extractMetadata(uri: Uri): MusicTrack = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: uri.lastPathSegment?.substringBeforeLast(".")
                ?: "Unknown"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L

            val thumbnailUri = getEmbeddedArtworkUri(uri)

            MusicTrack(
                uri = uri.toString(),
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                thumbnailUri = thumbnailUri,
                isFavorite = false,
                lastPlayed = 0L,
                playCount = 0,
                path = null,
                dateAdded = System.currentTimeMillis(),
                stableId = computeStableId(null, null, uri.toString(), title, artist)
            )
        } catch (e: Exception) {
            val fallbackTitle = uri.lastPathSegment?.substringBeforeLast(".") ?: "Unknown"
            MusicTrack(
                uri = uri.toString(),
                title = fallbackTitle,
                artist = "Unknown Artist",
                album = "Unknown Album",
                duration = 0,
                thumbnailUri = null,
                isFavorite = false,
                lastPlayed = 0L,
                playCount = 0,
                path = null,
                dateAdded = System.currentTimeMillis(),
                stableId = computeStableId(null, null, uri.toString(), fallbackTitle, "Unknown Artist")
            )
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }

    suspend fun incrementPlayCount(track: MusicTrack) {
        if (track.sourceUrl != null) {
            musicDao.incrementPlayCountBySourceUrl(track.sourceUrl, System.currentTimeMillis())
        } else {
            musicDao.incrementPlayCount(track.uri, System.currentTimeMillis())
        }
    }

    suspend fun incrementKaraokeSingCount(uri: String) {
        val track = getTrackByUri(uri) ?: return
        updateTrack(track.copy(karaokeSingCount = track.karaokeSingCount + 1))
    }

    suspend fun insertTrack(track: MusicTrack) {
        musicDao.insertTrack(track)
    }

    suspend fun upsertDownloadedTrack(track: MusicTrack) = withContext(Dispatchers.IO) {
        val existing = track.sourceUrl?.let { musicDao.getTrackBySourceUrl(it) }
            ?: musicDao.getTrackByUri(track.uri)

        // Ensure incoming has stableId
        val incomingStable = track.stableId.takeIf { it.isNotBlank() }
            ?: computeStableId(track.path, track.sourceUrl, track.uri, track.title, track.artist)
        val incoming = track.copy(stableId = incomingStable)
        if (existing == null) {
            musicDao.insertTrack(incoming)
        } else if (existing.uri == incoming.uri) {
            val stable = existing.stableId.takeIf { it.isNotBlank() } ?: incomingStable
            musicDao.updateTrack(existing.copy(
                title       = incoming.title,
                artist      = incoming.artist,
                album       = incoming.album,
                duration    = if (incoming.duration > 0L) incoming.duration else existing.duration,
                thumbnailUri= incoming.thumbnailUri ?: existing.thumbnailUri,
                path        = incoming.path,
                sourceUrl   = incoming.sourceUrl ?: existing.sourceUrl,
                dateAdded   = incoming.dateAdded,
                stableId    = stable
            ))
        } else {
            val stable = existing.stableId.takeIf { it.isNotBlank() } ?: incomingStable
            val merged = incoming.copy(
                isFavorite            = existing.isFavorite,
                playCount             = existing.playCount,
                lastPlayed            = existing.lastPlayed,
                aiLyrics              = existing.aiLyrics,
                aiArtistVitals        = existing.aiArtistVitals,
                aiSongMeaning         = existing.aiSongMeaning,
                aiRecommendationsJson = existing.aiRecommendationsJson,
                lastAiSync            = existing.lastAiSync,
                karaokeSingCount      = existing.karaokeSingCount,
                thumbnailUri          = incoming.thumbnailUri ?: existing.thumbnailUri,
                sourceUrl             = incoming.sourceUrl ?: existing.sourceUrl,
                stableId              = stable
            )
            // P0-01: atomic to keep playlist consistency
            musicDao.atomicMigrateUri(existing.uri, merged)
        }
    }

    // Backfill helpers (exposed for worker periodic throttle)
    suspend fun getAllTracksSyncForBackfill(): List<com.frerox.toolz.data.music.MusicTrack> = withContext(Dispatchers.IO) {
        musicDao.getAllTracksSync()
    }
    suspend fun fixThumbnailForTrack(track: com.frerox.toolz.data.music.MusicTrack) = withContext(Dispatchers.IO) {
        val current = track.thumbnailUri
        val valid = isThumbnailValid(current) && current != track.uri && current != track.path
        if (valid && !(track.sourceUrl != null && track.path != null)) return@withContext
        val fileUri = track.path?.let {
            if (it.startsWith("content://") || it.startsWith("file://")) Uri.parse(it) else Uri.fromFile(File(it))
        } ?: Uri.parse(track.uri)
        val newThumb = getEmbeddedArtworkUri(fileUri, track.path, track.albumId)
        if (newThumb != null && newThumb != current) {
            musicDao.updateTrack(track.copy(thumbnailUri = newThumb))
        }
    }

    suspend fun updateTrack(track: MusicTrack) {
        musicDao.updateTrack(track)
    }

    suspend fun createPlaylist(playlist: Playlist) {
        musicDao.insertPlaylist(playlist)
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        musicDao.updatePlaylist(playlist)
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        musicDao.deletePlaylist(playlist)
    }

    suspend fun deleteTrack(track: MusicTrack) {
        musicDao.deleteTrack(track)
    }

    suspend fun toggleFavorite(track: MusicTrack) {
        musicDao.updateTrack(track.copy(isFavorite = !track.isFavorite))
    }

    /**
     * Edit track metadata: title/artist/album/thumbnail/embedded lyrics.
     * Updates DB immediately (optimistic) and, if the file exists on disk,
     * attempts to rewrite ID3 tags via FFmpeg (MediaStore file).
     * Returns the updated track or null if not found.
     */
    suspend fun updateTrackTags(
        trackUri: String,
        newTitle: String?,
        newArtist: String?,
        newAlbum: String?,
        newThumbnailUri: String?, // content:// or file:// from picker, or null to keep
        embeddedLyrics: String?
    ): MusicTrack? = withContext(Dispatchers.IO) {
        val track = musicDao.getTrackByUri(trackUri) ?: return@withContext null
        var thumbToPersist = track.thumbnailUri
        // True when this save actually picked a new cover — used below to decide
        // whether re-extracting embedded art is safe (vs. clobbering a custom pick).
        val hadThumbChange = !newThumbnailUri.isNullOrBlank() && newThumbnailUri != track.thumbnailUri
        // Handle thumbnail picker copy
        if (hadThumbChange) {
            try {
                val sourceUri = Uri.parse(newThumbnailUri)
                val dir = getArtworkStorageDir()
                val target = File(dir, "thumb_custom_${track.stableId.ifBlank { track.uri.hashCode().toString() }}.jpg")
                // Already pointing at our persisted custom thumb — nothing to copy.
                if (newThumbnailUri == Uri.fromFile(target).toString()) {
                    thumbToPersist = newThumbnailUri
                } else {
                    val input = context.contentResolver.openInputStream(sourceUri)
                        // contentResolver may refuse temp/cache URIs; fall back to a direct file read.
                        ?: if (newThumbnailUri.startsWith("file://")) {
                            sourceUri.path?.let { File(it) }?.takeIf { it.exists() && it.isFile }?.inputStream()
                        } else null
                    if (input != null) {
                        input.use { ins ->
                            FileOutputStream(target).use { out -> ins.copyTo(out) }
                        }
                        // Persist the file URI only when the copy actually landed.
                        thumbToPersist =
                            if (target.exists() && target.length() > 0) Uri.fromFile(target).toString()
                            else newThumbnailUri
                    } else {
                        thumbToPersist = newThumbnailUri
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MusicRepository", "thumb copy failed", e)
                thumbToPersist = newThumbnailUri
            }
        }

        val updated = track.copy(
            title = newTitle?.takeIf { it.isNotBlank() } ?: track.title,
            artist = newArtist?.takeIf { it.isNotBlank() } ?: track.artist,
            album = newAlbum?.takeIf { it.isNotBlank() } ?: track.album,
            thumbnailUri = thumbToPersist,
            aiLyrics = embeddedLyrics?.takeIf { it.isNotBlank() } ?: track.aiLyrics,
            lastAiSync = if (!embeddedLyrics.isNullOrBlank()) System.currentTimeMillis() else track.lastAiSync
        )
        musicDao.updateTrack(updated)

        // Attempt to write ID3 tags to file if path is a real file
        val path = updated.path
        if (!path.isNullOrBlank() && File(path).exists() && File(path).canWrite()) {
            val writeOk = try {
                writeId3TagsToFile(
                    filePath = path,
                    title = updated.title,
                    artist = updated.artist,
                    album = updated.album,
                    thumbnailPath = thumbToPersist?.let { Uri.parse(it).path }?.let { File(it) }?.takeIf { it.exists() }?.absolutePath,
                    lyrics = embeddedLyrics
                )
            } catch (e: Exception) {
                android.util.Log.w("MusicRepository", "writeId3Tags failed (non-fatal)", e)
                false
            }
            // Re-extract embedded art ONLY when this save actually rewrote the file
            // AND a new cover was embedded. Guarding on writeOk keeps a failed FFmpeg
            // attempt (or a stale cached extraction) from clobbering the freshly picked
            // custom thumb with the OLD embedded art; forceRefresh invalidates the
            // per-path artwork cache so it reflects the file as rewritten just now.
            if (writeOk && hadThumbChange) {
                val refreshedThumb = runCatching {
                    getEmbeddedArtworkUri(Uri.fromFile(File(path)), path, updated.albumId, forceRefresh = true)
                }.getOrNull()
                if (refreshedThumb != null && refreshedThumb != updated.thumbnailUri) {
                    val withRefreshed = updated.copy(thumbnailUri = refreshedThumb)
                    musicDao.updateTrack(withRefreshed)
                    return@withContext withRefreshed
                }
            }
        } else if (!path.isNullOrBlank() && path.startsWith("content://")) {
            // For SAF content URIs, try content resolver edit via FFmpeg temp file -> write back
            // Best-effort: copy content to temp, edit, then write back (requires write permission)
            try {
                val inputUri = Uri.parse(path)
                val needsWrite = newTitle != null || newArtist != null || newAlbum != null || embeddedLyrics != null
                if (needsWrite) {
                    context.contentResolver.openFileDescriptor(inputUri, "r")?.use { pfd ->
                        // Just log — full SAF tag rewrite requires SAF persist + FFmpeg temp; skip for now but DB already updated
                        android.util.Log.d("MusicRepository", "SAF content tag edit requested for $path — DB updated, file write deferred (requires MediaStore write request)")
                    }
                }
            } catch (_: Exception) {}
        }

        updated
    }

    /**
     * Rewrites ID3/comment tags (plus optional attached cover) via FFmpeg.
     * If the file was created from a picker copy, [thumbnailPath] points at the
     * copied artwork so a successful rewrite re-embeds it into the track file.
     * Returns true only when the rewritten file replaced the original on disk.
     */
    private fun writeId3TagsToFile(
        filePath: String,
        title: String?,
        artist: String?,
        album: String?,
        thumbnailPath: String?,
        lyrics: String?
    ): Boolean {
        return try {
            val inputFile = File(filePath)
            if (!inputFile.exists()) return false
            val ext = inputFile.extension.lowercase()
            val tmpOut = File.createTempFile("toolz_tagedit_", ".$ext", context.cacheDir)

            val args = buildTagEditArgs(
                inputPath = inputFile.absolutePath,
                thumbnailPath = thumbnailPath,
                title = title,
                artist = artist,
                album = album,
                lyrics = lyrics,
                tmpOutPath = tmpOut.absolutePath
            )

            val session = com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments(args)
            if (!com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)) {
                android.util.Log.w("MusicRepository", "FFmpeg tag write failed: ${session.failStackTrace}")
                tmpOut.delete()
                return false
            }
            if (!tmpOut.exists() || tmpOut.length() <= 0) {
                tmpOut.delete()
                return false
            }

            // Atomic-ish replace with a rollback backup: a failed copy can never
            // destroy the original file (previous version deleted it first, which
            // risked losing the track if the copy then failed).
            val backup = File(inputFile.parentFile, ".${inputFile.name}.toolz.bak")
            try {
                inputFile.copyTo(backup, overwrite = true)
                tmpOut.copyTo(inputFile, overwrite = true)
                tmpOut.delete()
                backup.delete()
            } catch (e: Exception) {
                android.util.Log.w("MusicRepository", "tag file replace failed, restoring backup", e)
                runCatching { backup.copyTo(inputFile, overwrite = true) }
                backup.delete()
                tmpOut.delete()
                return false
            }

            // Let MediaStore know the file changed so embedded art / metadata
            // consumers pick up the new values.
            android.media.MediaScannerConnection.scanFile(context, arrayOf(inputFile.absolutePath), null, null)
            true
        } catch (e: Exception) {
            android.util.Log.w("MusicRepository", "writeId3TagsToFile exception", e)
            false
        }
    }
}

/**
 * Builds the FFmpeg arguments for a tag/cover rewrite. Pure string building kept
 * top-level (internal) so it is unit-testable without Android/FFmpegKit.
 *
 * Stream mapping:
 *  - With cover: keep ONLY the original audio streams and take the picked image
 *    as the attached-picture stream (`-map 0:a -map 1:v`). Mapping the full `0`
 *    would also carry a pre-existing cover stream across, producing duplicated
 *    artwork on mp3/m4a that already had art.
 *  - Without cover: preserve every original stream untouched (`-map 0`).
 */
internal fun buildTagEditArgs(
    inputPath: String,
    thumbnailPath: String?,
    title: String?,
    artist: String?,
    album: String?,
    lyrics: String?,
    tmpOutPath: String
): Array<String> {
    val ext = inputPath.substringAfterLast('.').lowercase()
    val args = mutableListOf<String>()
    args.addAll(listOf("-i", inputPath))
    var hasCover = false
    if (!thumbnailPath.isNullOrBlank() && File(thumbnailPath).exists() && ext != "opus") {
        args.addAll(listOf("-i", thumbnailPath))
        hasCover = true
    }
    if (hasCover) {
        args.addAll(listOf("-map", "0:a", "-map", "1:v"))
    } else {
        args.addAll(listOf("-map", "0"))
    }
    // Copy codecs where possible to avoid re-encode, but ensure metadata written
    args.addAll(listOf("-c", "copy"))
    // id3v2.3 is the most widely compatible tag version for players.
    if (ext == "mp3") args.addAll(listOf("-id3v2_version", "3"))
    if (!title.isNullOrBlank()) args.addAll(listOf("-metadata", "title=$title"))
    if (!artist.isNullOrBlank()) args.addAll(listOf("-metadata", "artist=$artist"))
    if (!album.isNullOrBlank()) args.addAll(listOf("-metadata", "album=$album"))
    if (!lyrics.isNullOrBlank()) {
        // MP3: ffmpeg maps `lyrics` to the USLT frame with id3v2.3;
        // MP4/M4A: written as the ©lyr metadata tag.
        args.addAll(listOf("-metadata", "lyrics=$lyrics"))
    }
    if (hasCover) {
        if (ext == "mp3") {
            args.addAll(listOf("-metadata:s:v", "title=Album cover", "-metadata:s:v", "comment=Cover (front)"))
        } else {
            args.addAll(listOf("-disposition:v", "attached_pic"))
        }
    }
    args.addAll(listOf("-y", tmpOutPath))
    return args.toTypedArray()
}
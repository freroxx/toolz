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
                    scanDeviceForMusic()
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
            e.printStackTrace()
        }
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
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun isThumbnailValid(thumbUri: String?): Boolean {
        if (thumbUri.isNullOrBlank()) return false
        if (thumbUri.startsWith("content://media/external/audio/albumart")) return false
        if (thumbUri.startsWith("file://")) {
            val filePath = Uri.parse(thumbUri).path ?: return false
            val file = File(filePath)
            // Storing in cacheDir is volatile; only filesDir thumbs are permanently valid
            return file.exists() && file.length() > 0 && !filePath.contains("/cache/")
        }
        return true
    }

    private fun getEmbeddedArtworkUri(uri: Uri, path: String? = null, albumId: Long = -1): String? {
        val thumbsDir = getArtworkStorageDir()
        val uniqueKey = "${path ?: uri.toString()}_${albumId}".hashCode()
        val targetFile = File(thumbsDir, "thumb_${uniqueKey}.jpg")

        if (targetFile.exists() && targetFile.length() > 0) {
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
            if (path != null && File(path).exists()) {
                try {
                    retriever.setDataSource(path)
                    val artwork = retriever.embeddedPicture
                    if (artwork != null && artwork.isNotEmpty()) {
                        FileOutputStream(targetFile).use { it.write(artwork) }
                        return Uri.fromFile(targetFile).toString()
                    }
                } catch (e2: Exception) {}
            }
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }

        // 2. Try ContentResolver.loadThumbnail on Android 10+ (Q+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
            try {
                val bitmap = context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
                FileOutputStream(targetFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                return Uri.fromFile(targetFile).toString()
            } catch (e: Exception) {}
        }

        // 3. Fallback to MediaStore album art URI if available
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
                        ?: musicDao.findDuplicate(title, artist, duration, path)

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
                            dateAdded = System.currentTimeMillis()
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
                            // Primary key changed (file relocated or re-indexed)
                            // 1. Delete old row
                            musicDao.deleteTrackByUri(existingTrack.uri)
                            // 2. Insert new row with preserved user metadata
                            val updatedTrack = existingTrack.copy(
                                uri = contentUriString,
                                title = if (title != "Unknown") title else existingTrack.title,
                                artist = if (artist != "Unknown Artist") artist else existingTrack.artist,
                                album = if (album != "Unknown Album") album else existingTrack.album,
                                albumId = albumId,
                                duration = if (duration > 0) duration else existingTrack.duration,
                                path = path,
                                thumbnailUri = newThumb
                            )
                            musicDao.insertTrack(updatedTrack)
                            newOrUpdatedTracks.add(updatedTrack)

                            // 3. Migrate playlist entries referencing the old URI
                            val allPlaylists = musicDao.getAllPlaylistsSync()
                            allPlaylists.forEach { pl ->
                                if (pl.trackUris.contains(existingTrack.uri)) {
                                    val updatedUris = pl.trackUris.map { if (it == existingTrack.uri) contentUriString else it }
                                    musicDao.updatePlaylist(pl.copy(trackUris = updatedUris))
                                }
                            }
                        } else {
                            // Same URI: update in place
                            val needsUpdate = existingTrack.path != path ||
                                    existingTrack.albumId != albumId ||
                                    (duration > 0 && existingTrack.duration <= 0) ||
                                    newThumb != currentThumb

                            if (needsUpdate) {
                                val updatedTrack = existingTrack.copy(
                                    path = path,
                                    albumId = albumId,
                                    duration = if (duration > 0) duration else existingTrack.duration,
                                    thumbnailUri = newThumb
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
                    val fileOnDiskExists = track.path?.let { File(it).exists() } == true
                    if (!fileOnDiskExists) {
                        // Dead track: remove from database and playlists
                        musicDao.deleteTrack(track)
                        val allPlaylists = musicDao.getAllPlaylistsSync()
                        allPlaylists.forEach { pl ->
                            if (pl.trackUris.contains(track.uri)) {
                                musicDao.updatePlaylist(pl.copy(trackUris = pl.trackUris - track.uri))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        fixAllThumbnails()
        newOrUpdatedTracks
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
                                ?: musicDao.findDuplicate(title, artist, duration, file.uri.path)

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
                                    dateAdded = System.currentTimeMillis()
                                )
                                musicDao.insertTrack(track)
                                tracks.add(track)
                            } else {
                                if (existingTrack.uri != fileUriString) {
                                    musicDao.deleteTrackByUri(existingTrack.uri)
                                    val updated = existingTrack.copy(
                                        uri = fileUriString,
                                        title = title,
                                        artist = artist,
                                        album = album,
                                        duration = if (duration > 0) duration else existingTrack.duration,
                                        thumbnailUri = thumb ?: existingTrack.thumbnailUri
                                    )
                                    musicDao.insertTrack(updated)
                                    tracks.add(updated)

                                    val allPlaylists = musicDao.getAllPlaylistsSync()
                                    allPlaylists.forEach { pl ->
                                        if (pl.trackUris.contains(existingTrack.uri)) {
                                            musicDao.updatePlaylist(pl.copy(trackUris = pl.trackUris.map { if (it == existingTrack.uri) fileUriString else it }))
                                        }
                                    }
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
        val extensions = listOf(".mp3", ".wav", ".m4a", ".ogg", ".flac")
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
                dateAdded = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            MusicTrack(
                uri = uri.toString(),
                title = uri.lastPathSegment?.substringBeforeLast(".") ?: "Unknown",
                artist = "Unknown Artist",
                album = "Unknown Album",
                duration = 0,
                thumbnailUri = null,
                isFavorite = false,
                lastPlayed = 0L,
                playCount = 0,
                path = null,
                dateAdded = System.currentTimeMillis()
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

        if (existing == null) {
            musicDao.insertTrack(track)
        } else if (existing.uri == track.uri) {
            musicDao.updateTrack(existing.copy(
                title       = track.title,
                artist      = track.artist,
                album       = track.album,
                duration    = if (track.duration > 0L) track.duration else existing.duration,
                thumbnailUri= track.thumbnailUri ?: existing.thumbnailUri,
                path        = track.path,
                sourceUrl   = track.sourceUrl ?: existing.sourceUrl,
                dateAdded   = track.dateAdded
            ))
        } else {
            val merged = track.copy(
                isFavorite            = existing.isFavorite,
                playCount             = existing.playCount,
                lastPlayed            = existing.lastPlayed,
                aiLyrics              = existing.aiLyrics,
                aiArtistVitals        = existing.aiArtistVitals,
                aiSongMeaning         = existing.aiSongMeaning,
                aiRecommendationsJson = existing.aiRecommendationsJson,
                lastAiSync            = existing.lastAiSync,
                karaokeSingCount      = existing.karaokeSingCount,
                thumbnailUri          = track.thumbnailUri ?: existing.thumbnailUri,
                sourceUrl             = track.sourceUrl ?: existing.sourceUrl
            )
            musicDao.deleteTrackByUri(existing.uri)
            musicDao.insertTrack(merged)
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
}
package com.frerox.toolz.data.music

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

    suspend fun getTrackByUri(uri: String): MusicTrack? = withContext(Dispatchers.IO) {
        musicDao.getTrackByUri(uri)
    }

    suspend fun getTrackBySourceUrl(sourceUrl: String): MusicTrack? = withContext(Dispatchers.IO) {
        musicDao.getTrackBySourceUrl(sourceUrl)
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

    suspend fun scanDeviceForMusic(): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()
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
                
                val albumArtUri = if (albumId >= 0) getAlbumArtUri(albumId)?.toString() else null
                
                val existingTrack = musicDao.getTrackByUri(contentUri.toString()) 
                    ?: (path?.let { musicDao.getTrackByPath(it) })
                    ?: musicDao.findDuplicate(title, artist, duration, path)
                
                if (existingTrack == null) {
                    tracks.add(
                        MusicTrack(
                            uri = contentUri.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId,
                            duration = duration,
                            thumbnailUri = albumArtUri,
                            isFavorite = false,
                            lastPlayed = 0L,
                            playCount = 0,
                            path = path,
                            dateAdded = java.lang.System.currentTimeMillis()
                        )
                    )
                } else {
                    // Update existing track and fix potential buggy thumbnailUri (where it was set to the audio file Uri)
                    val currentThumb = existingTrack.thumbnailUri
                    val isBuggyThumb = currentThumb != null && (currentThumb == existingTrack.uri || currentThumb == existingTrack.path)
                    
                    val needsUpdate = existingTrack.uri != contentUri.toString() || 
                                     existingTrack.path != path || 
                                     currentThumb == null || isBuggyThumb
                    
                    if (needsUpdate) {
                         musicDao.updateTrack(existingTrack.copy(
                             uri = contentUri.toString(), 
                             path = path,
                             albumId = albumId,
                             thumbnailUri = if (currentThumb == null || isBuggyThumb) albumArtUri else currentThumb
                         ))
                    }
                }
            }
        }
        
        tracks.forEach { musicDao.insertTrack(it) }
        tracks
    }

    private fun getAlbumArtUri(albumId: Long): Uri? {
        if (albumId < 0) return null
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
    }

    suspend fun scanCustomFolder(folderUri: Uri): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()
        val rootFolder = DocumentFile.fromTreeUri(context, folderUri)
        
        suspend fun scanRecursive(directory: DocumentFile) {
            directory.listFiles().forEach { file ->
                if (file.isDirectory) {
                    scanRecursive(file)
                } else if (isAudioFile(file.name ?: "")) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, file.uri)
                        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) 
                            ?: file.name?.substringBeforeLast(".") ?: "Unknown"
                        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                        retriever.release()

                        val existingTrack = musicDao.getTrackByUri(file.uri.toString())
                            ?: musicDao.findDuplicate(title, artist, duration, file.uri.path)

                        if (existingTrack == null) {
                            tracks.add(extractMetadata(file.uri))
                        } else {
                            // Fix buggy thumbnails or update data
                            val currentThumb = existingTrack.thumbnailUri
                            val isBuggyThumb = currentThumb != null && (currentThumb == existingTrack.uri || currentThumb == existingTrack.path)
                            if (currentThumb == null || isBuggyThumb) {
                                val meta = extractMetadata(file.uri)
                                musicDao.updateTrack(existingTrack.copy(
                                    thumbnailUri = meta.thumbnailUri,
                                    uri = file.uri.toString()
                                ))
                            }
                        }
                    } catch (e: Exception) {
                        val existingTrack = musicDao.getTrackByUri(file.uri.toString())
                        if (existingTrack == null) {
                            tracks.add(extractMetadata(file.uri))
                        }
                    }
                }
            }
        }

        rootFolder?.let { scanRecursive(it) }
        tracks.forEach { musicDao.insertTrack(it) }
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
            
            val artwork = retriever.embeddedPicture
            var thumbnailUri: String? = null
            if (artwork != null) {
                val fileName = "thumb_${uri.toString().hashCode()}.jpg"
                val file = File(context.cacheDir, fileName)
                FileOutputStream(file).use { it.write(artwork) }
                thumbnailUri = Uri.fromFile(file).toString()
            }

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
                dateAdded = java.lang.System.currentTimeMillis()
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
                dateAdded = java.lang.System.currentTimeMillis()
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

    suspend fun insertTrack(track: MusicTrack) {
        musicDao.insertTrack(track)
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

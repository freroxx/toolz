package com.frerox.toolz.data.music

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.frerox.toolz.data.music.MusicDao
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.Playlist
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
    private val musicDao: MusicDao,
    @ApplicationContext private val context: Context
) {
    val allTracks: Flow<List<MusicTrack>> = musicDao.getAllTracks()
    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists()
    val favoriteTracks: Flow<List<MusicTrack>> = musicDao.getFavoriteTracks()
    val recentlyPlayed: Flow<List<MusicTrack>> = musicDao.getRecentlyPlayed()
    val mostPlayed: Flow<List<MusicTrack>> = musicDao.getMostPlayed()

    suspend fun addTrack(track: MusicTrack) = musicDao.insertTrack(track)
    suspend fun updateTrack(track: MusicTrack) = musicDao.updateTrack(track)
    suspend fun deleteTrack(track: MusicTrack) = musicDao.deleteTrack(track)
    
    suspend fun toggleFavorite(track: MusicTrack) {
        val updatedTrack = track.copy(isFavorite = !track.isFavorite)
        musicDao.updateTrack(updatedTrack)
    }

    suspend fun incrementPlayCount(uri: String) {
        musicDao.incrementPlayCount(uri, System.currentTimeMillis())
    }

    suspend fun createPlaylist(playlist: Playlist) = musicDao.insertPlaylist(playlist)
    suspend fun updatePlaylist(playlist: Playlist) = musicDao.updatePlaylist(playlist)
    suspend fun deletePlaylist(playlist: Playlist) = musicDao.deletePlaylist(playlist)

    suspend fun scanDeviceForMusic(): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("10000") // 10 seconds minimum
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val albumId = cursor.getLong(albumIdColumn)
                val duration = cursor.getLong(durationColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                
                val existingTrack = musicDao.getTrackByUri(contentUri.toString())
                
                tracks.add(
                    MusicTrack(
                        uri = contentUri.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = albumId,
                        duration = duration,
                        thumbnailUri = getAlbumArtUri(albumId).toString(),
                        isFavorite = existingTrack?.isFavorite ?: false,
                        lastPlayed = existingTrack?.lastPlayed ?: 0L,
                        playCount = existingTrack?.playCount ?: 0
                    )
                )
            }
        }
        
        tracks.forEach { musicDao.insertTrack(it) }
        tracks
    }

    private fun getAlbumArtUri(albumId: Long): Uri {
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
                        val track = extractMetadata(file.uri)
                        tracks.add(track)
                    } catch (e: Exception) {
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

            val existingTrack = musicDao.getTrackByUri(uri.toString())

            MusicTrack(
                uri = uri.toString(),
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                thumbnailUri = thumbnailUri ?: uri.toString(),
                isFavorite = existingTrack?.isFavorite ?: false,
                lastPlayed = existingTrack?.lastPlayed ?: 0L,
                playCount = existingTrack?.playCount ?: 0
            )
        } catch (e: Exception) {
            val existingTrack = musicDao.getTrackByUri(uri.toString())
            MusicTrack(
                uri = uri.toString(),
                title = uri.lastPathSegment?.substringBeforeLast(".") ?: "Unknown",
                artist = "Unknown Artist",
                album = "Unknown Album",
                duration = 0,
                thumbnailUri = uri.toString(),
                isFavorite = existingTrack?.isFavorite ?: false,
                lastPlayed = existingTrack?.lastPlayed ?: 0L,
                playCount = existingTrack?.playCount ?: 0
            )
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }
}

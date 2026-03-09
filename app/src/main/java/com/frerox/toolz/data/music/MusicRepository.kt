package com.frerox.toolz.data.music

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
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

    suspend fun addTrack(track: MusicTrack) = musicDao.insertTrack(track)
    suspend fun deleteTrack(track: MusicTrack) = musicDao.deleteTrack(track)
    
    suspend fun createPlaylist(playlist: Playlist) = musicDao.insertPlaylist(playlist)
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
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        // Filter out tracks shorter than 30 seconds (likely ringtones/notifications)
        val selection = "${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("30000")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

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
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                
                // For folder view, we can extract the parent path
                val path = cursor.getString(dataColumn)
                
                tracks.add(
                    MusicTrack(
                        uri = contentUri.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        thumbnailUri = null // Will be loaded on demand or cached
                    )
                )
            }
        }
        
        // Save found tracks to database
        tracks.forEach { musicDao.insertTrack(it) }
        tracks
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
                val fileName = "thumb_${uri.hashCode()}.jpg"
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
                thumbnailUri = thumbnailUri
            )
        } catch (e: Exception) {
            MusicTrack(
                uri = uri.toString(),
                title = uri.lastPathSegment?.substringBeforeLast(".") ?: "Unknown",
                artist = "Unknown Artist",
                album = "Unknown Album",
                duration = 0
            )
        } finally {
            retriever.release()
        }
    }
}

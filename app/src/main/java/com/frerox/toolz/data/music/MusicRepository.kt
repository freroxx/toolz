package com.frerox.toolz.data.music

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
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

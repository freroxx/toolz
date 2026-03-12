package com.frerox.toolz.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.notepad.NoteDao
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.Playlist
import com.frerox.toolz.data.music.MusicDao
import com.frerox.toolz.data.music.MusicConverters
import com.frerox.toolz.data.steps.StepEntry
import com.frerox.toolz.data.steps.StepDao
import com.frerox.toolz.data.math.MathHistory
import com.frerox.toolz.data.math.MathHistoryDao
import com.frerox.toolz.data.pdf.PdfAnnotation
import com.frerox.toolz.data.pdf.PdfAnnotationDao
import com.frerox.toolz.data.pdf.PdfMetadata
import com.frerox.toolz.data.pdf.PdfMetadataDao
import com.frerox.toolz.data.notifications.NotificationEntry
import com.frerox.toolz.data.notifications.NotificationDao
import com.frerox.toolz.data.focus.AppLimit
import com.frerox.toolz.data.focus.AppLimitDao

@Database(
    entities = [
        Note::class, 
        MusicTrack::class, 
        Playlist::class, 
        StepEntry::class, 
        MathHistory::class,
        PdfAnnotation::class,
        PdfMetadata::class,
        NotificationEntry::class,
        AppLimit::class
    ], 
    version = 15,
    exportSchema = false
)
@TypeConverters(MusicConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun musicDao(): MusicDao
    abstract fun stepDao(): StepDao
    abstract fun mathHistoryDao(): MathHistoryDao
    abstract fun pdfAnnotationDao(): PdfAnnotationDao
    abstract fun pdfMetadataDao(): PdfMetadataDao
    abstract fun notificationDao(): NotificationDao
    abstract fun appLimitDao(): AppLimitDao
}

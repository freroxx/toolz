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
import com.frerox.toolz.data.focus.CaffeinateApp
import com.frerox.toolz.data.focus.CaffeinateDao
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.data.clipboard.ClipboardDao
import com.frerox.toolz.data.todo.TaskEntry
import com.frerox.toolz.data.todo.TaskDao
import com.frerox.toolz.data.todo.TodoConverters
import com.frerox.toolz.data.ai.AiChat
import com.frerox.toolz.data.ai.AiMessage
import com.frerox.toolz.data.ai.AiDao
import com.frerox.toolz.data.calendar.EventEntry
import com.frerox.toolz.data.calendar.EventDao
import com.frerox.toolz.data.password.PasswordEntity
import com.frerox.toolz.data.password.PasswordDao
import com.frerox.toolz.data.search.BookmarkEntry
import com.frerox.toolz.data.search.QuickLinkEntry
import com.frerox.toolz.data.search.SearchDao
import com.frerox.toolz.data.search.SearchHistoryEntry

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
        AppLimit::class,
        CaffeinateApp::class,
        ClipboardEntry::class,
        TaskEntry::class,
        AiChat::class,
        AiMessage::class,
        EventEntry::class,
        PasswordEntity::class,
        SearchHistoryEntry::class,
        BookmarkEntry::class,
        QuickLinkEntry::class
    ], 
    version = 31,
    exportSchema = false
)
@TypeConverters(MusicConverters::class, TodoConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun musicDao(): MusicDao
    abstract fun stepDao(): StepDao
    abstract fun mathHistoryDao(): MathHistoryDao
    abstract fun pdfAnnotationDao(): PdfAnnotationDao
    abstract fun pdfMetadataDao(): PdfMetadataDao
    abstract fun notificationDao(): NotificationDao
    abstract fun appLimitDao(): AppLimitDao
    abstract fun caffeinateDao(): CaffeinateDao
    abstract fun clipboardDao(): ClipboardDao
    abstract fun taskDao(): TaskDao
    abstract fun aiDao(): AiDao
    abstract fun eventDao(): EventDao
    abstract fun passwordDao(): PasswordDao
    abstract fun searchDao(): SearchDao
}

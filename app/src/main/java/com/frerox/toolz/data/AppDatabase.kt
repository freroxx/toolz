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

@Database(entities = [Note::class, MusicTrack::class, Playlist::class, StepEntry::class], version = 6, exportSchema = false)
@TypeConverters(MusicConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun musicDao(): MusicDao
    abstract fun stepDao(): StepDao
}

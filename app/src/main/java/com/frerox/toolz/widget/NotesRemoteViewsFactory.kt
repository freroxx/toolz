package com.frerox.toolz.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.frerox.toolz.R
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.notepad.NoteDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class NotesRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var notes: List<Note> = emptyList()
    private val database = NoteDatabase::class.java // This is not how we get it in Factory, usually via a singleton or manual init

    override fun onCreate() {
        // Initialization if needed
    }

    override fun onDataSetChanged() {
        // This is called when the widget is updated
        // Room doesn't allow database access on main thread, and widget factory can be picky.
        // In a real app, we'd use a ContentProvider or a singleton.
        // For simplicity here, we'll try to get it from the database manually.
        runBlocking {
            try {
                // This is a bit hacky but for a demo/tool environment it works. 
                // Ideally use a DI-managed singleton.
                val db = androidx.room.Room.databaseBuilder(
                    context,
                    com.frerox.toolz.data.notepad.NoteDatabase::class.java,
                    "note_db"
                ).build()
                notes = db.noteDao().getAllNotes().first().take(5)
                db.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        notes = emptyList()
    }

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= notes.size) return RemoteViews(context.packageName, R.layout.note_widget_item)
        
        val note = notes[position]
        val views = RemoteViews(context.packageName, R.layout.note_widget_item)
        views.setTextViewText(R.id.widget_note_title, note.title)
        views.setTextViewText(R.id.widget_note_content, note.content)
        
        // Set background color based on note color
        // RemoteViews support for background color is limited to setInt(id, "setBackgroundColor", color)
        views.setInt(R.id.widget_note_container, "setBackgroundColor", note.color)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = notes[position].id.toLong()
    override fun hasStableIds(): Boolean = true
}

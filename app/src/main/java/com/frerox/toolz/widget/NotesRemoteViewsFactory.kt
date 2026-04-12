package com.frerox.toolz.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.room.Room
import com.frerox.toolz.R
import com.frerox.toolz.data.AppDatabase
import com.frerox.toolz.data.notepad.Note
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import com.frerox.toolz.util.security.KeyManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class NotesRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var notes: List<Note> = emptyList()

    override fun onCreate() {
    }

    override fun onDataSetChanged() {
        // Room doesn't allow database access on main thread, and widget factory can be picky.
        runBlocking {
            try {
                System.loadLibrary("sqlcipher")
                val passphrase = KeyManager.getOrCreateMasterKey(context)
                val factory = SupportOpenHelperFactory(passphrase)

                val db = Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    "toolz_db"
                )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration(true)
                .build()
                notes = db.noteDao().getAllNotes().first().take(10)
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
        views.setTextViewText(R.id.widget_note_title, note.title.ifEmpty { "Note" })
        views.setTextViewText(R.id.widget_note_preview, note.content)
        
        // Material Expressive: Use a cleaner background if color is transparent or default
        val color = if (note.color == 0) 0xFFF5F5F5.toInt() else note.color
        views.setInt(R.id.widget_note_item_root, "setBackgroundColor", color)

        val fillInIntent = Intent().apply {
            putExtra("note_id", note.id)
            putExtra("navigate_to", "notepad")
        }
        views.setOnClickFillInIntent(R.id.widget_note_item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = try { notes[position].id.toLong() } catch (e: Exception) { position.toLong() }
    override fun hasStableIds(): Boolean = true
}

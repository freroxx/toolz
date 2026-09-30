package com.frerox.toolz.data.notepad

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes private files + releases grants when notes/attachments die.
 * DB cascade removes rows; without this, files/pdfs + note_img_*.jpg
 * accumulate forever (see audit §8).
 */
@Singleton
class AttachmentFileJanitor @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val noteDao: NoteDao,
    private val attachmentDao: NoteAttachmentDao,
) {
    fun fileForPrivatePdf(name: String): File = File(File(appContext.filesDir, "pdfs"), name)

    /** Best-effort cleanup for one attachment URI. Never throws. */
    fun cleanUri(uri: String?) {
        if (uri.isNullOrBlank()) return
        try {
            val parsed = android.net.Uri.parse(uri) ?: return
            if (parsed.scheme == "file") {
                val path = parsed.path ?: return
                val root = appContext.filesDir.canonicalPath
                val target = File(path).canonicalPath
                // Only delete inside our private dir — never arbitrary paths.
                if (target.startsWith(root)) {
                    if (File(target).delete()) return
                }
            }
        } catch (_: Exception) { }
        try {
            appContext.contentResolver.releasePersistableUriPermission(
                android.net.Uri.parse(uri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) { }
    }

    suspend fun onAttachmentRemoved(uri: String?) = cleanUri(uri)

    suspend fun onNotePermanentlyDeleted(note: Note) {
        try {
            val rows = attachmentDao.listForNote(note.id)
            rows.forEach { cleanUri(it.uri) }
        } catch (e: Exception) {
            Log.w("AttachmentJanitor", "list failed", e)
        }
        cleanUri(note.attachedPdfUri)
        cleanUri(note.attachedImageUri)
        // Audio URIs may be MediaStore (shared) — only release grant, never delete.
        try {
            note.attachedAudioUri?.let {
                appContext.contentResolver.releasePersistableUriPermission(
                    android.net.Uri.parse(it),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        } catch (_: Exception) { }
    }

    /** Nightly sweep: delete private pdfs/images no longer referenced. */
    suspend fun sweepOrphans(): Int {
        var deleted = 0
        try {
            val referenced = mutableSetOf<String>()
            noteDao.getAllNotesSync().forEach {
                it.attachedPdfUri?.let(referenced::add)
                it.attachedImageUri?.let(referenced::add)
            }
            val pdfDir = File(appContext.filesDir, "pdfs")
            pdfDir.listFiles()?.forEach { f ->
                val contentUri = "file://${f.absolutePath}"
                val contentAlt = android.net.Uri.fromFile(f).toString()
                if (contentUri !in referenced && contentAlt !in referenced) {
                    if (f.delete()) deleted++
                }
            }
            appContext.filesDir.listFiles { f -> f.name.startsWith("note_img_") }?.forEach { f ->
                val u1 = "file://${f.absolutePath}"
                val u2 = android.net.Uri.fromFile(f).toString()
                if (u1 !in referenced && u2 !in referenced) {
                    if (f.delete()) deleted++
                }
            }
        } catch (e: Exception) {
            Log.w("AttachmentJanitor", "sweep failed", e)
        }
        return deleted
    }
}

package com.frerox.toolz.data.notepad

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NoteRepository"

/**
 * Transactional note+attachment operations. Single place that keeps the
 * V2 `note_attachments` table and the legacy `notes.attached*` columns
 * consistent, with dedup and caps enforced atomically where Room allows.
 *
 * New code must go through here — never raw Dao calls from the UI layer.
 */
@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val attachmentDao: NoteAttachmentDao,
) {
    fun observeNotes() = noteDao.getAllNotes()
    fun observeTrash() = noteDao.getDeletedNotes()
    fun observeCounts() = noteDao.countActiveNotes()
    fun observeTrashCount() = noteDao.countTrashedNotes()
    fun search(query: String) = noteDao.searchNotes(query.trim(), NoteLimits.SEARCH_LIMIT)
    fun observeAttachments(noteId: Int) = attachmentDao.observeForNote(noteId)

    suspend fun get(noteId: Int): Note? = try {
        noteDao.getNoteById(noteId)
    } catch (e: Exception) {
        Log.w(TAG, "get($noteId) failed", e)
        null
    }

    suspend fun listAttachments(noteId: Int): List<NoteAttachment> = try {
        attachmentDao.listForNote(noteId)
    } catch (_: Exception) {
        emptyList()
    }

    /** Insert with createdAt/updatedAt stamped. Returns row id or -1. */
    suspend fun create(note: Note): Long = try {
        val now = System.currentTimeMillis()
        val stamped = note.copy(
            createdAt = now.takeIf { note.createdAt == 0L } ?: note.createdAt,
            updatedAt = now,
            timestamp = note.timestamp.takeIf { it != 0L } ?: now,
        )
        noteDao.insertNote(stamped)
    } catch (e: Exception) {
        Log.e(TAG, "create failed", e)
        -1L
    }

    /** Full-row save that always bumps updatedAt (fixes stale ordering). */
    suspend fun save(note: Note): Boolean = try {
        noteDao.insertNote(note.copy(updatedAt = System.currentTimeMillis()))
        true
    } catch (e: Exception) {
        Log.e(TAG, "save failed", e)
        false
    }

    /**
     * Insert attachment with cap + dedup. Returns the existing row when the
     * same (noteId, uri) is already attached — no duplicate rows.
     */
    suspend fun addAttachment(
        noteId: Int,
        kind: String,
        uri: String,
        displayName: String? = null,
        sizeBytes: Long = 0L,
        pageHint: Int = 0,
        mimeType: String? = null,
        durationMs: Long = 0L,
    ): AttachOutcome {
        val safeKind = try {
            NoteAttachmentKind.requireValid(kind)
        } catch (e: IllegalArgumentException) {
            return AttachOutcome.InvalidKind
        }
        if (uri.isBlank()) return AttachOutcome.InvalidKind
        return try {
            val existing = attachmentDao.findForNoteByUri(noteId, uri).firstOrNull()
            if (existing != null) return AttachOutcome.Duplicate(existing)
            val cap = when (safeKind) {
                NoteAttachment.KIND_PDF -> NoteLimits.MAX_PDF_PER_NOTE
                NoteAttachment.KIND_IMAGE -> NoteLimits.MAX_IMAGE_PER_NOTE
                NoteAttachment.KIND_AUDIO -> NoteLimits.MAX_AUDIO_PER_NOTE
                else -> NoteLimits.MAX_ATTACHMENTS_PER_NOTE
            }
            if (attachmentDao.countByKind(noteId, safeKind) >= cap) return AttachOutcome.Capped
            if (attachmentDao.countForNote(noteId) >= NoteLimits.MAX_ATTACHMENTS_PER_NOTE) {
                return AttachOutcome.Capped
            }
            val id = attachmentDao.insert(
                NoteAttachment(
                    noteId = noteId, kind = safeKind, uri = uri,
                    displayName = displayName, sizeBytes = sizeBytes,
                    pageHint = pageHint, mimeType = mimeType, durationMs = durationMs,
                )
            )
            // Legacy single-slot compat: fill when blank, never overwrite.
            syncLegacySlot(noteId, safeKind, uri, displayName)
            noteDao.getNoteById(noteId)?.let {
                noteDao.insertNote(it.copy(updatedAt = System.currentTimeMillis()))
            }
            AttachOutcome.Attached(id, uri)
        } catch (e: Exception) {
            Log.e(TAG, "addAttachment failed", e)
            AttachOutcome.Failed
        }
    }

    suspend fun removeAttachment(id: Long): Boolean = try {
        val row = attachmentDao.getById(id) ?: return false
        attachmentDao.deleteById(id)
        repointLegacySlot(row.noteId, row.kind, row.uri)
        true
    } catch (e: Exception) {
        Log.e(TAG, "removeAttachment failed", e)
        false
    }

    suspend fun removeAttachmentsByUri(noteId: Int, uri: String): Int = try {
        attachmentDao.deleteForNoteByUri(noteId, uri)
    } catch (e: Exception) {
        Log.e(TAG, "removeAttachmentsByUri failed", e)
        0
    }

    /** Forward-fill V2 rows from legacy columns (idempotent). */
    suspend fun healForward(note: Note) {
        if (note.id == 0) return
        try {
            if (attachmentDao.countForNote(note.id) > 0) return
            val rows = buildList {
                note.attachedPdfUri?.takeIf { it.isNotBlank() }?.let {
                    add(NoteAttachment(noteId = note.id, kind = NoteAttachment.KIND_PDF, uri = it))
                }
                note.attachedImageUri?.takeIf { it.isNotBlank() }?.let {
                    add(NoteAttachment(noteId = note.id, kind = NoteAttachment.KIND_IMAGE, uri = it))
                }
                note.attachedAudioUri?.takeIf { it.isNotBlank() }?.let {
                    add(
                        NoteAttachment(
                            noteId = note.id, kind = NoteAttachment.KIND_AUDIO, uri = it,
                            displayName = note.attachedAudioName,
                        )
                    )
                }
            }
            if (rows.isNotEmpty()) attachmentDao.insertAll(rows)
        } catch (e: Exception) {
            Log.w(TAG, "healForward failed for ${note.id}", e)
        }
    }

    private suspend fun syncLegacySlot(noteId: Int, kind: String, uri: String, name: String?) {
        try {
            val cur = noteDao.getNoteById(noteId) ?: return
            when (kind) {
                NoteAttachment.KIND_PDF ->
                    if (cur.attachedPdfUri.isNullOrBlank()) noteDao.updateAttachedPdfUri(noteId, uri)
                NoteAttachment.KIND_IMAGE ->
                    if (cur.attachedImageUri.isNullOrBlank()) noteDao.updateAttachedImageUri(noteId, uri)
                NoteAttachment.KIND_AUDIO ->
                    if (cur.attachedAudioUri.isNullOrBlank()) noteDao.updateAttachedAudio(noteId, uri, name)
            }
        } catch (_: Exception) { }
    }

    private suspend fun repointLegacySlot(noteId: Int, kind: String, deletedUri: String) {
        try {
            val note = noteDao.getNoteById(noteId) ?: return
            when (kind) {
                NoteAttachment.KIND_PDF -> {
                    if (note.attachedPdfUri != deletedUri) return
                    val next = attachmentDao.listForNote(noteId)
                        .firstOrNull { it.kind == NoteAttachment.KIND_PDF }?.uri
                    noteDao.updateAttachedPdfUri(noteId, next)
                }
                NoteAttachment.KIND_IMAGE -> {
                    if (note.attachedImageUri != deletedUri) return
                    val next = attachmentDao.listForNote(noteId)
                        .firstOrNull { it.kind == NoteAttachment.KIND_IMAGE }?.uri
                    noteDao.updateAttachedImageUri(noteId, next)
                }
                NoteAttachment.KIND_AUDIO -> {
                    if (note.attachedAudioUri != deletedUri) return
                    val next = attachmentDao.listForNote(noteId)
                        .firstOrNull { it.kind == NoteAttachment.KIND_AUDIO }
                    noteDao.updateAttachedAudio(noteId, next?.uri, next?.displayName)
                }
            }
        } catch (_: Exception) { }
    }

    sealed interface AttachOutcome {
        data class Attached(val id: Long, val uri: String) : AttachOutcome
        data class Duplicate(val existing: NoteAttachment) : AttachOutcome
        data object Capped : AttachOutcome
        data object InvalidKind : AttachOutcome
        data object Failed : AttachOutcome
    }
}

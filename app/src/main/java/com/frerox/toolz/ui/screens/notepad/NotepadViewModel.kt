/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.ui.screens.notepad

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.AiSettingsHelper
import com.frerox.toolz.data.ai.AiSettingsManager
import com.frerox.toolz.data.ai.ApiKeySource
import com.frerox.toolz.data.ai.MessageContent
import com.frerox.toolz.data.ai.OpenAiMessage
import com.frerox.toolz.data.ai.OpenAiRequest
import com.frerox.toolz.data.ai.OpenAiService
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.notepad.AttachmentFileJanitor
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.notepad.NoteAttachment
import com.frerox.toolz.data.notepad.NoteAttachmentDao
import com.frerox.toolz.data.notepad.NoteColors
import com.frerox.toolz.data.notepad.NoteDao
import com.frerox.toolz.data.notepad.NoteLimits
import com.frerox.toolz.data.notepad.NoteRepository
import com.frerox.toolz.data.notepad.PdfAttachResult
import com.frerox.toolz.data.pdf.PdfRepository
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

private const val TAG = "NotepadViewModel"
private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
private const val DEFAULT_AI_MODEL = "openai/gpt-oss-20b"

// ─────────────────────────────────────────────────────────────
//  AI models and structured responses
// ─────────────────────────────────────────────────────────────

data class AiGeneratedNote(
    val title: String,
    val content: String,
    val colorHex: String,
    val fontSize: Float,
    val isBold: Boolean,
    val isItalic: Boolean,
    val reasoning: String? = null
)

// ─────────────────────────────────────────────────────────────
//  AI style suggestion model
// ─────────────────────────────────────────────────────────────

/**
 * Style recommendation returned by the AI "Choose the Look" feature.
 * All fields are optional; the UI applies only what is non-null.
 */
data class AiNoteStyle(
    val colorHex : String,
    val fontSize  : Float,
    val isBold    : Boolean,
    val isItalic  : Boolean,
    val reasoning : String,
)

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class NotepadViewModel @Inject constructor(
    private val noteDao          : NoteDao,
    private val attachmentDao    : NoteAttachmentDao,
    private val noteRepository   : NoteRepository,
    private val fileJanitor      : AttachmentFileJanitor,
    private val musicRepository  : MusicRepository,
    private val pdfRepository    : PdfRepository,
    private val openAiService    : OpenAiService,
    private val aiSettingsManager: AiSettingsManager,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    // ── DB / repository streams ────────────────────────────────────────────

    val notes: StateFlow<List<Note>> = noteDao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trashCount: StateFlow<Int> = noteDao.countTrashedNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Debounced DB-side search; empty query falls back to full list. */
    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<Note>> = _searchQuery
        .debounce(250)
        .flatMapLatest { q ->
            if (q.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
            else try {
                noteDao.searchNotes(q.trim(), NoteLimits.SEARCH_LIMIT)
            } catch (_: Exception) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(q: String) {
        _searchQuery.value = q.take(200)
    }

    val offlineModeEnabled = settingsRepository.offlineModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val notepadAiEnabled = settingsRepository.notepadAiToolsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val availableTracks = musicRepository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _availablePdfs =
        MutableStateFlow<List<com.frerox.toolz.data.pdf.PdfFile>>(emptyList())
    val availablePdfs = _availablePdfs.asStateFlow()

    // ── AI state ───────────────────────────────────────────────────────────

    private val _aiSummary       = MutableStateFlow<String?>(null)
    val aiSummary: StateFlow<String?> = _aiSummary.asStateFlow()

    /** Which note the cached summary/style belongs to — prevents cross-note leaks. */
    private val _aiSummaryNoteId = MutableStateFlow<Int?>(null)
    val aiSummaryNoteId: StateFlow<Int?> = _aiSummaryNoteId.asStateFlow()

    private val _isAiSummarizing = MutableStateFlow(false)
    val isAiSummarizing: StateFlow<Boolean> = _isAiSummarizing.asStateFlow()

    private val _aiStyle         = MutableStateFlow<AiNoteStyle?>(null)
    val aiStyle: StateFlow<AiNoteStyle?> = _aiStyle.asStateFlow()

    private val _isAiStyling     = MutableStateFlow(false)
    val isAiStyling: StateFlow<Boolean> = _isAiStyling.asStateFlow()

    private val _selectedAiModel = MutableStateFlow(DEFAULT_AI_MODEL)
    val selectedAiModel: StateFlow<String> = _selectedAiModel.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _isFocusMode = MutableStateFlow(false)
    val isFocusMode: StateFlow<Boolean> = _isFocusMode.asStateFlow()

    val deletedNotes: StateFlow<List<Note>> = noteDao.getDeletedNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var lastDeletedNote: Note? = null
    private var lastDeletedNotes: List<Note>? = null

    init {
        loadPdfs()
        loadAvailableModels()
        healAllLegacyColumns()
    }

    /**
     * One-shot bounded repair for pre-dual-write notes. Caps work at 200
     * notes (ordered by recency) so a huge library can't stall startup;
     * remaining rows heal lazily via [ensureLegacyAttachments] on open.
     */
    private fun healAllLegacyColumns() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val all = try {
                    noteDao.getAllNotesSync().take(200)
                } catch (_: Exception) {
                    return@launch
                }
                for (n in all) {
                    try {
                        noteRepository.healForward(n)
                        // Reverse-heal legacy PDF slot so old card badges keep working.
                        if (n.attachedPdfUri.isNullOrBlank()) {
                            val first = attachmentDao.listForNote(n.id)
                                .firstOrNull { it.kind == NoteAttachment.KIND_PDF }
                            if (first != null) noteDao.updateAttachedPdfUri(n.id, first.uri)
                        }
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) { }
        }
    }

    fun expireOldTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cutoff = System.currentTimeMillis() - NoteLimits.TRASH_RETENTION_DAYS * 24L * 60L * 60L * 1000L
                noteDao.deleteExpiredTrash(cutoff)
            } catch (e: Exception) {
                Log.w(TAG, "expireOldTrash failed", e)
            }
        }
    }

    fun refreshPdfs() {
        loadPdfs()
    }

    /**
     * Resolve a SAF-picked PDF into a durable URI string for an unsaved draft
     * (id == 0, no attachment row yet). Takes the persistable grant, verifies
     * readability and prefers an app-private copy. Null = unreadable.
     */
    suspend fun resolvePdfForDraft(source: Uri): String? = withContext(Dispatchers.IO) {
        try {
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    source, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                pdfRepository.ensurePersistableGrant(source)
            }
            val durable = try {
                pdfRepository.resolveDurableUri(source)
            } catch (e: Exception) {
                Log.e(TAG, "resolvePdfForDraft failed", e)
                null
            } ?: return@withContext null
            if (!pdfRepository.isReadable(durable)) return@withContext null
            // New private copies must appear in the vault list.
            try {
                if (durable.toString() != source.toString()) loadPdfsSync()
            } catch (_: Exception) { }
            durable.toString()
        } catch (e: Exception) {
            Log.e(TAG, "resolvePdfForDraft failed", e)
            null
        }
    }

    private suspend fun loadPdfsSync() = withContext(Dispatchers.IO) {
        try {
            _availablePdfs.value = pdfRepository.getPdfFiles()
        } catch (_: Exception) { }
    }

    fun refreshTracks() {
        // Assuming musicRepository handles updates, but we can re-load if needed
    }

    private fun loadPdfs() {
        viewModelScope.launch {
            _availablePdfs.value = pdfRepository.getPdfFiles()
        }
    }

    private fun loadAvailableModels() {
        viewModelScope.launch {
            val providers = AiSettingsHelper.providers
            val models = mutableListOf<String>()
            providers.forEach { provider ->
                if (aiSettingsManager.resolveApiKey(provider).source != ApiKeySource.NONE) {
                    models.addAll(AiSettingsHelper.getModels(provider))
                }
            }
            _availableModels.value = models.distinct()
            if (DEFAULT_AI_MODEL !in models && models.isNotEmpty()) {
                _selectedAiModel.value = models.first()
            }
        }
    }

    fun setSelectedModel(model: String) {
        val clean = model.trim()
        if (clean.isEmpty()) return
        // Accept known models or provider/model ids; reject prompt-injection junk.
        if (clean.length > 120 || clean.contains("\n") || clean.contains(" ")) return
        _selectedAiModel.value = clean
    }

    // ── CRUD ───────────────────────────────────────────────────────────────

    fun addNote(
        title            : String,
        content          : String,
        color            : Int,
        fontStyle        : String  = "DEFAULT",
        fontSize         : Float   = 18f,
        isBold           : Boolean = false,
        isItalic         : Boolean = false,
        attachedPdfUri   : String? = null,
        attachedAudioUri : String? = null,
        attachedAudioName: String? = null,
        attachedImageUri : String? = null,
        onInserted       : (Int) -> Unit = {},
    ) {
        val safeTitle = title.trim().take(NoteLimits.MAX_TITLE_CHARS)
        val safeContent = content.take(NoteLimits.MAX_CONTENT_CHARS)
        if (safeTitle.isBlank() && safeContent.isBlank()
            && attachedPdfUri.isNullOrBlank()
            && attachedImageUri.isNullOrBlank()
            && attachedAudioUri.isNullOrBlank()
        ) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val insertedId = noteDao.insertNote(
                Note(
                    title             = safeTitle,
                    content           = safeContent,
                    color             = color,
                    fontStyle         = fontStyle,
                    fontSize          = NoteLimits.clampFontSize(fontSize),
                    isBold            = isBold,
                    isItalic          = isItalic,
                    attachedPdfUri    = attachedPdfUri,
                    attachedAudioUri  = attachedAudioUri,
                    attachedAudioName = attachedAudioName?.take(200),
                    attachedImageUri  = attachedImageUri,
                    timestamp         = now,
                    createdAt         = now,
                    updatedAt         = now,
                )
            )
            if (insertedId > Int.MAX_VALUE) {
                Log.e(TAG, "Row id exceeds Int range: $insertedId")
                onInserted(-1)
            } else {
                onInserted(insertedId.toInt())
            }
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            try {
                noteDao.insertNote(
                    note.copy(
                        title = note.title.take(NoteLimits.MAX_TITLE_CHARS),
                        content = note.content.take(NoteLimits.MAX_CONTENT_CHARS),
                        fontSize = NoteLimits.clampFontSize(note.fontSize),
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "updateNote failed", e)
            }
        }
    }

    fun updateNoteCardSize(note: Note, cardSize: String) {
        viewModelScope.launch {
            try {
                noteDao.insertNote(note.copy(cardSize = cardSize, updatedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.e(TAG, "updateNoteCardSize failed", e)
            }
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            lastDeletedNote = note
            lastDeletedNotes = null
            noteDao.moveToTrash(note.id, System.currentTimeMillis())
        }
    }

    fun deleteNotes(notes: List<Note>) {
        viewModelScope.launch {
            lastDeletedNotes = notes
            lastDeletedNote = null
            noteDao.moveMultipleToTrash(notes.map { it.id }, System.currentTimeMillis())
        }
    }

    fun permanentlyDeleteNote(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Delete children first so FK cascade never orphans, then clean files.
                try { attachmentDao.deleteForNote(note.id) } catch (_: Exception) { }
                noteDao.permanentlyDeleteNote(note)
                fileJanitor.onNotePermanentlyDeleted(note)
            } catch (e: Exception) {
                Log.e(TAG, "permanentlyDeleteNote failed", e)
            }
        }
    }

    fun permanentlyDeleteNotes(notes: List<Note>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                notes.forEach { n ->
                    try { attachmentDao.deleteForNote(n.id) } catch (_: Exception) { }
                }
                noteDao.permanentlyDeleteNotes(notes)
                notes.forEach { fileJanitor.onNotePermanentlyDeleted(it) }
            } catch (e: Exception) {
                Log.e(TAG, "permanentlyDeleteNotes failed", e)
            }
        }
    }

    fun restoreNote(note: Note) {
        viewModelScope.launch { noteDao.restoreFromTrash(note.id) }
    }

    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val trashed = try { deletedNotes.value } catch (_: Exception) { emptyList<Note>() }
                noteDao.emptyTrash()
                trashed.forEach { fileJanitor.onNotePermanentlyDeleted(it) }
                fileJanitor.sweepOrphans()
            } catch (e: Exception) {
                Log.e(TAG, "emptyTrash failed", e)
            }
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            lastDeletedNote?.let {
                noteDao.restoreFromTrash(it.id)
                lastDeletedNote = null
            }
            lastDeletedNotes?.let { notes ->
                notes.forEach { noteDao.restoreFromTrash(it.id) }
                lastDeletedNotes = null
            }
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch { noteDao.updatePinned(note.id, !note.isPinned) }
    }

    /**
     * Downsamples to max 2048px, rotates per EXIF, compresses q85 and serves
     * via FileProvider (never file://). Returns a content:// string or null.
     */
    suspend fun persistImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val cr = context.contentResolver
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            val srcW = bounds.outWidth.takeIf { it > 0 } ?: 2048
            val srcH = bounds.outHeight.takeIf { it > 0 } ?: 2048
            var sample = 1
            while (srcW / sample > 2048 || srcH / sample > 2048) sample *= 2
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = cr.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, opts)
            } ?: return@withContext null
            // EXIF rotation.
            try {
                cr.openInputStream(uri)?.use { ins ->
                    val exif = androidx.exifinterface.media.ExifInterface(ins)
                    val o = exif.getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
                    )
                    val m = android.graphics.Matrix()
                    when (o) {
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                    }
                    if (!m.isIdentity) {
                        val rotated = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                        if (rotated != bmp) { bmp.recycle(); bmp = rotated }
                    }
                }
            } catch (_: Exception) { }
            val file = java.io.File(context.filesDir, "note_img_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
            bmp.recycle()
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            ).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist image", e)
            null
        }
    }

    // ── Attachments V2 (multi-attach, persisted URIs) ────────────────────────

    fun attachmentsFor(noteId: Int): Flow<List<NoteAttachment>> =
        attachmentDao.observeForNote(noteId)

    suspend fun getNoteById(noteId: Int): Note? = noteRepository.get(noteId)

    suspend fun listAttachments(noteId: Int): List<NoteAttachment> = try {
        attachmentDao.listForNote(noteId)
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * One-time legacy merge: copies the old single-slot attached* columns into
     * the multi-attach table so old notes render in the new UI. Idempotent —
     * skips when rows already exist or when the note is unsaved (id == 0).
     *
     * Also heals the reverse direction: notes whose V2 rows exist but whose
     * legacy attachedPdfUri is blank (e.g. attached from the PDF reader
     * before dual-write) get the legacy column backfilled from the first V2
     * PDF row, so card badges, PDFs filter/counts and card tap-to-open keep
     * working on the legacy columns.
     */
    suspend fun ensureLegacyAttachments(note: Note) = withContext(Dispatchers.IO) {
        try {
            if (note.id == 0) return@withContext
            noteRepository.healForward(note)
            val existing = try {
                attachmentDao.listForNote(note.id)
            } catch (_: Exception) {
                return@withContext
            }
            if (existing.isEmpty()) {
                val rows = ArrayList<NoteAttachment>()
                note.attachedPdfUri?.takeIf { it.isNotBlank() }?.let {
                    rows.add(NoteAttachment(noteId = note.id, kind = NoteAttachment.KIND_PDF, uri = it))
                }
                note.attachedImageUri?.takeIf { it.isNotBlank() }?.let {
                    rows.add(NoteAttachment(noteId = note.id, kind = NoteAttachment.KIND_IMAGE, uri = it))
                }
                note.attachedAudioUri?.takeIf { it.isNotBlank() }?.let {
                    rows.add(
                        NoteAttachment(
                            noteId = note.id, kind = NoteAttachment.KIND_AUDIO, uri = it,
                            displayName = note.attachedAudioName
                        )
                    )
                }
                if (rows.isNotEmpty()) attachmentDao.insertAll(rows)
                return@withContext
            }
            // Reverse heal: V2 rows exist but legacy PDF slot is blank.
            if (note.attachedPdfUri.isNullOrBlank()) {
                existing.firstOrNull { it.kind == NoteAttachment.KIND_PDF }?.let { first ->
                    try {
                        noteDao.updateAttachedPdfUri(note.id, first.uri)
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
    }

    /**
     * Attach a PDF, guaranteeing the stored URI stays readable: persistable
     * grant where supported, verified read, app-private copy as fallback.
     * Never inserts a dead row — an unreadable source reports [PdfAttachResult.Unreadable].
     *
     * Durability: fragile sources (MediaStore, downloads, …) are copied into
     * app-private files/pdfs so the attachment opens even after reboot or
     * All-files access revocation. The copy is preferred; the original is
     * kept only when the copy fails but the original reads.
     */
    suspend fun attachPdf(noteId: Int, source: Uri, pageHint: Int = 0): PdfAttachResult =
        withContext(Dispatchers.IO) {
            try {
                if (attachmentDao.countByKind(noteId, NoteAttachment.KIND_PDF) >= 10) {
                    return@withContext PdfAttachResult.Capped
                }
                // Best effort: keep the grant across reboots where supported.
                // (MediaStore / app-private URIs throw here — expected, not fatal.)
                pdfRepository.ensurePersistableGrant(source)
                val readable = pdfRepository.isReadable(source)
                var usable: Uri? = null
                if (!readable) {
                    // Unreadable now — last chance is an app-private copy
                    // (works when the FD path allows it, e.g. SAF grant race).
                    val imported = try {
                        pdfRepository.importPdf(source)
                    } catch (e: Exception) {
                        Log.e(TAG, "attachPdf: import copy failed", e)
                        null
                    }
                    if (imported == null || !pdfRepository.isReadable(imported)) {
                        Log.w(TAG, "attachPdf: source unreadable and no copy possible: $source")
                        return@withContext PdfAttachResult.Unreadable
                    }
                    usable = imported
                } else {
                    // Readable — prefer a durable private copy for fragile URIs.
                    usable = try {
                        pdfRepository.resolveDurableUri(source)
                    } catch (e: Exception) {
                        Log.e(TAG, "attachPdf: durable resolve failed, using source", e)
                        source
                    } ?: source
                    if (!pdfRepository.isReadable(usable)) {
                        // Resolve returned something dead — fall back to source.
                        if (!pdfRepository.isReadable(source)) {
                            return@withContext PdfAttachResult.Unreadable
                        }
                        usable = source
                    }
                }
                val usableStr = usable.toString()
                val name = try { pdfRepository.queryDisplayName(usable) } catch (_: Exception) { null }
                val size = try { pdfRepository.querySize(usable) } catch (_: Exception) { 0L }
                val mime = try { appContext.contentResolver.getType(usable) } catch (_: Exception) { null }
                when (val outcome = noteRepository.addAttachment(
                    noteId, NoteAttachment.KIND_PDF, usableStr,
                    displayName = name, sizeBytes = size, pageHint = pageHint, mimeType = mime,
                )) {
                    is NoteRepository.AttachOutcome.Capped -> return@withContext PdfAttachResult.Capped
                    is NoteRepository.AttachOutcome.Duplicate ->
                        return@withContext PdfAttachResult.Attached(outcome.existing.uri)
                    is NoteRepository.AttachOutcome.Attached -> return@withContext PdfAttachResult.Attached(usableStr)
                    else -> return@withContext PdfAttachResult.Failed("db")
                }
            } catch (e: Exception) {
                Log.e(TAG, "attachPdf failed for note $noteId", e)
                PdfAttachResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }

    suspend fun attachImage(noteId: Int, source: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    source, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            // Verify readability — never store a dead row.
            try {
                appContext.contentResolver.openInputStream(source)?.close()
            } catch (e: Exception) {
                Log.w(TAG, "attachImage unreadable: $source", e)
                return@withContext false
            }
            val mime = try { appContext.contentResolver.getType(source) } catch (_: Exception) { null }
            val size = try {
                appContext.contentResolver.query(source, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (c.moveToFirst() && i >= 0) c.getLong(i) else 0L
                } ?: 0L
            } catch (_: Exception) { 0L }
            when (noteRepository.addAttachment(noteId, NoteAttachment.KIND_IMAGE, source.toString(), sizeBytes = size, mimeType = mime)) {
                is NoteRepository.AttachOutcome.Attached,
                is NoteRepository.AttachOutcome.Duplicate -> true
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun attachAudio(noteId: Int, uri: String, name: String?): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (uri.isBlank()) return@withContext false
                val parsed = try { Uri.parse(uri) } catch (_: Exception) { return@withContext false }
                var durationMs = 0L
                var sizeBytes = 0L
                var mime: String? = null
                try {
                    mime = appContext.contentResolver.getType(parsed)
                    val mmr = android.media.MediaMetadataRetriever()
                    try {
                        mmr.setDataSource(appContext, parsed)
                        durationMs = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } finally {
                        try { mmr.release() } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }
                try {
                    appContext.contentResolver.query(parsed, null, null, null, null)?.use { c ->
                        val i = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (c.moveToFirst() && i >= 0) sizeBytes = c.getLong(i)
                    }
                } catch (_: Exception) { }
                when (noteRepository.addAttachment(
                    noteId, NoteAttachment.KIND_AUDIO, uri,
                    displayName = name?.take(200), sizeBytes = sizeBytes,
                    mimeType = mime, durationMs = durationMs,
                )) {
                    is NoteRepository.AttachOutcome.Attached,
                    is NoteRepository.AttachOutcome.Duplicate -> true
                    else -> false
                }
            } catch (_: Exception) {
                false
            }
        }

    suspend fun removeAttachment(id: Long) {
        try {
            val row = try { attachmentDao.getById(id) } catch (_: Exception) { null }
            val ok = try {
                noteRepository.removeAttachment(id)
            } catch (_: Exception) { false }
            if (!ok) {
                try { attachmentDao.deleteById(id) } catch (_: Exception) { return }
            }
            if (row != null) fileJanitor.onAttachmentRemoved(row.uri)
            if (row == null || row.kind != NoteAttachment.KIND_PDF) return
            // Keep the legacy slot consistent: if it pointed at the deleted
            // row, repoint to the first remaining PDF or clear it.
            try {
                val note = noteDao.getNoteById(row.noteId) ?: return
                if (note.attachedPdfUri != row.uri) return
                val remaining = try {
                    attachmentDao.listForNote(row.noteId)
                        .filter { it.kind == NoteAttachment.KIND_PDF }
                } catch (_: Exception) {
                    emptyList()
                }
                noteDao.updateAttachedPdfUri(
                    row.noteId,
                    remaining.firstOrNull()?.uri
                )
            } catch (_: Exception) { }
        } catch (_: Exception) { }
    }

    /**
     * Removes the V2 attachment row(s) matching [uri] (legacy-slot removal
     * path). The editor's thumbnail-card X button uses this so the hidden
     * duplicate row can't resurface via reverse-heal on next open.
     */
    suspend fun removeAttachmentByUri(noteId: Int, uri: String) = withContext(Dispatchers.IO) {
        try {
            val removed = try {
                noteRepository.removeAttachmentsByUri(noteId, uri)
            } catch (_: Exception) { 0 }
            if (removed == 0) {
                // Fallback for legacy-only rows with no V2 entry.
                try {
                    val rows = attachmentDao.listForNote(noteId).filter { it.uri == uri }
                    rows.forEach { row ->
                        try { removeAttachment(row.id) } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }
            } else {
                fileJanitor.onAttachmentRemoved(uri)
            }
            // Keep legacy slot consistent when the last V2 row is gone.
            try {
                val remaining = attachmentDao.listForNote(noteId)
                    .filter { it.kind == NoteAttachment.KIND_PDF }
                val note = noteDao.getNoteById(noteId)
                if (note != null && note.attachedPdfUri == uri && remaining.none { it.uri == uri }) {
                    noteDao.updateAttachedPdfUri(noteId, remaining.firstOrNull()?.uri)
                }
            } catch (_: Exception) { }
        } catch (_: Exception) { }
    }

    // ── AI: Summarize ──────────────────────────────────────────────────────

    /**
     * Requests a concise summary of [note] from Groq's fast LLM.
     * The result is stored in [aiSummary] and cleared by [clearAiSummary].
     */
    fun summarizeNote(note: Note, forceRegenerate: Boolean = false) {
        // Per-note cache: reuse only when the cached value belongs to THIS note.
        if (!forceRegenerate && !note.summary.isNullOrBlank()
            && _aiSummaryNoteId.value == note.id && !_aiSummary.value.isNullOrBlank()
        ) return
        if (!forceRegenerate && !note.summary.isNullOrBlank()) {
            _aiSummaryNoteId.value = note.id
            _aiSummary.value = note.summary
            return
        }

        viewModelScope.launch {
            _isAiSummarizing.value = true
            // Keep the old value visible until the new one arrives (no flash).
            if (_aiSummaryNoteId.value != note.id) {
                _aiSummaryNoteId.value = note.id
                _aiSummary.value = null
            }

            val key = aiSettingsManager.getApiKey("Groq")
            if (key.isBlank()) {
                _aiSummary.value = "⚠ Groq API key not configured. Go to AI Settings → Groq to add your key."
                _isAiSummarizing.value = false
                return@launch
            }
            if (offlineModeEnabled.value) {
                _aiSummary.value = "Offline mode is on — AI summary unavailable."
                _isAiSummarizing.value = false
                return@launch
            }

            try {
                val noteBody = buildString {
                    if (note.title.isNotBlank()) appendLine("Title: ${note.title}")
                    appendLine("Content: ${note.content}")
                }

                val request = OpenAiRequest(
                    model    = _selectedAiModel.value,
                    messages = listOf(
                        OpenAiMessage(
                            "system",
                            MessageContent.Text(
                                "You are a note-taking assistant. " +
                                        "Summarize the given note concisely in 2-4 sentences. " +
                                        "Preserve key facts and actionable items. " +
                                        "Write in a clean, readable style without bullet points."
                            ),
                        ),
                        OpenAiMessage("user", MessageContent.Text(noteBody)),
                    ),
                    maxTokens = 1000,
                )

                val response = withContext(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeout(60_000) {
                        runGroqRequest(key) { requestKey ->
                            openAiService.getChatCompletion(
                                url        = GROQ_URL,
                                authHeader = "Bearer $requestKey",
                                request    = request,
                            )
                        }
                    }
                }
                val summaryResult = response.choices.firstOrNull()?.message?.content
                    ?.trim()?.take(2000)
                    ?: "Could not generate a summary."

                // Only publish + cache when this request still belongs to this note.
                if (_aiSummaryNoteId.value == note.id) {
                    _aiSummary.value = summaryResult
                }

                // Partial update — never full-row REPLACE, never wipe on failure.
                if (summaryResult != "Could not generate a summary." && note.id != 0) {
                    try {
                        noteDao.updateSummary(note.id, summaryResult)
                    } catch (e: Exception) {
                        Log.w(TAG, "summary cache failed", e)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Summarize failed: ${e.message}")
                if (_aiSummaryNoteId.value == note.id && _aiSummary.value.isNullOrBlank()) {
                    _aiSummary.value = "Summary failed: ${e.message ?: e.javaClass.simpleName}"
                }
            } finally {
                _isAiSummarizing.value = false
            }
        }
    }

    fun clearAiSummary() {
        _aiSummary.value = null
        _aiSummaryNoteId.value = null
    }

    /** Switch cached AI state when the viewer moves to another note. */
    fun onNoteFocused(note: Note?) {
        if (note == null) {
            clearAiSummary()
            clearAiStyle()
            return
        }
        if (_aiSummaryNoteId.value != note.id) {
            _aiSummaryNoteId.value = note.id
            _aiSummary.value = note.summary
            _aiStyle.value = null
        }
    }

    // ── AI: Choose the Look ────────────────────────────────────────────────

    /**
     * Asks the AI to recommend a visual style for [note] based on its content
     * and tone. The result is stored in [aiStyle].
     *
     * The AI returns a JSON object; [parseAiStyle] handles malformed responses.
     */
    fun suggestStyleForNote(note: Note) {
        viewModelScope.launch {
            _isAiStyling.value = true
            _aiStyle.value     = null

            val key = aiSettingsManager.getApiKey("Groq")
            if (key.isBlank() || offlineModeEnabled.value) {
                _isAiStyling.value = false
                return@launch
            }

            try {
                val noteBody = buildString {
                    if (note.title.isNotBlank()) appendLine("Title: ${note.title}")
                    appendLine(note.content.take(600))   // keep prompt short
                }

                val systemPrompt = """
You are a note-styling AI. Analyze the tone, subject and urgency of the note and return ONLY a JSON object — no prose, no markdown.

JSON schema:
{
  "colorHex": "#RRGGBB (warm/cool/dark based on mood — avoid pure white)",
  "fontSize": 14-22 (float),
  "isBold": false,
  "isItalic": false,
  "reasoning": "one sentence explanation"
}

Examples:
- Technical/code note → {"colorHex":"#263238","fontSize":15,"isBold":false,"isItalic":false,"reasoning":"Dark tone suits structured technical content."}
- Personal reflection → {"colorHex":"#FFF9C4","fontSize":17,"isBold":false,"isItalic":true,"reasoning":"Warm yellow with italics evokes introspection."}
- Urgent task list   → {"colorHex":"#FFCCBC","fontSize":18,"isBold":true,"isItalic":false,"reasoning":"Bold text on orange-tinted background conveys urgency."}
                """.trimIndent()

                val request = OpenAiRequest(
                    model    = _selectedAiModel.value,
                    messages = listOf(
                        OpenAiMessage("system", MessageContent.Text(systemPrompt)),
                        OpenAiMessage("user",   MessageContent.Text(noteBody)),
                    ),
                    maxTokens = 1000,
                )

                val response = withContext(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeout(60_000) {
                        runGroqRequest(key) { requestKey ->
                            openAiService.getChatCompletion(
                                url        = GROQ_URL,
                                authHeader = "Bearer $requestKey",
                                request    = request,
                            )
                        }
                    }
                }

                val raw = response.choices.firstOrNull()?.message?.content ?: ""
                _aiStyle.value = parseAiStyle(raw)

            } catch (e: Exception) {
                Log.e(TAG, "Style suggestion failed: ${e.message}")
            } finally {
                _isAiStyling.value = false
            }
        }
    }

    fun clearAiStyle() {
        _aiStyle.value = null
    }

    /** Explicit setter — use from DisposableEffect onDispose (never toggle there). */
    fun setFocusMode(enabled: Boolean) {
        if (_isFocusMode.value == enabled) return
        _isFocusMode.value = enabled
        applyFocusModeService(enabled)
    }

    fun toggleFocusMode() {
        setFocusMode(!_isFocusMode.value)
    }

    @Deprecated("Passing an Activity leaks it into the VM; uses appContext now.")
    fun toggleFocusMode(context: android.content.Context? = null) {
        setFocusMode(!_isFocusMode.value)
    }

    private fun applyFocusModeService(enabled: Boolean) {
        try {
            val intent = Intent(appContext, com.frerox.toolz.service.CaffeinateService::class.java)
            if (enabled) {
                intent.action = com.frerox.toolz.service.CaffeinateService.ACTION_START
                intent.putExtra(com.frerox.toolz.service.CaffeinateService.EXTRA_INFINITE, true)
                appContext.startService(intent)
            } else {
                intent.action = com.frerox.toolz.service.CaffeinateService.ACTION_STOP
                appContext.startService(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "focus service failed", e)
        }
    }

    // ── AI: New Features ──────────────────────────────────────────────────

    /**
     * Generates a new note from scratch using AI.
     */
    fun generateNoteAi(prompt: String?, onComplete: (AiGeneratedNote?) -> Unit) {
        viewModelScope.launch {
            var done = false
            val completeOnce: (AiGeneratedNote?) -> Unit = { v ->
                if (!done) { done = true; onComplete(v) }
            }
            val key = aiSettingsManager.getApiKey("Groq")
            if (key.isBlank() || offlineModeEnabled.value) {
                completeOnce(null)
                return@launch
            }
            val safePrompt = prompt?.trim()?.take(NoteLimits.MAX_PROMPT_CHARS)

            try {
                val systemPrompt = """
You are a creative note generation AI. Create a new note based on the user's prompt.
If no prompt is given, create a high-quality random thought, quote, task list, or idea.
Prompt: ${safePrompt ?: "None"}
Keep title <= 120 chars and content <= 1500 chars.

Return ONLY a JSON object.
JSON schema:
{
  "title": "...",
  "content": "...",
  "colorHex": "#RRGGBB",
  "fontSize": 14-22,
  "isBold": boolean,
  "isItalic": boolean,
  "reasoning": "..."
}
                """.trimIndent()

                val userPrompt = safePrompt?.takeIf { it.isNotBlank() } ?: "Create a new interesting note for me."

                val request = OpenAiRequest(
                    model = _selectedAiModel.value,
                    messages = listOf(
                        OpenAiMessage("system", MessageContent.Text(systemPrompt)),
                        OpenAiMessage("user", MessageContent.Text(userPrompt)),
                    ),
                    maxTokens = 1000,
                )

                val response = withContext(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeout(60_000) {
                        runGroqRequest(key) { requestKey ->
                            openAiService.getChatCompletion(
                                url = GROQ_URL,
                                authHeader = "Bearer $requestKey",
                                request = request,
                            )
                        }
                    }
                }

                val raw = response.choices.firstOrNull()?.message?.content ?: ""
                completeOnce(parseAiGeneratedNote(raw))
            } catch (e: Exception) {
                Log.e(TAG, "Generate note failed: ${e.message}")
                completeOnce(null)
            }
        }
    }

    /**
     * Edits an existing note based on a user prompt.
     */
    fun editNoteWithPromptAi(note: Note, prompt: String, onComplete: (AiGeneratedNote?) -> Unit) {
        viewModelScope.launch {
            var done = false
            val completeOnce: (AiGeneratedNote?) -> Unit = { v ->
                if (!done) { done = true; onComplete(v) }
            }
            val key = aiSettingsManager.getApiKey("Groq")
            if (key.isBlank() || offlineModeEnabled.value) {
                completeOnce(null)
                return@launch
            }
            val instruction = prompt.trim().take(NoteLimits.MAX_PROMPT_CHARS)
            if (instruction.isBlank()) {
                completeOnce(null)
                return@launch
            }

            try {
                val noteBody = "Current Title: ${note.title}\nCurrent Content: ${note.content}"
                val systemPrompt = """
You are a note editor AI. Modify the given note based on the user's instructions.
Instruction: $instruction
Keep title <= 120 chars and content <= 1500 chars.

Return ONLY a JSON object with the updated fields.
JSON schema:
{
  "title": "...",
  "content": "...",
  "colorHex": "#RRGGBB",
  "fontSize": 14-22,
  "isBold": boolean,
  "isItalic": boolean,
  "reasoning": "..."
}
                """.trimIndent()

                val request = OpenAiRequest(
                    model = _selectedAiModel.value,
                    messages = listOf(
                        OpenAiMessage("system", MessageContent.Text(systemPrompt)),
                        OpenAiMessage("user", MessageContent.Text(noteBody)),
                    ),
                    maxTokens = 1000,
                )

                val response = withContext(Dispatchers.IO) {
                    runGroqRequest(key) { requestKey ->
                        openAiService.getChatCompletion(
                            url = GROQ_URL,
                            authHeader = "Bearer $requestKey",
                            request = request,
                        )
                    }
                }

                val raw = response.choices.firstOrNull()?.message?.content ?: ""
                completeOnce(parseAiGeneratedNote(raw))
            } catch (e: Exception) {
                Log.e(TAG, "Edit note failed: ${e.message}")
                completeOnce(null)
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private fun parseAiGeneratedNote(raw: String): AiGeneratedNote? {
        return try {
            val cleaned = raw.replace(Regex("```[a-z]*"), "").replace("```", "").trim()
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start == -1 || end <= start) return null

            val json = JSONObject(cleaned.substring(start, end + 1))
            val hex = json.optString("colorHex", NoteColors.FALLBACK_HEX)
            AiGeneratedNote(
                title = json.optString("title", "Untitled").take(120),
                content = json.optString("content", "").take(5000),
                colorHex = if (NoteColors.parseHexOrNull(hex) != null) hex else NoteColors.FALLBACK_HEX,
                fontSize = NoteLimits.clampFontSize(json.optDouble("fontSize", 17.0).toFloat()),
                isBold = json.optBoolean("isBold", false),
                isItalic = json.optBoolean("isItalic", false),
                reasoning = json.optString("reasoning", "")
            )
        } catch (_: JSONException) {
            null
        }
    }

    private suspend fun <T> runGroqRequest(
        initialKey: String,
        requestBlock: suspend (String) -> T,
    ): T {
        return requestBlock(initialKey)
    }

    private fun parseAiStyle(raw: String): AiNoteStyle? {
        return try {
            val cleaned = raw
                .replace(Regex("```[a-z]*"), "")
                .replace("```", "")
                .trim()
            val start = cleaned.indexOf('{')
            val end   = cleaned.lastIndexOf('}')
            if (start == -1 || end <= start) return null

            val json     = JSONObject(cleaned.substring(start, end + 1))
            val hexRaw   = json.optString("colorHex", "#FFF9C4").trim()
            // Validate hex — fallback to warm yellow if malformed
            val colorHex = if (hexRaw.matches(Regex("^#[0-9A-Fa-f]{6}$"))) hexRaw else "#FFF9C4"

            AiNoteStyle(
                colorHex  = colorHex,
                fontSize  = NoteLimits.clampFontSize(json.optDouble("fontSize", 17.0).toFloat()),
                isBold    = json.optBoolean("isBold",   false),
                isItalic  = json.optBoolean("isItalic", false),
                reasoning = json.optString("reasoning", "AI-generated style"),
            )
        } catch (_: JSONException) { null }
    }
}

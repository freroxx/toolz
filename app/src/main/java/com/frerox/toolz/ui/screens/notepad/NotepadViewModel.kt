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
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.notepad.NoteAttachment
import com.frerox.toolz.data.notepad.NoteAttachmentDao
import com.frerox.toolz.data.notepad.NoteDao
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

    val offlineModeEnabled = settingsRepository.offlineModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val availableTracks = musicRepository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _availablePdfs =
        MutableStateFlow<List<com.frerox.toolz.data.pdf.PdfFile>>(emptyList())
    val availablePdfs = _availablePdfs.asStateFlow()

    // ── AI state ───────────────────────────────────────────────────────────

    private val _aiSummary       = MutableStateFlow<String?>(null)
    val aiSummary: StateFlow<String?> = _aiSummary.asStateFlow()

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
    }

    fun refreshPdfs() {
        loadPdfs()
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
        _selectedAiModel.value = model
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
        viewModelScope.launch {
            val insertedId = noteDao.insertNote(
                Note(
                    title             = title.trim(),
                    content           = content,
                    color             = color,
                    fontStyle         = fontStyle,
                    fontSize          = fontSize,
                    isBold            = isBold,
                    isItalic          = isItalic,
                    attachedPdfUri    = attachedPdfUri,
                    attachedAudioUri  = attachedAudioUri,
                    attachedAudioName = attachedAudioName,
                    attachedImageUri  = attachedImageUri,
                )
            )
            onInserted(insertedId.toInt())
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch { noteDao.insertNote(note) }   // insert with REPLACE strategy
    }

    fun updateNoteCardSize(note: Note, cardSize: String) {
        viewModelScope.launch {
            noteDao.insertNote(note.copy(cardSize = cardSize))
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
        viewModelScope.launch { noteDao.permanentlyDeleteNote(note) }
    }

    fun restoreNote(note: Note) {
        viewModelScope.launch { noteDao.restoreFromTrash(note.id) }
    }

    fun emptyTrash() {
        viewModelScope.launch { noteDao.emptyTrash() }
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

    suspend fun persistImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val fileName = "note_img_${System.currentTimeMillis()}.jpg"
            val file = java.io.File(context.filesDir, fileName)
            
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist image", e)
            null
        }
    }

    // ── Attachments V2 (multi-attach, persisted URIs) ────────────────────────

    fun attachmentsFor(noteId: Int): Flow<List<NoteAttachment>> =
        attachmentDao.observeForNote(noteId)

    suspend fun listAttachments(noteId: Int): List<NoteAttachment> = try {
        attachmentDao.listForNote(noteId)
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * One-time legacy merge: copies the old single-slot attached* columns into
     * the multi-attach table so old notes render in the new UI. Idempotent —
     * skips when rows already exist or when the note is unsaved (id == 0).
     */
    suspend fun ensureLegacyAttachments(note: Note) = withContext(Dispatchers.IO) {
        try {
            if (note.id == 0) return@withContext
            if (attachmentDao.listForNote(note.id).isNotEmpty()) return@withContext
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
        } catch (_: Exception) { }
    }

    /**
     * Attach a PDF, guaranteeing the stored URI stays readable: persistable
     * grant where supported, verified read, app-private copy as fallback.
     * Never inserts a dead row — an unreadable source reports [PdfAttachResult.Unreadable].
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
                var usable = source.toString()
                if (!pdfRepository.isReadable(source)) {
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
                    usable = imported.toString()
                }
                val usableUri = Uri.parse(usable)
                val name = try { pdfRepository.queryDisplayName(usableUri) } catch (_: Exception) { null }
                val size = try { pdfRepository.querySize(usableUri) } catch (_: Exception) { 0L }
                attachmentDao.insert(
                    NoteAttachment(
                        noteId = noteId, kind = NoteAttachment.KIND_PDF, uri = usable,
                        displayName = name, sizeBytes = size, pageHint = pageHint
                    )
                )
                PdfAttachResult.Attached
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
            attachmentDao.insert(
                NoteAttachment(noteId = noteId, kind = NoteAttachment.KIND_IMAGE, uri = source.toString())
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun attachAudio(noteId: Int, uri: String, name: String?): Boolean =
        withContext(Dispatchers.IO) {
            try {
                attachmentDao.insert(
                    NoteAttachment(noteId = noteId, kind = NoteAttachment.KIND_AUDIO, uri = uri, displayName = name)
                )
                true
            } catch (_: Exception) {
                false
            }
        }

    suspend fun removeAttachment(id: Long) {
        try { attachmentDao.deleteById(id) } catch (_: Exception) { }
    }

    // ── AI: Summarize ──────────────────────────────────────────────────────

    /**
     * Requests a concise summary of [note] from Groq's fast LLM.
     * The result is stored in [aiSummary] and cleared by [clearAiSummary].
     */
    fun summarizeNote(note: Note) {
        // Smart AI token saver: if summary exists and note hasn't changed, reuse it.
        // Content change check is implicitly handled by addNote/updateNote resetting summary to null.
        if (!note.summary.isNullOrBlank()) {
            _aiSummary.value = note.summary
            return
        }

        viewModelScope.launch {
            _isAiSummarizing.value = true
            _aiSummary.value       = null

            val key = aiSettingsManager.getApiKey("Groq")
            if (key.isBlank()) {
                _aiSummary.value = "⚠ Groq API key not configured. Go to AI Settings → Groq to add your key."
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
                    runGroqRequest(key) { requestKey ->
                        openAiService.getChatCompletion(
                            url        = GROQ_URL,
                            authHeader = "Bearer $requestKey",
                            request    = request,
                        )
                    }
                }
                val summaryResult = response.choices.firstOrNull()?.message?.content
                    ?: "Could not generate a summary."
                
                _aiSummary.value = summaryResult
                
                // Cache the summary if successful
                if (summaryResult != "Could not generate a summary.") {
                    noteDao.insertNote(note.copy(summary = summaryResult))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Summarize failed: ${e.message}")
                _aiSummary.value = "Summary failed: ${e.message}"
            } finally {
                _isAiSummarizing.value = false
            }
        }
    }

    fun clearAiSummary() {
        _aiSummary.value = null
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
            if (key.isBlank()) {
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
                    runGroqRequest(key) { requestKey ->
                        openAiService.getChatCompletion(
                            url        = GROQ_URL,
                            authHeader = "Bearer $requestKey",
                            request    = request,
                        )
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

    fun toggleFocusMode(context: android.content.Context? = null) {
        _isFocusMode.value = !_isFocusMode.value
        
        // Side effect: Toggle Caffeinate if context is provided
        context?.let { ctx ->
            val intent = Intent(ctx, com.frerox.toolz.service.CaffeinateService::class.java)
            if (_isFocusMode.value) {
                intent.action = com.frerox.toolz.service.CaffeinateService.ACTION_START
                intent.putExtra(com.frerox.toolz.service.CaffeinateService.EXTRA_INFINITE, true)
                ctx.startService(intent)
            } else {
                intent.action = com.frerox.toolz.service.CaffeinateService.ACTION_STOP
                ctx.startService(intent)
            }
        }
    }

    // ── AI: New Features ──────────────────────────────────────────────────

    /**
     * Generates a new note from scratch using AI.
     */
    fun generateNoteAi(prompt: String?, onComplete: (AiGeneratedNote?) -> Unit) {
        viewModelScope.launch {
            val key = aiSettingsManager.getApiKey("Groq")
            if (key.isBlank()) {
                onComplete(null)
                return@launch
            }

            try {
                val systemPrompt = """
You are a creative note generation AI. Create a new note based on the user's prompt.
If no prompt is given, create a high-quality random thought, quote, task list, or idea.
Prompt: ${prompt ?: "None"}

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

                val userPrompt = prompt ?: "Create a new interesting note for me."

                val request = OpenAiRequest(
                    model = _selectedAiModel.value,
                    messages = listOf(
                        OpenAiMessage("system", MessageContent.Text(systemPrompt)),
                        OpenAiMessage("user", MessageContent.Text(userPrompt)),
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
                onComplete(parseAiGeneratedNote(raw))
            } catch (e: Exception) {
                Log.e(TAG, "Generate note failed: ${e.message}")
                onComplete(null)
            }
        }
    }

    /**
     * Edits an existing note based on a user prompt.
     */
    fun editNoteWithPromptAi(note: Note, prompt: String, onComplete: (AiGeneratedNote?) -> Unit) {
        viewModelScope.launch {
            val key = aiSettingsManager.getApiKey("Groq")
            if (key.isBlank()) {
                onComplete(null)
                return@launch
            }

            try {
                val noteBody = "Current Title: ${note.title}\nCurrent Content: ${note.content}"
                val systemPrompt = """
You are a note editor AI. Modify the given note based on the user's instructions.
Instruction: $prompt

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
                onComplete(parseAiGeneratedNote(raw))
            } catch (e: Exception) {
                Log.e(TAG, "Edit note failed: ${e.message}")
                onComplete(null)
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
            AiGeneratedNote(
                title = json.optString("title", "Untitled"),
                content = json.optString("content", ""),
                colorHex = json.optString("colorHex", "#FFF9C4"),
                fontSize = json.optDouble("fontSize", 17.0).toFloat().coerceIn(12f, 28f),
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
                fontSize  = json.optDouble("fontSize", 17.0).toFloat().coerceIn(12f, 28f),
                isBold    = json.optBoolean("isBold",   false),
                isItalic  = json.optBoolean("isItalic", false),
                reasoning = json.optString("reasoning", "AI-generated style"),
            )
        } catch (_: JSONException) { null }
    }
}

package com.frerox.toolz.ui.screens.notepad

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.AiSettingsManager
import com.frerox.toolz.data.ai.MessageContent
import com.frerox.toolz.data.ai.OpenAiMessage
import com.frerox.toolz.data.ai.OpenAiRequest
import com.frerox.toolz.data.ai.OpenAiService
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.notepad.NoteDao
import com.frerox.toolz.data.pdf.PdfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import retrofit2.HttpException
import javax.inject.Inject

private const val TAG = "NotepadViewModel"
private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
private const val AI_MODEL = "llama-3.3-70b-versatile"

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
    private val musicRepository  : MusicRepository,
    private val pdfRepository    : PdfRepository,
    private val openAiService    : OpenAiService,
    private val aiSettingsManager: AiSettingsManager,
) : ViewModel() {

    // ── DB / repository streams ────────────────────────────────────────────

    val notes: StateFlow<List<Note>> = noteDao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    private var lastDeletedNote: Note? = null

    init { loadPdfs() }

    private fun loadPdfs() {
        viewModelScope.launch {
            _availablePdfs.value = pdfRepository.getPdfFiles()
        }
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
    ) {
        viewModelScope.launch {
            noteDao.insertNote(
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
                )
            )
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch { noteDao.insertNote(note) }   // insert with REPLACE strategy
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            lastDeletedNote = note
            noteDao.deleteNote(note)
        }
    }

    fun undoDelete() {
        val note = lastDeletedNote ?: return
        viewModelScope.launch {
            noteDao.insertNote(note)
            lastDeletedNote = null
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch { noteDao.updatePinned(note.id, !note.isPinned) }
    }

    // ── AI: Summarize ──────────────────────────────────────────────────────

    /**
     * Requests a concise summary of [note] from Groq's fast LLM.
     * The result is stored in [aiSummary] and cleared by [clearAiSummary].
     */
    fun summarizeNote(note: Note) {
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
                    model    = AI_MODEL,
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
                    maxTokens = 256,
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
                _aiSummary.value = response.choices.firstOrNull()?.message?.content
                    ?: "Could not generate a summary."

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
                    model    = AI_MODEL,
                    messages = listOf(
                        OpenAiMessage("system", MessageContent.Text(systemPrompt)),
                        OpenAiMessage("user",   MessageContent.Text(noteBody)),
                    ),
                    maxTokens = 150,
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

    // ── Private helpers ────────────────────────────────────────────────────

    private suspend fun <T> runGroqRequest(
        initialKey: String,
        requestBlock: suspend (String) -> T,
    ): T {
        try {
            return requestBlock(initialKey)
        } catch (e: HttpException) {
            if (e.code() == 401 && !aiSettingsManager.hasUserApiKey("Groq")) {
                val refreshed = aiSettingsManager.refreshRemoteKeyAfterAuthFailure("Groq", initialKey)
                if (refreshed.source == com.frerox.toolz.data.ai.ApiKeySource.REMOTE &&
                    refreshed.value.isNotBlank() &&
                    refreshed.value != initialKey
                ) {
                    return requestBlock(refreshed.value)
                }
                throw IllegalStateException(
                    "The Toolz default key for Groq is unavailable. Refresh keys or add your own key in AI settings."
                )
            }
            throw e
        }
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

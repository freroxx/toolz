package com.frerox.toolz.ui.screens.ai

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

private const val TAG          = "AiAssistantVM"
private const val GROQ_URL     = "https://api.groq.com/openai/v1/chat/completions"
private const val GROQ_MODEL   = "llama-3.3-70b-versatile"

// ─────────────────────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────────────────────

data class AiAssistantUiState(
    val chats             : List<AiChat>     = emptyList(),
    val currentChatId     : Int?             = null,
    val messages          : List<AiMessage>  = emptyList(),
    val isLoading         : Boolean          = false,
    val error             : String?          = null,
    val quotaExceeded     : Boolean          = false,
    val suggestedProvider : String?          = null,
    val isHistoryOpen     : Boolean          = false,
    val isConfigured      : Boolean          = false,
    val savedConfigs      : List<AiConfig>   = emptyList(),
    val pendingConfig     : AiConfig?        = null,
    val selectedImage     : Bitmap?          = null,
    val streamingText     : String           = "",
    val isSyncingKeys     : Boolean          = false,
    val keysUnavailable   : Boolean          = false,
    // ── New fields ───────────────────────────────────────────
    val chatSummary       : String?          = null,
    val isSummarizing     : Boolean          = false,
    val isGeneratingTitle : Boolean          = false,
)

data class AiSettingsUiState(
    val provider             : String    = "Groq",
    val apiKey               : String    = "",
    val selectedModel        : String    = "llama-3.3-70b-versatile",
    val isTesting            : Boolean   = false,
    val testResult           : String?   = null,
    val isKeyValid           : Boolean   = true,
    val isRemoteKeyAvailable : Boolean   = false,
    val editingConfig        : AiConfig? = null,
    val selectedIcon         : String    = "AUTO",
    val customIconUri        : String?   = null,
)

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val aiDao          : AiDao,
    private val chatRepository : ChatRepository,
    private val settingsManager: AiSettingsManager,
    private val openAiService  : OpenAiService,
) : ViewModel() {

    private val _uiState         = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private val _settingsUiState = MutableStateFlow(AiSettingsUiState())
    val settingsUiState: StateFlow<AiSettingsUiState> = _settingsUiState.asStateFlow()

    private var messagesJob        : Job? = null
    private var activeInferenceJob : Job? = null

    init {
        loadSettings()
        loadConfigs()
        syncDefaultKeysInBackground()
        viewModelScope.launch {
            aiDao.getAllChats().collect { chats -> _uiState.update { it.copy(chats = chats) } }
        }
        createNewChat()
    }

    private fun loadSettings() {
        val provider = settingsManager.getAiProvider()
        _settingsUiState.update {
            it.copy(
                provider             = provider,
                apiKey               = settingsManager.getRawApiKey(provider),
                selectedModel        = settingsManager.getSelectedModel(provider),
                isRemoteKeyAvailable = settingsManager.getRemoteKey(provider).isNotBlank(),
            )
        }
        _uiState.update { it.copy(isConfigured = settingsManager.isConfigured()) }
    }

    private fun loadConfigs() { _uiState.update { it.copy(savedConfigs = settingsManager.getSavedConfigs()) } }

    fun updateProvider(provider: String) {
        val availableModels = AiSettingsHelper.getModels(provider)
        _settingsUiState.update {
            it.copy(
                provider             = provider,
                apiKey               = settingsManager.getRawApiKey(provider),
                selectedModel        = it.selectedModel.takeIf { model -> model in availableModels }
                    ?: AiSettingsHelper.getRecommendedModel(provider),
                isRemoteKeyAvailable = settingsManager.getRemoteKey(provider).isNotBlank(),
            )
        }
        validateKey()
        if (settingsManager.getRawApiKey(provider).isBlank()) syncDefaultKeysInBackground()
    }

    fun updateApiKey(key: String) {
        _settingsUiState.update { it.copy(apiKey = AiSettingsHelper.normalizeApiKeyInput(key)) }
        validateKey()
    }
    fun updateModel(model: String) { _settingsUiState.update { it.copy(selectedModel = model) } }
    fun updateIcon(icon: String)   { _settingsUiState.update { it.copy(selectedIcon = icon, customIconUri = null) } }
    fun updateCustomIcon(uri: Uri?) { _settingsUiState.update { it.copy(customIconUri = uri?.toString(), selectedIcon = "CUSTOM") } }

    private fun validateKey() {
        val s = _settingsUiState.value
        _settingsUiState.update { it.copy(isKeyValid = s.apiKey.isEmpty() || AiSettingsHelper.validateApiKey(s.provider, s.apiKey)) }
    }

    fun onSettingsSaveRequest() {
        val s = _settingsUiState.value
        val currentProvider = settingsManager.getAiProvider()
        val currentModel = settingsManager.getSelectedModel(currentProvider)
        val currentApiKey = settingsManager.getRawApiKey(currentProvider)

        val changed = s.provider != currentProvider || s.selectedModel != currentModel || s.apiKey != currentApiKey

        if (changed && _uiState.value.messages.isNotEmpty()) {
            _uiState.update { it.copy(pendingConfig = AiConfig("Current Settings", s.provider, s.selectedModel, s.apiKey, s.selectedIcon, s.customIconUri)) }
        } else {
            saveSettings()
        }
    }

    fun saveSettings() {
        with(_settingsUiState.value) {
            settingsManager.setAiProvider(provider); settingsManager.setApiKey(AiSettingsHelper.normalizeApiKeyInput(apiKey), provider)
            settingsManager.setSelectedModel(selectedModel, provider)
            if (apiKey.isBlank()) syncDefaultKeysInBackground()
        }
        _uiState.update { it.copy(isConfigured = settingsManager.isConfigured()) }
        loadSettings()
    }

    fun saveConfig(name: String) {
        with(_settingsUiState.value) {
            settingsManager.saveConfig(AiConfig(name = name, provider = provider, model = selectedModel, apiKey = apiKey, iconRes = selectedIcon, customIconUri = customIconUri), editingConfig?.name)
        }
        _settingsUiState.update { it.copy(editingConfig = null) }
        loadConfigs()
    }

    fun deleteConfig(config: AiConfig) { settingsManager.deleteConfig(config.name); loadConfigs() }

    fun editConfig(config: AiConfig) {
        _settingsUiState.update {
            it.copy(
                provider = config.provider, apiKey = config.apiKey,
                selectedModel = config.model, editingConfig = config,
                selectedIcon = config.iconRes,
                customIconUri = config.customIconUri,
                isRemoteKeyAvailable = settingsManager.getRemoteKey(config.provider).isNotBlank(),
            )
        }
    }

    fun moveConfig(from: Int, to: Int) {
        val configs = _uiState.value.savedConfigs.toMutableList()
        if (from in configs.indices && to in configs.indices) {
            configs.add(to, configs.removeAt(from))
            settingsManager.saveAllConfigs(configs); loadConfigs()
        }
    }

    // ── Config switching ──────────────────────────────────────────────────

    fun onConfigRequest(config: AiConfig) {
        val s = _settingsUiState.value
        if (s.provider == config.provider && s.selectedModel == config.model && s.apiKey == config.apiKey) return
        if (_uiState.value.messages.isEmpty()) applyConfig(config)
        else _uiState.update { it.copy(pendingConfig = config) }
    }

    fun confirmConfigSwitch() { val c = _uiState.value.pendingConfig ?: return; applyConfig(c); createNewChat(); _uiState.update { it.copy(pendingConfig = null) } }
    fun cancelConfigSwitch()  { _uiState.update { it.copy(pendingConfig = null) } }
    private fun applyConfig(config: AiConfig) { settingsManager.applyConfig(config); loadSettings() }

    // ── Test connection ────────────────────────────────────────────────────

    fun testConnection() {
        val s = _settingsUiState.value
        if (s.apiKey.isNotBlank() && !AiSettingsHelper.validateApiKey(s.provider, s.apiKey)) {
            _settingsUiState.update { it.copy(testResult = "Invalid key format for ${s.provider}") }; return
        }
        viewModelScope.launch {
            _settingsUiState.update { it.copy(isTesting = true, testResult = null) }
            val keyToTest = s.apiKey.ifBlank { settingsManager.resolveApiKeyWithRemoteSync(s.provider).value }
            if (keyToTest.isBlank()) {
                _settingsUiState.update { it.copy(isTesting = false, isRemoteKeyAvailable = settingsManager.getRemoteKey(s.provider).isNotBlank(), testResult = "No API key for ${s.provider}. Tap Refresh or add your own.") }
                return@launch
            }
            chatRepository.testConnection(AiConfig("__test__", s.provider, s.selectedModel, keyToTest, "AUTO")).collect { r ->
                r.onSuccess { reply -> _settingsUiState.update { it.copy(isTesting = false, testResult = "✓ Connected — $reply") } }
                    .onFailure { e    -> _settingsUiState.update { it.copy(isTesting = false, testResult = "✗ ${e.message}") } }
            }
        }
    }

    fun refreshRemoteKeys() {
        viewModelScope.launch {
            _settingsUiState.update { it.copy(isTesting = true, testResult = "Syncing keys…") }
            val ok = settingsManager.syncRemoteKeys(force = true)
            _settingsUiState.update {
                it.copy(isTesting = false, testResult = if (ok) "✓ Keys refreshed" else "✗ Sync failed — check network",
                    isRemoteKeyAvailable = settingsManager.getRemoteKey(it.provider).isNotBlank())
            }
            if (ok) _uiState.update { it.copy(keysUnavailable = false, error = null) }
        }
    }

    fun retrySyncKeys() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingKeys = true, error = null) }
            val ok = settingsManager.syncRemoteKeys(force = true)
            _settingsUiState.update { it.copy(isRemoteKeyAvailable = settingsManager.getRemoteKey(it.provider).isNotBlank()) }
            _uiState.update {
                it.copy(isSyncingKeys = false, keysUnavailable = !ok && !settingsManager.hasRemoteKeys,
                    error = if (!ok && !settingsManager.hasRemoteKeys) "Could not load API keys. Check network." else null)
            }
        }
    }

    // ── Chat lifecycle ────────────────────────────────────────────────────

    fun loadChat(chatId: Int) {
        messagesJob?.cancel()
        _uiState.update { it.copy(currentChatId = chatId, isHistoryOpen = false, error = null, chatSummary = null) }
        messagesJob = viewModelScope.launch {
            aiDao.getMessagesForChat(chatId).collect { messages -> _uiState.update { it.copy(messages = messages) } }
        }
    }

    fun createNewChat() {
        cancelRequest()
        messagesJob?.cancel()
        _uiState.update { it.copy(currentChatId = null, messages = emptyList(), error = null, quotaExceeded = false, streamingText = "", chatSummary = null) }
    }

    fun onImageSelected(bitmap: Bitmap?) { _uiState.update { it.copy(selectedImage = bitmap) } }

    fun cancelRequest() {
        activeInferenceJob?.cancel(); activeInferenceJob = null
        _uiState.update { it.copy(isLoading = false, streamingText = "") }
    }

    // ── Send message ───────────────────────────────────────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank() && _uiState.value.selectedImage == null) return
        if (_uiState.value.isLoading) return

        activeInferenceJob = viewModelScope.launch {
            var currentId    = _uiState.value.currentChatId
            val currentImage = _uiState.value.selectedImage
            val history      = _uiState.value.messages.toList()
            val provider     = settingsManager.getAiProvider()
            val selectedModel = settingsManager.getSelectedModel(provider)

            if (currentImage != null && !AiSettingsHelper.supportsVision(provider, selectedModel)) {
                _uiState.update {
                    it.copy(
                        error = "$provider model '$selectedModel' does not support image input. Pick a vision-capable model or remove the image."
                    )
                }
                return@launch
            }

            val resolved = settingsManager.resolveApiKeyWithRemoteSync(provider)
            if (resolved.source == ApiKeySource.NONE) {
                _uiState.update { it.copy(error = "No API key. Tap Retry Sync or add a key in Settings.", keysUnavailable = true) }
                return@launch
            }

            if (currentId == null) {
                val tempTitle = if (text.isNotBlank()) text.take(20).trimEnd() + "…" else "New conversation"
                currentId = aiDao.insertChat(AiChat(title = tempTitle)).toInt()
                _uiState.update { it.copy(currentChatId = currentId) }
                loadChat(currentId)
            }

            aiDao.insertMessage(AiMessage(chatId = currentId, text = text, isUser = true))

            _uiState.update { it.copy(isLoading = true, error = null, quotaExceeded = false, selectedImage = null, streamingText = "", keysUnavailable = false) }

            val accumulated = StringBuilder()

            chatRepository.getChatResponse(text, history, currentImage).collect { r ->
                r.onSuccess { chunk ->
                    accumulated.append(chunk)
                    _uiState.update { it.copy(streamingText = accumulated.toString()) }
                }.onFailure { e ->
                    val msg     = e.message ?: "Unknown error"
                    val isQuota = msg.contains("quota", true) || msg.contains("limit", true) || msg.contains("429")
                    val isAuth  = msg.contains("401") || msg.contains("Unauthorized") || msg.contains("Invalid API key")

                    if (isAuth) {
                        settingsManager.invalidateRemoteKey(provider)
                        _uiState.update { it.copy(isLoading = false, streamingText = "", error = "Auth failed. Tap Retry Sync to refresh keys.", keysUnavailable = true) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, streamingText = "", error = if (isQuota) "Quota exceeded for $provider." else "Error: $msg", quotaExceeded = isQuota, suggestedProvider = if (isQuota) nextProvider(provider) else null) }
                    }
                }
            }

            if (accumulated.isNotEmpty()) {
                aiDao.insertMessage(AiMessage(chatId = currentId, text = accumulated.toString(), isUser = false))

                if (history.isEmpty() && text.isNotBlank()) {
                    generateChatTitle(currentId, text, accumulated.toString())
                }
            }

            _uiState.update { it.copy(isLoading = false, streamingText = "") }
        }
    }

    // ── AI Title Generation ────────────────────────────────────────────────

    fun generateChatTitle(chatId: Int, userMessage: String, aiReply: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingTitle = true) }
            val title = callGroqForTitle(userMessage, aiReply)
            if (title != null && title.isNotBlank()) {
                aiDao.updateChat(AiChat(id = chatId, title = title))
                Log.d(TAG, "AI title: $title")
            }
            _uiState.update { it.copy(isGeneratingTitle = false) }
        }
    }

    fun refreshChatTitle() {
        val chatId   = _uiState.value.currentChatId ?: return
        val messages = _uiState.value.messages
        if (messages.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingTitle = true) }
            val context = messages.take(6).joinToString("\n") {
                if (it.isUser) "User: ${it.text.take(200)}" else "AI: ${it.text.take(200)}"
            }
            val title = callGroqForTitle(context, "")
            if (title != null && title.isNotBlank()) {
                aiDao.updateChat(AiChat(id = chatId, title = title))
            }
            _uiState.update { it.copy(isGeneratingTitle = false) }
        }
    }

    private suspend fun callGroqForTitle(userMsg: String, aiReply: String): String? {
        val groqKey = settingsManager.getApiKey("Groq").ifBlank { settingsManager.getApiKey() }
        if (groqKey.isBlank()) return null
        return try {
            val prompt = buildString {
                append("Generate a concise, descriptive chat title (max 5 words, no quotes, no punctuation at end).\n")
                append("User: ${userMsg.take(300)}\n")
                if (aiReply.isNotBlank()) append("AI: ${aiReply.take(300)}")
            }
            val resp = withContext(Dispatchers.IO) {
                openAiService.getChatCompletion(
                    url        = GROQ_URL,
                    authHeader = "Bearer $groqKey",
                    request    = OpenAiRequest(
                        model    = GROQ_MODEL,
                        messages = listOf(
                            OpenAiMessage("system", MessageContent.Text("You generate short chat titles. Reply with ONLY the title — no explanation, no quotes.")),
                            OpenAiMessage("user",   MessageContent.Text(prompt)),
                        ),
                        maxTokens = 20,
                    )
                )
            }
            resp.choices.firstOrNull()?.message?.content?.trim()
                ?.removePrefix("\"")?.removeSuffix("\"")
                ?.take(60)
        } catch (e: Exception) {
            Log.e(TAG, "Title gen failed: ${e.message}"); null
        }
    }

    // ── Chat Summarization ─────────────────────────────────────────────────

    fun summarizeChat() {
        val messages = _uiState.value.messages
        if (messages.isEmpty()) return
        _uiState.update { it.copy(isSummarizing = true, chatSummary = null) }

        viewModelScope.launch {
            val groqKey = settingsManager.getApiKey("Groq").ifBlank { settingsManager.getApiKey() }
            if (groqKey.isBlank()) {
                _uiState.update { it.copy(isSummarizing = false, chatSummary = "⚠ No API key available. Add a Groq key in Settings.") }
                return@launch
            }

            val chatText = messages.takeLast(40).joinToString("\n") {
                if (it.isUser) "User: ${it.text.take(400)}" else "AI: ${it.text.take(400)}"
            }

            val systemPrompt = """
Summarize this AI conversation as 3-6 concise bullet points.
- Start each bullet with "•"
- Focus on the main topics, decisions, and outcomes
- Keep total under 150 words
- No preamble, just bullet points
            """.trimIndent()

            try {
                val resp = withContext(Dispatchers.IO) {
                    openAiService.getChatCompletion(
                        url        = GROQ_URL,
                        authHeader = "Bearer $groqKey",
                        request    = OpenAiRequest(
                            model    = GROQ_MODEL,
                            messages = listOf(
                                OpenAiMessage("system", MessageContent.Text(systemPrompt)),
                                OpenAiMessage("user",   MessageContent.Text(chatText)),
                            ),
                            maxTokens = 256,
                        )
                    )
                }
                val summary = resp.choices.firstOrNull()?.message?.content?.trim() ?: "Could not generate summary."
                _uiState.update { it.copy(isSummarizing = false, chatSummary = summary) }
            } catch (e: Exception) {
                Log.e(TAG, "Summarize failed: ${e.message}")
                _uiState.update { it.copy(isSummarizing = false, chatSummary = "Summary failed: ${e.message}") }
            }
        }
    }

    fun clearChatSummary() { _uiState.update { it.copy(chatSummary = null) } }

    // ── Provider switch ────────────────────────────────────────────────────

    fun switchProvider(provider: String) {
        settingsManager.setAiProvider(provider)
        settingsManager.setSelectedModel(AiSettingsHelper.getRecommendedModel(provider))
        loadSettings()
        if (settingsManager.getRawApiKey(provider).isBlank()) syncDefaultKeysInBackground()
        _uiState.update { it.copy(error = null, quotaExceeded = false, keysUnavailable = false) }
        if (_uiState.value.messages.isNotEmpty()) createNewChat()
    }

    fun toggleHistory() { _uiState.update { it.copy(isHistoryOpen = !it.isHistoryOpen) } }

    fun deleteChat(chat: AiChat) {
        viewModelScope.launch { aiDao.deleteChat(chat); if (_uiState.value.currentChatId == chat.id) createNewChat() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun nextProvider(current: String): String = when (current) {
        "Gemini" -> "Groq"; "Groq" -> "ChatGPT"; "ChatGPT" -> "Claude"
        "Claude" -> "OpenRouter"; "OpenRouter" -> "DeepSeek"; else -> "Gemini"
    }

    private fun syncDefaultKeysInBackground(force: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingKeys = true) }
            val ok = settingsManager.syncRemoteKeys(force = force)
            val p  = _settingsUiState.value.provider
            _settingsUiState.update { it.copy(isRemoteKeyAvailable = settingsManager.getRemoteKey(p).isNotBlank()) }
            _uiState.update { it.copy(isSyncingKeys = false, keysUnavailable = !ok && !settingsManager.hasRemoteKeys) }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Chat grouping
// ─────────────────────────────────────────────────────────────

enum class ChatGroup { TODAY, YESTERDAY, THIS_WEEK, OLDER }

fun AiChat.chatGroup(): ChatGroup {
    val now = Calendar.getInstance(); val c = Calendar.getInstance().apply { timeInMillis = this@chatGroup.createdAt }
    return when {
        now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR) && now.get(Calendar.YEAR) == c.get(Calendar.YEAR) -> ChatGroup.TODAY
        now.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR) == 1 && now.get(Calendar.YEAR) == c.get(Calendar.YEAR) -> ChatGroup.YESTERDAY
        now.get(Calendar.WEEK_OF_YEAR) == c.get(Calendar.WEEK_OF_YEAR) && now.get(Calendar.YEAR) == c.get(Calendar.YEAR) -> ChatGroup.THIS_WEEK
        else -> ChatGroup.OLDER
    }
}

fun ChatGroup.label(): String = when (this) {
    ChatGroup.TODAY -> "Today"; ChatGroup.YESTERDAY -> "Yesterday"
    ChatGroup.THIS_WEEK -> "This Week"; ChatGroup.OLDER -> "Older"
}

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
    val chatSummary       : String?          = null,
    val isSummarizing     : Boolean          = false,
    val isGeneratingTitle : Boolean          = false,
    val suggestedPrompts  : List<String>     = emptyList(),
    val isGeneratingPrompts: Boolean         = false,
    val aiSearchEnabled   : Boolean          = false,
    val aiSearchIconVisible: Boolean         = true,
    val loadingPhaseText  : String           = "",
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
    val dynamicPromptsEnabled: Boolean   = true,
    val promptFormat         : String    = "medium",
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
    private val settingsRepository: com.frerox.toolz.data.settings.SettingsRepository,
) : ViewModel() {

    private val premadePrompts = listOf(
        "Explain quantum computing in simple terms.",
        "Write a poem about a lonely robot on Mars.",
        "Give me a recipe for a healthy 15-minute dinner.",
        "How can I improve my productivity while working from home?",
        "Tell me a joke about programming.",
        "What are some interesting facts about the deep ocean?",
        "Suggest a 3-day itinerary for a trip to Tokyo.",
        "How do I bake a chocolate cake from scratch?",
        "What are the best exercises for core strength?",
        "Explain the concept of 'time dilation' in physics.",
        "Write a short science fiction story about time travel.",
        "Give me some tips for learning a new language quickly.",
        "What are the benefits of meditation?",
        "How do I start a small herb garden at home?",
        "Write a formal email requesting a meeting with a manager.",
        "Summarize the plot of the Great Gatsby.",
        "What are some creative gift ideas for a 10-year-old?",
        "How do I change a flat tire on a car?",
        "Give me a list of must-watch classic movies.",
        "Explain how a blockchain works."
    )

    private val _uiState         = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private val _settingsUiState = MutableStateFlow(AiSettingsUiState())
    val settingsUiState: StateFlow<AiSettingsUiState> = _settingsUiState.asStateFlow()

    private var messagesJob        : Job? = null
    private var activeInferenceJob : Job? = null
    private var loadingPhaseJob    : Job? = null

    init {
        loadSettings()
        loadConfigs()
        observeSearchSettings()
        viewModelScope.launch {
            aiDao.getAllChats().collect { chats -> _uiState.update { it.copy(chats = chats) } }
        }
        createNewChat()
        refreshPrompts()
    }

    private fun loadSettings() {
        val provider = settingsManager.getAiProvider()
        _settingsUiState.update {
            it.copy(
                provider             = provider,
                apiKey               = settingsManager.getRawApiKey(provider),
                selectedModel        = settingsManager.getSelectedModel(provider),
                isRemoteKeyAvailable = settingsManager.isUsingDefaultKey(provider),
                dynamicPromptsEnabled = settingsManager.isDynamicPromptsEnabled(),
                promptFormat         = settingsManager.getPromptFormat()
            )
        }
        _uiState.update { it.copy(isConfigured = settingsManager.isConfigured()) }
    }

    fun toggleDynamicPrompts(enabled: Boolean) {
        settingsManager.setDynamicPromptsEnabled(enabled)
        _settingsUiState.update { it.copy(dynamicPromptsEnabled = enabled) }
        refreshPrompts()
    }

    fun updatePromptFormat(format: String) {
        settingsManager.setPromptFormat(format)
        _settingsUiState.update { it.copy(promptFormat = format) }
        refreshPrompts()
    }

    // ── Suggested Prompts ────────────────────────────────────────────────

    fun refreshPrompts() {
        viewModelScope.launch {
            if (settingsManager.isDynamicPromptsEnabled()) {
                generateDynamicPrompts()
            } else {
                _uiState.update { it.copy(suggestedPrompts = getFilteredPremadePrompts()) }
            }
        }
    }

    private fun getFilteredPremadePrompts(): List<String> {
        val neverShow = try { settingsManager.getNeverShowPrompts() } catch (e: Exception) { null } ?: emptyList()
        val edited = try { settingsManager.getEditedPrompts() } catch (e: Exception) { null } ?: emptyMap()
        val available = (premadePrompts ?: emptyList()).filter { !neverShow.contains(it) }
            .map { edited[it] ?: it }
        return available.shuffled().take(2)
    }

    private suspend fun generateDynamicPrompts() {
        val recentChats = try { aiDao.getRecentChats(5).firstOrNull() } catch (e: Exception) { null } ?: emptyList()
        if (recentChats.isEmpty()) {
            _uiState.update { it.copy(suggestedPrompts = getFilteredPremadePrompts()) }
            return
        }

        _uiState.update { it.copy(isGeneratingPrompts = true) }
        
        val groqKey = settingsManager.getApiKey("Groq").ifBlank { settingsManager.getApiKey() }
        if (groqKey.isBlank()) {
            _uiState.update { it.copy(isGeneratingPrompts = false, suggestedPrompts = getFilteredPremadePrompts()) }
            return
        }

        val topics = recentChats.joinToString(", ") { it.title }
        val format = settingsManager.getPromptFormat()
        val lengthDesc = when(format) {
            "short" -> "very short (3-5 words)"
            "long" -> "long and detailed (15-25 words)"
            else -> "medium length (8-12 words)"
        }
        val prompt = "Based on these previous chat topics: $topics. Generate 2 suggested prompts for a new chat. The prompts should be $lengthDesc. Reply with ONLY the two prompts, one per line, no numbers, no quotes."

        try {
            val resp = withContext(Dispatchers.IO) {
                openAiService.getChatCompletion(
                    url = GROQ_URL,
                    authHeader = "Bearer $groqKey",
                    request = OpenAiRequest(
                        model = GROQ_MODEL,
                        messages = listOf(
                            OpenAiMessage("system", MessageContent.Text("You are a helpful assistant that suggests chat prompts. Reply ONLY with prompts, one per line.")),
                            OpenAiMessage("user", MessageContent.Text(prompt)),
                        ),
                        maxTokens = 60,
                    )
                )
            }
            val generated = resp.choices.firstOrNull()?.message?.content?.lines()
                ?.map { it.trim().removePrefix("- ").removePrefix("1. ").removePrefix("2. ").removePrefix("\"").removeSuffix("\"") }
                ?.filter { it.isNotBlank() }
                ?.take(2) ?: emptyList()

            val neverShow = try { settingsManager.getNeverShowPrompts() } catch (e: Exception) { null } ?: emptyList()
            val finalPrompts = if (generated.size < 2) {
                (generated + getFilteredPremadePrompts()).distinct().take(2)
            } else {
                generated
            }.filter { !neverShow.contains(it) }

            _uiState.update { it.copy(suggestedPrompts = finalPrompts, isGeneratingPrompts = false) }
        } catch (e: Exception) {
            Log.e(TAG, "Dynamic prompts failed: ${e.message}")
            _uiState.update { it.copy(isGeneratingPrompts = false, suggestedPrompts = getFilteredPremadePrompts()) }
        }
    }

    fun neverShowPrompt(prompt: String) {
        settingsManager.addNeverShowPrompt(prompt)
        refreshPrompts()
    }

    fun editPrompt(original: String, edited: String) {
        settingsManager.saveEditedPrompt(original, edited)
        refreshPrompts()
    }

    fun resetPrompts() {
        settingsManager.resetPromptsData()
        refreshPrompts()
    }

    private fun observeSearchSettings() {
        viewModelScope.launch {
            settingsRepository.aiSearchEnabled.collect { enabled ->
                _uiState.update { it.copy(aiSearchEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.aiSearchIconVisible.collect { visible ->
                _uiState.update { it.copy(aiSearchIconVisible = visible) }
            }
        }
    }

    fun toggleAiSearch() {
        viewModelScope.launch {
            settingsRepository.setAiSearchEnabled(!_uiState.value.aiSearchEnabled)
        }
    }
    
    fun setAiSearchIconVisible(visible: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAiSearchIconVisible(visible)
        }
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
                isRemoteKeyAvailable = settingsManager.isUsingDefaultKey(provider),
            )
        }
        validateKey()
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
                isRemoteKeyAvailable = settingsManager.isUsingDefaultKey(config.provider),
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
            val keyToTest = s.apiKey.ifBlank { settingsManager.resolveApiKey(s.provider).value }
            if (keyToTest.isBlank()) {
                _settingsUiState.update { it.copy(isTesting = false, testResult = "No API key for ${s.provider}.") }
                return@launch
            }
            chatRepository.testConnection(AiConfig("__test__", s.provider, s.selectedModel, keyToTest, "AUTO")).collect { r ->
                r.onSuccess { reply -> _settingsUiState.update { it.copy(isTesting = false, testResult = "✓ Connected — $reply") } }
                    .onFailure { e    -> _settingsUiState.update { it.copy(isTesting = false, testResult = "✗ ${e.message}") } }
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
        refreshPrompts()
    }

    fun onImageSelected(bitmap: Bitmap?) { _uiState.update { it.copy(selectedImage = bitmap) } }

    fun cancelRequest() {
        activeInferenceJob?.cancel(); activeInferenceJob = null
        loadingPhaseJob?.cancel(); loadingPhaseJob = null
        _uiState.update { it.copy(isLoading = false, streamingText = "", loadingPhaseText = "") }
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

            val resolved = settingsManager.resolveApiKey(provider)
            if (resolved.source == ApiKeySource.NONE) {
                _uiState.update { it.copy(error = "No API key. Add a key in Settings.", keysUnavailable = true) }
                return@launch
            }

            if (currentId == null) {
                val tempTitle = if (text.isNotBlank()) text.take(20).trimEnd() + "…" else "New conversation"
                currentId = aiDao.insertChat(AiChat(title = tempTitle)).toInt()
                _uiState.update { it.copy(currentChatId = currentId) }
                loadChat(currentId)
            }

            aiDao.insertMessage(AiMessage(chatId = currentId, text = text, isUser = true))

            val webSearchEnabled = _uiState.value.aiSearchEnabled
            _uiState.update { it.copy(isLoading = true, error = null, quotaExceeded = false, selectedImage = null, streamingText = "", keysUnavailable = false, loadingPhaseText = if (webSearchEnabled) "Surfing the web" else "Analyzing") }
            
            loadingPhaseJob?.cancel()
            loadingPhaseJob = viewModelScope.launch {
                val phases = if (webSearchEnabled) listOf("Surfing the web", "Working", "Finalizing response") else listOf("Analyzing", "Working", "Finalizing response")
                var current = 0
                while(true) {
                    kotlinx.coroutines.delay(2000L)
                    current = (current + 1).coerceAtMost(phases.lastIndex)
                    _uiState.update { s -> s.copy(loadingPhaseText = phases[current]) }
                }
            }

            val accumulated = StringBuilder()
            var lastSources: String? = null

            chatRepository.getChatResponse(text, history, currentImage).collect { r ->
                r.onSuccess { chunk ->
                    accumulated.append(chunk.text)
                    if (chunk.sources != null) {
                        lastSources = chunk.sources
                    }
                    _uiState.update { it.copy(streamingText = accumulated.toString()) }
                }.onFailure { e ->
                    val msg     = e.message ?: "Unknown error"
                    val isQuota = msg.contains("quota", true) || msg.contains("limit", true) || msg.contains("429")
                    _uiState.update { it.copy(isLoading = false, streamingText = "", error = if (isQuota) "Quota exceeded for $provider." else "Error: $msg", quotaExceeded = isQuota, suggestedProvider = if (isQuota) nextProvider(provider) else null) }
                }
            }

            if (accumulated.isNotEmpty()) {
                aiDao.insertMessage(AiMessage(chatId = currentId, text = accumulated.toString(), isUser = false, searchSources = lastSources))

                if (history.isEmpty() && text.isNotBlank()) {
                    generateChatTitle(currentId, text, accumulated.toString())
                }
            }

            loadingPhaseJob?.cancel()
            _uiState.update { it.copy(isLoading = false, streamingText = "", loadingPhaseText = "") }
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
        _uiState.update { it.copy(error = null, quotaExceeded = false, keysUnavailable = false) }
        if (_uiState.value.messages.isNotEmpty()) createNewChat()
    }

    fun toggleHistory() { _uiState.update { it.copy(isHistoryOpen = !it.isHistoryOpen) } }

    fun regenerateMessage(messageId: Int) = viewModelScope.launch {
        val currentMessages = _uiState.value.messages
        val messageIndex = currentMessages.indexOfFirst { it.id == messageId }
        if (messageIndex == -1) return@launch
        
        val message = currentMessages[messageIndex]
        if (message.isUser) return@launch

        // Find the last user message before this one
        val historyBefore = currentMessages.take(messageIndex)
        val lastUserMessage = historyBefore.lastOrNull { it.isUser } ?: return@launch

        // Remove the message and everything after it
        val newMessages = currentMessages.take(messageIndex).toMutableList()
        _uiState.update { it.copy(messages = newMessages) }

        // Trigger send with the last user message text
        sendMessage(lastUserMessage.text)
    }

    fun deleteChat(chat: AiChat) {
        viewModelScope.launch { aiDao.deleteChat(chat); if (_uiState.value.currentChatId == chat.id) createNewChat() }
    }

    // ── Remote Keys ───────────────────────────────────────────────────────

    fun refreshRemoteKeys() { settingsManager.refreshRemoteKeys() }
    fun retrySyncKeys() { settingsManager.retrySyncKeys() }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun nextProvider(current: String): String = when (current) {
        "Gemini" -> "Groq"; "Groq" -> "ChatGPT"; "ChatGPT" -> "Claude"
        "Claude" -> "OpenRouter"; "OpenRouter" -> "DeepSeek"; else -> "Gemini"
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

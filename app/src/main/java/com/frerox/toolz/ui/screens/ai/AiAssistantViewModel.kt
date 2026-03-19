package com.frerox.toolz.ui.screens.ai

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────────────────────

data class AiAssistantUiState(
    val chats: List<AiChat>          = emptyList(),
    val currentChatId: Int?          = null,
    val messages: List<AiMessage>    = emptyList(),
    val isLoading: Boolean           = false,
    val error: String?               = null,
    val quotaExceeded: Boolean       = false,
    val suggestedProvider: String?   = null,
    val isHistoryOpen: Boolean       = false,
    val isConfigured: Boolean        = false,
    val savedConfigs: List<AiConfig> = emptyList(),
    val pendingConfig: AiConfig?     = null,
    val selectedImage: Bitmap?       = null,
    /** Non-empty while the AI is streaming so the UI can show partial text. */
    val streamingText: String        = "",
)

data class AiSettingsUiState(
    val provider: String               = "Groq",
    val apiKey: String                 = "",
    val selectedModel: String          = "llama-3.3-70b-versatile",
    val isTesting: Boolean             = false,
    val testResult: String?            = null,
    val isKeyValid: Boolean            = true,
    val isRemoteKeyAvailable: Boolean  = false,
    val editingConfig: AiConfig?       = null,
    val selectedIcon: String           = "AUTO",
)

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val aiDao: AiDao,
    private val chatRepository: ChatRepository,
    private val settingsManager: AiSettingsManager,
) : ViewModel() {

    private val _uiState           = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private val _settingsUiState   = MutableStateFlow(AiSettingsUiState())
    val settingsUiState: StateFlow<AiSettingsUiState> = _settingsUiState.asStateFlow()

    /** Tracks the current message-observation job so it can be cancelled on chat switch. */
    private var messagesJob: Job? = null

    /** Tracks the current inference job so the user can cancel it mid-flight. */
    private var activeInferenceJob: Job? = null

    init {
        loadSettings()
        loadConfigs()
        viewModelScope.launch {
            aiDao.getAllChats().collect { chats ->
                _uiState.update { it.copy(chats = chats) }
            }
        }
        createNewChat()
    }

    // ── Settings ──────────────────────────────────────────────────────────

    private fun loadSettings() {
        val provider = settingsManager.getAiProvider() ?: "Groq"
        _settingsUiState.update {
            it.copy(
                provider             = provider,
                apiKey               = settingsManager.getRawApiKey(provider),
                selectedModel        = settingsManager.getSelectedModel(),
                isRemoteKeyAvailable = settingsManager.getRemoteKey(provider).isNotBlank()
            )
        }
        _uiState.update { it.copy(isConfigured = settingsManager.isConfigured()) }
    }

    private fun loadConfigs() {
        _uiState.update { it.copy(savedConfigs = settingsManager.getSavedConfigs()) }
    }

    fun updateProvider(provider: String) {
        val model      = AiSettingsHelper.getRecommendedModel(provider)
        val existingKey = settingsManager.getRawApiKey(provider)
        _settingsUiState.update {
            it.copy(
                provider             = provider,
                apiKey               = existingKey,
                selectedModel        = model,
                isRemoteKeyAvailable = settingsManager.getRemoteKey(provider).isNotBlank()
            )
        }
        validateKey()
    }

    fun updateApiKey(key: String) {
        _settingsUiState.update { it.copy(apiKey = key) }
        validateKey()
    }

    fun updateModel(model: String) {
        _settingsUiState.update { it.copy(selectedModel = model) }
    }

    fun updateIcon(icon: String) {
        _settingsUiState.update { it.copy(selectedIcon = icon) }
    }

    private fun validateKey() {
        val key   = _settingsUiState.value.apiKey
        val valid = key.isEmpty() || AiSettingsHelper.validateApiKey(_settingsUiState.value.provider, key)
        _settingsUiState.update { it.copy(isKeyValid = valid) }
    }

    fun saveSettings() {
        with(_settingsUiState.value) {
            settingsManager.setAiProvider(provider)
            settingsManager.setApiKey(apiKey)
            settingsManager.setSelectedModel(selectedModel)
        }
        _uiState.update { it.copy(isConfigured = settingsManager.isConfigured()) }
        loadSettings() // Refresh availability state
    }

    fun saveConfig(name: String) {
        with(_settingsUiState.value) {
            val config = AiConfig(
                name      = name,
                provider  = provider,
                model     = selectedModel,
                apiKey    = apiKey,
                iconRes   = selectedIcon,
            )
            settingsManager.saveConfig(config, editingConfig?.name)
        }
        _settingsUiState.update { it.copy(editingConfig = null) }
        loadConfigs()
    }

    fun deleteConfig(config: AiConfig) {
        settingsManager.deleteConfig(config.name)
        loadConfigs()
    }

    fun editConfig(config: AiConfig) {
        _settingsUiState.update {
            it.copy(
                provider             = config.provider,
                apiKey               = config.apiKey,
                selectedModel        = config.model,
                editingConfig        = config,
                selectedIcon         = config.iconRes,
                isRemoteKeyAvailable = settingsManager.getRemoteKey(config.provider).isNotBlank()
            )
        }
    }

    fun moveConfig(fromIndex: Int, toIndex: Int) {
        val configs = _uiState.value.savedConfigs.toMutableList()
        if (fromIndex in configs.indices && toIndex in configs.indices) {
            val item = configs.removeAt(fromIndex)
            configs.add(toIndex, item)
            settingsManager.saveAllConfigs(configs)
            loadConfigs()
        }
    }

    // ── Config switching ──────────────────────────────────────────────────

    fun onConfigRequest(config: AiConfig) {
        with(_settingsUiState.value) {
            val alreadyCurrent = provider == config.provider &&
                    selectedModel == config.model && apiKey == config.apiKey
            if (alreadyCurrent) return
        }
        if (_uiState.value.messages.isEmpty()) {
            applyConfig(config)
        } else {
            _uiState.update { it.copy(pendingConfig = config) }
        }
    }

    fun confirmConfigSwitch() {
        val config = _uiState.value.pendingConfig ?: return
        applyConfig(config)
        createNewChat()
        _uiState.update { it.copy(pendingConfig = null) }
    }

    fun cancelConfigSwitch() {
        _uiState.update { it.copy(pendingConfig = null) }
    }

    private fun applyConfig(config: AiConfig) {
        settingsManager.applyConfig(config)
        loadSettings()
    }

    // ── Test connection — fixed race condition ────────────────────────────
    /**
     * Tests the connection using a **temporary, isolated** ChatRepository call.
     * Unlike the old implementation, this never writes to global `settingsManager`
     * state, so there is no race condition if the user edits settings during the test.
     */
    fun testConnection() {
        val state = _settingsUiState.value
        // Use the entered key, or fallback to the remote key if blank
        val keyToTest = state.apiKey.ifBlank { settingsManager.getRemoteKey(state.provider) }

        if (keyToTest.isBlank()) {
            _settingsUiState.update {
                it.copy(testResult = "No API key available for ${state.provider}. Refresh keys or add your own.")
            }
            return
        }

        if (!AiSettingsHelper.validateApiKey(state.provider, keyToTest)) {
            _settingsUiState.update { it.copy(testResult = "Invalid key format for ${state.provider}") }
            return
        }

        viewModelScope.launch {
            _settingsUiState.update { it.copy(isTesting = true, testResult = null) }

            // Build an ephemeral config that reflects the in-dialog selections without
            // touching the global settingsManager.
            val tempConfig = AiConfig(
                name     = "__test__",
                provider = state.provider,
                model    = state.selectedModel,
                apiKey    = keyToTest,
                iconRes  = "AUTO",
            )

            chatRepository.testConnection(tempConfig).collect { result ->
                result
                    .onSuccess { reply ->
                        _settingsUiState.update {
                            it.copy(isTesting = false, testResult = "✓ Connected: $reply")
                        }
                    }
                    .onFailure { e ->
                        _settingsUiState.update {
                            it.copy(isTesting = false, testResult = "✗ Failed: ${e.message}")
                        }
                    }
            }
        }
    }

    fun refreshRemoteKeys() {
        viewModelScope.launch {
            _settingsUiState.update { it.copy(isTesting = true, testResult = "Syncing keys...") }
            val success = settingsManager.syncRemoteKeys(force = true)
            val provider = _settingsUiState.value.provider
            val remoteKeyAvailable = settingsManager.getRemoteKey(provider).isNotBlank()
            _settingsUiState.update {
                it.copy(
                    isTesting = false,
                    testResult = if (success) "✓ Keys refreshed" else "✗ Sync failed",
                    isRemoteKeyAvailable = settingsManager.getRemoteKey(it.provider).isNotBlank()
                )
            }
        }
    }

    // ── Chat lifecycle ────────────────────────────────────────────────────

    fun loadChat(chatId: Int) {
        messagesJob?.cancel()
        _uiState.update { it.copy(currentChatId = chatId, isHistoryOpen = false, error = null) }
        messagesJob = viewModelScope.launch {
            aiDao.getMessagesForChat(chatId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun createNewChat() {
        cancelRequest()
        messagesJob?.cancel()
        _uiState.update {
            it.copy(
                currentChatId = null,
                messages      = emptyList(),
                error         = null,
                quotaExceeded = false,
                streamingText = "",
            )
        }
    }

    fun onImageSelected(bitmap: Bitmap?) {
        _uiState.update { it.copy(selectedImage = bitmap) }
    }

    // ── Inference cancellation ────────────────────────────────────────────

    /**
     * Cancels the currently running inference job.
     * Safe to call when bandage is running.
     */
    fun cancelRequest() {
        activeInferenceJob?.cancel()
        activeInferenceJob = null
        _uiState.update {
            it.copy(isLoading = false, streamingText = "")
        }
    }

    // ── Message sending — fixed streaming accumulation ────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank() && _uiState.value.selectedImage == null) return
        if (_uiState.value.isLoading) return

        activeInferenceJob = viewModelScope.launch {
            var currentId   = _uiState.value.currentChatId
            val currentImage = _uiState.value.selectedImage
            val historySnapshot = _uiState.value.messages.toList()

            // Create a new chat session if none exists
            if (currentId == null) {
                val title = if (text.isNotBlank()) text.take(20).trimEnd() + "…" else "Image Analysis"
                currentId = aiDao.insertChat(AiChat(title = title)).toInt()
                _uiState.update { it.copy(currentChatId = currentId) }
                loadChat(currentId)
            }

            // Persist user message
            val userMsg = AiMessage(chatId = currentId, text = text, isUser = true)
            aiDao.insertMessage(userMsg)

            _uiState.update {
                it.copy(
                    isLoading    = true,
                    error        = null,
                    quotaExceeded = false,
                    selectedImage = null,
                    streamingText = "",
                )
            }

            // Accumulate streaming chunks into a single final string.
            // This handles both streaming APIs (many small emissions) and
            // request/response APIs (single emission) correctly.
            val accumulated = StringBuilder()

            chatRepository.getChatResponse(text, historySnapshot, currentImage).collect { result ->
                result
                    .onSuccess { chunk ->
                        accumulated.append(chunk)
                        // Update streaming preview so the UI can show partial text live
                        _uiState.update { it.copy(streamingText = accumulated.toString()) }
                    }
                    .onFailure { e ->
                        val errorMsg = e.message ?: "Unknown error"
                        val isQuota  = errorMsg.contains("quota", true) ||
                                errorMsg.contains("limit", true) ||
                                errorMsg.contains("429")
                        val suggested = nextProviderSuggestion(settingsManager.getAiProvider() ?: "Gemini")

                        _uiState.update {
                            it.copy(
                                isLoading     = false,
                                streamingText = "",
                                error         = "Error: $errorMsg",
                                quotaExceeded = isQuota,
                                suggestedProvider = if (isQuota) suggested else null,
                            )
                        }
                    }
            }

            // Only persist the AI message if we actually received content
            if (accumulated.isNotEmpty()) {
                val aiMsg = AiMessage(chatId = currentId, text = accumulated.toString(), isUser = false)
                aiDao.insertMessage(aiMsg)

                // Update chat title after first exchange
                if (historySnapshot.isEmpty() && text.isNotBlank()) {
                    val finalTitle = text.take(30).let { if (it.length == 30) "$it…" else it }
                    aiDao.updateChat(AiChat(id = currentId, title = finalTitle))
                }
            }

            _uiState.update { it.copy(isLoading = false, streamingText = "") }
        }
    }

    fun switchProvider(provider: String) {
        settingsManager.setAiProvider(provider)
        settingsManager.setSelectedModel(AiSettingsHelper.getRecommendedModel(provider))
        loadSettings()
        _uiState.update { it.copy(error = null, quotaExceeded = false) }
        if (_uiState.value.messages.isNotEmpty()) createNewChat()
    }

    fun toggleHistory() {
        _uiState.update { it.copy(isHistoryOpen = !it.isHistoryOpen) }
    }

    fun deleteChat(chat: AiChat) {
        viewModelScope.launch {
            aiDao.deleteChat(chat)
            if (_uiState.value.currentChatId == chat.id) createNewChat()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun nextProviderSuggestion(current: String): String = when (current) {
        "Gemini"     -> "Groq"
        "Groq"       -> "ChatGPT"
        "ChatGPT"    -> "Claude"
        "Claude"     -> "OpenRouter"
        "OpenRouter" -> "DeepSeek"
        else         -> "Gemini"
    }
}

// ─────────────────────────────────────────────────────────────
//  Chat history grouping helper (used by the drawer UI)
// ─────────────────────────────────────────────────────────────

enum class ChatGroup { TODAY, YESTERDAY, THIS_WEEK, OLDER }

fun AiChat.chatGroup(): ChatGroup {
    val now = Calendar.getInstance()
    val chatCal = Calendar.getInstance().apply { timeInMillis = this@chatGroup.createdAt }
    return when {
        now.get(Calendar.DAY_OF_YEAR) == chatCal.get(Calendar.DAY_OF_YEAR) &&
                now.get(Calendar.YEAR) == chatCal.get(Calendar.YEAR) -> ChatGroup.TODAY

        now.get(Calendar.DAY_OF_YEAR) - chatCal.get(Calendar.DAY_OF_YEAR) == 1 &&
                now.get(Calendar.YEAR) == chatCal.get(Calendar.YEAR) -> ChatGroup.YESTERDAY

        now.get(Calendar.WEEK_OF_YEAR) == chatCal.get(Calendar.WEEK_OF_YEAR) &&
                now.get(Calendar.YEAR) == chatCal.get(Calendar.YEAR) -> ChatGroup.THIS_WEEK

        else -> ChatGroup.OLDER
    }
}

fun ChatGroup.label(): String = when (this) {
    ChatGroup.TODAY     -> "Today"
    ChatGroup.YESTERDAY -> "Yesterday"
    ChatGroup.THIS_WEEK -> "This Week"
    ChatGroup.OLDER     -> "Older"
}

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
private const val GROQ_MODEL_HARD = "openai/gpt-oss-120b"
private const val GROQ_MODEL_EASY = "openai/gpt-oss-20b"

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
    val chatSummary       : String?          = null,
    val isSummarizing     : Boolean          = false,
    val isGeneratingTitle : Boolean          = false,
    val suggestedPrompts  : List<String>     = emptyList(),
    val isGeneratingPrompts: Boolean         = false,
    // Search is ON by default (privacy-friendly meta engines). Icon always visible by default.
    val aiSearchEnabled   : Boolean          = true,
    val aiSearchIconVisible: Boolean         = true,
    val loadingPhaseText  : String           = "",
    val isCoachMode       : Boolean          = false,
    val hasApiKey         : Boolean          = true,
    // Applied (persisted) provider/model — the ones actually used for inference.
    // The settings dialog edits a *draft* (AiSettingsUiState); the top-bar tag,
    // avatar and vision-gate must always reflect these applied values, never the
    // un-saved draft, so closing Settings without Apply can't desync the tag.
    val activeProvider    : String           = "Groq",
    val activeModel       : String           = "openai/gpt-oss-20b",
)

data class AiSettingsUiState(
    val provider             : String    = "Groq",
    val apiKey               : String    = "",
    val selectedModel        : String    = "openai/gpt-oss-20b",
    val isTesting            : Boolean   = false,
    val testResult           : String?   = null,
    val isKeyValid           : Boolean   = true,
    val editingConfig        : AiConfig? = null,
    val selectedIcon         : String    = "AUTO",
    val customIconUri        : String?   = null,
    val dynamicPromptsEnabled: Boolean   = true,
    val promptFormat         : String    = "medium",
    val showGroqKeyMissingDialog: Boolean = false,
    val isGroqConfigured     : Boolean   = false,
    val modelAvailability    : ModelAvailability = ModelAvailability.UNKNOWN,
    val showNoKeyWarningFor  : String?   = null,
    val catalogVersion       : Int?      = null,
    val catalogUpdatedAt     : String?   = null,
    val isRefreshingCatalog  : Boolean   = false,
    val catalogRefreshResult : String?   = null,
)

enum class ModelAvailability { AVAILABLE, UNAVAILABLE, CHECKING, UNKNOWN }

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
    private val catalogRepository: AiCatalogRepository,
    private val offlineManager: com.frerox.toolz.util.OfflineManager,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle,
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

    /**
     * The assistant is online-only (inference + model catalog + web search
     * are all server-side). False on airplane mode / no route / manual
     * offline mode. The screen gates on this; send paths double-check it.
     */
    val isOnline: StateFlow<Boolean> = offlineManager.offlineState
        .map { it == com.frerox.toolz.util.OfflineState.ONLINE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private var messagesJob        : Job? = null
    private var activeInferenceJob : Job? = null
    private var loadingPhaseJob    : Job? = null

    init {
        loadSettings()
        loadConfigs()
        observeSearchSettings()
        observeCatalog()
        // Auto-update the server model catalog when stale (TTL-guarded,
        // silent unless it fails — manual refresh lives in Settings).
        viewModelScope.launch {
            runCatching { catalogRepository.refresh() }
        }
        viewModelScope.launch {
            aiDao.getAllChats().collect { chats -> _uiState.update { it.copy(chats = chats) } }
        }
        
        val initialChatId = savedStateHandle.get<Int>("chatId")
        val isCoach = savedStateHandle.get<Boolean>("isCoachMode") ?: false
        
        _uiState.update { it.copy(isCoachMode = isCoach) }

        if (initialChatId != null && initialChatId != -1) {
            loadChat(initialChatId)
        } else {
            // createNewChat already calls refreshPrompts, so we don't need to call it again here
            createNewChat()
        }
    }

    private fun loadSettings() {
        val provider = settingsManager.getAiProvider()
        val model = settingsManager.getSelectedModel(provider)
        _settingsUiState.update {
            it.copy(
                provider             = provider,
                apiKey               = settingsManager.getRawApiKey(provider),
                selectedModel        = model,
                dynamicPromptsEnabled = settingsManager.isDynamicPromptsEnabled(),
                promptFormat         = settingsManager.getPromptFormat()
            )
        }
        val hasKey = settingsManager.resolveApiKey(provider).source != com.frerox.toolz.data.ai.ApiKeySource.NONE
        _uiState.update { it.copy(isConfigured = settingsManager.isConfigured(), hasApiKey = hasKey, activeProvider = provider, activeModel = model) }
        checkModelAvailability()
    }

    /**
     * Discard un-applied edits in the settings dialog (user pressed Close
     * without Apply). Re-loads the draft from persisted prefs so the top-bar
     * tag and any other applied-state readers snap back to what's really used.
     */
    fun discardSettingsDraft() {
        loadSettings()
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
            val hasGroqKey = settingsManager.hasUserApiKey("Groq")
            if (settingsManager.isDynamicPromptsEnabled() && hasGroqKey) {
                generateDynamicPrompts()
            } else {
                _uiState.update { it.copy(isGeneratingPrompts = false, suggestedPrompts = getFilteredPremadePrompts()) }
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
                        model = GROQ_MODEL_EASY,
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

    // ── Server model catalog ─────────────────────────────────────────────

    private fun observeCatalog() {
        viewModelScope.launch {
            catalogRepository.installedVersion.collect { v ->
                _settingsUiState.update { it.copy(catalogVersion = v) }
            }
        }
        viewModelScope.launch {
            catalogRepository.catalogUpdatedAt.collect { date ->
                _settingsUiState.update { it.copy(catalogUpdatedAt = date) }
            }
        }
        viewModelScope.launch {
            catalogRepository.isRefreshing.collect { refreshing ->
                _settingsUiState.update { it.copy(isRefreshingCatalog = refreshing) }
            }
        }
    }

    /**
     * Manual catalog refresh (Settings → Model catalog → Refresh).
     * Re-reads provider/model tables afterwards so the picker shows the
     * new lineup without leaving the dialog.
     */
    fun refreshCatalog() {
        viewModelScope.launch {
            _settingsUiState.update { it.copy(catalogRefreshResult = null) }
            val ok = catalogRepository.refresh(force = true)
            if (ok) {
                // Re-resolve current provider/model against the new tables
                // (may swap a just-retired model for its replacement).
                updateProvider(_settingsUiState.value.provider)
                val v = catalogRepository.installedVersion.value
                _settingsUiState.update {
                    it.copy(catalogRefreshResult = if (v != null) "✓ Models updated (v$v)" else "✓ Models updated")
                }
            } else {
                _settingsUiState.update {
                    it.copy(catalogRefreshResult = "✗ Refresh failed — check your connection and try again.")
                }
            }
        }
    }

    /**
     * Offline-gate retry: re-attempt the catalog fetch (result surfaces in
     * settings + the gate auto-clears the moment connectivity returns).
     */
    fun retryConnection() {
        viewModelScope.launch {
            catalogRepository.refresh(force = true)
        }
    }

    fun toggleAiSearch() {
        viewModelScope.launch {
            val nextState = !_uiState.value.aiSearchEnabled
            // Search now works with ANY provider (local heuristic + any helper key).
            // No Groq gate — just flip the switch. Icon stays visible.
            settingsRepository.setAiSearchEnabled(nextState)
            _uiState.update { it.copy(aiSearchEnabled = nextState) }
        }
    }

    fun dismissGroqDialog() {
        _settingsUiState.update { it.copy(showGroqKeyMissingDialog = false) }
    }

    fun performDeepDive(message: AiMessage) {
        val chatId = message.chatId
        val sources = message.searchSources ?: return
        val userPrompt = _uiState.value.messages.findLast { it.isUser }?.text ?: return
        
        viewModelScope.launch {
            // Update state to COMPLETED (to hide buttons immediately)
            aiDao.updateMessage(message.copy(deepDiveState = DeepDiveState.COMPLETED))
            
            _uiState.update { it.copy(isLoading = true, loadingPhaseText = "Deep diving into websites...") }
            
            val accumulated = StringBuilder()
            chatRepository.performDeepDive(userPrompt, sources, _uiState.value.messages).collect { r ->
                r.onSuccess { chunk ->
                    accumulated.append(chunk.text)
                    _uiState.update { it.copy(streamingText = accumulated.toString()) }
                }.onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = "Deep dive failed: ${e.message}") }
                }
            }
            
            if (accumulated.isNotEmpty()) {
                aiDao.insertMessage(AiMessage(chatId = chatId, text = accumulated.toString(), isUser = false))
            }
            _uiState.update { it.copy(isLoading = false, streamingText = "", loadingPhaseText = "") }
        }
    }

    fun dismissDeepDive(message: AiMessage) {
        viewModelScope.launch {
            aiDao.updateMessage(message.copy(deepDiveState = DeepDiveState.FADED))
        }
    }

    fun saveGroqKey(key: String) {
        viewModelScope.launch {
            settingsManager.setApiKey(key, "Groq")
            _settingsUiState.update { it.copy(showGroqKeyMissingDialog = false, isGroqConfigured = true) }
            settingsRepository.setAiSearchEnabled(true)
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
            )
        }
        validateKey()
        checkModelAvailability()
    }

    fun updateApiKey(key: String) {
        _settingsUiState.update { it.copy(apiKey = AiSettingsHelper.normalizeApiKeyInput(key)) }
        validateKey()
        checkModelAvailability() // Re-check if API key changes
    }
    fun updateModel(model: String) {
        _settingsUiState.update { it.copy(selectedModel = model) }
        checkModelAvailability()
    }

    private fun checkModelAvailability() {
        val s = _settingsUiState.value
        val provider = s.provider
        val model = s.selectedModel

        if (model.isBlank()) {
            _settingsUiState.update { it.copy(modelAvailability = ModelAvailability.UNKNOWN) }
            return
        }

        viewModelScope.launch {
            _settingsUiState.update { it.copy(modelAvailability = ModelAvailability.CHECKING) }
            val isAvailable = chatRepository.checkModelAvailability(provider, model)
            _settingsUiState.update {
                it.copy(modelAvailability = if (isAvailable) ModelAvailability.AVAILABLE else ModelAvailability.UNAVAILABLE)
            }
        }
    }

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
        if (!changed) return

        // Warn when switching to (or saving) a provider with no key set —
        // chats would just fail. User can go back or save anyway.
        val effectiveKey = s.apiKey.ifBlank { settingsManager.getRawApiKey(s.provider) }
        if (effectiveKey.isBlank()) {
            _settingsUiState.update { it.copy(showNoKeyWarningFor = s.provider) }
            return
        }

        if (_uiState.value.messages.isNotEmpty()) {
            _uiState.update { it.copy(pendingConfig = AiConfig("Current Settings", s.provider, s.selectedModel, s.apiKey, s.selectedIcon, s.customIconUri)) }
        } else {
            saveSettings()
        }
    }

    /** User accepted saving a keyless provider — persist + clear warning. */
    fun confirmSaveWithoutKey() {
        _settingsUiState.update { it.copy(showNoKeyWarningFor = null) }
        if (_uiState.value.messages.isNotEmpty()) {
            val s = _settingsUiState.value
            _uiState.update { it.copy(pendingConfig = AiConfig("Current Settings", s.provider, s.selectedModel, s.apiKey, s.selectedIcon, s.customIconUri)) }
        } else {
            saveSettings()
        }
    }

    fun dismissNoKeyWarning() {
        _settingsUiState.update { it.copy(showNoKeyWarningFor = null) }
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
        viewModelScope.launch {
            val chat = aiDao.getAllChatsSync().find { it.id == chatId }
            val isCoach = chat?.title == "AI Fitness Coach"
            _uiState.update { 
                it.copy(
                    currentChatId = chatId, 
                    isHistoryOpen = false, 
                    error = null, 
                    chatSummary = null,
                    isCoachMode = isCoach
                ) 
            }
        }
        messagesJob = viewModelScope.launch {
            aiDao.getMessagesForChat(chatId).collect { messages -> _uiState.update { it.copy(messages = messages) } }
        }
    }

    fun createNewChat() {
        cancelRequest()
        messagesJob?.cancel()
        _uiState.update { 
            it.copy(
                currentChatId = null, 
                messages = emptyList(), 
                error = null, 
                quotaExceeded = false, 
                streamingText = "", 
                chatSummary = null,
                isCoachMode = false
            ) 
        }
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
        if (!isOnline.value) {
            _uiState.update { it.copy(error = "You're offline. Reconnect to keep chatting.") }
            return
        }

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
                _uiState.update { it.copy(error = "No API key. Add a key in Settings.") }
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
            _uiState.update { it.copy(isLoading = true, error = null, quotaExceeded = false, selectedImage = null, streamingText = "", loadingPhaseText = if (webSearchEnabled) "Surfing the web" else "Analyzing") }
            
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
            val requestStartMs = android.os.SystemClock.elapsedRealtime()

            val systemPrompt = if (_uiState.value.isCoachMode) {
                """You are an elite AI Fitness Coach.
                RULES:
                1. Answer directly and concisely (2-4 sentences).
                2. Use **bold** for emphasis and bullet points for lists.
                3. Be punchy, professional, and actionable.
                4. Ground your advice in real fitness data."""
            } else null

            chatRepository.getChatResponse(
                prompt = text,
                history = history,
                image = currentImage,
                systemPromptOverride = systemPrompt
            ).collect { r ->
                r.onSuccess { chunk ->
                    accumulated.append(chunk.text)
                    if (chunk.sources != null) {
                        lastSources = chunk.sources
                    }
                    _uiState.update { it.copy(streamingText = accumulated.toString()) }
                }.onFailure { e ->
                    val msg     = e.message ?: "Unknown error"
                    val lower = msg.lowercase()
                    val isQuota = lower.contains("quota") || lower.contains("rate limit") || lower.contains("429") || lower.contains("503")
                    val isNotFound = lower.contains("404") || lower.contains("retired") || lower.contains("not found") || lower.contains("decommissioned")
                    val isAuth = lower.contains("401") || lower.contains("invalid api key") || lower.contains("unauthorized")
                    val display = when {
                        isNotFound -> "Model retired (404) — auto-switched to a live model. Tap retry to resend."
                        isQuota -> "Quota exceeded for $provider."
                        isAuth -> "Invalid API key for $provider. Open Settings to fix it."
                        else -> "Error: $msg"
                    }
                    _uiState.update { it.copy(isLoading = false, streamingText = "", error = display, quotaExceeded = isQuota, suggestedProvider = if (isQuota) nextProvider(provider) else null) }
                }
            }

            if (accumulated.isNotEmpty()) {
                val responseText = accumulated.toString()
                // Re-read: the repo persists a working fallback model on 404
                // recovery, so this is the model that actually replied.
                val effectiveModel = settingsManager.getSelectedModel(provider)
                val elapsedMs = android.os.SystemClock.elapsedRealtime() - requestStartMs

                aiDao.insertMessage(AiMessage(
                    chatId = currentId,
                    text = responseText,
                    isUser = false,
                    searchSources = lastSources,
                    canDeepDive = (lastSources != null),
                    deepDiveState = if (lastSources != null) DeepDiveState.PENDING else DeepDiveState.NONE,
                    modelName = effectiveModel,
                    responseTimeMs = elapsedMs
                ))

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
            val userContext = messages.filter { it.isUser }.takeLast(3).joinToString("\n") {
                "User: ${it.text.take(200)}"
            }.ifBlank {
                messages.take(6).joinToString("\n") { "User: ${it.text.take(200)}" }
            }
            val lastReply = messages.lastOrNull { !it.isUser }?.text.orEmpty()
            val title = callGroqForTitle(userContext, lastReply)
            if (title != null && title.isNotBlank()) {
                aiDao.updateChat(AiChat(id = chatId, title = title))
            }
            _uiState.update { it.copy(isGeneratingTitle = false) }
        }
    }

    private suspend fun callGroqForTitle(userMsg: String, aiReply: String): String? {
        // Try Groq fast model first, then any available helper key (Zen free, current provider).
        // 3 attempts total with cheap models only — titles must never block chat.
        val prompt = buildString {
            append("Write a short chat-list title for this conversation.\n")
            append("RULES: 3-5 words, noun phrase, no verbs like 'chat/help/ask/discuss', ")
            append("no quotes, no emojis, no trailing punctuation, no generic words like 'conversation' or 'question'.\n")
            append("User: ${userMsg.take(300)}\n")
            if (aiReply.isNotBlank()) append("Assistant reply: ${aiReply.take(300)}")
        }
        val candidates = buildList {
            val groqKey = settingsManager.getApiKey("Groq")
            if (groqKey.isNotBlank()) add(Triple("Groq", GROQ_URL, groqKey to GROQ_MODEL_EASY))
            val zenKey = settingsManager.getApiKey("OpenCode Zen")
            if (zenKey.isNotBlank()) add(Triple("OpenCode Zen", "https://opencode.ai/zen/v1/chat/completions", zenKey to "muse-spark-1.3-contributor-free"))
            val current = AiSettingsHelper.canonicalProvider(settingsManager.getAiProvider())
            val curKey = settingsManager.getApiKey(current)
            val curUrl = AiSettingsHelper.getChatCompletionUrl(current)
            if (curKey.isNotBlank() && curUrl != null && current != "Groq" && current != "OpenCode Zen") {
                add(Triple(current, curUrl, curKey to settingsManager.getSelectedModel(current)))
            }
        }.take(3)
        if (candidates.isEmpty()) return null
        for ((provider, url, keyModel) in candidates) {
            repeat(2) { attempt ->
                try {
                    val (key, model) = keyModel
                    val resp = withContext(Dispatchers.IO) {
                        openAiService.getChatCompletion(
                            url = url,
                            authHeader = "Bearer $key",
                            referer = if (provider.startsWith("OpenCode")) "https://github.com/frerox/toolz" else null,
                            title = if (provider.startsWith("OpenCode")) "Toolz AI" else null,
                            request = OpenAiRequest(
                                model = model,
                                messages = listOf(
                                    OpenAiMessage("system", MessageContent.Text("You generate short chat titles. Reply with ONLY the title — no explanation, no quotes.")),
                                    OpenAiMessage("user", MessageContent.Text(prompt)),
                                ),
                                maxTokens = 20,
                            )
                        )
                    }
                    return resp.choices.firstOrNull()?.message?.content
                        ?.let { sanitizeChatTitle(it) }
                        ?.takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    Log.e(TAG, "Title gen failed ($provider attempt ${attempt + 1}): ${e.message}")
                    if (attempt == 0) {
                        try { kotlinx.coroutines.delay(500L) } catch (_: Exception) {}
                    }
                }
            }
        }
        return null
    }

    /**
     * Normalize a raw model-generated title into a clean chat-list title:
     * single line, no markdown/quotes/bullets/numbering, capitalized.
     */
    private fun sanitizeChatTitle(raw: String): String {
        var t = raw.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        t = t.removePrefix("-").removePrefix("*").removePrefix("•").trim()
        t = t.replace(Regex("^\\d+[.)]\\s*"), "")
        t = t.removeSurrounding("\"").removeSurrounding("'")
            .removeSurrounding("**").removeSurrounding("*")
            .removeSurrounding("_").removeSurrounding("#").trim()
        t = t.trimEnd('.', '!', '?', ':', ';', ',', '"', '\'').trim()
        t = t.replace(Regex("\\s+"), " ")
        if (t.length > 42) t = t.take(42).trimEnd()
        if (t.isNotEmpty()) t = t.replaceFirstChar { it.uppercase() }
        return t
    }

    // ── Chat Summarization ─────────────────────────────────────────────────

    fun summarizeChat() {
        val messages = _uiState.value.messages
        if (messages.isEmpty()) return
        _uiState.update { it.copy(isSummarizing = true, chatSummary = null) }

        viewModelScope.launch {
            // Prefer current provider, else Groq (fast 20b), else Zen free — distinct only.
            data class Helper(val provider: String, val url: String, val key: String, val model: String)
            val helpers = buildList {
                val current = AiSettingsHelper.canonicalProvider(settingsManager.getAiProvider())
                val curKey = settingsManager.getApiKey(current)
                AiSettingsHelper.getChatCompletionUrl(current)?.let { url ->
                    if (curKey.isNotBlank()) add(Helper(current, url, curKey, settingsManager.getSelectedModel(current)))
                }
                val groqKey = settingsManager.getApiKey("Groq")
                if (groqKey.isNotBlank()) add(Helper("Groq", GROQ_URL, groqKey, GROQ_MODEL_EASY))
                val zenKey = settingsManager.getApiKey("OpenCode Zen")
                if (zenKey.isNotBlank()) add(Helper("OpenCode Zen", "https://opencode.ai/zen/v1/chat/completions", zenKey, "muse-spark-1.3-contributor-free"))
            }.distinctBy { AiSettingsHelper.canonicalProvider(it.provider) }.take(3)
            if (helpers.isEmpty()) {
                _uiState.update { it.copy(isSummarizing = false, chatSummary = "⚠ No API key available. Add a key in Settings.") }
                return@launch
            }

            // Budget the payload so long chats fit every provider's context:
            // head (first 2, for the original topic) + tail (most recent),
            // each message capped, total capped at ~8k chars (~2k tokens).
            fun buildChatText(maxMessages: Int, perMessage: Int, maxChars: Int): String {
                val picked: List<AiMessage> = if (messages.size <= maxMessages) {
                    messages.takeLast(maxMessages)
                } else {
                    // Keep opening context + recent context when truncating long chats.
                    val head = messages.take(2)
                    val tail = messages.takeLast(maxMessages - 2)
                    head + tail
                }
                val sb = StringBuilder()
                // Walk from most-recent backwards so the newest (most relevant)
                // content always survives the budget cut.
                val ordered = picked.reversed()
                var included = 0
                for (m in ordered) {
                    val line = (if (m.isUser) "User: " else "AI: ") + m.text.take(perMessage) + "\n"
                    if (sb.length + line.length > maxChars) break
                    sb.insert(0, line)
                    included++
                }
                val omitted = messages.size - included
                val prefix = if (omitted > 0) "(+$omitted earlier messages omitted for length)\n" else ""
                return prefix + sb.toString()
            }

            var chatText = buildChatText(maxMessages = 30, perMessage = 300, maxChars = 8000)

            val systemPrompt = """
Summarize this AI conversation as 3-6 concise bullet points.
- Start each bullet with "•"
- Focus on the main topics, decisions, and outcomes
- Keep total under 150 words
- No preamble, just bullet points
            """.trimIndent()

            suspend fun tryHelper(h: Helper, payload: String): com.frerox.toolz.data.ai.OpenAiResponse {
                return withContext(Dispatchers.IO) {
                    openAiService.getChatCompletion(
                        url = h.url,
                        authHeader = "Bearer ${h.key}",
                        referer = if (h.provider.startsWith("OpenCode")) "https://github.com/frerox/toolz" else null,
                        title = if (h.provider.startsWith("OpenCode")) "Toolz AI" else null,
                        request = OpenAiRequest(
                            model = h.model,
                            messages = listOf(
                                OpenAiMessage("system", MessageContent.Text(systemPrompt)),
                                OpenAiMessage("user", MessageContent.Text(payload)),
                            ),
                            maxTokens = 300,
                        )
                    )
                }
            }

            fun isPayloadTooLarge(e: Exception): Boolean {
                val msg = (e.message ?: "").lowercase()
                return msg.contains("413") || msg.contains("too large") ||
                    msg.contains("context") && (msg.contains("length") || msg.contains("long") || msg.contains("exceed")) ||
                    msg.contains("maximum context") || msg.contains("token limit")
            }

            try {
                var resp: com.frerox.toolz.data.ai.OpenAiResponse? = null
                var lastErr: Exception? = null
                outer@ for (h in helpers) {
                    // Up to 2 attempts per helper, but NEVER re-call after success.
                    for (attempt in 0 until 2) {
                        if (resp != null) break
                        try {
                            resp = tryHelper(h, chatText)
                            break
                        } catch (e: Exception) {
                            lastErr = e as? Exception ?: Exception(e.message)
                            Log.e(TAG, "Summarize failed (${h.provider} attempt ${attempt + 1}): ${e.message}")
                        }
                        if (attempt == 0 && resp == null && lastErr != null && isPayloadTooLarge(lastErr!!)) {
                            // Payload-too-large → shrink once and retry same helper
                            // instead of burning the retry on the same oversized body.
                            chatText = buildChatText(maxMessages = 15, perMessage = 200, maxChars = 4000)
                        }
                        if (attempt == 0 && resp == null) {
                            try { kotlinx.coroutines.delay(600L) } catch (_: Exception) {}
                        }
                    }
                    if (resp != null) break@outer
                }
                val summary = resp?.choices?.firstOrNull()?.message?.content?.trim()?.takeIf { it.isNotBlank() }
                    ?: "Summary failed: ${lastErr?.message ?: "no helper responded"}"
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
        _uiState.update { it.copy(error = null, quotaExceeded = false) }
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

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun nextProvider(current: String): String = when (AiSettingsHelper.canonicalProvider(current)) {
        "Gemini" -> "Groq"; "Groq" -> "ChatGPT"; "ChatGPT" -> "Claude"
        "Claude" -> "OpenRouter"; "OpenRouter" -> "DeepSeek"; "DeepSeek" -> "OpenCode Zen"
        "OpenCode Zen" -> "OpenCode Go"; else -> "Gemini"
    }

    /** Retry the last user message (used by error banner retry button). */
    fun retryLastMessage() {
        val lastUser = _uiState.value.messages.lastOrNull { it.isUser } ?: return
        _uiState.update { it.copy(error = null) }
        sendMessage(lastUser.text)
    }

    /** Export current chat as markdown text (for share sheet). */
    fun exportChatAsMarkdown(): String {
        val title = _uiState.value.chats.find { it.id == _uiState.value.currentChatId }?.title ?: "Toolz AI chat"
        val sb = StringBuilder("# $title\n\n")
        _uiState.value.messages.forEach {
            sb.append(if (it.isUser) "## You\n" else "## AI\n").append(it.text.trim()).append("\n\n")
        }
        return sb.toString()
    }

    fun clearAllChats() {
        viewModelScope.launch {
            _uiState.value.chats.forEach { runCatching { aiDao.deleteChat(it) } }
            createNewChat()
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

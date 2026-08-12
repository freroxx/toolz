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

package com.frerox.toolz.ui.screens.dashboard

import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.ChatRepository
import com.frerox.toolz.data.notepad.NoteDao
import com.frerox.toolz.data.todo.TaskDao
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.update.UpdateRepository
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.util.OfflineManager
import com.frerox.toolz.util.OfflineState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

enum class DashboardTab(@StringRes val titleRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME(com.frerox.toolz.R.string.st_Dashboard_Tab_Home, Icons.Rounded.Home),
    TIME(com.frerox.toolz.R.string.st_Dashboard_Tab_Time, Icons.Rounded.Schedule),
    MEDIA(com.frerox.toolz.R.string.st_Dashboard_Tab_Media, Icons.Rounded.LibraryMusic),
    UTILITIES(com.frerox.toolz.R.string.st_Dashboard_Tab_Utilities, Icons.Rounded.Construction),
    SYSTEM(com.frerox.toolz.R.string.st_Dashboard_Tab_System, Icons.Rounded.SettingsInputComponent)
}

data class ToolCategory(
    @StringRes val titleRes: Int,
    val items: List<ToolItem>,
)

data class ToolItem(
    @StringRes val titleRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String,
    @StringRes val descriptionRes: Int,
    val color: Color = Color.Unspecified,
)

data class DashboardStats(
    val batteryLevel: Int = 0,
    val isBatteryCharging: Boolean = false,
    val storageUsedPercentage: Float = 0f,
    val storageAvailableGb: Double = 0.0,
)

// Index sentinel meaning "All categories shown"
const val CATEGORY_ALL = -1

// ─────────────────────────────────────────────────────────────────────────────
// Dashboard ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiRepository: ChatRepository,
    private val noteDao: NoteDao,
    private val taskDao: TaskDao,
    private val aiDao: com.frerox.toolz.data.ai.AiDao,
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val offlineManager: OfflineManager,
) : ViewModel() {

    // ── Search ────────────────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isAiSearching = MutableStateFlow(false)
    val isAiSearching = _isAiSearching.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking = _isAiThinking.asStateFlow()

    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse = _aiResponse.asStateFlow()

    private val _aiSuggestedRoutes = MutableStateFlow<List<String>>(emptyList())
    val aiSuggestedRoutes = _aiSuggestedRoutes.asStateFlow()

    // ── Navigation ────────────────────────────────────────────────────────────
    private val _selectedTab = MutableStateFlow(DashboardTab.HOME)
    val selectedTab = _selectedTab.asStateFlow()

    fun setSelectedTab(tab: DashboardTab) {
        _selectedTab.value = tab
    }

    // ── Stats ─────────────────────────────────────────────────────────────────
    private val _dashboardStats = MutableStateFlow(DashboardStats())
    val dashboardStats = _dashboardStats.asStateFlow()

    // ── Offline / update ──────────────────────────────────────────────────────
    val offlineState = offlineManager.offlineState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OfflineState.ONLINE)

    val manualOfflineMode = settingsRepository.offlineModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val pinnedTools = settingsRepository.pinnedTools
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val recentTools = settingsRepository.recentTools
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Full category list, filtered for offline state ────────────────────────
    val categories: StateFlow<List<ToolCategory>> = offlineState.map { state ->
        val base = getDashboardCategories()
        if (state == OfflineState.OFFLINE) {
            base.map { cat ->
                cat.copy(
                    items = cat.items.filter { item ->
                        item.route != Screen.AiAssistant.route &&
                                item.route != Screen.Search.route
                    },
                )
            }.filter { it.items.isNotEmpty() }
        } else {
            base
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), getDashboardCategories())

    // ── Derived: categories visible in the current tab ────────────────────────
    val tabCategories: StateFlow<List<ToolCategory>> = combine(
        categories,
        _selectedTab,
        _searchQuery
    ) { allCats, tab, query ->
        val tabSpecificCats = when (tab) {
            DashboardTab.HOME -> allCats.filter { it.titleRes == com.frerox.toolz.R.string.st_Dashboard_Cat_SmartFlow }
            DashboardTab.TIME -> allCats.filter { it.titleRes == com.frerox.toolz.R.string.st_Dashboard_Cat_Time }
            DashboardTab.MEDIA -> allCats.filter { it.titleRes == com.frerox.toolz.R.string.st_Dashboard_Cat_Media }
            DashboardTab.UTILITIES -> allCats.filter { 
                it.titleRes == com.frerox.toolz.R.string.st_Dashboard_Cat_Utilities || 
                it.titleRes == com.frerox.toolz.R.string.st_Dashboard_Cat_Sensors 
            }
            DashboardTab.SYSTEM -> allCats.filter { it.titleRes == com.frerox.toolz.R.string.st_Dashboard_Cat_System }
        }

        if (query.isBlank()) {
            tabSpecificCats
        } else {
            val q = query.trim().lowercase()
            tabSpecificCats.map { cat ->
                cat.copy(items = cat.items.filter { 
                    context.getString(it.titleRes).lowercase().contains(q) || 
                    context.getString(it.descriptionRes).lowercase().contains(q) 
                })
            }.filter { it.items.isNotEmpty() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Update availability ───────────────────────────────────────────────────
    val updateAvailableVersion = combine(settingsRepository.updateAvailableVersion, offlineState) { v, s ->
        if (s == OfflineState.OFFLINE) null else v
    }
    val updateChangelog = combine(settingsRepository.updateChangelog, offlineState) { c, s ->
        if (s == OfflineState.OFFLINE) null else c
    }
    val updateApkUrl = combine(settingsRepository.updateApkUrl, offlineState) { u, s ->
        if (s == OfflineState.OFFLINE) null else u
    }

    private var searchJob: Job? = null

    // ── Spotlight ─────────────────────────────────────────────────────────────
    private val _spotlightTool = MutableStateFlow<ToolItem?>(null)
    val spotlightTool = _spotlightTool.asStateFlow()

    init {
        setupSearchDebounce()
        checkForUpdates()
        startStatsUpdate()
        updateSpotlightTool()
    }

    private fun updateSpotlightTool() {
        viewModelScope.launch {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val allTools = getDashboardCategories().flatMap { it.items }
            
            _spotlightTool.value = when {
                hour in 6..9 -> allTools.find { it.route == Screen.Todo.route }
                hour in 22..23 || hour in 0..4 -> allTools.find { it.route == Screen.Notepad.route }
                else -> allTools.random()
            }
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        
        // Instant URL detection
        if (query.trim().let { it.startsWith("http://") || it.startsWith("https://") || (it.contains(".") && !it.contains(" ") && it.length > 3) }) {
            // We don't auto-navigate here to avoid jarring the user, 
            // but we could provide a special "Open URL" suggestion.
        }
    }

    @OptIn(FlowPreview::class)
    private fun setupSearchDebounce() {
        viewModelScope.launch {
            _searchQuery
                .debounce(280)
                .collect { query ->
                    if (query.isBlank()) {
                        _aiSuggestedRoutes.value = emptyList()
                        _aiResponse.value = null
                        _isAiSearching.value = false
                        _isAiThinking.value = false
                        return@collect
                    }
                    
                    performLocalSearch(query)
                    if (query.length > 2 && offlineState.value != OfflineState.OFFLINE) {
                        performPowerfulSmartSearch(query)
                    } else {
                        _aiSuggestedRoutes.value = emptyList()
                        _isAiSearching.value = false
                        _isAiThinking.value = false
                    }
                }
        }
    }

    private fun performLocalSearch(query: String) {
        val q = query.trim().lowercase()
        // Here we search across all categories to give broad local hits
        categories.value.flatMap { it.items }.filter { tool ->
            context.getString(tool.titleRes).lowercase().contains(q) || 
            context.getString(tool.descriptionRes).lowercase().contains(q)
        }
    }

    private fun performPowerfulSmartSearch(query: String) {
        if (offlineState.value == OfflineState.OFFLINE || query.isBlank()) {
            _isAiSearching.value = false
            _isAiThinking.value = false
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isAiSearching.value = true
            _isAiThinking.value = true
            _aiResponse.value = null
            
            try {
                val currentCategories = categories.value
                if (currentCategories.isEmpty()) {
                    _isAiSearching.value = false
                    _isAiThinking.value = false
                    return@launch
                }

                val toolContext = currentCategories.flatMap { it.items }
                    .joinToString("\n") { "[TOOL] ${context.getString(it.titleRes)}: ${context.getString(it.descriptionRes)} (Route: ${it.route})" }

                val notes = noteDao.getAllNotes().first().take(10)
                val notesContext = if (notes.isNotEmpty()) {
                    "\nUSER'S RECENT NOTES:\n" +
                            notes.joinToString("\n") { "[NOTE] ${it.title}: ${it.content.take(100)}" }
                } else ""

                val tasks = taskDao.getActiveTasks().first().take(10)
                val tasksContext = if (tasks.isNotEmpty()) {
                    "\nUSER'S ACTIVE TASKS:\n" +
                            tasks.joinToString("\n") { "[TASK] ${it.title}" }
                } else ""

                val prompt = """
                    You are the 'Toolz Intelligence Engine'.
                    
                    Phase 1: Direct Tool Match
                    Match user intent to the most relevant tools.
                    USER QUERY: "$query"
                    $toolContext
                    $notesContext
                    $tasksContext
                    Return ONLY a comma-separated list of the TOP 3 most relevant tool routes.
                    If no tool is relevant, respond with "NONE".
                """.trimIndent()

                var fullResponse = ""
                aiRepository.getChatResponse(prompt, emptyList(), null).collect { result ->
                    result.onSuccess { chunk ->
                        fullResponse += chunk.text
                    }.onFailure {
                        triggerConversationalFallback(query)
                    }
                }

                val response = fullResponse.trim().removeSurrounding("\"").removeSurrounding("'")
                if (response != "NONE" && response.isNotBlank()) {
                    val routes = response.split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() && (it.contains("_") || it.contains("/")) }
                    
                    if (routes.isNotEmpty()) {
                        _aiSuggestedRoutes.value = routes
                    } else {
                        triggerConversationalFallback(query)
                    }
                } else {
                    triggerConversationalFallback(query)
                }
            } catch (_: Exception) {
                _aiSuggestedRoutes.value = emptyList()
            } finally {
                _isAiThinking.value = false
                _isAiSearching.value = false
            }
        }
    }

    private suspend fun triggerConversationalFallback(query: String) {
        _aiSuggestedRoutes.value = emptyList()
        if (settingsRepository.aiSearchChatEnabled.first()) {
            performConversationalSearch(query)
        }
    }

    private suspend fun performConversationalSearch(query: String) {
        val prompt = "You are Toolz Assistant. The user is searching for something that doesn't match a tool. Answer VERY BRIEFLY (max 2 sentences), stay concise, and use Markdown for formatting: \"$query\""
        
        try {
            aiRepository.getChatResponse(prompt, emptyList(), null).collect { result ->
                result.onSuccess { chunk ->
                    _aiResponse.value = (_aiResponse.value ?: "") + chunk.text
                }
            }
        } catch (_: Exception) {
            // Error handled by finally in caller or just ignored
        }
    }

    fun transferToAiAssistant(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val query = _searchQuery.value
            val response = _aiResponse.value ?: return@launch
            
            val chat = com.frerox.toolz.data.ai.AiChat(title = query.take(30))
            val chatId = aiDao.insertChat(chat).toInt()
            
            aiDao.insertMessage(com.frerox.toolz.data.ai.AiMessage(
                chatId = chatId,
                text = query,
                isUser = true
            ))
            
            aiDao.insertMessage(com.frerox.toolz.data.ai.AiMessage(
                chatId = chatId,
                text = response,
                isUser = false
            ))
            
            onComplete(chatId)
        }
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun togglePinnedTool(route: String) {
        viewModelScope.launch { settingsRepository.togglePinnedTool(route) }
    }

    fun addRecentTool(route: String) {
        viewModelScope.launch { settingsRepository.addRecentTool(route) }
    }

    fun toggleOfflineMode(enabled: Boolean) {
        viewModelScope.launch { offlineManager.setOfflineMode(enabled) }
    }

    fun dismissUpdate() {
        viewModelScope.launch { settingsRepository.setAvailableUpdate(null, null, null) }
    }

    fun togglePerformanceMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPerformanceMode(enabled) }
    }

    // ── System stats ──────────────────────────────────────────────────────────

    private fun startStatsUpdate() {
        viewModelScope.launch {
            while (true) {
                updateStats()
                delay(60_000)
            }
        }
    }

    private fun updateStats() {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val available = stat.availableBytes
        val usedPct = if (total > 0) ((total - available).toFloat() / total.toFloat()) else 0f
        val availGb = available.toDouble() / (1024.0 * 1024.0 * 1024.0)

        _dashboardStats.value = DashboardStats(
            batteryLevel = level,
            isBatteryCharging = isCharging,
            storageUsedPercentage = usedPct,
            storageAvailableGb = availGb,
        )
    }

    private fun checkForUpdates() {
        viewModelScope.launch {
            val lastCheck = settingsRepository.lastUpdateCheck.first()
            if (System.currentTimeMillis() - lastCheck > 12 * 60 * 60 * 1000L) {
                updateRepository.checkForUpdates()
            }
        }
    }

    // ── Static category definitions ───────────────────────────────────────────

    fun getDashboardCategories() = listOf(
        ToolCategory(
            com.frerox.toolz.R.string.st_Dashboard_Cat_SmartFlow,
            listOf(
                ToolItem(com.frerox.toolz.R.string.st_Tool_AiAssistant, Icons.Rounded.AutoAwesome, Screen.AiAssistant.route, com.frerox.toolz.R.string.st_Tool_AiAssistant_Desc, Color(0xFF8E24AA)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_Whisper, Icons.Rounded.Lock, Screen.WhisperAuth.route, com.frerox.toolz.R.string.st_Tool_Whisper_Desc, Color(0xFF00B0FF)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_Search, Icons.Rounded.Search, Screen.Search.route, com.frerox.toolz.R.string.st_Tool_Search_Desc, Color(0xFF3F51B5)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_FocusFlow, Icons.Rounded.Toll, Screen.FocusFlow.route, com.frerox.toolz.R.string.st_Tool_FocusFlow_Desc, Color(0xFF1976D2)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_TodoList, Icons.Rounded.TaskAlt, Screen.Todo.route, com.frerox.toolz.R.string.st_Tool_TodoList_Desc, Color(0xFF43A047)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_Notepad, Icons.Rounded.Description, Screen.Notepad.route, com.frerox.toolz.R.string.st_Tool_Notepad_Desc, Color(0xFFFDD835)),
            ),
        ),
        ToolCategory(
            com.frerox.toolz.R.string.st_Dashboard_Cat_Time,
            listOf(
                ToolItem(com.frerox.toolz.R.string.st_Tool_Calendar, Icons.Rounded.CalendarMonth, Screen.Calendar.route, com.frerox.toolz.R.string.st_Tool_Calendar_Desc, Color(0xFF1E88E5)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_Timer, Icons.Rounded.Timer, Screen.Timer.route, com.frerox.toolz.R.string.st_Tool_Timer_Desc, Color(0xFF43A047)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_Stopwatch, Icons.Rounded.History, Screen.Stopwatch.route, com.frerox.toolz.R.string.st_Tool_Stopwatch_Desc, Color(0xFFFB8C00)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_Pomodoro, Icons.Rounded.AvTimer, Screen.Pomodoro.route, com.frerox.toolz.R.string.st_Tool_Pomodoro_Desc, Color(0xFFFF5252)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_WorldClock, Icons.Rounded.Public, Screen.WorldClock.route, com.frerox.toolz.R.string.st_Tool_WorldClock_Desc, Color(0xFF3949AB)),
            ),
        ),
        ToolCategory(
            com.frerox.toolz.R.string.st_Dashboard_Cat_Media,
            listOf(
                ToolItem(com.frerox.toolz.R.string.st_Tool_MusicPlayer, Icons.Rounded.MusicNote, Screen.MusicPlayer.route, com.frerox.toolz.R.string.st_Tool_MusicPlayer_Desc, Color(0xFFD81B60)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_VoiceRecorder, Icons.Rounded.Mic, Screen.VoiceRecorder.route, com.frerox.toolz.R.string.st_Tool_VoiceRecorder_Desc, Color(0xFFE53935)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_FileConverter, Icons.Rounded.Transform, Screen.FileConverter.route, com.frerox.toolz.R.string.st_Tool_FileConverter_Desc, Color(0xFFFB8C00)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_SoundMeter, Icons.Rounded.GraphicEq, Screen.SoundMeter.route, com.frerox.toolz.R.string.st_Tool_SoundMeter_Desc, Color(0xFF00B0FF)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_BackgroundRemover, Icons.Rounded.Portrait, Screen.BackgroundRemover.route, com.frerox.toolz.R.string.st_Tool_BackgroundRemover_Desc, Color(0xFF673AB7)),
            ),
        ),
        ToolCategory(
            com.frerox.toolz.R.string.st_Dashboard_Cat_Utilities,
            listOf(
                ToolItem(com.frerox.toolz.R.string.st_Tool_Calculator, Icons.Rounded.Calculate, Screen.Calculator.route, com.frerox.toolz.R.string.st_Tool_Calculator_Desc, Color(0xFF00ACC1)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_UnitConverter, Icons.Rounded.SyncAlt, Screen.UnitConverter.route, com.frerox.toolz.R.string.st_Tool_UnitConverter_Desc, Color(0xFF3949AB)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_Encrypter, Icons.Rounded.EnhancedEncryption, Screen.SmartEncrypter.route, com.frerox.toolz.R.string.st_Tool_Encrypter_Desc, Color(0xFF2E7D32)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_EquationSolver, Icons.Rounded.Functions, Screen.EquationSolver.route, com.frerox.toolz.R.string.st_Tool_EquationSolver_Desc, Color(0xFF5E35B1)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_PdfReader, Icons.Rounded.PictureAsPdf, Screen.PdfReader.route, com.frerox.toolz.R.string.st_Tool_PdfReader_Desc, Color(0xFFE53935)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_TipCalc, Icons.AutoMirrored.Rounded.ReceiptLong, Screen.TipCalculator.route, com.frerox.toolz.R.string.st_Tool_TipCalc_Desc, Color(0xFFD81B60)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_Clipboard, Icons.Rounded.ContentPaste, Screen.Clipboard.route, com.frerox.toolz.R.string.st_Tool_Clipboard_Desc, Color(0xFF546E7A)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_Ruler, Icons.Rounded.Straighten, Screen.Ruler.route, com.frerox.toolz.R.string.st_Tool_Ruler_Desc, Color(0xFF6D4C41)),
            ),
        ),
        ToolCategory(
            com.frerox.toolz.R.string.st_Dashboard_Cat_Sensors,
            listOf(
                ToolItem(com.frerox.toolz.R.string.st_Tool_Scanner, Icons.Rounded.QrCodeScanner, Screen.Scanner.route, com.frerox.toolz.R.string.st_Tool_Scanner_Desc, Color(0xFF546E7A)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_QrGenerator, Icons.Rounded.QrCode, Screen.QrGenerator.route, com.frerox.toolz.R.string.st_Tool_QrGenerator_Desc, Color(0xFF26A69A)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_Flashlight, Icons.Rounded.FlashlightOn, Screen.Flashlight.route, com.frerox.toolz.R.string.st_Tool_Flashlight_Desc, Color(0xFFFFD600)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_ScreenLight, Icons.Rounded.Laptop, Screen.ScreenLight.route, com.frerox.toolz.R.string.st_Tool_ScreenLight_Desc, Color(0xFF81D4FA)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_Magnifier, Icons.Rounded.ZoomIn, Screen.Magnifier.route, com.frerox.toolz.R.string.st_Tool_Magnifier_Desc, Color(0xFF00ACC1)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_Compass, Icons.Rounded.Explore, Screen.Compass.route, com.frerox.toolz.R.string.st_Tool_Compass_Desc, Color(0xFF00897B)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_BubbleLevel, Icons.Rounded.Architecture, Screen.BubbleLevel.route, com.frerox.toolz.R.string.st_Tool_BubbleLevel_Desc, Color(0xFF7CB342)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_LightMeter, Icons.Rounded.LightMode, Screen.LightMeter.route, com.frerox.toolz.R.string.st_Tool_LightMeter_Desc, Color(0xFFFBC02D)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_Speedometer, Icons.Rounded.Speed, Screen.Speedometer.route, com.frerox.toolz.R.string.st_Tool_Speedometer_Desc, Color(0xFF1976D2)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_Altimeter, Icons.Rounded.Terrain, Screen.Altimeter.route, com.frerox.toolz.R.string.st_Tool_Altimeter_Desc, Color(0xFF795548)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_ColorPicker, Icons.Rounded.Palette, Screen.ColorPicker.route, com.frerox.toolz.R.string.st_Tool_ColorPicker_Desc, Color(0xFF6200EA)),
            ),
        ),
        ToolCategory(
            com.frerox.toolz.R.string.st_Dashboard_Cat_System,
            listOf(
                ToolItem(com.frerox.toolz.R.string.st_Tool_PasswordVault, Icons.Rounded.Security, Screen.PasswordVault.route, com.frerox.toolz.R.string.st_Tool_PasswordVault_Desc, Color(0xFF2E7D32)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_NetworkTweaks, Icons.Rounded.NetworkCheck, Screen.WifiTweaks.route, com.frerox.toolz.R.string.st_Tool_NetworkTweaks_Desc, Color(0xFF1976D2)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_RandomGen, Icons.Rounded.Key, Screen.PasswordGenerator.route, com.frerox.toolz.R.string.st_Tool_RandomGen_Desc, Color(0xFF455A64)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_DeviceInfo, Icons.Rounded.Info, Screen.DeviceInfo.route, com.frerox.toolz.R.string.st_Tool_DeviceInfo_Desc, Color(0xFF757575)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_BatteryInfo, Icons.Rounded.BatteryChargingFull, Screen.BatteryInfo.route, com.frerox.toolz.R.string.st_Tool_BatteryInfo_Desc, Color(0xFF2E7D32)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_FileCleaner, Icons.Rounded.CleaningServices, Screen.FileCleaner.route, com.frerox.toolz.R.string.st_Tool_FileCleaner_Desc, Color(0xFFD81B60)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_StepCounter, Icons.AutoMirrored.Rounded.DirectionsRun, Screen.StepCounter.route, com.frerox.toolz.R.string.st_Tool_StepCounter_Desc, Color(0xFF43A047)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_NotificationVault, Icons.Rounded.VerifiedUser, Screen.NotificationVault.route, com.frerox.toolz.R.string.st_Tool_NotificationVault_Desc, Color(0xFF3949AB)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_BmiCalc, Icons.Rounded.MonitorWeight, Screen.BmiCalculator.route, com.frerox.toolz.R.string.st_Tool_BmiCalc_Desc, Color(0xFF00ACC1)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_PeriodicTable, Icons.Rounded.Science, Screen.PeriodicTable.route, com.frerox.toolz.R.string.st_Tool_PeriodicTable_Desc, Color(0xFF5E35B1)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_Caffeinate, Icons.Rounded.Coffee, Screen.Caffeinate.route, com.frerox.toolz.R.string.st_Tool_Caffeinate_Desc, Color(0xFF6F4E37)),
                ToolItem(com.frerox.toolz.R.string.st_Tool_FlipCoin, Icons.Rounded.Casino, Screen.FlipCoin.route, com.frerox.toolz.R.string.st_Tool_FlipCoin_Desc, Color(0xFFFB8C00)),
            ),
        ),
    )
}

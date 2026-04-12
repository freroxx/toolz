package com.frerox.toolz.ui.screens.dashboard

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val aiRepository: ChatRepository,
    private val noteDao: NoteDao,
    private val taskDao: TaskDao,
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isAiSearching = MutableStateFlow(false)
    val isAiSearching = _isAiSearching.asStateFlow()

    private val _aiSuggestedRoutes = MutableStateFlow<List<String>>(emptyList())
    val aiSuggestedRoutes = _aiSuggestedRoutes.asStateFlow()

    val categories = getDashboardCategories()

    private val _localSearchResults = MutableStateFlow<List<ToolItem>>(emptyList())
    val localSearchResults = _localSearchResults.asStateFlow()

    val updateAvailableVersion = settingsRepository.updateAvailableVersion
    val updateChangelog = settingsRepository.updateChangelog
    val updateApkUrl = settingsRepository.updateApkUrl

    private var searchJob: Job? = null

    init {
        setupSearchDebounce()
        checkForUpdates()
    }

    private fun checkForUpdates() {
        viewModelScope.launch {
            val lastCheck = settingsRepository.lastUpdateCheck.first()
            if (System.currentTimeMillis() - lastCheck > 12 * 60 * 60 * 1000) {
                updateRepository.checkForUpdates()
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun setupSearchDebounce() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .collect { query ->
                    performLocalSearch(query)
                    if (query.isNotBlank() && query.length > 2) {
                        performPowerfulSmartSearch(query)
                    } else {
                        _aiSuggestedRoutes.value = emptyList()
                        _isAiSearching.value = false
                        searchJob?.cancel()
                    }
                }
        }
    }

    private fun performLocalSearch(query: String) {
        if (query.isBlank()) {
            _localSearchResults.value = emptyList()
            return
        }
        val allTools = categories.flatMap { it.items }
        _localSearchResults.value = allTools.filter { 
            it.title.contains(query, ignoreCase = true) || 
            it.description.contains(query, ignoreCase = true)
        }.take(5)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _aiSuggestedRoutes.value = emptyList()
            _localSearchResults.value = emptyList()
            searchJob?.cancel()
            _isAiSearching.value = false
        } else {
            // Immediate local search for better responsiveness
            performLocalSearch(query)
        }
    }

    fun dismissUpdate() {
        viewModelScope.launch {
            settingsRepository.setAvailableUpdate(null, null, null)
        }
    }

    fun addRecentTool(route: String) {
        viewModelScope.launch {
            settingsRepository.addRecentTool(route)
        }
    }

    private fun performPowerfulSmartSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isAiSearching.value = true
            try {
                val toolsContext = categories.flatMap { it.items }.joinToString("\n") { 
                    "- ${it.title}: ${it.description} (Route: ${it.route})" 
                }
                
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
                    You are the 'Toolz Intelligence Engine'. Match user intent to the most relevant tools.
                    USER QUERY: "$query"
                    $toolsContext
                    $notesContext
                    $tasksContext
                    Return ONLY a comma-separated list of the TOP 3 most relevant tool routes.
                    If no tool is relevant, respond with "NONE".
                """.trimIndent()

                aiRepository.getChatResponse(prompt, emptyList(), null).collect { result ->
                    result.onSuccess { response ->
                        val cleanResponse = response.trim().removeSurrounding("\"").removeSurrounding("'")
                        if (cleanResponse != "NONE" && cleanResponse.isNotBlank()) {
                            val routes = cleanResponse.split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() && (it.contains("_") || it.contains("/")) }
                            _aiSuggestedRoutes.value = routes
                        } else {
                            _aiSuggestedRoutes.value = emptyList()
                        }
                    }
                }
            } catch (e: Exception) {
                _aiSuggestedRoutes.value = emptyList()
            } finally {
                _isAiSearching.value = false
            }
        }
    }

    fun getDashboardCategories() = listOf(
        ToolCategory(
            "SMART FLOW & AI",
            listOf(
                ToolItem("Ai Assistant", Icons.Rounded.AutoAwesome, Screen.AiAssistant.route, "Gemini Flash AI", Color(0xFF8E24AA)),
                ToolItem("Search", Icons.Rounded.Search, Screen.Search.route, "Search the web", Color(0xFF3F51B5)),
                ToolItem("Focus Flow", Icons.Rounded.Toll, Screen.FocusFlow.route, "Flow insights", Color(0xFF1976D2)),
                ToolItem("Todo List", Icons.Rounded.TaskAlt, Screen.Todo.route, "Physics tasks", Color(0xFF43A047)),
                ToolItem("Notepad", Icons.Rounded.Description, Screen.Notepad.route, "Quick notes", Color(0xFFFDD835))
            )
        ),
        ToolCategory(
            "TIME & AGENDA",
            listOf(
                ToolItem("Calendar", Icons.Rounded.CalendarMonth, Screen.Calendar.route, "Time agenda", Color(0xFF1E88E5)),
                ToolItem("Timer", Icons.Rounded.Timer, Screen.Timer.route, "Countdown", Color(0xFF43A047)),
                ToolItem("Stopwatch", Icons.Rounded.History, Screen.Stopwatch.route, "Laps", Color(0xFFFB8C00)),
                ToolItem("Pomodoro", Icons.Rounded.AvTimer, Screen.Pomodoro.route, "Deep focus", Color(0xFFFF5252)),
                ToolItem("World Clock", Icons.Rounded.Public, Screen.WorldClock.route, "Global time", Color(0xFF3949AB))
            )
        ),
        ToolCategory(
            "MEDIA & AUDIO",
            listOf(
                ToolItem("Music Player", Icons.Rounded.MusicNote, Screen.MusicPlayer.route, "Audio library", Color(0xFFD81B60)),
                ToolItem("Voice Recorder", Icons.Rounded.Mic, Screen.VoiceRecorder.route, "Audio memo", Color(0xFFE53935)),
                ToolItem("File Converter", Icons.Rounded.Transform, Screen.FileConverter.route, "Media transform", Color(0xFFFB8C00)),
                ToolItem("Sound Meter", Icons.Rounded.GraphicEq, Screen.SoundMeter.route, "Noise DB", Color(0xFF00B0FF))
            )
        ),
        ToolCategory(
            "UTILITIES & MATH",
            listOf(
                ToolItem("Calculator", Icons.Rounded.Calculate, Screen.Calculator.route, "Standard math", Color(0xFF00ACC1)),
                ToolItem("Unit Converter", Icons.Rounded.SyncAlt, Screen.UnitConverter.route, "Instant swap", Color(0xFF3949AB)),
                ToolItem("Equation Solver", Icons.Rounded.Functions, Screen.EquationSolver.route, "Solve math", Color(0xFF5E35B1)),
                ToolItem("PDF Reader", Icons.Rounded.PictureAsPdf, Screen.PdfReader.route, "View docs", Color(0xFFE53935)),
                ToolItem("Tip Calc", Icons.AutoMirrored.Rounded.ReceiptLong, Screen.TipCalculator.route, "Split bills", Color(0xFFD81B60)),
                ToolItem("Clipboard", Icons.Rounded.ContentPaste, Screen.Clipboard.route, "History", Color(0xFF546E7A)),
                ToolItem("Ruler", Icons.Rounded.Straighten, Screen.Ruler.route, "Measure", Color(0xFF6D4C41))
            )
        ),
        ToolCategory(
            "SENSORS & VISION",
            listOf(
                ToolItem("Scanner", Icons.Rounded.QrCodeScanner, Screen.Scanner.route, "QR / Barcode", Color(0xFF546E7A)),
                ToolItem("Flashlight", Icons.Rounded.FlashlightOn, Screen.Flashlight.route, "Torch tools", Color(0xFFFFD600)),
                ToolItem("Screen Light", Icons.Rounded.Laptop, Screen.ScreenLight.route, "Bright display", Color(0xFF81D4FA)),
                ToolItem("Magnifier", Icons.Rounded.ZoomIn, Screen.Magnifier.route, "Camera zoom", Color(0xFF00ACC1)),
                ToolItem("Compass", Icons.Rounded.Explore, Screen.Compass.route, "Navigation", Color(0xFF00897B)),
                ToolItem("Bubble Level", Icons.Rounded.Architecture, Screen.BubbleLevel.route, "Leveling", Color(0xFF7CB342)),
                ToolItem("Light Meter", Icons.Rounded.LightMode, Screen.LightMeter.route, "Lux measure", Color(0xFFFBC02D)),
                ToolItem("Speedometer", Icons.Rounded.Speed, Screen.Speedometer.route, "GPS Speed", Color(0xFF1976D2)),
                ToolItem("Altimeter", Icons.Rounded.Terrain, Screen.Altimeter.route, "Altitude", Color(0xFF795548)),
                ToolItem("Color Picker", Icons.Rounded.Palette, Screen.ColorPicker.route, "Identify color", Color(0xFF6200EA))
            )
        ),
        ToolCategory(
            "SYSTEM & HEALTH",
            listOf(
                ToolItem("Password Vault", Icons.Rounded.Security, Screen.PasswordVault.route, "Encrypted", Color(0xFF2E7D32)),
                ToolItem("Random Gen", Icons.Rounded.Key, Screen.PasswordGenerator.route, "Secure keys", Color(0xFF455A64)),
                ToolItem("Device Info", Icons.Rounded.Info, Screen.DeviceInfo.route, "Hardware", Color(0xFF757575)),
                ToolItem("Battery Info", Icons.Rounded.BatteryChargingFull, Screen.BatteryInfo.route, "Status", Color(0xFF2E7D32)),
                ToolItem("File Cleaner", Icons.Rounded.CleaningServices, Screen.FileCleaner.route, "Storage", Color(0xFFD81B60)),
                ToolItem("Step Counter", Icons.AutoMirrored.Rounded.DirectionsRun, Screen.StepCounter.route, "Fitness", Color(0xFF43A047)),
                ToolItem("Notification Vault", Icons.Rounded.VerifiedUser, Screen.NotificationVault.route, "Logs", Color(0xFF3949AB)),
                ToolItem("BMI Calc", Icons.Rounded.MonitorWeight, Screen.BmiCalculator.route, "Health", Color(0xFF00ACC1)),
                ToolItem("Periodic Table", Icons.Rounded.Science, Screen.PeriodicTable.route, "Elements", Color(0xFF5E35B1)),
                ToolItem("Caffeinate", Icons.Rounded.Coffee, Screen.Caffeinate.route, "Wake mode", Color(0xFF6F4E37)),
                ToolItem("Flip Coin", Icons.Rounded.Casino, Screen.FlipCoin.route, "Decide", Color(0xFFFB8C00))
            )
        )
    )
}

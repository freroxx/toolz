package com.frerox.toolz.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.ChatRepository
import com.frerox.toolz.data.notepad.NoteDao
import com.frerox.toolz.data.todo.TaskDao
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
    private val taskDao: TaskDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isAiSearching = MutableStateFlow(false)
    val isAiSearching = _isAiSearching.asStateFlow()

    private val _aiSuggestedRoutes = MutableStateFlow<List<String>>(emptyList())
    val aiSuggestedRoutes = _aiSuggestedRoutes.asStateFlow()

    // Backward compatibility for existing UI observers
    val aiSuggestedRoute = _aiSuggestedRoutes.map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var searchJob: Job? = null

    init {
        setupSearchDebounce()
    }

    @OptIn(FlowPreview::class)
    private fun setupSearchDebounce() {
        viewModelScope.launch {
            _searchQuery
                .debounce(500) // Faster response for better "automatic" feel
                .collect { query ->
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

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _aiSuggestedRoutes.value = emptyList()
            searchJob?.cancel()
            _isAiSearching.value = false
        }
    }

    private fun performPowerfulSmartSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isAiSearching.value = true
            try {
                // 1. Gather all tools with their descriptions for the AI
                val toolsContext = getCategories().flatMap { it.items }.joinToString("\n") { 
                    "- ${it.title}: ${it.description} (Route: ${it.route})" 
                }
                
                // 2. Add user context for personalized results
                val notes = noteDao.getAllNotes().first().take(10)
                val notesContext = if (notes.isNotEmpty()) {
                    "\nUSER'S RECENT NOTES (Check if user wants to find/edit these):\n" + 
                    notes.joinToString("\n") { "[NOTE] ${it.title}: ${it.content.take(100)}" }
                } else ""

                val tasks = taskDao.getActiveTasks().first().take(10)
                val tasksContext = if (tasks.isNotEmpty()) {
                    "\nUSER'S ACTIVE TASKS:\n" + 
                    tasks.joinToString("\n") { "[TASK] ${it.title}" }
                } else ""

                val prompt = """
                    You are the 'Toolz Intelligence Engine'. Your role is to match user intent to the most relevant tools or user content.
                    
                    USER QUERY: "$query"
                    
                    AVAILABLE TOOLS & CAPABILITIES:
                    $toolsContext
                    
                    $notesContext
                    $tasksContext
                    
                    INSTRUCTIONS:
                    - Analyze the query semantically.
                    - If the user wants to calculate, convert, or solve equations, prioritize those tools.
                    - If the user is looking for information they might have saved, prioritize Notepad or Todo.
                    - If the user is expressing a feeling (e.g., "I'm stressed"), suggest relaxation tools like Music Player or Pomodoro.
                    - Return a comma-separated list of the TOP 3 most relevant routes.
                    
                    RESPONSE FORMAT:
                    Respond ONLY with a comma-separated list of routes (e.g., screen_calculator, screen_notepad).
                    If no tool is relevant, respond with "NONE".
                    Strictly no explanations or other text.
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
}

package com.frerox.toolz.ui.screens.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class RandomGeneratorState(
    val isGenerating: Boolean = false,
    val randomNumber: String = "",
    val min: String = "1",
    val max: String = "100",
    val quantity: Float = 1f,
    val allowDuplicates: Boolean = true,
    val sortResults: Boolean = false,
    val decimalPlaces: Float = 0f,
    
    val password: String = "",
    val passwordLength: Float = 12f,
    val includeUpper: Boolean = true,
    val includeNumbers: Boolean = true,
    val includeSymbols: Boolean = true,
    val includeLower: Boolean = true,
    val customSymbols: String = "",
    val passwordStrength: Float = 0f,
    
    val diceCount: Float = 1f,
    val diceSides: Float = 6f,
    val diceResults: List<Int> = emptyList(),
    val totalDiceSum: Int = 0,
    
    val wordCount: Float = 1f,
    val generatedWords: String = "",
    
    val itemsToPick: String = "",
    val pickedItem: String = "",
    val shuffledList: String = "",
    val pickingIndex: Int = -1,
    
    val teamCount: Float = 2f,
    val generatedTeams: List<List<String>> = emptyList(),
    
    val generatedColor: Int = 0xFF6200EE.toInt(),
    val colorName: String = "#6200EE",
    
    val startDate: String = LocalDate.now().minusYears(1).format(DateTimeFormatter.ISO_DATE),
    val endDate: String = LocalDate.now().plusYears(1).format(DateTimeFormatter.ISO_DATE),
    val generatedDate: String = "",
    
    val generatedLetter: String = "",
    val generatedUuid: String = "",
    val loremIpsum: String = "",
    
    val coinResult: String = "", // Heads or Tails
    val decisionResult: String = "", // Yes / No / Maybe
    
    val history: List<RandomResult> = emptyList()
)

data class RandomResult(
    val type: String,
    val value: String,
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class RandomGeneratorViewModel @Inject constructor() : ViewModel() {

    private val secureRandom = SecureRandom()
    private val _uiState = MutableStateFlow(RandomGeneratorState())
    val uiState: StateFlow<RandomGeneratorState> = _uiState.asStateFlow()

    fun onMinChange(min: String) = _uiState.update { it.copy(min = min.filter { c -> c.isDigit() || c == '-' || (it.decimalPlaces > 0 && c == '.') }) }
    fun onMaxChange(max: String) = _uiState.update { it.copy(max = max.filter { c -> c.isDigit() || c == '-' || (it.decimalPlaces > 0 && c == '.') }) }
    fun onQuantityChange(q: Float) = _uiState.update { it.copy(quantity = q) }
    fun onToggleDuplicates(v: Boolean) = _uiState.update { it.copy(allowDuplicates = v) }
    fun onToggleSort(v: Boolean) = _uiState.update { it.copy(sortResults = v) }
    fun onDecimalPlacesChange(v: Float) = _uiState.update { it.copy(decimalPlaces = v) }

    fun generateNumber() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true) }
            delay(300) // Animation delay
            
            val min = _uiState.value.min.toDoubleOrNull() ?: 1.0
            val max = _uiState.value.max.toDoubleOrNull() ?: 100.0
            val qty = _uiState.value.quantity.toInt().coerceAtLeast(1)
            val decimals = _uiState.value.decimalPlaces.toInt()
            val allowDuplicates = _uiState.value.allowDuplicates
            
            if (max < min) {
                _uiState.update { it.copy(randomNumber = "Invalid Range", isGenerating = false) }
                return@launch
            }

            val results = mutableListOf<Double>()
            val rangeSize = max - min
            
            if (!allowDuplicates && decimals == 0 && qty > (max - min + 1).toInt()) {
                _uiState.update { it.copy(randomNumber = "Range too small", isGenerating = false) }
                return@launch
            }

            while (results.size < qty) {
                val num = min + (secureRandom.nextDouble() * rangeSize)
                val rounded = if (decimals == 0) num.toLong().toDouble() else String.format("%.${decimals}f", num).toDouble()
                
                if (allowDuplicates || !results.contains(rounded)) {
                    results.add(rounded)
                }
            }

            if (_uiState.value.sortResults) results.sort()
            
            val res = results.joinToString(", ") { 
                if (decimals == 0) it.toLong().toString() else String.format("%.${decimals}f", it)
            }
            
            _uiState.update { 
                it.copy(
                    randomNumber = res, 
                    isGenerating = false,
                    history = (listOf(RandomResult("Number", res)) + it.history).take(50)
                ) 
            }
        }
    }

    fun onPasswordLengthChange(l: Float) = _uiState.update { it.copy(passwordLength = l) }
    fun onToggleUpper(v: Boolean) = _uiState.update { it.copy(includeUpper = v) }
    fun onToggleLower(v: Boolean) = _uiState.update { it.copy(includeLower = v) }
    fun onToggleNumbers(v: Boolean) = _uiState.update { it.copy(includeNumbers = v) }
    fun onToggleSymbols(v: Boolean) = _uiState.update { it.copy(includeSymbols = v) }
    fun onCustomSymbolsChange(v: String) = _uiState.update { it.copy(customSymbols = v) }

    fun generatePassword() {
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val numbers = "0123456789"
        val symbols = if (_uiState.value.customSymbols.isNotEmpty()) _uiState.value.customSymbols else "!@#$%^&*()_+-=[]{}|;:,.<>?"
        
        var pool = ""
        if (_uiState.value.includeLower) pool += lower
        if (_uiState.value.includeUpper) pool += upper
        if (_uiState.value.includeNumbers) pool += numbers
        if (_uiState.value.includeSymbols) pool += symbols
        
        if (pool.isEmpty()) return
        
        val len = _uiState.value.passwordLength.toInt()
        val pwd = (1..len).map { pool[secureRandom.nextInt(pool.length)] }.joinToString("")
        
        _uiState.update { 
            it.copy(
                password = pwd, 
                passwordStrength = calculateStrength(pwd, len), 
                history = (listOf(RandomResult("Password", "****")) + it.history).take(50)
            ) 
        }
    }

    private fun calculateStrength(p: String, l: Int): Float {
        var s = 0f
        if (l >= 8) s += 0.2f
        if (l >= 16) s += 0.2f
        if (p.any { it.isDigit() }) s += 0.2f
        if (p.any { it.isUpperCase() }) s += 0.2f
        if (p.any { !it.isLetterOrDigit() }) s += 0.2f
        return s.coerceIn(0f, 1f)
    }

    fun onDiceCountChange(c: Float) = _uiState.update { it.copy(diceCount = c) }
    fun onDiceSidesChange(s: Float) = _uiState.update { it.copy(diceSides = s) }
    
    fun rollDice() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true) }
            delay(500) // Simulated roll time
            val c = _uiState.value.diceCount.toInt()
            val s = _uiState.value.diceSides.toInt()
            val r = List(c) { secureRandom.nextInt(s) + 1 }
            _uiState.update { 
                it.copy(
                    diceResults = r, 
                    totalDiceSum = r.sum(), 
                    isGenerating = false,
                    history = (listOf(RandomResult("Dice", r.joinToString(", "))) + it.history).take(50)
                ) 
            }
        }
    }

    fun onWordCountChange(c: Float) = _uiState.update { it.copy(wordCount = c) }
    fun generateWords() {
        val adjectives = listOf("Swift", "Silent", "Neon", "Crimson", "Azure", "Golden", "Shadow", "Crystal", "Electric", "Velvet", "Lunar", "Solar", "Cosmic", "Mystic", "Ancient", "Cyber", "Quantum", "Astral", "Frosted", "Blazing", "Ethereal", "Vibrant", "Obscure", "Radiant")
        val nouns = listOf("Tiger", "Dragon", "Phoenix", "Wolf", "Eagle", "Falcon", "Panther", "Fox", "Bear", "Lion", "Shark", "Whale", "Hawk", "Cobra", "Viper", "Raven", "Owl", "Stag", "Lynx", "Griffin", "Nebula", "Pulsar", "Void", "Zenith")
        val w = List(_uiState.value.wordCount.toInt()) { 
            "${adjectives[secureRandom.nextInt(adjectives.size)]} ${nouns[secureRandom.nextInt(nouns.size)]}" 
        }
        val res = w.joinToString("\n")
        _uiState.update { 
            it.copy(
                generatedWords = res, 
                history = (listOf(RandomResult("Words", w.joinToString(", "))) + it.history).take(50)
            ) 
        }
    }

    fun onItemsToPickChange(v: String) = _uiState.update { it.copy(itemsToPick = v) }
    fun pickItem() {
        val items = _uiState.value.itemsToPick.split("\n").filter { it.isNotBlank() }
        if (items.isNotEmpty()) {
            viewModelScope.launch {
                _uiState.update { it.copy(isGenerating = true) }
                // Visual shuffling effect
                repeat(10) { i ->
                    _uiState.update { it.copy(pickingIndex = secureRandom.nextInt(items.size)) }
                    delay(50L + (i * 10))
                }
                
                val p = items[secureRandom.nextInt(items.size)]
                _uiState.update { 
                    it.copy(
                        pickedItem = p, 
                        pickingIndex = -1,
                        isGenerating = false,
                        history = (listOf(RandomResult("Pick", p)) + it.history).take(50)
                    ) 
                }
            }
        }
    }

    fun shuffleList() {
        val items = _uiState.value.itemsToPick.split("\n").filter { it.isNotBlank() }.shuffled(secureRandom)
        if (items.isNotEmpty()) {
            val res = items.joinToString("\n")
            _uiState.update { 
                it.copy(
                    shuffledList = res, 
                    history = (listOf(RandomResult("Shuffle", "List Shuffled")) + it.history).take(50)
                ) 
            }
        }
    }

    fun onTeamCountChange(c: Float) = _uiState.update { it.copy(teamCount = c) }
    fun generateTeams() {
        val items = _uiState.value.itemsToPick.split("\n").filter { it.isNotBlank() }.shuffled(secureRandom)
        if (items.isEmpty()) return
        val count = _uiState.value.teamCount.toInt().coerceAtLeast(1)
        val teams = List(count) { mutableListOf<String>() }
        items.forEachIndexed { i, item -> teams[i % count].add(item) }
        _uiState.update { 
            it.copy(
                generatedTeams = teams, 
                history = (listOf(RandomResult("Teams", "Split into $count groups")) + it.history).take(50)
            ) 
        }
    }

    fun generateColor() {
        val r = secureRandom.nextInt(256)
        val g = secureRandom.nextInt(256)
        val b = secureRandom.nextInt(256)
        val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        val hex = String.format("#%02X%02X%02X", r, g, b)
        _uiState.update { 
            it.copy(
                generatedColor = color, 
                colorName = hex,
                history = (listOf(RandomResult("Color", hex)) + it.history).take(50)
            ) 
        }
    }

    fun onStartDateChange(v: String) = _uiState.update { it.copy(startDate = v) }
    fun onEndDateChange(v: String) = _uiState.update { it.copy(endDate = v) }
    fun generateDate() {
        try {
            val s = LocalDate.parse(_uiState.value.startDate)
            val e = LocalDate.parse(_uiState.value.endDate)
            if (e.isAfter(s)) {
                val d = java.time.temporal.ChronoUnit.DAYS.between(s, e)
                val res = s.plusDays((secureRandom.nextDouble() * (d + 1)).toLong()).format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                _uiState.update { 
                    it.copy(
                        generatedDate = res, 
                        history = (listOf(RandomResult("Date", res)) + it.history).take(50)
                    ) 
                }
            }
        } catch (ex: Exception) { }
    }

    fun generateLetter() {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val letter = alphabet[secureRandom.nextInt(alphabet.length)].toString()
        _uiState.update { 
            it.copy(
                generatedLetter = letter, 
                coinResult = "",
                decisionResult = "",
                generatedUuid = "",
                history = (listOf(RandomResult("Letter", letter)) + it.history).take(50)
            ) 
        }
    }

    fun generateUuid() {
        val uuid = UUID.randomUUID().toString()
        _uiState.update { 
            it.copy(
                generatedUuid = uuid, 
                coinResult = "",
                decisionResult = "",
                generatedLetter = "",
                history = (listOf(RandomResult("UUID", uuid)) + it.history).take(50)
            ) 
        }
    }

    fun generateLoremIpsum() {
        val phrases = listOf(
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
            "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
            "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi.",
            "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.",
            "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."
        )
        val text = phrases.shuffled().take(3).joinToString(" ")
        _uiState.update { 
            it.copy(
                loremIpsum = text, 
                history = (listOf(RandomResult("Lorem", "Placeholder text generated")) + it.history).take(50)
            ) 
        }
    }

    fun flipCoin() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, coinResult = "", decisionResult = "") }
            delay(600)
            val res = if (secureRandom.nextBoolean()) "HEADS" else "TAILS"
            _uiState.update { 
                it.copy(
                    coinResult = res, 
                    isGenerating = false,
                    history = (listOf(RandomResult("Coin", res)) + it.history).take(50)
                ) 
            }
        }
    }

    fun makeDecision() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, decisionResult = "", coinResult = "") }
            delay(500)
            val options = listOf("YES", "NO", "MAYBE", "DEFINITELY", "NOT NOW", "PROBABLY", "NEVER", "TRY AGAIN")
            val res = options[secureRandom.nextInt(options.size)]
            _uiState.update { 
                it.copy(
                    decisionResult = res, 
                    isGenerating = false,
                    history = (listOf(RandomResult("Decision", res)) + it.history).take(50)
                ) 
            }
        }
    }

    fun clearHistory() = _uiState.update { it.copy(history = emptyList()) }
}

package com.frerox.toolz.ui.screens.utils

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.SecureRandom
import javax.inject.Inject

data class RandomGeneratorState(
    val randomNumber: String = "",
    val min: String = "1",
    val max: String = "100",
    val password: String = "",
    val passwordLength: Float = 12f,
    val includeUpper: Boolean = true,
    val includeNumbers: Boolean = true,
    val includeSymbols: Boolean = true,
    val includeLower: Boolean = true,
    val customSymbols: String = "",
    val diceCount: Float = 1f,
    val diceSides: Float = 6f,
    val diceResults: List<Int> = emptyList(),
    val totalDiceSum: Int = 0,
    val wordCount: Float = 1f,
    val generatedWords: String = "",
    val passwordStrength: Float = 0f // 0 to 1
)

@HiltViewModel
class RandomGeneratorViewModel @Inject constructor() : ViewModel() {

    private val secureRandom = SecureRandom()
    private val _uiState = MutableStateFlow(RandomGeneratorState())
    val uiState: StateFlow<RandomGeneratorState> = _uiState.asStateFlow()

    fun onMinChange(min: String) {
        _uiState.update { it.copy(min = min.filter { c -> c.isDigit() || c == '-' }) }
    }

    fun onMaxChange(max: String) {
        _uiState.update { it.copy(max = max.filter { c -> c.isDigit() || c == '-' }) }
    }

    fun generateNumber() {
        val min = _uiState.value.min.toLongOrNull() ?: 1L
        val max = _uiState.value.max.toLongOrNull() ?: 100L
        if (max >= min) {
            val range = max - min + 1
            val num = min + (secureRandom.nextDouble() * range).toLong()
            _uiState.update { it.copy(randomNumber = num.toString()) }
        }
    }

    fun onPasswordLengthChange(length: Float) {
        _uiState.update { it.copy(passwordLength = length) }
    }

    fun onToggleUpper(value: Boolean) = _uiState.update { it.copy(includeUpper = value) }
    fun onToggleLower(value: Boolean) = _uiState.update { it.copy(includeLower = value) }
    fun onToggleNumbers(value: Boolean) = _uiState.update { it.copy(includeNumbers = value) }
    fun onToggleSymbols(value: Boolean) = _uiState.update { it.copy(includeSymbols = value) }
    fun onCustomSymbolsChange(value: String) = _uiState.update { it.copy(customSymbols = value) }

    fun generatePassword() {
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val numbers = "0123456789"
        val symbols = if (_uiState.value.customSymbols.isNotEmpty()) _uiState.value.customSymbols else "!@#$%^&*()_+-=[]{}|;:,.<>?"
        
        var charPool = ""
        val guaranteedChars = mutableListOf<Char>()
        
        if (_uiState.value.includeLower) {
            charPool += lower
            guaranteedChars.add(lower[secureRandom.nextInt(lower.length)])
        }
        if (_uiState.value.includeUpper) {
            charPool += upper
            guaranteedChars.add(upper[secureRandom.nextInt(upper.length)])
        }
        if (_uiState.value.includeNumbers) {
            charPool += numbers
            guaranteedChars.add(numbers[secureRandom.nextInt(numbers.length)])
        }
        if (_uiState.value.includeSymbols && symbols.isNotEmpty()) {
            charPool += symbols
            guaranteedChars.add(symbols[secureRandom.nextInt(symbols.length)])
        }
        
        if (charPool.isEmpty()) {
            _uiState.update { it.copy(password = "Select at least one option") }
            return
        }

        val length = _uiState.value.passwordLength.toInt()
        val pwdChars = mutableListOf<Char>()
        
        // Add guaranteed chars first
        pwdChars.addAll(guaranteedChars.take(length))
        
        // Fill the rest
        while (pwdChars.size < length) {
            pwdChars.add(charPool[secureRandom.nextInt(charPool.length)])
        }
        
        pwdChars.shuffle(secureRandom)
        
        val pwd = pwdChars.joinToString("")
        val strength = calculateStrength(pwd, length)
        _uiState.update { it.copy(password = pwd, passwordStrength = strength) }
    }

    private fun calculateStrength(password: String, length: Int): Float {
        if (password.isEmpty()) return 0f
        var score = 0f
        if (length >= 12) score += 0.3f
        if (length >= 20) score += 0.2f
        
        if (password.any { it.isDigit() }) score += 0.15f
        if (password.any { it.isUpperCase() }) score += 0.15f
        if (password.any { it.isLowerCase() }) score += 0.1f
        if (password.any { "!@#$%^&*()_+-=[]{}|;:,.<>?".contains(it) }) score += 0.1f
        
        return score.coerceIn(0f, 1f)
    }

    // Dice
    fun onDiceCountChange(count: Float) = _uiState.update { it.copy(diceCount = count) }
    fun onDiceSidesChange(sides: Float) = _uiState.update { it.copy(diceSides = sides) }
    
    fun rollDice() {
        val count = _uiState.value.diceCount.toInt()
        val sides = _uiState.value.diceSides.toInt()
        val results = List(count) { secureRandom.nextInt(sides) + 1 }
        _uiState.update { it.copy(diceResults = results, totalDiceSum = results.sum()) }
    }

    // Words
    fun onWordCountChange(count: Float) = _uiState.update { it.copy(wordCount = count) }

    fun generateWords() {
        val adjectives = listOf(
            "Swift", "Silent", "Neon", "Crimson", "Azure", "Golden", "Shadow", "Crystal", "Electric", "Velvet", 
            "Lunar", "Solar", "Cosmic", "Mystic", "Ancient", "Cyber", "Quantum", "Astral", "Frosted", "Blazing",
            "Midnight", "Ethereal", "Vibrant", "Primal", "Digital", "Kinetic", "Ethereal", "Infinite", "Stellar", "Titan"
        )
        val nouns = listOf(
            "Tiger", "Dragon", "Phoenix", "Wolf", "Eagle", "Falcon", "Panther", "Fox", "Bear", "Lion", 
            "Shark", "Whale", "Hawk", "Cobra", "Viper", "Raven", "Owl", "Stag", "Lynx", "Griffin",
            "Nebula", "Pulsar", "Zenith", "Horizon", "Vortex", "Matrix", "Cipher", "Aegis", "Oracle", "Sentry"
        )
        val count = _uiState.value.wordCount.toInt()
        val words = List(count) {
            "${adjectives[secureRandom.nextInt(adjectives.size)]} ${nouns[secureRandom.nextInt(nouns.size)]}"
        }
        _uiState.update { it.copy(generatedWords = words.joinToString("\n")) }
    }
}

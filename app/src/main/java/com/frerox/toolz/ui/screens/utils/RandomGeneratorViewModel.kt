package com.frerox.toolz.ui.screens.utils

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.random.Random

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
    val generatedWords: String = ""
)

@HiltViewModel
class RandomGeneratorViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(RandomGeneratorState())
    val uiState: StateFlow<RandomGeneratorState> = _uiState.asStateFlow()

    fun onMinChange(min: String) {
        _uiState.update { it.copy(min = min.filter { c -> c.isDigit() }) }
    }

    fun onMaxChange(max: String) {
        _uiState.update { it.copy(max = max.filter { c -> c.isDigit() }) }
    }

    fun generateNumber() {
        val min = _uiState.value.min.toIntOrNull() ?: 1
        val max = _uiState.value.max.toIntOrNull() ?: 100
        if (max >= min) {
            val num = Random.nextInt(min, max + 1)
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
        val symbols = if (_uiState.value.customSymbols.isNotEmpty()) _uiState.value.customSymbols else "!@#$%^&*"
        
        var charPool = ""
        val guaranteedChars = mutableListOf<Char>()
        
        if (_uiState.value.includeLower) {
            charPool += lower
            guaranteedChars.add(lower[Random.nextInt(lower.length)])
        }
        if (_uiState.value.includeUpper) {
            charPool += upper
            guaranteedChars.add(upper[Random.nextInt(upper.length)])
        }
        if (_uiState.value.includeNumbers) {
            charPool += numbers
            guaranteedChars.add(numbers[Random.nextInt(numbers.length)])
        }
        if (_uiState.value.includeSymbols && symbols.isNotEmpty()) {
            charPool += symbols
            guaranteedChars.add(symbols[Random.nextInt(symbols.length)])
        }
        
        if (charPool.isEmpty()) {
            _uiState.update { it.copy(password = "Select at least one option") }
            return
        }

        val length = _uiState.value.passwordLength.toInt()
        val pwdChars = mutableListOf<Char>()
        
        for (i in 0 until length) {
            if (i < guaranteedChars.size) {
                pwdChars.add(guaranteedChars[i])
            } else {
                pwdChars.add(charPool[Random.nextInt(charPool.length)])
            }
        }
        pwdChars.shuffle()
        
        val pwd = pwdChars.joinToString("")
        _uiState.update { it.copy(password = pwd) }
    }

    // Dice
    fun onDiceCountChange(count: Float) = _uiState.update { it.copy(diceCount = count) }
    fun onDiceSidesChange(sides: Float) = _uiState.update { it.copy(diceSides = sides) }
    
    fun rollDice() {
        val count = _uiState.value.diceCount.toInt()
        val sides = _uiState.value.diceSides.toInt()
        val results = List(count) { Random.nextInt(1, sides + 1) }
        _uiState.update { it.copy(diceResults = results, totalDiceSum = results.sum()) }
    }

    // Words
    fun onWordCountChange(count: Float) = _uiState.update { it.copy(wordCount = count) }

    fun generateWords() {
        val adjectives = listOf("Swift", "Silent", "Neon", "Crimson", "Azure", "Golden", "Shadow", "Crystal", "Electric", "Velvet", "Lunar", "Solar", "Cosmic", "Mystic", "Ancient", "Cyber", "Quantum", "Astral", "Frosted", "Blazing")
        val nouns = listOf("Tiger", "Dragon", "Phoenix", "Wolf", "Eagle", "Falcon", "Panther", "Fox", "Bear", "Lion", "Shark", "Whale", "Hawk", "Cobra", "Viper", "Raven", "Owl", "Stag", "Lynx", "Griffin")
        val count = _uiState.value.wordCount.toInt()
        val words = List(count) {
            "${adjectives.random()} ${nouns.random()}"
        }
        _uiState.update { it.copy(generatedWords = words.joinToString("\n")) }
    }
}

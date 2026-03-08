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
    val includeSymbols: Boolean = true
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
    fun onToggleNumbers(value: Boolean) = _uiState.update { it.copy(includeNumbers = value) }
    fun onToggleSymbols(value: Boolean) = _uiState.update { it.copy(includeSymbols = value) }

    fun generatePassword() {
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val numbers = "0123456789"
        val symbols = "!@#$%^&*()_+-=[]{}|;:,.<>?"
        
        var charPool = lower
        if (_uiState.value.includeUpper) charPool += upper
        if (_uiState.value.includeNumbers) charPool += numbers
        if (_uiState.value.includeSymbols) charPool += symbols
        
        val length = _uiState.value.passwordLength.toInt()
        val pwd = (1..length)
            .map { charPool[Random.nextInt(charPool.length)] }
            .joinToString("")
        
        _uiState.update { it.copy(password = pwd) }
    }
}

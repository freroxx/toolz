package com.frerox.toolz.ui.screens.math

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.*

data class TipState(
    val billAmount: String = "",
    val tipPercentage: Float = 15f,
    val customTip: String = "",
    val splitCount: Int = 1,
    val totalTip: Double = 0.0,
    val totalPerPerson: Double = 0.0,
    val currencySymbol: String = "$",
    val currencyCode: String = "USD",
    val isAiDetecting: Boolean = false
)

@HiltViewModel
class TipCalculatorViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TipState())
    val uiState: StateFlow<TipState> = _uiState.asStateFlow()

    init {
        detectCurrencyWithAi()
    }

    fun onBillChange(amount: String) {
        _uiState.update { it.copy(billAmount = amount.filter { c -> c.isDigit() || c == '.' }) }
        calculate()
    }

    fun onTipChange(percentage: Float) {
        _uiState.update { it.copy(tipPercentage = percentage, customTip = "") }
        calculate()
    }

    fun onCustomTipChange(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update { it.copy(customTip = filtered) }
        calculate()
    }

    fun onSplitChange(count: Int) {
        if (count >= 1) {
            _uiState.update { it.copy(splitCount = count) }
            calculate()
        }
    }

    fun onCurrencyChange(code: String, symbol: String) {
        _uiState.update { it.copy(currencyCode = code, currencySymbol = symbol) }
    }

    private fun detectCurrencyWithAi() {
        val locale = Locale.getDefault()
        val country = locale.displayCountry
        val language = locale.displayLanguage
        
        viewModelScope.launch {
            _uiState.update { it.copy(isAiDetecting = true) }
            val prompt = "Based on the device location (Country: $country, Language: $language), what is the official currency code (ISO 4217) and symbol? Respond ONLY in this JSON format: {\"code\": \"USD\", \"symbol\": \"$\"}"
            
            chatRepository.getChatResponse(prompt, emptyList(), modelOverride = "llama-3.3-70b-versatile")
                .collect { result ->
                    result.onSuccess { response ->
                        try {
                            // Simple manual parse if needed, but let's assume it returns clean JSON as requested
                            val json = response.trim()
                            val code = json.substringAfter("\"code\":").substringAfter("\"").substringBefore("\"")
                            val symbol = json.substringAfter("\"symbol\":").substringAfter("\"").substringBefore("\"")
                            if (code.length == 3) {
                                _uiState.update { it.copy(currencyCode = code, currencySymbol = symbol, isAiDetecting = false) }
                            }
                        } catch (e: Exception) {
                            _uiState.update { it.copy(isAiDetecting = false) }
                        }
                    }.onFailure {
                        _uiState.update { it.copy(isAiDetecting = false) }
                    }
                }
        }
    }

    private fun calculate() {
        val bill = _uiState.value.billAmount.toDoubleOrNull() ?: 0.0
        val tipPercent = if (_uiState.value.customTip.isNotEmpty()) {
            _uiState.value.customTip.toDoubleOrNull() ?: 0.0
        } else {
            _uiState.value.tipPercentage.toDouble()
        }
        
        val tip = bill * (tipPercent / 100.0)
        val total = bill + tip
        val perPerson = total / _uiState.value.splitCount
        
        _uiState.update { it.copy(totalTip = tip, totalPerPerson = perPerson) }
    }
}

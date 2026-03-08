package com.frerox.toolz.ui.screens.math

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class TipState(
    val billAmount: String = "",
    val tipPercentage: Float = 15f,
    val splitCount: Int = 1,
    val totalTip: Double = 0.0,
    val totalPerPerson: Double = 0.0
)

@HiltViewModel
class TipCalculatorViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(TipState())
    val uiState: StateFlow<TipState> = _uiState.asStateFlow()

    fun onBillChange(amount: String) {
        _uiState.update { it.copy(billAmount = amount.filter { c -> c.isDigit() || c == '.' }) }
        calculate()
    }

    fun onTipChange(percentage: Float) {
        _uiState.update { it.copy(tipPercentage = percentage) }
        calculate()
    }

    fun onSplitChange(count: Int) {
        if (count >= 1) {
            _uiState.update { it.copy(splitCount = count) }
            calculate()
        }
    }

    private fun calculate() {
        val bill = _uiState.value.billAmount.toDoubleOrNull() ?: 0.0
        val tip = bill * (_uiState.value.tipPercentage / 100.0)
        val total = bill + tip
        val perPerson = total / _uiState.value.splitCount
        
        _uiState.update { it.copy(totalTip = tip, totalPerPerson = perPerson) }
    }
}

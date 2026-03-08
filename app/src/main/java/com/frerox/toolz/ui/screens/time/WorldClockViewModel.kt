package com.frerox.toolz.ui.screens.time

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class WorldClockItem(
    val cityName: String,
    val zoneId: String,
    val currentTime: String,
    val date: String
)

@HiltViewModel
class WorldClockViewModel @Inject constructor() : ViewModel() {

    private val selectedZones = listOf(
        "UTC", "America/New_York", "Europe/London", "Asia/Tokyo", "Australia/Sydney", "Europe/Paris", "Asia/Dubai"
    )

    private val _clocks = MutableStateFlow<List<WorldClockItem>>(emptyList())
    val clocks: StateFlow<List<WorldClockItem>> = _clocks.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                updateClocks()
                delay(1000)
            }
        }
    }

    private fun updateClocks() {
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
        
        val items = selectedZones.map { zone ->
            val now = ZonedDateTime.now(ZoneId.of(zone))
            WorldClockItem(
                cityName = zone.substringAfter("/").replace("_", " "),
                zoneId = zone,
                currentTime = now.format(formatter),
                date = now.format(dateFormatter)
            )
        }
        _clocks.value = items
    }
}

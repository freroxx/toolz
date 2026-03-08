package com.frerox.toolz.ui.screens.time

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
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
    val date: String,
    val isLocal: Boolean = false
)

@HiltViewModel
class WorldClockViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _clocks = MutableStateFlow<List<WorldClockItem>>(emptyList())
    val clocks: StateFlow<List<WorldClockItem>> = _clocks.asStateFlow()

    val availableZones = ZoneId.getAvailableZoneIds().toList().sorted()

    init {
        viewModelScope.launch {
            repository.worldClockZones.collectLatest { zones ->
                while (true) {
                    updateClocks(zones)
                    delay(1000)
                }
            }
        }
    }

    private fun updateClocks(zones: Set<String>) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
        
        val localZone = ZoneId.systemDefault()
        val items = mutableListOf<WorldClockItem>()
        
        // Always add local time first
        val nowLocal = ZonedDateTime.now(localZone)
        items.add(WorldClockItem(
            cityName = "Current Location",
            zoneId = localZone.id,
            currentTime = nowLocal.format(formatter),
            date = nowLocal.format(dateFormatter),
            isLocal = true
        ))

        zones.filter { it != localZone.id }.forEach { zoneId ->
            try {
                val now = ZonedDateTime.now(ZoneId.of(zoneId))
                items.add(WorldClockItem(
                    cityName = zoneId.substringAfter("/").replace("_", " "),
                    zoneId = zoneId,
                    currentTime = now.format(formatter),
                    date = now.format(dateFormatter),
                    isLocal = false
                ))
            } catch (e: Exception) {
                // Skip invalid zones
            }
        }
        _clocks.value = items
    }

    fun addZone(zoneId: String) {
        viewModelScope.launch {
            repository.addWorldClockZone(zoneId)
        }
    }

    fun removeZone(zoneId: String) {
        viewModelScope.launch {
            repository.removeWorldClockZone(zoneId)
        }
    }
}

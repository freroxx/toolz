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
    val isLocal: Boolean = false,
    val offset: String,
    val isNight: Boolean
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
        val localZone = ZoneId.systemDefault()
        val items = mutableListOf<WorldClockItem>()
        
        // Always add local time first
        val nowLocal = ZonedDateTime.now(localZone)
        items.add(createClockItem("Current Location", localZone.id, nowLocal, true))

        zones.filter { it != localZone.id }.forEach { zoneId ->
            try {
                val now = ZonedDateTime.now(ZoneId.of(zoneId))
                items.add(createClockItem(
                    zoneId.substringAfter("/").replace("_", " "),
                    zoneId,
                    now,
                    false
                ))
            } catch (e: Exception) {
                // Skip invalid zones
            }
        }
        _clocks.value = items
    }

    private fun createClockItem(name: String, zoneId: String, dateTime: ZonedDateTime, isLocal: Boolean): WorldClockItem {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
        
        val hour = dateTime.hour
        val isNight = hour < 6 || hour >= 18
        
        val localOffset = ZonedDateTime.now(ZoneId.systemDefault()).offset
        val targetOffset = dateTime.offset
        val secondsDiff = targetOffset.totalSeconds - localOffset.totalSeconds
        val hoursDiff = secondsDiff / 3600
        val minsDiff = (secondsDiff % 3600) / 60
        
        val offsetText = when {
            isLocal -> "Local Time"
            hoursDiff == 0 && minsDiff == 0 -> "Same as local"
            else -> {
                val sign = if (secondsDiff >= 0) "+" else "-"
                val h = Math.abs(hoursDiff)
                val m = Math.abs(minsDiff)
                if (m == 0) "$sign${h}h" else "$sign${h}h ${m}m"
            }
        }

        return WorldClockItem(
            cityName = name,
            zoneId = zoneId,
            currentTime = dateTime.format(formatter),
            date = dateTime.format(dateFormatter),
            isLocal = isLocal,
            offset = offsetText,
            isNight = isNight
        )
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

package com.frerox.toolz.ui.screens.time

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

data class WorldClockLocation(
    val city: String,
    val country: String,
    val zoneId: String,
    val latitude: Double,
    val longitude: Double,
    val priority: Int = 0,
) {
    val label: String = "$city, $country"
    val searchable: String = "$city $country $zoneId".lowercase(Locale.ROOT)
}

data class WorldClockItem(
    val cityName: String,
    val country: String,
    val zoneId: String,
    val currentTime: String,
    val seconds: String,
    val date: String,
    val isLocal: Boolean = false,
    val offset: String,
    val utcOffset: String,
    val timeShift: String,
    val isNight: Boolean,
    val progressOfDay: Float,
    val latitude: Double?,
    val longitude: Double?,
)

data class WorldClockSelection(
    val location: WorldClockLocation,
    val time: String,
    val seconds: String,
    val date: String,
    val offset: String,
    val utcOffset: String,
    val timeShift: String,
    val isNight: Boolean,
    val progressOfDay: Float,
    val saved: Boolean,
)

data class WorldClockUiState(
    val clocks: List<WorldClockItem> = emptyList(),
    val selected: WorldClockSelection? = null,
    val searchQuery: String = "",
    val searchResults: List<WorldClockLocation> = emptyList(),
    val highlightedZones: Set<String> = emptySet(),
    val userLatLon: Pair<Double, Double>? = null,
    val mapMode: MapMode = MapMode.NORMAL,
    val locationGranted: Boolean = false,
)

@HiltViewModel
class WorldClockViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorldClockUiState())
    val uiState: StateFlow<WorldClockUiState> = _uiState.asStateFlow()

    val locations: List<WorldClockLocation> = worldClockLocations
    val availableZones: List<String> = locations.map { it.zoneId }.distinct().sorted()

    private var savedZones: Set<String> = emptySet()
    private var tick: ZonedDateTime = ZonedDateTime.now()

    init {
        _uiState.update { it.copy(searchResults = locations.sortedByDescending(WorldClockLocation::priority).take(8)) }
        viewModelScope.launch {
            repository.worldClockZones.collectLatest { zones ->
                savedZones = zones
                while (true) {
                    tick = ZonedDateTime.now()
                    refreshState()
                    delay(1000)
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            val results = searchLocations(query)
            current.copy(
                searchQuery = query,
                searchResults = results,
                highlightedZones = results.map { it.zoneId }.toSet(),
            )
        }
    }

    fun selectLocation(location: WorldClockLocation) {
        _uiState.update { 
            it.copy(
                selected = createSelection(location),
                highlightedZones = setOf(location.zoneId)
            )
        }
    }

    fun selectNearest(latitude: Double, longitude: Double) {
        selectLocation(nearestLocation(latitude, longitude))
    }

    fun addSelectedZone() {
        _uiState.value.selected?.location?.zoneId?.let(::addZone)
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

    /** Toggle between NORMAL and SATELLITE map palettes. */
    fun toggleMapMode() {
        _uiState.update { it.copy(mapMode = if (it.mapMode == MapMode.NORMAL) MapMode.SATELLITE else MapMode.NORMAL) }
    }

    /** Called once the GPS permission result is known. */
    fun setLocationGranted(granted: Boolean) {
        _uiState.update { it.copy(locationGranted = granted) }
    }

    /**
     * Store the user's real GPS coordinates and auto-select the nearest timezone.
     * The composable is responsible for calling the platform location API.
     */
    fun updateUserLocation(lat: Double, lon: Double) {
        _uiState.update { it.copy(userLatLon = lat to lon) }
        selectNearest(lat, lon)
    }

    private fun refreshState() {
        _uiState.update { current ->
            val selectedLocation = current.selected?.location
            current.copy(
                clocks = buildClockItems(),
                selected = selectedLocation?.let(::createSelection),
            )
        }
    }

    private fun buildClockItems(): List<WorldClockItem> {
        val localZone = ZoneId.systemDefault()
        val localLocation = locations.firstOrNull { it.zoneId == localZone.id }
        val localNow = ZonedDateTime.now(localZone)
        val localItem = createClockItem(
            city = "Current location",
            country = localZone.id,
            zoneId = localZone.id,
            dateTime = localNow,
            isLocal = true,
            latitude = localLocation?.latitude,
            longitude = localLocation?.longitude,
        )

        val savedItems = savedZones
            .filter { it != localZone.id }
            .mapNotNull { zoneId ->
                val location = locations.firstOrNull { it.zoneId == zoneId }
                runCatching {
                    val now = ZonedDateTime.now(ZoneId.of(zoneId))
                    createClockItem(
                        city = location?.city ?: zoneId.substringAfter("/").replace("_", " "),
                        country = location?.country ?: zoneId.substringBefore("/", ""),
                        zoneId = zoneId,
                        dateTime = now,
                        isLocal = false,
                        latitude = location?.latitude,
                        longitude = location?.longitude,
                    )
                }.getOrNull()
            }

        return listOf(localItem) + savedItems.sortedBy { it.cityName }
    }

    private fun createSelection(location: WorldClockLocation): WorldClockSelection {
        val dateTime = ZonedDateTime.now(ZoneId.of(location.zoneId))
        return WorldClockSelection(
            location = location,
            time = timeFormatter.format(dateTime),
            seconds = secondsFormatter.format(dateTime),
            date = dateFormatter.format(dateTime),
            offset = relativeOffset(dateTime, false),
            utcOffset = utcOffset(dateTime),
            timeShift = timeShift(dateTime),
            isNight = dateTime.hour < 6 || dateTime.hour >= 18,
            progressOfDay = progressOfDay(dateTime),
            saved = savedZones.contains(location.zoneId),
        )
    }

    private fun createClockItem(
        city: String,
        country: String,
        zoneId: String,
        dateTime: ZonedDateTime,
        isLocal: Boolean,
        latitude: Double?,
        longitude: Double?,
    ): WorldClockItem {
        val night = dateTime.hour < 6 || dateTime.hour >= 18
        return WorldClockItem(
            cityName = city,
            country = country,
            zoneId = zoneId,
            currentTime = timeFormatter.format(dateTime),
            seconds = secondsFormatter.format(dateTime),
            date = dateFormatter.format(dateTime),
            isLocal = isLocal,
            offset = relativeOffset(dateTime, isLocal),
            utcOffset = utcOffset(dateTime),
            timeShift = timeShift(dateTime),
            isNight = night,
            progressOfDay = progressOfDay(dateTime),
            latitude = latitude,
            longitude = longitude,
        )
    }

    private fun searchLocations(query: String): List<WorldClockLocation> {
        val cleaned = query.trim().lowercase(Locale.ROOT)
        if (cleaned.isEmpty()) return locations.sortedByDescending(WorldClockLocation::priority).take(8)

        return locations
            .asSequence()
            .filter { it.searchable.contains(cleaned) }
            .sortedWith(compareByDescending<WorldClockLocation> {
                it.city.lowercase(Locale.ROOT).startsWith(cleaned)
            }.thenByDescending { it.priority }.thenBy { it.city })
            .take(18)
            .toList()
    }

    private fun nearestLocation(latitude: Double, longitude: Double): WorldClockLocation {
        val latScale = cos(Math.toRadians(latitude)).coerceAtLeast(0.35)
        return locations.minBy { location ->
            val dLat = location.latitude - latitude
            val dLon = shortestLongitudeDistance(location.longitude, longitude) * latScale
            sqrt(dLat.pow(2) + dLon.pow(2)) - (location.priority * 0.02)
        }
    }

    private fun relativeOffset(dateTime: ZonedDateTime, isLocal: Boolean): String {
        if (isLocal) return "Local time"
        val localOffset = ZonedDateTime.now(ZoneId.systemDefault()).offset.totalSeconds
        val secondsDiff = dateTime.offset.totalSeconds - localOffset
        if (secondsDiff == 0) return "Same as local"

        val sign = if (secondsDiff >= 0) "+" else "-"
        val absolute = abs(secondsDiff)
        val hours = absolute / 3600
        val minutes = (absolute % 3600) / 60
        return if (minutes == 0) "$sign${hours}h" else "$sign${hours}h ${minutes}m"
    }

    private fun utcOffset(dateTime: ZonedDateTime): String {
        val seconds = dateTime.offset.totalSeconds
        val sign = if (seconds >= 0) "+" else "-"
        val absolute = abs(seconds)
        val hours = absolute / 3600
        val minutes = (absolute % 3600) / 60
        return "UTC$sign%02d:%02d".format(hours, minutes)
    }

    private fun timeShift(dateTime: ZonedDateTime): String {
        val localDate = ZonedDateTime.now(ZoneId.systemDefault()).toLocalDate()
        val date = dateTime.toLocalDate()
        val days = Duration.between(localDate.atStartOfDay(), date.atStartOfDay()).toDays()
        return when {
            days < 0 -> "Yesterday"
            days > 0 -> "Tomorrow"
            else -> "Today"
        }
    }

    private fun progressOfDay(dateTime: ZonedDateTime): Float {
        val seconds = dateTime.toLocalTime().toSecondOfDay()
        return (seconds / 86_400f).coerceIn(0f, 1f)
    }

    private fun shortestLongitudeDistance(first: Double, second: Double): Double {
        val diff = abs(first - second)
        return if (diff > 180) 360 - diff else diff
    }

    companion object {
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val secondsFormatter = DateTimeFormatter.ofPattern("ss")
        private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

        private val worldClockLocations = listOf(
            WorldClockLocation("UTC", "Universal", "UTC", 0.0, 0.0, 100),
            WorldClockLocation("Andorra", java.util.Locale("", "AD").displayCountry, "Europe/Andorra", 42.5000, 1.5167, 30),
            WorldClockLocation("Dubai", java.util.Locale("", "AE").displayCountry, "Asia/Dubai", 25.3000, 55.3000, 30),
            WorldClockLocation("Kabul", java.util.Locale("", "AF").displayCountry, "Asia/Kabul", 34.5167, 69.2000, 30),
            WorldClockLocation("Tirane", java.util.Locale("", "AL").displayCountry, "Europe/Tirane", 41.3333, 19.8333, 30),
            WorldClockLocation("Yerevan", java.util.Locale("", "AM").displayCountry, "Asia/Yerevan", 40.1833, 44.5000, 30),
            WorldClockLocation("Casey", java.util.Locale("", "AQ").displayCountry, "Antarctica/Casey", -66.2833, 110.5167, 30),
            WorldClockLocation("Davis", java.util.Locale("", "AQ").displayCountry, "Antarctica/Davis", -68.5833, 77.9667, 30),
            WorldClockLocation("Mawson", java.util.Locale("", "AQ").displayCountry, "Antarctica/Mawson", -67.6000, 62.8833, 30),
            WorldClockLocation("Palmer", java.util.Locale("", "AQ").displayCountry, "Antarctica/Palmer", -64.8000, -64.1000, 30),
            WorldClockLocation("Rothera", java.util.Locale("", "AQ").displayCountry, "Antarctica/Rothera", -67.5667, -68.1333, 30),
            WorldClockLocation("Troll", java.util.Locale("", "AQ").displayCountry, "Antarctica/Troll", -72.0114, 2.5350, 30),
            WorldClockLocation("Vostok", java.util.Locale("", "AQ").displayCountry, "Antarctica/Vostok", -78.4000, 106.9000, 30),
            WorldClockLocation("Buenos Aires", java.util.Locale("", "AR").displayCountry, "America/Argentina/Buenos_Aires", -34.6000, -58.4500, 30),
            WorldClockLocation("Cordoba", java.util.Locale("", "AR").displayCountry, "America/Argentina/Cordoba", -31.4000, -64.1833, 30),
            WorldClockLocation("Salta", java.util.Locale("", "AR").displayCountry, "America/Argentina/Salta", -24.7833, -65.4167, 30),
            WorldClockLocation("Jujuy", java.util.Locale("", "AR").displayCountry, "America/Argentina/Jujuy", -24.1833, -65.3000, 30),
            WorldClockLocation("Tucuman", java.util.Locale("", "AR").displayCountry, "America/Argentina/Tucuman", -26.8167, -65.2167, 30),
            WorldClockLocation("Catamarca", java.util.Locale("", "AR").displayCountry, "America/Argentina/Catamarca", -28.4667, -65.7833, 30),
            WorldClockLocation("La Rioja", java.util.Locale("", "AR").displayCountry, "America/Argentina/La_Rioja", -29.4333, -66.8500, 30),
            WorldClockLocation("San Juan", java.util.Locale("", "AR").displayCountry, "America/Argentina/San_Juan", -31.5333, -68.5167, 30),
            WorldClockLocation("Mendoza", java.util.Locale("", "AR").displayCountry, "America/Argentina/Mendoza", -32.8833, -68.8167, 30),
            WorldClockLocation("San Luis", java.util.Locale("", "AR").displayCountry, "America/Argentina/San_Luis", -33.3167, -66.3500, 30),
            WorldClockLocation("Rio Gallegos", java.util.Locale("", "AR").displayCountry, "America/Argentina/Rio_Gallegos", -51.6333, -69.2167, 30),
            WorldClockLocation("Ushuaia", java.util.Locale("", "AR").displayCountry, "America/Argentina/Ushuaia", -54.8000, -68.3000, 30),
            WorldClockLocation("Pago Pago", java.util.Locale("", "AS").displayCountry, "Pacific/Pago_Pago", -14.2667, -170.7000, 30),
            WorldClockLocation("Vienna", java.util.Locale("", "AT").displayCountry, "Europe/Vienna", 48.2167, 16.3333, 30),
            WorldClockLocation("Lord Howe", java.util.Locale("", "AU").displayCountry, "Australia/Lord_Howe", -31.5500, 159.0833, 30),
            WorldClockLocation("Macquarie", java.util.Locale("", "AU").displayCountry, "Antarctica/Macquarie", -54.5000, 158.9500, 30),
            WorldClockLocation("Hobart", java.util.Locale("", "AU").displayCountry, "Australia/Hobart", -42.8833, 147.3167, 30),
            WorldClockLocation("Melbourne", java.util.Locale("", "AU").displayCountry, "Australia/Melbourne", -37.8167, 144.9667, 30),
            WorldClockLocation("Sydney", java.util.Locale("", "AU").displayCountry, "Australia/Sydney", -33.8667, 151.2167, 30),
            WorldClockLocation("Broken Hill", java.util.Locale("", "AU").displayCountry, "Australia/Broken_Hill", -31.9500, 141.4500, 30),
            WorldClockLocation("Brisbane", java.util.Locale("", "AU").displayCountry, "Australia/Brisbane", -27.4667, 153.0333, 30),
            WorldClockLocation("Lindeman", java.util.Locale("", "AU").displayCountry, "Australia/Lindeman", -20.2667, 149.0000, 30),
            WorldClockLocation("Adelaide", java.util.Locale("", "AU").displayCountry, "Australia/Adelaide", -34.9167, 138.5833, 30),
            WorldClockLocation("Darwin", java.util.Locale("", "AU").displayCountry, "Australia/Darwin", -12.4667, 130.8333, 30),
            WorldClockLocation("Perth", java.util.Locale("", "AU").displayCountry, "Australia/Perth", -31.9500, 115.8500, 30),
            WorldClockLocation("Eucla", java.util.Locale("", "AU").displayCountry, "Australia/Eucla", -31.7167, 128.8667, 30),
            WorldClockLocation("Baku", java.util.Locale("", "AZ").displayCountry, "Asia/Baku", 40.3833, 49.8500, 30),
            WorldClockLocation("Barbados", java.util.Locale("", "BB").displayCountry, "America/Barbados", 13.1000, -59.6167, 30),
            WorldClockLocation("Dhaka", java.util.Locale("", "BD").displayCountry, "Asia/Dhaka", 23.7167, 90.4167, 30),
            WorldClockLocation("Brussels", java.util.Locale("", "BE").displayCountry, "Europe/Brussels", 50.8333, 4.3333, 30),
            WorldClockLocation("Sofia", java.util.Locale("", "BG").displayCountry, "Europe/Sofia", 42.6833, 23.3167, 30),
            WorldClockLocation("Bermuda", java.util.Locale("", "BM").displayCountry, "Atlantic/Bermuda", 32.2833, -64.7667, 30),
            WorldClockLocation("La Paz", java.util.Locale("", "BO").displayCountry, "America/La_Paz", -16.5000, -68.1500, 30),
            WorldClockLocation("Noronha", java.util.Locale("", "BR").displayCountry, "America/Noronha", -3.8500, -32.4167, 30),
            WorldClockLocation("Belem", java.util.Locale("", "BR").displayCountry, "America/Belem", -1.4500, -48.4833, 30),
            WorldClockLocation("Fortaleza", java.util.Locale("", "BR").displayCountry, "America/Fortaleza", -3.7167, -38.5000, 30),
            WorldClockLocation("Recife", java.util.Locale("", "BR").displayCountry, "America/Recife", -8.0500, -34.9000, 30),
            WorldClockLocation("Araguaina", java.util.Locale("", "BR").displayCountry, "America/Araguaina", -7.2000, -48.2000, 30),
            WorldClockLocation("Maceio", java.util.Locale("", "BR").displayCountry, "America/Maceio", -9.6667, -35.7167, 30),
            WorldClockLocation("Bahia", java.util.Locale("", "BR").displayCountry, "America/Bahia", -12.9833, -38.5167, 30),
            WorldClockLocation("Sao Paulo", java.util.Locale("", "BR").displayCountry, "America/Sao_Paulo", -23.5333, -46.6167, 30),
            WorldClockLocation("Campo Grande", java.util.Locale("", "BR").displayCountry, "America/Campo_Grande", -20.4500, -54.6167, 30),
            WorldClockLocation("Cuiaba", java.util.Locale("", "BR").displayCountry, "America/Cuiaba", -15.5833, -56.0833, 30),
            WorldClockLocation("Santarem", java.util.Locale("", "BR").displayCountry, "America/Santarem", -2.4333, -54.8667, 30),
            WorldClockLocation("Porto Velho", java.util.Locale("", "BR").displayCountry, "America/Porto_Velho", -8.7667, -63.9000, 30),
            WorldClockLocation("Boa Vista", java.util.Locale("", "BR").displayCountry, "America/Boa_Vista", 2.8167, -60.6667, 30),
            WorldClockLocation("Manaus", java.util.Locale("", "BR").displayCountry, "America/Manaus", -3.1333, -60.0167, 30),
            WorldClockLocation("Eirunepe", java.util.Locale("", "BR").displayCountry, "America/Eirunepe", -6.6667, -69.8667, 30),
            WorldClockLocation("Rio Branco", java.util.Locale("", "BR").displayCountry, "America/Rio_Branco", -9.9667, -67.8000, 30),
            WorldClockLocation("Thimphu", java.util.Locale("", "BT").displayCountry, "Asia/Thimphu", 27.4667, 89.6500, 30),
            WorldClockLocation("Minsk", java.util.Locale("", "BY").displayCountry, "Europe/Minsk", 53.9000, 27.5667, 30),
            WorldClockLocation("Belize", java.util.Locale("", "BZ").displayCountry, "America/Belize", 17.5000, -88.2000, 30),
            WorldClockLocation("St Johns", java.util.Locale("", "CA").displayCountry, "America/St_Johns", 47.5667, -52.7167, 30),
            WorldClockLocation("Halifax", java.util.Locale("", "CA").displayCountry, "America/Halifax", 44.6500, -63.6000, 30),
            WorldClockLocation("Glace Bay", java.util.Locale("", "CA").displayCountry, "America/Glace_Bay", 46.2000, -59.9500, 30),
            WorldClockLocation("Moncton", java.util.Locale("", "CA").displayCountry, "America/Moncton", 46.1000, -64.7833, 30),
            WorldClockLocation("Goose Bay", java.util.Locale("", "CA").displayCountry, "America/Goose_Bay", 53.3333, -60.4167, 30),
            WorldClockLocation("Toronto", java.util.Locale("", "CA").displayCountry, "America/Toronto", 43.6500, -79.3833, 30),
            WorldClockLocation("Iqaluit", java.util.Locale("", "CA").displayCountry, "America/Iqaluit", 63.7333, -68.4667, 30),
            WorldClockLocation("Winnipeg", java.util.Locale("", "CA").displayCountry, "America/Winnipeg", 49.8833, -97.1500, 30),
            WorldClockLocation("Resolute", java.util.Locale("", "CA").displayCountry, "America/Resolute", 74.6956, -94.8292, 30),
            WorldClockLocation("Rankin Inlet", java.util.Locale("", "CA").displayCountry, "America/Rankin_Inlet", 62.8167, -92.0831, 30),
            WorldClockLocation("Regina", java.util.Locale("", "CA").displayCountry, "America/Regina", 50.4000, -104.6500, 30),
            WorldClockLocation("Swift Current", java.util.Locale("", "CA").displayCountry, "America/Swift_Current", 50.2833, -107.8333, 30),
            WorldClockLocation("Edmonton", java.util.Locale("", "CA").displayCountry, "America/Edmonton", 53.5500, -113.4667, 30),
            WorldClockLocation("Cambridge Bay", java.util.Locale("", "CA").displayCountry, "America/Cambridge_Bay", 69.1139, -105.0528, 30),
            WorldClockLocation("Inuvik", java.util.Locale("", "CA").displayCountry, "America/Inuvik", 68.3497, -133.7167, 30),
            WorldClockLocation("Vancouver", java.util.Locale("", "CA").displayCountry, "America/Vancouver", 49.2667, -123.1167, 30),
            WorldClockLocation("Dawson Creek", java.util.Locale("", "CA").displayCountry, "America/Dawson_Creek", 55.7667, -120.2333, 30),
            WorldClockLocation("Fort Nelson", java.util.Locale("", "CA").displayCountry, "America/Fort_Nelson", 58.8000, -122.7000, 30),
            WorldClockLocation("Whitehorse", java.util.Locale("", "CA").displayCountry, "America/Whitehorse", 60.7167, -135.0500, 30),
            WorldClockLocation("Dawson", java.util.Locale("", "CA").displayCountry, "America/Dawson", 64.0667, -139.4167, 30),
            WorldClockLocation("Zurich", java.util.Locale("", "CH").displayCountry, "Europe/Zurich", 47.3833, 8.5333, 30),
            WorldClockLocation("Abidjan", java.util.Locale("", "CI").displayCountry, "Africa/Abidjan", 5.3167, -4.0333, 30),
            WorldClockLocation("Rarotonga", java.util.Locale("", "CK").displayCountry, "Pacific/Rarotonga", -21.2333, -159.7667, 30),
            WorldClockLocation("Santiago", java.util.Locale("", "CL").displayCountry, "America/Santiago", -33.4500, -70.6667, 30),
            WorldClockLocation("Coyhaique", java.util.Locale("", "CL").displayCountry, "America/Coyhaique", -45.5667, -72.0667, 30),
            WorldClockLocation("Punta Arenas", java.util.Locale("", "CL").displayCountry, "America/Punta_Arenas", -53.1500, -70.9167, 30),
            WorldClockLocation("Easter", java.util.Locale("", "CL").displayCountry, "Pacific/Easter", -27.1500, -109.4333, 30),
            WorldClockLocation("Shanghai", java.util.Locale("", "CN").displayCountry, "Asia/Shanghai", 31.2333, 121.4667, 30),
            WorldClockLocation("Urumqi", java.util.Locale("", "CN").displayCountry, "Asia/Urumqi", 43.8000, 87.5833, 30),
            WorldClockLocation("Bogota", java.util.Locale("", "CO").displayCountry, "America/Bogota", 4.6000, -74.0833, 30),
            WorldClockLocation("Costa Rica", java.util.Locale("", "CR").displayCountry, "America/Costa_Rica", 9.9333, -84.0833, 30),
            WorldClockLocation("Havana", java.util.Locale("", "CU").displayCountry, "America/Havana", 23.1333, -82.3667, 30),
            WorldClockLocation("Cape Verde", java.util.Locale("", "CV").displayCountry, "Atlantic/Cape_Verde", 14.9167, -23.5167, 30),
            WorldClockLocation("Nicosia", java.util.Locale("", "CY").displayCountry, "Asia/Nicosia", 35.1667, 33.3667, 30),
            WorldClockLocation("Famagusta", java.util.Locale("", "CY").displayCountry, "Asia/Famagusta", 35.1167, 33.9500, 30),
            WorldClockLocation("Prague", java.util.Locale("", "CZ").displayCountry, "Europe/Prague", 50.0833, 14.4333, 30),
            WorldClockLocation("Berlin", java.util.Locale("", "DE").displayCountry, "Europe/Berlin", 52.5000, 13.3667, 30),
            WorldClockLocation("Santo Domingo", java.util.Locale("", "DO").displayCountry, "America/Santo_Domingo", 18.4667, -69.9000, 30),
            WorldClockLocation("Algiers", java.util.Locale("", "DZ").displayCountry, "Africa/Algiers", 36.7833, 3.0500, 30),
            WorldClockLocation("Guayaquil", java.util.Locale("", "EC").displayCountry, "America/Guayaquil", -2.1667, -79.8333, 30),
            WorldClockLocation("Galapagos", java.util.Locale("", "EC").displayCountry, "Pacific/Galapagos", -0.9000, -89.6000, 30),
            WorldClockLocation("Tallinn", java.util.Locale("", "EE").displayCountry, "Europe/Tallinn", 59.4167, 24.7500, 30),
            WorldClockLocation("Cairo", java.util.Locale("", "EG").displayCountry, "Africa/Cairo", 30.0500, 31.2500, 30),
            WorldClockLocation("El Aaiun", java.util.Locale("", "EH").displayCountry, "Africa/El_Aaiun", 27.1500, -13.2000, 30),
            WorldClockLocation("Madrid", java.util.Locale("", "ES").displayCountry, "Europe/Madrid", 40.4000, -3.6833, 30),
            WorldClockLocation("Ceuta", java.util.Locale("", "ES").displayCountry, "Africa/Ceuta", 35.8833, -5.3167, 30),
            WorldClockLocation("Canary", java.util.Locale("", "ES").displayCountry, "Atlantic/Canary", 28.1000, -15.4000, 30),
            WorldClockLocation("Helsinki", java.util.Locale("", "FI").displayCountry, "Europe/Helsinki", 60.1667, 24.9667, 30),
            WorldClockLocation("Fiji", java.util.Locale("", "FJ").displayCountry, "Pacific/Fiji", -18.1333, 178.4167, 30),
            WorldClockLocation("Stanley", java.util.Locale("", "FK").displayCountry, "Atlantic/Stanley", -51.7000, -57.8500, 30),
            WorldClockLocation("Kosrae", java.util.Locale("", "FM").displayCountry, "Pacific/Kosrae", 5.3167, 162.9833, 30),
            WorldClockLocation("Faroe", java.util.Locale("", "FO").displayCountry, "Atlantic/Faroe", 62.0167, -6.7667, 30),
            WorldClockLocation("Paris", java.util.Locale("", "FR").displayCountry, "Europe/Paris", 48.8667, 2.3333, 30),
            WorldClockLocation("London", java.util.Locale("", "GB").displayCountry, "Europe/London", 51.5083, -0.1253, 30),
            WorldClockLocation("Tbilisi", java.util.Locale("", "GE").displayCountry, "Asia/Tbilisi", 41.7167, 44.8167, 30),
            WorldClockLocation("Cayenne", java.util.Locale("", "GF").displayCountry, "America/Cayenne", 4.9333, -52.3333, 30),
            WorldClockLocation("Gibraltar", java.util.Locale("", "GI").displayCountry, "Europe/Gibraltar", 36.1333, -5.3500, 30),
            WorldClockLocation("Nuuk", java.util.Locale("", "GL").displayCountry, "America/Nuuk", 64.1833, -51.7333, 30),
            WorldClockLocation("Danmarkshavn", java.util.Locale("", "GL").displayCountry, "America/Danmarkshavn", 76.7667, -18.6667, 30),
            WorldClockLocation("Scoresbysund", java.util.Locale("", "GL").displayCountry, "America/Scoresbysund", 70.4833, -21.9667, 30),
            WorldClockLocation("Thule", java.util.Locale("", "GL").displayCountry, "America/Thule", 76.5667, -68.7833, 30),
            WorldClockLocation("Athens", java.util.Locale("", "GR").displayCountry, "Europe/Athens", 37.9667, 23.7167, 30),
            WorldClockLocation("South Georgia", java.util.Locale("", "GS").displayCountry, "Atlantic/South_Georgia", -54.2667, -36.5333, 30),
            WorldClockLocation("Guatemala", java.util.Locale("", "GT").displayCountry, "America/Guatemala", 14.6333, -90.5167, 30),
            WorldClockLocation("Guam", java.util.Locale("", "GU").displayCountry, "Pacific/Guam", 13.4667, 144.7500, 30),
            WorldClockLocation("Bissau", java.util.Locale("", "GW").displayCountry, "Africa/Bissau", 11.8500, -15.5833, 30),
            WorldClockLocation("Guyana", java.util.Locale("", "GY").displayCountry, "America/Guyana", 6.8000, -58.1667, 30),
            WorldClockLocation("Hong Kong", java.util.Locale("", "HK").displayCountry, "Asia/Hong_Kong", 22.2833, 114.1500, 30),
            WorldClockLocation("Tegucigalpa", java.util.Locale("", "HN").displayCountry, "America/Tegucigalpa", 14.1000, -87.2167, 30),
            WorldClockLocation("Port-au-Prince", java.util.Locale("", "HT").displayCountry, "America/Port-au-Prince", 18.5333, -72.3333, 30),
            WorldClockLocation("Budapest", java.util.Locale("", "HU").displayCountry, "Europe/Budapest", 47.5000, 19.0833, 30),
            WorldClockLocation("Jakarta", java.util.Locale("", "ID").displayCountry, "Asia/Jakarta", -6.1667, 106.8000, 30),
            WorldClockLocation("Pontianak", java.util.Locale("", "ID").displayCountry, "Asia/Pontianak", -0.0333, 109.3333, 30),
            WorldClockLocation("Makassar", java.util.Locale("", "ID").displayCountry, "Asia/Makassar", -5.1167, 119.4000, 30),
            WorldClockLocation("Jayapura", java.util.Locale("", "ID").displayCountry, "Asia/Jayapura", -2.5333, 140.7000, 30),
            WorldClockLocation("Dublin", java.util.Locale("", "IE").displayCountry, "Europe/Dublin", 53.3333, -6.2500, 30),
            WorldClockLocation("Jerusalem", java.util.Locale("", "IL").displayCountry, "Asia/Jerusalem", 31.7806, 35.2239, 30),
            WorldClockLocation("Kolkata", java.util.Locale("", "IN").displayCountry, "Asia/Kolkata", 22.5333, 88.3667, 30),
            WorldClockLocation("Chagos", java.util.Locale("", "IO").displayCountry, "Indian/Chagos", -7.3333, 72.4167, 30),
            WorldClockLocation("Baghdad", java.util.Locale("", "IQ").displayCountry, "Asia/Baghdad", 33.3500, 44.4167, 30),
            WorldClockLocation("Tehran", java.util.Locale("", "IR").displayCountry, "Asia/Tehran", 35.6667, 51.4333, 30),
            WorldClockLocation("Rome", java.util.Locale("", "IT").displayCountry, "Europe/Rome", 41.9000, 12.4833, 30),
            WorldClockLocation("Jamaica", java.util.Locale("", "JM").displayCountry, "America/Jamaica", 17.9681, -76.7933, 30),
            WorldClockLocation("Amman", java.util.Locale("", "JO").displayCountry, "Asia/Amman", 31.9500, 35.9333, 30),
            WorldClockLocation("Tokyo", java.util.Locale("", "JP").displayCountry, "Asia/Tokyo", 35.6544, 139.7447, 30),
            WorldClockLocation("Nairobi", java.util.Locale("", "KE").displayCountry, "Africa/Nairobi", -1.2833, 36.8167, 30),
            WorldClockLocation("Bishkek", java.util.Locale("", "KG").displayCountry, "Asia/Bishkek", 42.9000, 74.6000, 30),
            WorldClockLocation("Tarawa", java.util.Locale("", "KI").displayCountry, "Pacific/Tarawa", 1.4167, 173.0000, 30),
            WorldClockLocation("Kanton", java.util.Locale("", "KI").displayCountry, "Pacific/Kanton", -2.7833, -171.7167, 30),
            WorldClockLocation("Kiritimati", java.util.Locale("", "KI").displayCountry, "Pacific/Kiritimati", 1.8667, -157.3333, 30),
            WorldClockLocation("Pyongyang", java.util.Locale("", "KP").displayCountry, "Asia/Pyongyang", 39.0167, 125.7500, 30),
            WorldClockLocation("Seoul", java.util.Locale("", "KR").displayCountry, "Asia/Seoul", 37.5500, 126.9667, 30),
            WorldClockLocation("Almaty", java.util.Locale("", "KZ").displayCountry, "Asia/Almaty", 43.2500, 76.9500, 30),
            WorldClockLocation("Qyzylorda", java.util.Locale("", "KZ").displayCountry, "Asia/Qyzylorda", 44.8000, 65.4667, 30),
            WorldClockLocation("Qostanay", java.util.Locale("", "KZ").displayCountry, "Asia/Qostanay", 53.2000, 63.6167, 30),
            WorldClockLocation("Aqtobe", java.util.Locale("", "KZ").displayCountry, "Asia/Aqtobe", 50.2833, 57.1667, 30),
            WorldClockLocation("Aqtau", java.util.Locale("", "KZ").displayCountry, "Asia/Aqtau", 44.5167, 50.2667, 30),
            WorldClockLocation("Atyrau", java.util.Locale("", "KZ").displayCountry, "Asia/Atyrau", 47.1167, 51.9333, 30),
            WorldClockLocation("Oral", java.util.Locale("", "KZ").displayCountry, "Asia/Oral", 51.2167, 51.3500, 30),
            WorldClockLocation("Beirut", java.util.Locale("", "LB").displayCountry, "Asia/Beirut", 33.8833, 35.5000, 30),
            WorldClockLocation("Colombo", java.util.Locale("", "LK").displayCountry, "Asia/Colombo", 6.9333, 79.8500, 30),
            WorldClockLocation("Monrovia", java.util.Locale("", "LR").displayCountry, "Africa/Monrovia", 6.3000, -10.7833, 30),
            WorldClockLocation("Vilnius", java.util.Locale("", "LT").displayCountry, "Europe/Vilnius", 54.6833, 25.3167, 30),
            WorldClockLocation("Riga", java.util.Locale("", "LV").displayCountry, "Europe/Riga", 56.9500, 24.1000, 30),
            WorldClockLocation("Tripoli", java.util.Locale("", "LY").displayCountry, "Africa/Tripoli", 32.9000, 13.1833, 30),
            WorldClockLocation("Casablanca", java.util.Locale("", "MA").displayCountry, "Africa/Casablanca", 33.6500, -7.5833, 30),
            WorldClockLocation("Chisinau", java.util.Locale("", "MD").displayCountry, "Europe/Chisinau", 47.0000, 28.8333, 30),
            WorldClockLocation("Kwajalein", java.util.Locale("", "MH").displayCountry, "Pacific/Kwajalein", 9.0833, 167.3333, 30),
            WorldClockLocation("Yangon", java.util.Locale("", "MM").displayCountry, "Asia/Yangon", 16.7833, 96.1667, 30),
            WorldClockLocation("Ulaanbaatar", java.util.Locale("", "MN").displayCountry, "Asia/Ulaanbaatar", 47.9167, 106.8833, 30),
            WorldClockLocation("Hovd", java.util.Locale("", "MN").displayCountry, "Asia/Hovd", 48.0167, 91.6500, 30),
            WorldClockLocation("Macau", java.util.Locale("", "MO").displayCountry, "Asia/Macau", 22.1972, 113.5417, 30),
            WorldClockLocation("Martinique", java.util.Locale("", "MQ").displayCountry, "America/Martinique", 14.6000, -61.0833, 30),
            WorldClockLocation("Malta", java.util.Locale("", "MT").displayCountry, "Europe/Malta", 35.9000, 14.5167, 30),
            WorldClockLocation("Mauritius", java.util.Locale("", "MU").displayCountry, "Indian/Mauritius", -20.1667, 57.5000, 30),
            WorldClockLocation("Maldives", java.util.Locale("", "MV").displayCountry, "Indian/Maldives", 4.1667, 73.5000, 30),
            WorldClockLocation("Mexico City", java.util.Locale("", "MX").displayCountry, "America/Mexico_City", 19.4000, -99.1500, 30),
            WorldClockLocation("Cancun", java.util.Locale("", "MX").displayCountry, "America/Cancun", 21.0833, -86.7667, 30),
            WorldClockLocation("Merida", java.util.Locale("", "MX").displayCountry, "America/Merida", 20.9667, -89.6167, 30),
            WorldClockLocation("Monterrey", java.util.Locale("", "MX").displayCountry, "America/Monterrey", 25.6667, -100.3167, 30),
            WorldClockLocation("Matamoros", java.util.Locale("", "MX").displayCountry, "America/Matamoros", 25.8333, -97.5000, 30),
            WorldClockLocation("Chihuahua", java.util.Locale("", "MX").displayCountry, "America/Chihuahua", 28.6333, -106.0833, 30),
            WorldClockLocation("Ciudad Juarez", java.util.Locale("", "MX").displayCountry, "America/Ciudad_Juarez", 31.7333, -106.4833, 30),
            WorldClockLocation("Ojinaga", java.util.Locale("", "MX").displayCountry, "America/Ojinaga", 29.5667, -104.4167, 30),
            WorldClockLocation("Mazatlan", java.util.Locale("", "MX").displayCountry, "America/Mazatlan", 23.2167, -106.4167, 30),
            WorldClockLocation("Bahia Banderas", java.util.Locale("", "MX").displayCountry, "America/Bahia_Banderas", 20.8000, -105.2500, 30),
            WorldClockLocation("Hermosillo", java.util.Locale("", "MX").displayCountry, "America/Hermosillo", 29.0667, -110.9667, 30),
            WorldClockLocation("Tijuana", java.util.Locale("", "MX").displayCountry, "America/Tijuana", 32.5333, -117.0167, 30),
            WorldClockLocation("Kuching", java.util.Locale("", "MY").displayCountry, "Asia/Kuching", 1.5500, 110.3333, 30),
            WorldClockLocation("Maputo", java.util.Locale("", "MZ").displayCountry, "Africa/Maputo", -25.9667, 32.5833, 30),
            WorldClockLocation("Windhoek", java.util.Locale("", "NA").displayCountry, "Africa/Windhoek", -22.5667, 17.1000, 30),
            WorldClockLocation("Noumea", java.util.Locale("", "NC").displayCountry, "Pacific/Noumea", -22.2667, 166.4500, 30),
            WorldClockLocation("Norfolk", java.util.Locale("", "NF").displayCountry, "Pacific/Norfolk", -29.0500, 167.9667, 30),
            WorldClockLocation("Lagos", java.util.Locale("", "NG").displayCountry, "Africa/Lagos", 6.4500, 3.4000, 30),
            WorldClockLocation("Managua", java.util.Locale("", "NI").displayCountry, "America/Managua", 12.1500, -86.2833, 30),
            WorldClockLocation("Kathmandu", java.util.Locale("", "NP").displayCountry, "Asia/Kathmandu", 27.7167, 85.3167, 30),
            WorldClockLocation("Nauru", java.util.Locale("", "NR").displayCountry, "Pacific/Nauru", -0.5167, 166.9167, 30),
            WorldClockLocation("Niue", java.util.Locale("", "NU").displayCountry, "Pacific/Niue", -19.0167, -169.9167, 30),
            WorldClockLocation("Auckland", java.util.Locale("", "NZ").displayCountry, "Pacific/Auckland", -36.8667, 174.7667, 30),
            WorldClockLocation("Chatham", java.util.Locale("", "NZ").displayCountry, "Pacific/Chatham", -43.9500, -176.5500, 30),
            WorldClockLocation("Panama", java.util.Locale("", "PA").displayCountry, "America/Panama", 8.9667, -79.5333, 30),
            WorldClockLocation("Lima", java.util.Locale("", "PE").displayCountry, "America/Lima", -12.0500, -77.0500, 30),
            WorldClockLocation("Tahiti", java.util.Locale("", "PF").displayCountry, "Pacific/Tahiti", -17.5333, -149.5667, 30),
            WorldClockLocation("Marquesas", java.util.Locale("", "PF").displayCountry, "Pacific/Marquesas", -9.0000, -139.5000, 30),
            WorldClockLocation("Gambier", java.util.Locale("", "PF").displayCountry, "Pacific/Gambier", -23.1333, -134.9500, 30),
            WorldClockLocation("Port Moresby", java.util.Locale("", "PG").displayCountry, "Pacific/Port_Moresby", -9.5000, 147.1667, 30),
            WorldClockLocation("Bougainville", java.util.Locale("", "PG").displayCountry, "Pacific/Bougainville", -6.2167, 155.5667, 30),
            WorldClockLocation("Manila", java.util.Locale("", "PH").displayCountry, "Asia/Manila", 14.5867, 120.9678, 30),
            WorldClockLocation("Karachi", java.util.Locale("", "PK").displayCountry, "Asia/Karachi", 24.8667, 67.0500, 30),
            WorldClockLocation("Warsaw", java.util.Locale("", "PL").displayCountry, "Europe/Warsaw", 52.2500, 21.0000, 30),
            WorldClockLocation("Miquelon", java.util.Locale("", "PM").displayCountry, "America/Miquelon", 47.0500, -56.3333, 30),
            WorldClockLocation("Pitcairn", java.util.Locale("", "PN").displayCountry, "Pacific/Pitcairn", -25.0667, -130.0833, 30),
            WorldClockLocation("Puerto Rico", java.util.Locale("", "PR").displayCountry, "America/Puerto_Rico", 18.4683, -66.1061, 30),
            WorldClockLocation("Gaza", java.util.Locale("", "PS").displayCountry, "Asia/Gaza", 31.5000, 34.4667, 30),
            WorldClockLocation("Hebron", java.util.Locale("", "PS").displayCountry, "Asia/Hebron", 31.5333, 35.0950, 30),
            WorldClockLocation("Lisbon", java.util.Locale("", "PT").displayCountry, "Europe/Lisbon", 38.7167, -9.1333, 30),
            WorldClockLocation("Madeira", java.util.Locale("", "PT").displayCountry, "Atlantic/Madeira", 32.6333, -16.9000, 30),
            WorldClockLocation("Azores", java.util.Locale("", "PT").displayCountry, "Atlantic/Azores", 37.7333, -25.6667, 30),
            WorldClockLocation("Palau", java.util.Locale("", "PW").displayCountry, "Pacific/Palau", 7.3333, 134.4833, 30),
            WorldClockLocation("Asuncion", java.util.Locale("", "PY").displayCountry, "America/Asuncion", -25.2667, -57.6667, 30),
            WorldClockLocation("Qatar", java.util.Locale("", "QA").displayCountry, "Asia/Qatar", 25.2833, 51.5333, 30),
            WorldClockLocation("Bucharest", java.util.Locale("", "RO").displayCountry, "Europe/Bucharest", 44.4333, 26.1000, 30),
            WorldClockLocation("Belgrade", java.util.Locale("", "RS").displayCountry, "Europe/Belgrade", 44.8333, 20.5000, 30),
            WorldClockLocation("Kaliningrad", java.util.Locale("", "RU").displayCountry, "Europe/Kaliningrad", 54.7167, 20.5000, 30),
            WorldClockLocation("Moscow", java.util.Locale("", "RU").displayCountry, "Europe/Moscow", 55.7558, 37.6178, 30),
            WorldClockLocation("Simferopol", java.util.Locale("", "RU").displayCountry, "Europe/Simferopol", 44.9500, 34.1000, 30),
            WorldClockLocation("Kirov", java.util.Locale("", "RU").displayCountry, "Europe/Kirov", 58.6000, 49.6500, 30),
            WorldClockLocation("Volgograd", java.util.Locale("", "RU").displayCountry, "Europe/Volgograd", 48.7333, 44.4167, 30),
            WorldClockLocation("Astrakhan", java.util.Locale("", "RU").displayCountry, "Europe/Astrakhan", 46.3500, 48.0500, 30),
            WorldClockLocation("Saratov", java.util.Locale("", "RU").displayCountry, "Europe/Saratov", 51.5667, 46.0333, 30),
            WorldClockLocation("Ulyanovsk", java.util.Locale("", "RU").displayCountry, "Europe/Ulyanovsk", 54.3333, 48.4000, 30),
            WorldClockLocation("Samara", java.util.Locale("", "RU").displayCountry, "Europe/Samara", 53.2000, 50.1500, 30),
            WorldClockLocation("Yekaterinburg", java.util.Locale("", "RU").displayCountry, "Asia/Yekaterinburg", 56.8500, 60.6000, 30),
            WorldClockLocation("Omsk", java.util.Locale("", "RU").displayCountry, "Asia/Omsk", 55.0000, 73.4000, 30),
            WorldClockLocation("Novosibirsk", java.util.Locale("", "RU").displayCountry, "Asia/Novosibirsk", 55.0333, 82.9167, 30),
            WorldClockLocation("Barnaul", java.util.Locale("", "RU").displayCountry, "Asia/Barnaul", 53.3667, 83.7500, 30),
            WorldClockLocation("Tomsk", java.util.Locale("", "RU").displayCountry, "Asia/Tomsk", 56.5000, 84.9667, 30),
            WorldClockLocation("Novokuznetsk", java.util.Locale("", "RU").displayCountry, "Asia/Novokuznetsk", 53.7500, 87.1167, 30),
            WorldClockLocation("Krasnoyarsk", java.util.Locale("", "RU").displayCountry, "Asia/Krasnoyarsk", 56.0167, 92.8333, 30),
            WorldClockLocation("Irkutsk", java.util.Locale("", "RU").displayCountry, "Asia/Irkutsk", 52.2667, 104.3333, 30),
            WorldClockLocation("Chita", java.util.Locale("", "RU").displayCountry, "Asia/Chita", 52.0500, 113.4667, 30),
            WorldClockLocation("Yakutsk", java.util.Locale("", "RU").displayCountry, "Asia/Yakutsk", 62.0000, 129.6667, 30),
            WorldClockLocation("Khandyga", java.util.Locale("", "RU").displayCountry, "Asia/Khandyga", 62.6564, 135.5539, 30),
            WorldClockLocation("Vladivostok", java.util.Locale("", "RU").displayCountry, "Asia/Vladivostok", 43.1667, 131.9333, 30),
            WorldClockLocation("Ust-Nera", java.util.Locale("", "RU").displayCountry, "Asia/Ust-Nera", 64.5603, 143.2267, 30),
            WorldClockLocation("Magadan", java.util.Locale("", "RU").displayCountry, "Asia/Magadan", 59.5667, 150.8000, 30),
            WorldClockLocation("Sakhalin", java.util.Locale("", "RU").displayCountry, "Asia/Sakhalin", 46.9667, 142.7000, 30),
            WorldClockLocation("Srednekolymsk", java.util.Locale("", "RU").displayCountry, "Asia/Srednekolymsk", 67.4667, 153.7167, 30),
            WorldClockLocation("Kamchatka", java.util.Locale("", "RU").displayCountry, "Asia/Kamchatka", 53.0167, 158.6500, 30),
            WorldClockLocation("Anadyr", java.util.Locale("", "RU").displayCountry, "Asia/Anadyr", 64.7500, 177.4833, 30),
            WorldClockLocation("Riyadh", java.util.Locale("", "SA").displayCountry, "Asia/Riyadh", 24.6333, 46.7167, 30),
            WorldClockLocation("Guadalcanal", java.util.Locale("", "SB").displayCountry, "Pacific/Guadalcanal", -9.5333, 160.2000, 30),
            WorldClockLocation("Khartoum", java.util.Locale("", "SD").displayCountry, "Africa/Khartoum", 15.6000, 32.5333, 30),
            WorldClockLocation("Singapore", java.util.Locale("", "SG").displayCountry, "Asia/Singapore", 1.2833, 103.8500, 30),
            WorldClockLocation("Paramaribo", java.util.Locale("", "SR").displayCountry, "America/Paramaribo", 5.8333, -55.1667, 30),
            WorldClockLocation("Juba", java.util.Locale("", "SS").displayCountry, "Africa/Juba", 4.8500, 31.6167, 30),
            WorldClockLocation("Sao Tome", java.util.Locale("", "ST").displayCountry, "Africa/Sao_Tome", 0.3333, 6.7333, 30),
            WorldClockLocation("El Salvador", java.util.Locale("", "SV").displayCountry, "America/El_Salvador", 13.7000, -89.2000, 30),
            WorldClockLocation("Damascus", java.util.Locale("", "SY").displayCountry, "Asia/Damascus", 33.5000, 36.3000, 30),
            WorldClockLocation("Grand Turk", java.util.Locale("", "TC").displayCountry, "America/Grand_Turk", 21.4667, -71.1333, 30),
            WorldClockLocation("Ndjamena", java.util.Locale("", "TD").displayCountry, "Africa/Ndjamena", 12.1167, 15.0500, 30),
            WorldClockLocation("Bangkok", java.util.Locale("", "TH").displayCountry, "Asia/Bangkok", 13.7500, 100.5167, 30),
            WorldClockLocation("Dushanbe", java.util.Locale("", "TJ").displayCountry, "Asia/Dushanbe", 38.5833, 68.8000, 30),
            WorldClockLocation("Fakaofo", java.util.Locale("", "TK").displayCountry, "Pacific/Fakaofo", -9.3667, -171.2333, 30),
            WorldClockLocation("Dili", java.util.Locale("", "TL").displayCountry, "Asia/Dili", -8.5500, 125.5833, 30),
            WorldClockLocation("Ashgabat", java.util.Locale("", "TM").displayCountry, "Asia/Ashgabat", 37.9500, 58.3833, 30),
            WorldClockLocation("Tunis", java.util.Locale("", "TN").displayCountry, "Africa/Tunis", 36.8000, 10.1833, 30),
            WorldClockLocation("Tongatapu", java.util.Locale("", "TO").displayCountry, "Pacific/Tongatapu", -21.1333, -175.2000, 30),
            WorldClockLocation("Istanbul", java.util.Locale("", "TR").displayCountry, "Europe/Istanbul", 41.0167, 28.9667, 30),
            WorldClockLocation("Taipei", java.util.Locale("", "TW").displayCountry, "Asia/Taipei", 25.0500, 121.5000, 30),
            WorldClockLocation("Kyiv", java.util.Locale("", "UA").displayCountry, "Europe/Kyiv", 50.4333, 30.5167, 30),
            WorldClockLocation("New York", java.util.Locale("", "US").displayCountry, "America/New_York", 40.7142, -74.0064, 30),
            WorldClockLocation("Detroit", java.util.Locale("", "US").displayCountry, "America/Detroit", 42.3314, -83.0458, 30),
            WorldClockLocation("Louisville", java.util.Locale("", "US").displayCountry, "America/Kentucky/Louisville", 38.2542, -85.7594, 30),
            WorldClockLocation("Monticello", java.util.Locale("", "US").displayCountry, "America/Kentucky/Monticello", 36.8297, -84.8492, 30),
            WorldClockLocation("Indianapolis", java.util.Locale("", "US").displayCountry, "America/Indiana/Indianapolis", 39.7683, -86.1581, 30),
            WorldClockLocation("Vincennes", java.util.Locale("", "US").displayCountry, "America/Indiana/Vincennes", 38.6772, -87.5286, 30),
            WorldClockLocation("Winamac", java.util.Locale("", "US").displayCountry, "America/Indiana/Winamac", 41.0514, -86.6031, 30),
            WorldClockLocation("Marengo", java.util.Locale("", "US").displayCountry, "America/Indiana/Marengo", 38.3756, -86.3447, 30),
            WorldClockLocation("Petersburg", java.util.Locale("", "US").displayCountry, "America/Indiana/Petersburg", 38.4919, -87.2786, 30),
            WorldClockLocation("Vevay", java.util.Locale("", "US").displayCountry, "America/Indiana/Vevay", 38.7478, -85.0672, 30),
            WorldClockLocation("Chicago", java.util.Locale("", "US").displayCountry, "America/Chicago", 41.8500, -87.6500, 30),
            WorldClockLocation("Tell City", java.util.Locale("", "US").displayCountry, "America/Indiana/Tell_City", 37.9531, -86.7614, 30),
            WorldClockLocation("Knox", java.util.Locale("", "US").displayCountry, "America/Indiana/Knox", 41.2958, -86.6250, 30),
            WorldClockLocation("Menominee", java.util.Locale("", "US").displayCountry, "America/Menominee", 45.1078, -87.6142, 30),
            WorldClockLocation("Center", java.util.Locale("", "US").displayCountry, "America/North_Dakota/Center", 47.1164, -101.2992, 30),
            WorldClockLocation("New Salem", java.util.Locale("", "US").displayCountry, "America/North_Dakota/New_Salem", 46.8450, -101.4108, 30),
            WorldClockLocation("Beulah", java.util.Locale("", "US").displayCountry, "America/North_Dakota/Beulah", 47.2642, -101.7778, 30),
            WorldClockLocation("Denver", java.util.Locale("", "US").displayCountry, "America/Denver", 39.7392, -104.9842, 30),
            WorldClockLocation("Boise", java.util.Locale("", "US").displayCountry, "America/Boise", 43.6136, -116.2025, 30),
            WorldClockLocation("Phoenix", java.util.Locale("", "US").displayCountry, "America/Phoenix", 33.4483, -112.0733, 30),
            WorldClockLocation("Los Angeles", java.util.Locale("", "US").displayCountry, "America/Los_Angeles", 34.0522, -118.2428, 30),
            WorldClockLocation("Anchorage", java.util.Locale("", "US").displayCountry, "America/Anchorage", 61.2181, -149.9003, 30),
            WorldClockLocation("Juneau", java.util.Locale("", "US").displayCountry, "America/Juneau", 58.3019, -134.4197, 30),
            WorldClockLocation("Sitka", java.util.Locale("", "US").displayCountry, "America/Sitka", 57.1764, -135.3019, 30),
            WorldClockLocation("Metlakatla", java.util.Locale("", "US").displayCountry, "America/Metlakatla", 55.1269, -131.5764, 30),
            WorldClockLocation("Yakutat", java.util.Locale("", "US").displayCountry, "America/Yakutat", 59.5469, -139.7272, 30),
            WorldClockLocation("Nome", java.util.Locale("", "US").displayCountry, "America/Nome", 64.5011, -165.4064, 30),
            WorldClockLocation("Adak", java.util.Locale("", "US").displayCountry, "America/Adak", 51.8800, -176.6581, 30),
            WorldClockLocation("Honolulu", java.util.Locale("", "US").displayCountry, "Pacific/Honolulu", 21.3069, -157.8583, 30),
            WorldClockLocation("Montevideo", java.util.Locale("", "UY").displayCountry, "America/Montevideo", -34.9092, -56.2125, 30),
            WorldClockLocation("Samarkand", java.util.Locale("", "UZ").displayCountry, "Asia/Samarkand", 39.6667, 66.8000, 30),
            WorldClockLocation("Tashkent", java.util.Locale("", "UZ").displayCountry, "Asia/Tashkent", 41.3333, 69.3000, 30),
            WorldClockLocation("Caracas", java.util.Locale("", "VE").displayCountry, "America/Caracas", 10.5000, -66.9333, 30),
            WorldClockLocation("Ho Chi Minh", java.util.Locale("", "VN").displayCountry, "Asia/Ho_Chi_Minh", 10.7500, 106.6667, 30),
            WorldClockLocation("Efate", java.util.Locale("", "VU").displayCountry, "Pacific/Efate", -17.6667, 168.4167, 30),
            WorldClockLocation("Apia", java.util.Locale("", "WS").displayCountry, "Pacific/Apia", -13.8333, -171.7333, 30),
            WorldClockLocation("Johannesburg", java.util.Locale("", "ZA").displayCountry, "Africa/Johannesburg", -26.2500, 28.0000, 30),
            WorldClockLocation("ACT", java.util.Locale("", "AU").displayCountry, "Australia/ACT", -33.8667, 151.2167, 30),
            WorldClockLocation("LHI", java.util.Locale("", "AU").displayCountry, "Australia/LHI", -31.5500, 159.0833, 30),
            WorldClockLocation("NSW", java.util.Locale("", "AU").displayCountry, "Australia/NSW", -33.8667, 151.2167, 30),
            WorldClockLocation("North", java.util.Locale("", "AU").displayCountry, "Australia/North", -12.4667, 130.8333, 30),
            WorldClockLocation("Queensland", java.util.Locale("", "AU").displayCountry, "Australia/Queensland", -27.4667, 153.0333, 30),
            WorldClockLocation("South", java.util.Locale("", "AU").displayCountry, "Australia/South", -34.9167, 138.5833, 30),
            WorldClockLocation("Tasmania", java.util.Locale("", "AU").displayCountry, "Australia/Tasmania", -42.8833, 147.3167, 30),
            WorldClockLocation("Victoria", java.util.Locale("", "AU").displayCountry, "Australia/Victoria", -37.8167, 144.9667, 30),
            WorldClockLocation("West", java.util.Locale("", "AU").displayCountry, "Australia/West", -31.9500, 115.8500, 30),
            WorldClockLocation("Yancowinna", java.util.Locale("", "AU").displayCountry, "Australia/Yancowinna", -31.9500, 141.4500, 30),
            WorldClockLocation("Acre", java.util.Locale("", "BR").displayCountry, "Brazil/Acre", -9.9667, -67.8000, 30),
            WorldClockLocation("DeNoronha", java.util.Locale("", "BR").displayCountry, "Brazil/DeNoronha", -3.8500, -32.4167, 30),
            WorldClockLocation("East", java.util.Locale("", "BR").displayCountry, "Brazil/East", -23.5333, -46.6167, 30),
            WorldClockLocation("West", java.util.Locale("", "BR").displayCountry, "Brazil/West", -3.1333, -60.0167, 30),
            WorldClockLocation("CET", java.util.Locale("", "BE").displayCountry, "CET", 50.8333, 4.3333, 30),
            WorldClockLocation("CST6CDT", java.util.Locale("", "US").displayCountry, "CST6CDT", 41.8500, -87.6500, 30),
            WorldClockLocation("Atlantic", java.util.Locale("", "CA").displayCountry, "Canada/Atlantic", 44.6500, -63.6000, 30),
            WorldClockLocation("Central", java.util.Locale("", "CA").displayCountry, "Canada/Central", 49.8833, -97.1500, 30),
            WorldClockLocation("Eastern", java.util.Locale("", "CA").displayCountry, "Canada/Eastern", 43.6500, -79.3833, 30),
            WorldClockLocation("Mountain", java.util.Locale("", "CA").displayCountry, "Canada/Mountain", 53.5500, -113.4667, 30),
            WorldClockLocation("Newfoundland", java.util.Locale("", "CA").displayCountry, "Canada/Newfoundland", 47.5667, -52.7167, 30),
            WorldClockLocation("Pacific", java.util.Locale("", "CA").displayCountry, "Canada/Pacific", 49.2667, -123.1167, 30),
            WorldClockLocation("Saskatchewan", java.util.Locale("", "CA").displayCountry, "Canada/Saskatchewan", 50.4000, -104.6500, 30),
            WorldClockLocation("Yukon", java.util.Locale("", "CA").displayCountry, "Canada/Yukon", 60.7167, -135.0500, 30),
            WorldClockLocation("Continental", java.util.Locale("", "CL").displayCountry, "Chile/Continental", -33.4500, -70.6667, 30),
            WorldClockLocation("EasterIsland", java.util.Locale("", "CL").displayCountry, "Chile/EasterIsland", -27.1500, -109.4333, 30),
            WorldClockLocation("Cuba", java.util.Locale("", "CU").displayCountry, "Cuba", 23.1333, -82.3667, 30),
            WorldClockLocation("EET", java.util.Locale("", "GR").displayCountry, "EET", 37.9667, 23.7167, 30),
            WorldClockLocation("EST", java.util.Locale("", "PA").displayCountry, "EST", 8.9667, -79.5333, 30),
            WorldClockLocation("EST5EDT", java.util.Locale("", "US").displayCountry, "EST5EDT", 40.7142, -74.0064, 30),
            WorldClockLocation("Egypt", java.util.Locale("", "EG").displayCountry, "Egypt", 30.0500, 31.2500, 30),
            WorldClockLocation("Eire", java.util.Locale("", "IE").displayCountry, "Eire", 53.3333, -6.2500, 30),
            WorldClockLocation("GB", java.util.Locale("", "GB").displayCountry, "GB", 51.5083, -0.1253, 30),
            WorldClockLocation("GB-Eire", java.util.Locale("", "GB").displayCountry, "GB-Eire", 51.5083, -0.1253, 30),
            WorldClockLocation("Hongkong", java.util.Locale("", "HK").displayCountry, "Hongkong", 22.2833, 114.1500, 30),
            WorldClockLocation("Iceland", java.util.Locale("", "CI").displayCountry, "Iceland", 5.3167, -4.0333, 30),
            WorldClockLocation("Iran", java.util.Locale("", "IR").displayCountry, "Iran", 35.6667, 51.4333, 30),
            WorldClockLocation("Israel", java.util.Locale("", "IL").displayCountry, "Israel", 31.7806, 35.2239, 30),
            WorldClockLocation("Jamaica", java.util.Locale("", "JM").displayCountry, "Jamaica", 17.9681, -76.7933, 30),
            WorldClockLocation("Japan", java.util.Locale("", "JP").displayCountry, "Japan", 35.6544, 139.7447, 30),
            WorldClockLocation("Kwajalein", java.util.Locale("", "MH").displayCountry, "Kwajalein", 9.0833, 167.3333, 30),
            WorldClockLocation("Libya", java.util.Locale("", "LY").displayCountry, "Libya", 32.9000, 13.1833, 30),
            WorldClockLocation("MET", java.util.Locale("", "BE").displayCountry, "MET", 50.8333, 4.3333, 30),
            WorldClockLocation("MST", java.util.Locale("", "US").displayCountry, "MST", 33.4483, -112.0733, 30),
            WorldClockLocation("MST7MDT", java.util.Locale("", "US").displayCountry, "MST7MDT", 39.7392, -104.9842, 30),
            WorldClockLocation("BajaNorte", java.util.Locale("", "MX").displayCountry, "Mexico/BajaNorte", 32.5333, -117.0167, 30),
            WorldClockLocation("BajaSur", java.util.Locale("", "MX").displayCountry, "Mexico/BajaSur", 23.2167, -106.4167, 30),
            WorldClockLocation("General", java.util.Locale("", "MX").displayCountry, "Mexico/General", 19.4000, -99.1500, 30),
            WorldClockLocation("NZ", java.util.Locale("", "NZ").displayCountry, "NZ", -36.8667, 174.7667, 30),
            WorldClockLocation("NZ-CHAT", java.util.Locale("", "NZ").displayCountry, "NZ-CHAT", -43.9500, -176.5500, 30),
            WorldClockLocation("Navajo", java.util.Locale("", "US").displayCountry, "Navajo", 39.7392, -104.9842, 30),
            WorldClockLocation("PRC", java.util.Locale("", "CN").displayCountry, "PRC", 31.2333, 121.4667, 30),
            WorldClockLocation("Poland", java.util.Locale("", "PL").displayCountry, "Poland", 52.2500, 21.0000, 30),
            WorldClockLocation("Portugal", java.util.Locale("", "PT").displayCountry, "Portugal", 38.7167, -9.1333, 30),
            WorldClockLocation("ROC", java.util.Locale("", "TW").displayCountry, "ROC", 25.0500, 121.5000, 30),
            WorldClockLocation("ROK", java.util.Locale("", "KR").displayCountry, "ROK", 37.5500, 126.9667, 30),
            WorldClockLocation("Singapore", java.util.Locale("", "SG").displayCountry, "Singapore", 1.2833, 103.8500, 30),
            WorldClockLocation("Turkey", java.util.Locale("", "TR").displayCountry, "Turkey", 41.0167, 28.9667, 30),
            WorldClockLocation("Alaska", java.util.Locale("", "US").displayCountry, "US/Alaska", 61.2181, -149.9003, 30),
            WorldClockLocation("Aleutian", java.util.Locale("", "US").displayCountry, "US/Aleutian", 51.8800, -176.6581, 30),
            WorldClockLocation("Arizona", java.util.Locale("", "US").displayCountry, "US/Arizona", 33.4483, -112.0733, 30),
            WorldClockLocation("Central", java.util.Locale("", "US").displayCountry, "US/Central", 41.8500, -87.6500, 30),
            WorldClockLocation("East-Indiana", java.util.Locale("", "US").displayCountry, "US/East-Indiana", 39.7683, -86.1581, 30),
            WorldClockLocation("Eastern", java.util.Locale("", "US").displayCountry, "US/Eastern", 40.7142, -74.0064, 30),
            WorldClockLocation("Hawaii", java.util.Locale("", "US").displayCountry, "US/Hawaii", 21.3069, -157.8583, 30),
            WorldClockLocation("Indiana-Starke", java.util.Locale("", "US").displayCountry, "US/Indiana-Starke", 41.2958, -86.6250, 30),
            WorldClockLocation("Michigan", java.util.Locale("", "US").displayCountry, "US/Michigan", 42.3314, -83.0458, 30),
            WorldClockLocation("Mountain", java.util.Locale("", "US").displayCountry, "US/Mountain", 39.7392, -104.9842, 30),
            WorldClockLocation("Pacific", java.util.Locale("", "US").displayCountry, "US/Pacific", 34.0522, -118.2428, 30),
            WorldClockLocation("Samoa", java.util.Locale("", "AS").displayCountry, "US/Samoa", -14.2667, -170.7000, 30),
            WorldClockLocation("W-SU", java.util.Locale("", "RU").displayCountry, "W-SU", 55.7558, 37.6178, 30),
            WorldClockLocation("Buenos Aires", java.util.Locale("", "AR").displayCountry, "America/Buenos_Aires", -34.6000, -58.4500, 30),
            WorldClockLocation("Catamarca", java.util.Locale("", "AR").displayCountry, "America/Catamarca", -28.4667, -65.7833, 30),
            WorldClockLocation("Cordoba", java.util.Locale("", "AR").displayCountry, "America/Cordoba", -31.4000, -64.1833, 30),
            WorldClockLocation("Indianapolis", java.util.Locale("", "US").displayCountry, "America/Indianapolis", 39.7683, -86.1581, 30),
            WorldClockLocation("Jujuy", java.util.Locale("", "AR").displayCountry, "America/Jujuy", -24.1833, -65.3000, 30),
            WorldClockLocation("Knox IN", java.util.Locale("", "US").displayCountry, "America/Knox_IN", 41.2958, -86.6250, 30),
            WorldClockLocation("Louisville", java.util.Locale("", "US").displayCountry, "America/Louisville", 38.2542, -85.7594, 30),
            WorldClockLocation("Mendoza", java.util.Locale("", "AR").displayCountry, "America/Mendoza", -32.8833, -68.8167, 30),
            WorldClockLocation("Virgin", java.util.Locale("", "PR").displayCountry, "America/Virgin", 18.4683, -66.1061, 30),
            WorldClockLocation("Samoa", java.util.Locale("", "AS").displayCountry, "Pacific/Samoa", -14.2667, -170.7000, 30),
            WorldClockLocation("Accra", java.util.Locale("", "CI").displayCountry, "Africa/Accra", 5.3167, -4.0333, 30),
            WorldClockLocation("Addis Ababa", java.util.Locale("", "KE").displayCountry, "Africa/Addis_Ababa", -1.2833, 36.8167, 30),
            WorldClockLocation("Asmara", java.util.Locale("", "KE").displayCountry, "Africa/Asmara", -1.2833, 36.8167, 30),
            WorldClockLocation("Bamako", java.util.Locale("", "CI").displayCountry, "Africa/Bamako", 5.3167, -4.0333, 30),
            WorldClockLocation("Bangui", java.util.Locale("", "NG").displayCountry, "Africa/Bangui", 6.4500, 3.4000, 30),
            WorldClockLocation("Banjul", java.util.Locale("", "CI").displayCountry, "Africa/Banjul", 5.3167, -4.0333, 30),
            WorldClockLocation("Blantyre", java.util.Locale("", "MZ").displayCountry, "Africa/Blantyre", -25.9667, 32.5833, 30),
            WorldClockLocation("Brazzaville", java.util.Locale("", "NG").displayCountry, "Africa/Brazzaville", 6.4500, 3.4000, 30),
            WorldClockLocation("Bujumbura", java.util.Locale("", "MZ").displayCountry, "Africa/Bujumbura", -25.9667, 32.5833, 30),
            WorldClockLocation("Conakry", java.util.Locale("", "CI").displayCountry, "Africa/Conakry", 5.3167, -4.0333, 30),
            WorldClockLocation("Dakar", java.util.Locale("", "CI").displayCountry, "Africa/Dakar", 5.3167, -4.0333, 30),
            WorldClockLocation("Dar es Salaam", java.util.Locale("", "KE").displayCountry, "Africa/Dar_es_Salaam", -1.2833, 36.8167, 30),
            WorldClockLocation("Djibouti", java.util.Locale("", "KE").displayCountry, "Africa/Djibouti", -1.2833, 36.8167, 30),
            WorldClockLocation("Douala", java.util.Locale("", "NG").displayCountry, "Africa/Douala", 6.4500, 3.4000, 30),
            WorldClockLocation("Freetown", java.util.Locale("", "CI").displayCountry, "Africa/Freetown", 5.3167, -4.0333, 30),
            WorldClockLocation("Gaborone", java.util.Locale("", "MZ").displayCountry, "Africa/Gaborone", -25.9667, 32.5833, 30),
            WorldClockLocation("Harare", java.util.Locale("", "MZ").displayCountry, "Africa/Harare", -25.9667, 32.5833, 30),
            WorldClockLocation("Kampala", java.util.Locale("", "KE").displayCountry, "Africa/Kampala", -1.2833, 36.8167, 30),
            WorldClockLocation("Kigali", java.util.Locale("", "MZ").displayCountry, "Africa/Kigali", -25.9667, 32.5833, 30),
            WorldClockLocation("Kinshasa", java.util.Locale("", "NG").displayCountry, "Africa/Kinshasa", 6.4500, 3.4000, 30),
            WorldClockLocation("Libreville", java.util.Locale("", "NG").displayCountry, "Africa/Libreville", 6.4500, 3.4000, 30),
            WorldClockLocation("Lome", java.util.Locale("", "CI").displayCountry, "Africa/Lome", 5.3167, -4.0333, 30),
            WorldClockLocation("Luanda", java.util.Locale("", "NG").displayCountry, "Africa/Luanda", 6.4500, 3.4000, 30),
            WorldClockLocation("Lubumbashi", java.util.Locale("", "MZ").displayCountry, "Africa/Lubumbashi", -25.9667, 32.5833, 30),
            WorldClockLocation("Lusaka", java.util.Locale("", "MZ").displayCountry, "Africa/Lusaka", -25.9667, 32.5833, 30),
            WorldClockLocation("Malabo", java.util.Locale("", "NG").displayCountry, "Africa/Malabo", 6.4500, 3.4000, 30),
            WorldClockLocation("Maseru", java.util.Locale("", "ZA").displayCountry, "Africa/Maseru", -26.2500, 28.0000, 30),
            WorldClockLocation("Mbabane", java.util.Locale("", "ZA").displayCountry, "Africa/Mbabane", -26.2500, 28.0000, 30),
            WorldClockLocation("Mogadishu", java.util.Locale("", "KE").displayCountry, "Africa/Mogadishu", -1.2833, 36.8167, 30),
            WorldClockLocation("Niamey", java.util.Locale("", "NG").displayCountry, "Africa/Niamey", 6.4500, 3.4000, 30),
            WorldClockLocation("Nouakchott", java.util.Locale("", "CI").displayCountry, "Africa/Nouakchott", 5.3167, -4.0333, 30),
            WorldClockLocation("Ouagadougou", java.util.Locale("", "CI").displayCountry, "Africa/Ouagadougou", 5.3167, -4.0333, 30),
            WorldClockLocation("Porto-Novo", java.util.Locale("", "NG").displayCountry, "Africa/Porto-Novo", 6.4500, 3.4000, 30),
            WorldClockLocation("Anguilla", java.util.Locale("", "PR").displayCountry, "America/Anguilla", 18.4683, -66.1061, 30),
            WorldClockLocation("Antigua", java.util.Locale("", "PR").displayCountry, "America/Antigua", 18.4683, -66.1061, 30),
            WorldClockLocation("Aruba", java.util.Locale("", "PR").displayCountry, "America/Aruba", 18.4683, -66.1061, 30),
            WorldClockLocation("Atikokan", java.util.Locale("", "PA").displayCountry, "America/Atikokan", 8.9667, -79.5333, 30),
            WorldClockLocation("Blanc-Sablon", java.util.Locale("", "PR").displayCountry, "America/Blanc-Sablon", 18.4683, -66.1061, 30),
            WorldClockLocation("Cayman", java.util.Locale("", "PA").displayCountry, "America/Cayman", 8.9667, -79.5333, 30),
            WorldClockLocation("Creston", java.util.Locale("", "US").displayCountry, "America/Creston", 33.4483, -112.0733, 30),
            WorldClockLocation("Curacao", java.util.Locale("", "PR").displayCountry, "America/Curacao", 18.4683, -66.1061, 30),
            WorldClockLocation("Dominica", java.util.Locale("", "PR").displayCountry, "America/Dominica", 18.4683, -66.1061, 30),
            WorldClockLocation("Grenada", java.util.Locale("", "PR").displayCountry, "America/Grenada", 18.4683, -66.1061, 30),
            WorldClockLocation("Guadeloupe", java.util.Locale("", "PR").displayCountry, "America/Guadeloupe", 18.4683, -66.1061, 30),
            WorldClockLocation("Kralendijk", java.util.Locale("", "PR").displayCountry, "America/Kralendijk", 18.4683, -66.1061, 30),
            WorldClockLocation("Lower Princes", java.util.Locale("", "PR").displayCountry, "America/Lower_Princes", 18.4683, -66.1061, 30),
            WorldClockLocation("Marigot", java.util.Locale("", "PR").displayCountry, "America/Marigot", 18.4683, -66.1061, 30),
            WorldClockLocation("Montserrat", java.util.Locale("", "PR").displayCountry, "America/Montserrat", 18.4683, -66.1061, 30),
            WorldClockLocation("Nassau", java.util.Locale("", "CA").displayCountry, "America/Nassau", 43.6500, -79.3833, 30),
            WorldClockLocation("Port of Spain", java.util.Locale("", "PR").displayCountry, "America/Port_of_Spain", 18.4683, -66.1061, 30),
            WorldClockLocation("St Barthelemy", java.util.Locale("", "PR").displayCountry, "America/St_Barthelemy", 18.4683, -66.1061, 30),
            WorldClockLocation("St Kitts", java.util.Locale("", "PR").displayCountry, "America/St_Kitts", 18.4683, -66.1061, 30),
            WorldClockLocation("St Lucia", java.util.Locale("", "PR").displayCountry, "America/St_Lucia", 18.4683, -66.1061, 30),
            WorldClockLocation("St Thomas", java.util.Locale("", "PR").displayCountry, "America/St_Thomas", 18.4683, -66.1061, 30),
            WorldClockLocation("St Vincent", java.util.Locale("", "PR").displayCountry, "America/St_Vincent", 18.4683, -66.1061, 30),
            WorldClockLocation("Tortola", java.util.Locale("", "PR").displayCountry, "America/Tortola", 18.4683, -66.1061, 30),
            WorldClockLocation("DumontDUrville", java.util.Locale("", "PG").displayCountry, "Antarctica/DumontDUrville", -9.5000, 147.1667, 30),
            WorldClockLocation("McMurdo", java.util.Locale("", "NZ").displayCountry, "Antarctica/McMurdo", -36.8667, 174.7667, 30),
            WorldClockLocation("Syowa", java.util.Locale("", "SA").displayCountry, "Antarctica/Syowa", 24.6333, 46.7167, 30),
            WorldClockLocation("Longyearbyen", java.util.Locale("", "DE").displayCountry, "Arctic/Longyearbyen", 52.5000, 13.3667, 30),
            WorldClockLocation("Aden", java.util.Locale("", "SA").displayCountry, "Asia/Aden", 24.6333, 46.7167, 30),
            WorldClockLocation("Bahrain", java.util.Locale("", "QA").displayCountry, "Asia/Bahrain", 25.2833, 51.5333, 30),
            WorldClockLocation("Brunei", java.util.Locale("", "MY").displayCountry, "Asia/Brunei", 1.5500, 110.3333, 30),
            WorldClockLocation("Kuala Lumpur", java.util.Locale("", "SG").displayCountry, "Asia/Kuala_Lumpur", 1.2833, 103.8500, 30),
            WorldClockLocation("Kuwait", java.util.Locale("", "SA").displayCountry, "Asia/Kuwait", 24.6333, 46.7167, 30),
            WorldClockLocation("Muscat", java.util.Locale("", "AE").displayCountry, "Asia/Muscat", 25.3000, 55.3000, 30),
            WorldClockLocation("Phnom Penh", java.util.Locale("", "TH").displayCountry, "Asia/Phnom_Penh", 13.7500, 100.5167, 30),
            WorldClockLocation("Vientiane", java.util.Locale("", "TH").displayCountry, "Asia/Vientiane", 13.7500, 100.5167, 30),
            WorldClockLocation("Reykjavik", java.util.Locale("", "CI").displayCountry, "Atlantic/Reykjavik", 5.3167, -4.0333, 30),
            WorldClockLocation("St Helena", java.util.Locale("", "CI").displayCountry, "Atlantic/St_Helena", 5.3167, -4.0333, 30),
            WorldClockLocation("Amsterdam", java.util.Locale("", "BE").displayCountry, "Europe/Amsterdam", 50.8333, 4.3333, 30),
            WorldClockLocation("Bratislava", java.util.Locale("", "CZ").displayCountry, "Europe/Bratislava", 50.0833, 14.4333, 30),
            WorldClockLocation("Busingen", java.util.Locale("", "CH").displayCountry, "Europe/Busingen", 47.3833, 8.5333, 30),
            WorldClockLocation("Copenhagen", java.util.Locale("", "DE").displayCountry, "Europe/Copenhagen", 52.5000, 13.3667, 30),
            WorldClockLocation("Guernsey", java.util.Locale("", "GB").displayCountry, "Europe/Guernsey", 51.5083, -0.1253, 30),
            WorldClockLocation("Isle of Man", java.util.Locale("", "GB").displayCountry, "Europe/Isle_of_Man", 51.5083, -0.1253, 30),
            WorldClockLocation("Jersey", java.util.Locale("", "GB").displayCountry, "Europe/Jersey", 51.5083, -0.1253, 30),
            WorldClockLocation("Ljubljana", java.util.Locale("", "RS").displayCountry, "Europe/Ljubljana", 44.8333, 20.5000, 30),
            WorldClockLocation("Luxembourg", java.util.Locale("", "BE").displayCountry, "Europe/Luxembourg", 50.8333, 4.3333, 30),
            WorldClockLocation("Mariehamn", java.util.Locale("", "FI").displayCountry, "Europe/Mariehamn", 60.1667, 24.9667, 30),
            WorldClockLocation("Monaco", java.util.Locale("", "FR").displayCountry, "Europe/Monaco", 48.8667, 2.3333, 30),
            WorldClockLocation("Oslo", java.util.Locale("", "DE").displayCountry, "Europe/Oslo", 52.5000, 13.3667, 30),
            WorldClockLocation("Podgorica", java.util.Locale("", "RS").displayCountry, "Europe/Podgorica", 44.8333, 20.5000, 30),
            WorldClockLocation("San Marino", java.util.Locale("", "IT").displayCountry, "Europe/San_Marino", 41.9000, 12.4833, 30),
            WorldClockLocation("Sarajevo", java.util.Locale("", "RS").displayCountry, "Europe/Sarajevo", 44.8333, 20.5000, 30),
            WorldClockLocation("Skopje", java.util.Locale("", "RS").displayCountry, "Europe/Skopje", 44.8333, 20.5000, 30),
            WorldClockLocation("Stockholm", java.util.Locale("", "DE").displayCountry, "Europe/Stockholm", 52.5000, 13.3667, 30),
            WorldClockLocation("Vaduz", java.util.Locale("", "CH").displayCountry, "Europe/Vaduz", 47.3833, 8.5333, 30),
            WorldClockLocation("Vatican", java.util.Locale("", "IT").displayCountry, "Europe/Vatican", 41.9000, 12.4833, 30),
            WorldClockLocation("Zagreb", java.util.Locale("", "RS").displayCountry, "Europe/Zagreb", 44.8333, 20.5000, 30),
            WorldClockLocation("Antananarivo", java.util.Locale("", "KE").displayCountry, "Indian/Antananarivo", -1.2833, 36.8167, 30),
            WorldClockLocation("Christmas", java.util.Locale("", "TH").displayCountry, "Indian/Christmas", 13.7500, 100.5167, 30),
            WorldClockLocation("Cocos", java.util.Locale("", "MM").displayCountry, "Indian/Cocos", 16.7833, 96.1667, 30),
            WorldClockLocation("Comoro", java.util.Locale("", "KE").displayCountry, "Indian/Comoro", -1.2833, 36.8167, 30),
            WorldClockLocation("Kerguelen", java.util.Locale("", "MV").displayCountry, "Indian/Kerguelen", 4.1667, 73.5000, 30),
            WorldClockLocation("Mahe", java.util.Locale("", "AE").displayCountry, "Indian/Mahe", 25.3000, 55.3000, 30),
            WorldClockLocation("Mayotte", java.util.Locale("", "KE").displayCountry, "Indian/Mayotte", -1.2833, 36.8167, 30),
            WorldClockLocation("Reunion", java.util.Locale("", "AE").displayCountry, "Indian/Reunion", 25.3000, 55.3000, 30),
            WorldClockLocation("Chuuk", java.util.Locale("", "PG").displayCountry, "Pacific/Chuuk", -9.5000, 147.1667, 30),
            WorldClockLocation("Funafuti", java.util.Locale("", "KI").displayCountry, "Pacific/Funafuti", 1.4167, 173.0000, 30),
            WorldClockLocation("Majuro", java.util.Locale("", "KI").displayCountry, "Pacific/Majuro", 1.4167, 173.0000, 30),
            WorldClockLocation("Midway", java.util.Locale("", "AS").displayCountry, "Pacific/Midway", -14.2667, -170.7000, 30),
            WorldClockLocation("Pohnpei", java.util.Locale("", "SB").displayCountry, "Pacific/Pohnpei", -9.5333, 160.2000, 30),
            WorldClockLocation("Saipan", java.util.Locale("", "GU").displayCountry, "Pacific/Saipan", 13.4667, 144.7500, 30),
            WorldClockLocation("Wake", java.util.Locale("", "KI").displayCountry, "Pacific/Wake", 1.4167, 173.0000, 30),
            WorldClockLocation("Wallis", java.util.Locale("", "KI").displayCountry, "Pacific/Wallis", 1.4167, 173.0000, 30),
            WorldClockLocation("Timbuktu", java.util.Locale("", "CI").displayCountry, "Africa/Timbuktu", 5.3167, -4.0333, 30),
            WorldClockLocation("ComodRivadavia", java.util.Locale("", "AR").displayCountry, "America/Argentina/ComodRivadavia", -28.4667, -65.7833, 30),
            WorldClockLocation("Atka", java.util.Locale("", "US").displayCountry, "America/Atka", 51.8800, -176.6581, 30),
            WorldClockLocation("Coral Harbour", java.util.Locale("", "PA").displayCountry, "America/Coral_Harbour", 8.9667, -79.5333, 30),
            WorldClockLocation("Ensenada", java.util.Locale("", "MX").displayCountry, "America/Ensenada", 32.5333, -117.0167, 30),
            WorldClockLocation("Fort Wayne", java.util.Locale("", "US").displayCountry, "America/Fort_Wayne", 39.7683, -86.1581, 30),
            WorldClockLocation("Montreal", java.util.Locale("", "CA").displayCountry, "America/Montreal", 43.6500, -79.3833, 30),
            WorldClockLocation("Nipigon", java.util.Locale("", "CA").displayCountry, "America/Nipigon", 43.6500, -79.3833, 30),
            WorldClockLocation("Pangnirtung", java.util.Locale("", "CA").displayCountry, "America/Pangnirtung", 63.7333, -68.4667, 30),
            WorldClockLocation("Porto Acre", java.util.Locale("", "BR").displayCountry, "America/Porto_Acre", -9.9667, -67.8000, 30),
            WorldClockLocation("Rainy River", java.util.Locale("", "CA").displayCountry, "America/Rainy_River", 49.8833, -97.1500, 30),
            WorldClockLocation("Rosario", java.util.Locale("", "AR").displayCountry, "America/Rosario", -31.4000, -64.1833, 30),
            WorldClockLocation("Santa Isabel", java.util.Locale("", "MX").displayCountry, "America/Santa_Isabel", 32.5333, -117.0167, 30),
            WorldClockLocation("Shiprock", java.util.Locale("", "US").displayCountry, "America/Shiprock", 39.7392, -104.9842, 30),
            WorldClockLocation("Thunder Bay", java.util.Locale("", "CA").displayCountry, "America/Thunder_Bay", 43.6500, -79.3833, 30),
            WorldClockLocation("Yellowknife", java.util.Locale("", "CA").displayCountry, "America/Yellowknife", 53.5500, -113.4667, 30),
            WorldClockLocation("South Pole", java.util.Locale("", "NZ").displayCountry, "Antarctica/South_Pole", -36.8667, 174.7667, 30),
            WorldClockLocation("Choibalsan", java.util.Locale("", "MN").displayCountry, "Asia/Choibalsan", 47.9167, 106.8833, 30),
            WorldClockLocation("Chongqing", java.util.Locale("", "CN").displayCountry, "Asia/Chongqing", 31.2333, 121.4667, 30),
            WorldClockLocation("Harbin", java.util.Locale("", "CN").displayCountry, "Asia/Harbin", 31.2333, 121.4667, 30),
            WorldClockLocation("Kashgar", java.util.Locale("", "CN").displayCountry, "Asia/Kashgar", 43.8000, 87.5833, 30),
            WorldClockLocation("Tel Aviv", java.util.Locale("", "IL").displayCountry, "Asia/Tel_Aviv", 31.7806, 35.2239, 30),
            WorldClockLocation("Jan Mayen", java.util.Locale("", "DE").displayCountry, "Atlantic/Jan_Mayen", 52.5000, 13.3667, 30),
            WorldClockLocation("Canberra", java.util.Locale("", "AU").displayCountry, "Australia/Canberra", -33.8667, 151.2167, 30),
            WorldClockLocation("Currie", java.util.Locale("", "AU").displayCountry, "Australia/Currie", -42.8833, 147.3167, 30),
            WorldClockLocation("Belfast", java.util.Locale("", "GB").displayCountry, "Europe/Belfast", 51.5083, -0.1253, 30),
            WorldClockLocation("Tiraspol", java.util.Locale("", "MD").displayCountry, "Europe/Tiraspol", 47.0000, 28.8333, 30),
            WorldClockLocation("Uzhgorod", java.util.Locale("", "UA").displayCountry, "Europe/Uzhgorod", 50.4333, 30.5167, 30),
            WorldClockLocation("Zaporozhye", java.util.Locale("", "UA").displayCountry, "Europe/Zaporozhye", 50.4333, 30.5167, 30),
            WorldClockLocation("Enderbury", java.util.Locale("", "KI").displayCountry, "Pacific/Enderbury", -2.7833, -171.7167, 30),
            WorldClockLocation("Johnston", java.util.Locale("", "US").displayCountry, "Pacific/Johnston", 21.3069, -157.8583, 30),
            WorldClockLocation("Yap", java.util.Locale("", "PG").displayCountry, "Pacific/Yap", -9.5000, 147.1667, 30),
            WorldClockLocation("WET", java.util.Locale("", "PT").displayCountry, "WET", 38.7167, -9.1333, 30),
            WorldClockLocation("Asmera", java.util.Locale("", "KE").displayCountry, "Africa/Asmera", -1.2833, 36.8167, 30),
            WorldClockLocation("Godthab", java.util.Locale("", "GL").displayCountry, "America/Godthab", 64.1833, -51.7333, 30),
            WorldClockLocation("Ashkhabad", java.util.Locale("", "TM").displayCountry, "Asia/Ashkhabad", 37.9500, 58.3833, 30),
            WorldClockLocation("Calcutta", java.util.Locale("", "IN").displayCountry, "Asia/Calcutta", 22.5333, 88.3667, 30),
            WorldClockLocation("Chungking", java.util.Locale("", "CN").displayCountry, "Asia/Chungking", 31.2333, 121.4667, 30),
            WorldClockLocation("Dacca", java.util.Locale("", "BD").displayCountry, "Asia/Dacca", 23.7167, 90.4167, 30),
            WorldClockLocation("Istanbul", java.util.Locale("", "TR").displayCountry, "Asia/Istanbul", 41.0167, 28.9667, 30),
            WorldClockLocation("Katmandu", java.util.Locale("", "NP").displayCountry, "Asia/Katmandu", 27.7167, 85.3167, 30),
            WorldClockLocation("Macao", java.util.Locale("", "MO").displayCountry, "Asia/Macao", 22.1972, 113.5417, 30),
            WorldClockLocation("Rangoon", java.util.Locale("", "MM").displayCountry, "Asia/Rangoon", 16.7833, 96.1667, 30),
            WorldClockLocation("Saigon", java.util.Locale("", "VN").displayCountry, "Asia/Saigon", 10.7500, 106.6667, 30),
            WorldClockLocation("Thimbu", java.util.Locale("", "BT").displayCountry, "Asia/Thimbu", 27.4667, 89.6500, 30),
            WorldClockLocation("Ujung Pandang", java.util.Locale("", "ID").displayCountry, "Asia/Ujung_Pandang", -5.1167, 119.4000, 30),
            WorldClockLocation("Ulan Bator", java.util.Locale("", "MN").displayCountry, "Asia/Ulan_Bator", 47.9167, 106.8833, 30),
            WorldClockLocation("Faeroe", java.util.Locale("", "FO").displayCountry, "Atlantic/Faeroe", 62.0167, -6.7667, 30),
            WorldClockLocation("Kiev", java.util.Locale("", "UA").displayCountry, "Europe/Kiev", 50.4333, 30.5167, 30),
            WorldClockLocation("Nicosia", java.util.Locale("", "CY").displayCountry, "Europe/Nicosia", 35.1667, 33.3667, 30),
            WorldClockLocation("HST", java.util.Locale("", "US").displayCountry, "HST", 21.3069, -157.8583, 30),
            WorldClockLocation("PST8PDT", java.util.Locale("", "US").displayCountry, "PST8PDT", 34.0522, -118.2428, 30),
            WorldClockLocation("Ponape", java.util.Locale("", "SB").displayCountry, "Pacific/Ponape", -9.5333, 160.2000, 30),
            WorldClockLocation("Truk", java.util.Locale("", "PG").displayCountry, "Pacific/Truk", -9.5000, 147.1667, 30)
        )
    }
}

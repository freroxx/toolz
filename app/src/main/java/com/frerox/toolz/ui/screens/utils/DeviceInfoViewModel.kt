/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.ui.screens.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.R
import com.frerox.toolz.data.device.DeviceSpecMapper
import com.frerox.toolz.data.device.DeviceSpecResponse
import com.frerox.toolz.data.device.DeviceSpecUiModel
import com.frerox.toolz.data.device.DeviceSpecsRepository
import retrofit2.HttpException
import org.json.JSONObject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DeviceInfoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val specsRepository: DeviceSpecsRepository,
) : ViewModel() {

    // 1. Smart Query Builder — produces the optimal query for our GSMArena server engine.
    //
    // The server already handles multi-strategy normalization (Samsung SM-xxx, brand-aware
    // scoring, etc.) so we focus on producing one authoritative, human-readable query that
    // maps cleanly to GSMArena's device catalog — avoiding raw model codes whenever possible.
    private val defaultModelQuery: String by lazy {
        buildBestQuery()
    }

    /**
     * Constructs the best possible search query for the device specs API.
     *
     * Priority order:
     *  1. Build.MODEL when it already contains a human-readable market name
     *     (e.g. "Pixel 9 Pro", "Galaxy S24 Ultra", "Nothing Phone (2)")
     *  2. "Brand MarketName" when model is a pure code (e.g. SM-A366B, 2201116SG)
     *  3. Sanitized "Brand Model" as last resort
     *
     * The server engine handles further normalization (SM-xxx stripping, quicksearch
     * fuzzy scoring), so we just need to give it the cleanest possible signal.
     */
    private fun buildBestQuery(): String {
        val manufacturer = Build.MANUFACTURER.trim()  // e.g. "samsung", "Google", "Nothing"
        val model       = Build.MODEL.trim()          // e.g. "SM-A366B", "Pixel 9 Pro", "A065F"
        val brand       = Build.BRAND.trim()          // often same as manufacturer but nicer case
        val device      = Build.DEVICE.trim()         // e.g. "a36", "husky"

        // Normalize brand for display (capitalize first letter)
        val displayBrand = brand.replaceFirstChar { it.uppercaseChar() }
            .ifBlank { manufacturer.replaceFirstChar { it.uppercaseChar() } }

        // Heuristic: is the model string a raw device code?
        // Codes are typically short and/or dominated by digits + dashes (e.g. SM-G991U, A065F, NX769J)
        val looksLikeCode = isModelCode(model)

        val query = when {
            // Model already includes brand → use as-is (e.g. "Galaxy S24 Ultra")
            model.contains(manufacturer, ignoreCase = true) ||
            model.contains(brand, ignoreCase = true) -> model

            // Model is human-readable market name (Pixel 9 Pro, Nothing Phone (1))
            !looksLikeCode && model.length > 6 -> "$displayBrand $model".trim()

            // Model is a Samsung code → let server do SM-xxx normalization but also
            // send "Samsung Galaxy <stripped>" as a hint for better quicksearch scoring
            looksLikeCode && (manufacturer.equals("samsung", ignoreCase = true) ||
                              brand.equals("samsung", ignoreCase = true)) -> {
                // Strip SM-/GT- prefix and trailing region suffix (e.g. SM-A366B → A366B → A36)
                val stripped = model
                    .uppercase()
                    .removePrefix("SM-")
                    .removePrefix("GT-")
                    .removePrefix("SCH-")
                    .removePrefix("SGH-")
                val baseModel = stripped.replace(Regex("[A-Z]$"), "") // remove trailing letter variant
                "Samsung Galaxy $baseModel".trim()
            }

            // Generic code device: prepend brand and let server strategy matching do the rest
            else -> "$displayBrand $model".trim()
        }

        return query
            .ifBlank { "$displayBrand $device".trim() }
            .ifBlank { "Unknown" }
            // Remove any double-spaces from concatenation
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    /**
     * Returns true if [model] looks like a raw device/internal code rather than
     * a human-readable market name.
     *
     * Examples of codes: SM-A366B, A065F, NX769J, XT2125-4, 2201116SG
     * Examples of market names: Pixel 9 Pro, Galaxy S24, Nothing Phone (1)
     */
    private fun isModelCode(model: String): Boolean {
        if (model.length < 3) return true

        // Known code prefixes
        val knownPrefixes = listOf("SM-", "GT-", "SCH-", "SGH-", "SPH-", "SCV", "SC-", "SCG",
                                   "XT", "LG-", "HTC", "ZTE", "CPH", "CPB", "RMX", "RMP",
                                   "IN20", "M2", "NX", "BV", "V20")
        if (knownPrefixes.any { model.uppercase().startsWith(it) }) return true

        // Model is short (≤7 chars) and contains both letters AND digits — likely a code
        val hasDigit  = model.any { it.isDigit() }
        val hasLetter = model.any { it.isLetter() }
        if (model.length <= 7 && hasDigit && hasLetter) return true

        // Model is purely digits (some Xiaomi/Realme internal codes)
        if (model.all { it.isDigit() || it == '-' || it == '_' }) return true

        // Model contains no spaces and is all-uppercase (internal code style)
        if (!model.contains(' ') && model == model.uppercase() && model.length <= 10) return true

        return false
    }

    // 2. State Initialization Pipeline
    private val _uiState = MutableStateFlow(
        DeviceInfoUiState(
            localDevice = getDetailedDeviceInfo(context),
            queryModel = defaultModelQuery,
            isRemoteLoading = true
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        // Initial silent retrieval utilizing server edge cache signatures
        refresh(refreshLocal = false, query = defaultModelQuery)
    }

    fun refresh() {
        refresh(refreshLocal = true, query = _uiState.value.queryModel)
    }

    // 3. Manual query modifications (e.g., search text field overrides)
    fun updateQuery(newQuery: String) {
        val cleanQuery = newQuery.trim()
        if (cleanQuery.isBlank() || cleanQuery.length < 2) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    queryModel = cleanQuery,
                    isRemoteLoading = true,
                    remoteError = null
                )
            }
            executeSearch(cleanQuery, forceRefresh = true)
        }
    }

    // 4. Cleaned baseline refresh scheduler
    private fun refresh(refreshLocal: Boolean, query: String) {
        viewModelScope.launch {
            val targetQuery = query

            _uiState.update {
                it.copy(
                    isRemoteLoading = it.remoteSpec == null,
                    isRefreshing = refreshLocal,
                    remoteError = null,
                    queryModel = targetQuery
                )
            }

            if (refreshLocal) {
                val local = withContext(Dispatchers.IO) { getDetailedDeviceInfo(context) }
                _uiState.update { it.copy(localDevice = local) }
            }

            executeSearch(targetQuery, forceRefresh = refreshLocal)
        }
    }

    // 5. Single-Flight Server Search (Vercel serverless layer handles internal fallbacks)
    private suspend fun executeSearch(query: String, forceRefresh: Boolean = false) {
        try {
            val result = withContext(Dispatchers.IO) {
                specsRepository.getDeviceSpecs(query, forceRefresh = forceRefresh)
            }

            result.map { (response, isCached) -> DeviceSpecMapper.mapResponse(response, isCached) }
                .onSuccess { parsedSpecs ->
                    updateSuccessState(parsedSpecs)
                }
                .onFailure { exception ->
                    handleFailure(exception)
                }
        } catch (e: Exception) {
            handleFailure(e)
        }
    }

    private fun updateSuccessState(specs: DeviceSpecUiModel) {
        _uiState.update {
            it.copy(
                remoteSpec = specs,
                isRemoteLoading = false,
                isRefreshing = false,
                remoteError = null,
                lastUpdatedMillis = System.currentTimeMillis(),
                isFromCache = specs.isFromCache
            )
        }
    }

    private fun handleFailure(error: Throwable) {
        val errorMessage = when (error) {
            is HttpException -> {
                val errorBody = error.response()?.errorBody()?.string()
                try {
                    errorBody?.let { JSONObject(it).getString("error") }
                        ?: "Device specifications not found."
                } catch (e: Exception) {
                    if (error.code() == 404) "Device profile not available in database."
                    else "Server error (${error.code()})"
                }
            }
            else -> error.message?.takeIf(String::isNotBlank)
                ?: "Could not load market specifications right now."
        }
        _uiState.update {
            it.copy(
                isRemoteLoading = false,
                isRefreshing = false,
                remoteError = errorMessage,
            )
        }
    }
}

    private fun getDetailedDeviceInfo(context: Context): DetailedDeviceData {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            dm.getDisplay(Display.DEFAULT_DISPLAY)
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay
        }

        @Suppress("DEPRECATION")
        display?.getRealMetrics(metrics)

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val internalStorage = StatFs(Environment.getDataDirectory().path)
        val totalInternal = internalStorage.blockCountLong * internalStorage.blockSizeLong
        val availableInternal = internalStorage.availableBlocksLong * internalStorage.blockSizeLong

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryCapacity = getBatteryCapacity(context)

        return DetailedDeviceData(
            brand = Build.BRAND.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            device = Build.DEVICE,
            hardware = Build.HARDWARE,
            soc = getImprovedSocName(),
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A",
            kernelVersion = System.getProperty("os.version") ?: "Unknown",
            buildId = Build.ID,
            
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuFreq = getCpuMaxFreq(),
            cpuArch = System.getProperty("os.arch") ?: "Unknown",
            
            totalRam = memoryInfo.totalMem,
            availRam = memoryInfo.availMem,
            totalInternal = totalInternal,
            availInternal = availableInternal,
            
            screenRes = "${metrics.widthPixels} x ${metrics.heightPixels}",
            screenDensity = "${metrics.densityDpi} DPI",
            screenSize = calculateScreenSize(metrics),
            refreshRate = display?.mode?.refreshRate?.toInt() ?: 0,
            
            batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            batteryHealth = getBatteryHealth(context),
            batteryTech = getBatteryTech(context),
            batteryVoltage = getBatteryVoltage(context),
            batteryTemp = getBatteryTemp(context),
            batteryCapacity = batteryCapacity,

            cameras = getCameraInfo(context),
            sensorsCount = (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getSensorList(Sensor.TYPE_ALL).size,
            
            wifiIp = getWifiIpAddress(context),
            isRooted = checkRootMethod()
        )
    }

    private fun getImprovedSocName(): String {
        // Try reading from /proc/cpuinfo first as it often contains more accurate hardware info
        try {
            val cpuInfo = File("/proc/cpuinfo").readLines()
            val hardware = cpuInfo.find { it.startsWith("Hardware", true) }?.split(":")?.getOrNull(1)?.trim()
            if (!hardware.isNullOrBlank() && !hardware.contains("unknown", true)) {
                return hardware
            }
            val modelName = cpuInfo.find { it.startsWith("Model Name", true) || it.startsWith("Processor", true) }?.split(":")?.getOrNull(1)?.trim()
            if (!modelName.isNullOrBlank() && !modelName.contains("unknown", true)) {
                return modelName
            }
        } catch (e: Exception) {}

        // Fallback to Build properties
        return try {
            val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manufacturer = Build.SOC_MANUFACTURER
                val model = Build.SOC_MODEL
                if (!manufacturer.isNullOrBlank() && !model.isNullOrBlank()) {
                    "$manufacturer $model"
                } else model ?: Build.BOARD
            } else {
                Build.BOARD
            }
            if (soc.isBlank() || soc.contains("unknown", true)) Build.HARDWARE else soc
        } catch (e: Exception) {
            Build.HARDWARE
        }
    }

    private fun getCpuMaxFreq(): String {
        return try {
            val reader = RandomAccessFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq", "r")
            val freq = reader.readLine().toLong() / 1000
            reader.close()
            "$freq MHz"
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun calculateScreenSize(metrics: DisplayMetrics): Double {
        val x = (metrics.widthPixels.toDouble() / metrics.xdpi).let { it * it }
        val y = (metrics.heightPixels.toDouble() / metrics.ydpi).let { it * it }
        return Math.sqrt(x + y)
    }

    @StringRes
    private fun getBatteryHealth(context: Context): Int {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        return when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> R.string.st_DeviceInfoScreen_health_good
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.st_DeviceInfoScreen_health_overheating
            BatteryManager.BATTERY_HEALTH_DEAD -> R.string.st_DeviceInfoScreen_health_dead
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.st_DeviceInfoScreen_health_over_voltage
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> R.string.st_DeviceInfoScreen_health_failure
            BatteryManager.BATTERY_HEALTH_COLD -> R.string.st_DeviceInfoScreen_health_cold
            else -> R.string.st_DeviceInfoScreen_health_unknown
        }
    }

    private fun getBatteryTech(context: Context): String {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        return intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
    }

    private fun getBatteryVoltage(context: Context): String {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        return if (voltage > 0) "$voltage mV" else "N/A"
    }

    private fun getBatteryTemp(context: Context): String {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        return if (temp > 0) "${temp / 10.0} °C" else "N/A"
    }

    private fun getBatteryCapacity(context: Context): String {
        val powerProfileClass = "com.android.internal.os.PowerProfile"
        return try {
            val mPowerProfile = Class.forName(powerProfileClass)
                .getConstructor(Context::class.java)
                .newInstance(context)
            val batteryCapacity = Class.forName(powerProfileClass)
                .getMethod("getBatteryCapacity")
                .invoke(mPowerProfile) as Double
            "${batteryCapacity.toInt()} mAh"
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun getCameraInfo(context: Context): List<String> {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            cameraManager.cameraIdList.mapNotNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
                val facing = when (lensFacing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    else -> "External"
                }
                val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                if (sensorSize != null) {
                    val megapixels = (sensorSize.width * sensorSize.height) / 1000000.0
                    "$facing: ${String.format("%.1f", megapixels)} MP"
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getWifiIpAddress(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val linkProperties = cm.getLinkProperties(cm.activeNetwork)
        return linkProperties?.linkAddresses?.find { it.address.isSiteLocalAddress }?.address?.hostAddress ?: "N/A"
    }

    private fun checkRootMethod(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

data class DetailedDeviceData(
    val brand: String,
    val model: String,
    val device: String,
    val hardware: String,
    val soc: String,
    val androidVersion: String,
    val apiLevel: Int,
    val securityPatch: String,
    val kernelVersion: String,
    val buildId: String,
    
    val cpuCores: Int,
    val cpuFreq: String,
    val cpuArch: String,
    
    val totalRam: Long,
    val availRam: Long,
    val totalInternal: Long,
    val availInternal: Long,
    
    val screenRes: String,
    val screenDensity: String,
    val screenSize: Double,
    val refreshRate: Int,
    
    val batteryLevel: Int,
    val batteryHealth: Int,
    val batteryTech: String,
    val batteryVoltage: String,
    val batteryTemp: String,
    val batteryCapacity: String,

    val cameras: List<String>,
    val sensorsCount: Int,
    
    val wifiIp: String,
    val isRooted: Boolean
)

data class DeviceInfoUiState(
    val localDevice: DetailedDeviceData,
    val queryModel: String,
    val remoteSpec: DeviceSpecUiModel? = null,
    val isRemoteLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val remoteError: String? = null,
    val lastUpdatedMillis: Long? = null,
    val isFromCache: Boolean = false
)

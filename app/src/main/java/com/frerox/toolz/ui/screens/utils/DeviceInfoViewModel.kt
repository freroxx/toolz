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
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DeviceInfoViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _deviceData = MutableStateFlow(getDetailedDeviceInfo(context))
    val deviceData = _deviceData.asStateFlow()

    fun refresh() {
        _deviceData.value = getDetailedDeviceInfo(context)
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
            soc = getSocName(),
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A",
            kernelVersion = System.getProperty("os.version") ?: "Unknown",
            buildId = Build.ID,
            
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuFreq = getCpuMaxFreq(),
            
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

    private fun getSocName(): String {
        return try {
            val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL
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

    private fun getBatteryHealth(context: Context): String {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        return when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
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
    
    val totalRam: Long,
    val availRam: Long,
    val totalInternal: Long,
    val availInternal: Long,
    
    val screenRes: String,
    val screenDensity: String,
    val screenSize: Double,
    val refreshRate: Int,
    
    val batteryLevel: Int,
    val batteryHealth: String,
    val batteryTech: String,
    val batteryVoltage: String,
    val batteryTemp: String,
    val batteryCapacity: String,

    val cameras: List<String>,
    val sensorsCount: Int,
    
    val wifiIp: String,
    val isRooted: Boolean
)

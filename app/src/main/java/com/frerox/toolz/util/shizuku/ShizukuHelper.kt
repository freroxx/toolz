package com.frerox.toolz.util.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuHelper {
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun isAuthorized(): Boolean {
        return if (isAvailable()) {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }

    fun requestPermission(requestCode: Int) {
        if (isAvailable()) {
            Shizuku.requestPermission(requestCode)
        }
    }
}

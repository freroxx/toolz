package com.frerox.toolz

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltAndroidApp
class ToolzApplication : Application(), Configuration.Provider {

    companion object {
        private val _isFocused = MutableStateFlow(false)
        val isFocused: StateFlow<Boolean> = _isFocused

        fun setFocused(focused: Boolean) {
            _isFocused.value = focused
        }
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // The new standard for SQLCipher 4.6.1+ is a direct native load
        System.loadLibrary("sqlcipher")
        runCatching {
            val youtubeDl = Class.forName("com.yausername.youtubedl_android.YoutubeDL")
                .getMethod("getInstance")
                .invoke(null)
            youtubeDl.javaClass.getMethod("init", android.content.Context::class.java)
                .invoke(youtubeDl, this)
        }.onFailure {
            android.util.Log.w("ToolzApplication", "yt-dlp initialization failed; extractor fallback remains available", it)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

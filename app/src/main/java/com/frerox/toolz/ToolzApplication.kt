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

package com.frerox.toolz

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import com.frerox.toolz.util.network.AdBlockManager

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

    @Inject
    lateinit var adBlockManager: AdBlockManager

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

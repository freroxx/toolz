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

package com.frerox.toolz.ui.screens.livingframes

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameFit
import com.frerox.toolz.data.livingframes.LivingFrameLoopMode
import com.frerox.toolz.data.livingframes.LivingFrameTapAction
import com.frerox.toolz.data.livingframes.LivingFrameType
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameImporter
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import com.frerox.toolz.data.livingframes.LivingWidgetsPower
import com.frerox.toolz.data.livingframes.ShizukuState
import com.frerox.toolz.widget.livingframes.LivingFramesWidgetHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LivingFramesUiState(
    val frames: List<LivingFrame> = emptyList(),
    val selectedId: String? = null,
    val isImporting: Boolean = false,
    val importError: String? = null,
    val importSuccess: String? = null,
    val widgetsSupported: Boolean = true
)

@HiltViewModel
class LivingFramesViewModel @Inject constructor(
    private val repo: LivingFramesRepository,
    private val importer: LivingFrameImporter,
    private val power: LivingWidgetsPower,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _ui = MutableStateFlow(LivingFramesUiState())
    val ui: StateFlow<LivingFramesUiState> = _ui.asStateFlow()
    val frames = repo.frames

    private val _status = MutableStateFlow(LivingFramesWidgetHelper.PlaybackStatus(false, 0))
    val status: StateFlow<LivingFramesWidgetHelper.PlaybackStatus> = _status.asStateFlow()

    private val _diag = MutableStateFlow<Map<Int, String>>(emptyMap())
    val diagnostics: StateFlow<Map<Int, String>> = _diag.asStateFlow()

    private val _saverOn = MutableStateFlow(false)
    val saverOn: StateFlow<Boolean> = _saverOn.asStateFlow()

    private val _shizuku = MutableStateFlow(ShizukuState(false, false))
    val shizuku: StateFlow<ShizukuState> = _shizuku.asStateFlow()

    private val _busyMessage = MutableStateFlow<String?>(null)
    val busyMessage: StateFlow<String?> = _busyMessage.asStateFlow()

    init {
        viewModelScope.launch {
            try { repo.load() } catch (_: Exception) {}
            repo.frames.collect { list ->
                _ui.update {
                    it.copy(
                        frames = list,
                        selectedId = it.selectedId?.takeIf { id -> list.any { f -> f.id == id } }
                            ?: list.firstOrNull()?.id
                    )
                }
            }
        }
        // Opening the tool resumes playback (foreground start is allowed here;
        // background starts are denied on Android 12+ — see helper docs).
        startPlayback()
        com.frerox.toolz.worker.LivingFramesWatchdogWorker.schedule(context)
        viewModelScope.launch {
            while (true) {
                try {
                    _status.value = LivingFramesWidgetHelper.readStatus(context)
                    _diag.value = LivingFramesWidgetHelper.readDiag(context)
                    _saverOn.value = power.isSaverOn()
                } catch (_: Exception) {}
                delay(2000L)
            }
        }
        refreshShizuku()
    }

    fun refreshShizuku() = viewModelScope.launch {
        try {
            _shizuku.value = power.shizukuState()
        } catch (_: Exception) {}
    }

    fun requestShizukuAuth() {
        try {
            com.frerox.toolz.util.shizuku.ShizukuHelper.requestAuthorization { refreshShizuku() }
        } catch (_: Exception) {}
    }

    /** Turn Battery Saver off via Shizuku shell. Null message = success. */
    fun disableSaver(onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        _busyMessage.value = "Turning Battery Saver off…"
        try {
            val err = power.disableSaver()
            _saverOn.value = power.isSaverOn()
            if (err == null) startPlayback()
            onDone(err)
        } catch (e: Exception) {
            onDone(e.message ?: "Failed")
        } finally {
            _busyMessage.value = null
        }
    }

    /** (Re)start animation. Must only be called from foreground UI. */
    fun startPlayback() {
        try {
            LivingFramesWidgetHelper.startPlaybackFromForeground(context)
        } catch (_: Exception) {}
    }

    fun selected(): LivingFrame? =
        _ui.value.frames.firstOrNull { it.id == _ui.value.selectedId }
            ?: _ui.value.frames.firstOrNull()

    fun select(id: String) {
        _ui.update { it.copy(selectedId = id, importError = null, importSuccess = null) }
    }

    fun clearMessages() {
        _ui.update { it.copy(importError = null, importSuccess = null) }
    }

    fun importGif(uri: Uri, fps: Int) = runImport("GIF Loop") { importer.importGif(uri, fps) }

    fun importVideo(uri: Uri, fps: Int) = runImport("Video Loop") { importer.importVideo(uri, fps) }

    fun importPhoto(uri: Uri) = viewModelScope.launch {
        _ui.update { it.copy(isImporting = true, importError = null, importSuccess = null) }
        try {
            val res = importer.importPhoto(uri, "Photo Frame")
            _ui.update {
                it.copy(isImporting = false, selectedId = res.frame.id,
                    importSuccess = "Photo frame ready — pin it to your home screen.")
            }
        } catch (e: Exception) {
            _ui.update { it.copy(isImporting = false, importError = e.message ?: "Import failed") }
        }
    }

    fun importSlideshow(uris: List<Uri>) = viewModelScope.launch {
        _ui.update { it.copy(isImporting = true, importError = null, importSuccess = null) }
        try {
            val res = importer.importSlideshow(uris, "Slideshow")
            _ui.update {
                it.copy(isImporting = false, selectedId = res.frame.id,
                    importSuccess = "${res.frame.frameCount} photos — pin it to your home screen.")
            }
        } catch (e: Exception) {
            _ui.update { it.copy(isImporting = false, importError = e.message ?: "Import failed") }
        }
    }

    private fun runImport(defaultName: String, block: suspend () -> com.frerox.toolz.data.livingframes.LivingFrameImportResult) =
        viewModelScope.launch {
            _ui.update { it.copy(isImporting = true, importError = null, importSuccess = null) }
            try {
                val res = block()
                val extra = if (res.droppedFrames > 0) " (${res.droppedFrames} frames trimmed for size)" else ""
                _ui.update {
                    it.copy(
                        isImporting = false, selectedId = res.frame.id,
                        importSuccess = "$defaultName ready — ${res.frame.frameCount} frames$extra. Pin it!"
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isImporting = false, importError = e.message ?: "Import failed") }
            }
        }

    fun setFps(id: String, fps: Int) = viewModelScope.launch {
        repo.update(id) { it.copy(fps = LivingFrameEngine.clampFps(fps)) }
        startPlayback()
    }

    fun setLoopMode(id: String, mode: LivingFrameLoopMode) = viewModelScope.launch {
        repo.update(id) { it.copy(loopMode = mode) }
    }

    fun setFit(id: String, fit: LivingFrameFit) = viewModelScope.launch {
        repo.update(id) { it.copy(fit = fit) }
    }

    fun setTapAction(id: String, action: LivingFrameTapAction) = viewModelScope.launch {
        repo.update(id) { it.copy(tapAction = action) }
        refreshWidgets()
    }

    fun setOpacity(id: String, opacity: Float) = viewModelScope.launch {
        repo.update(id) { it.copy(opacity = opacity.coerceIn(0.4f, 1f)) }
        refreshWidgets()
    }

    fun setCorner(id: String, dp: Int) = viewModelScope.launch {
        repo.update(id) { it.copy(cornerRadiusDp = dp.coerceIn(0, 32)) }
    }

    fun setShuffle(id: String, shuffle: Boolean) = viewModelScope.launch {
        repo.update(id) { it.copy(shuffle = shuffle) }
    }

    fun setShowCaption(id: String, show: Boolean) = viewModelScope.launch {
        repo.update(id) { it.copy(showCaption = show) }
        refreshWidgets()
    }

    fun setSlideshowInterval(id: String, mins: Int) = viewModelScope.launch {
        val clamped = mins.coerceIn(1, 1440)
        repo.update(id) { it.copy(slideshowIntervalMin = clamped) }
        // Reschedule every widget bound to this slideshow.
        try {
            repo.boundWidgetIds(id).forEach { wid ->
                LivingFramesWidgetHelper.scheduleSlideshow(context, wid, clamped)
            }
        } catch (_: Exception) {}
    }

    fun setName(id: String, name: String) = viewModelScope.launch {
        repo.update(id) { it.copy(name = name.take(40)) }
    }

    fun delete(id: String) = viewModelScope.launch {
        try {
            val bound = repo.boundWidgetIds(id)
            // Leave a removal hint on the home screen: apps cannot remove
            // their own widgets, only the user can (long-press → Remove).
            bound.forEach { wid ->
                try {
                    LivingFramesWidgetHelper.pushFull(
                        context, wid,
                        LivingFramesWidgetHelper.placeholderViews(
                            context, "Widget deleted — long-press to remove"
                        )
                    )
                } catch (_: Exception) {}
                LivingFramesWidgetHelper.cancelSlideshow(context, wid)
                LivingFramesWidgetHelper.clearDiag(context, wid)
            }
        } catch (_: Exception) {}
        repo.delete(id)
        startPlayback()
    }

    fun pinToHome(frame: LivingFrame): Boolean {
        LivingFramesWidgetHelper.setPendingPinFrame(context, frame.id)
        val ok = LivingFramesWidgetHelper.requestPin(context, frame)
        startPlayback()
        return ok
    }

    /**
     * Push the current frame to bound widgets on demand and report the raw
     * outcome — separates "IPC path broken" from "loop broken".
     */
    fun testPush(frameId: String, onDone: (String) -> Unit = {}) = viewModelScope.launch {
        try {
            val frame = repo.get(frameId)
            if (frame == null) {
                onDone("Widget set not found")
                return@launch
            }
            val ids = repo.boundWidgetIds(frameId)
            if (ids.isEmpty()) {
                onDone("No home-screen widgets bound — pin one first")
                return@launch
            }
            val bmp = withContext(Dispatchers.IO) {
                try { repo.decodeWidgetFrame(frame, 0) } catch (_: Exception) { null }
            }
            if (bmp == null) {
                onDone("Couldn't decode frame — re-import this widget")
                return@launch
            }
            try {
                var ok = 0
                ids.forEach { wid ->
                    if (LivingFramesWidgetHelper.pushFrame(context, wid, frame, bmp)) ok++
                }
                onDone(if (ok == ids.size) "Test frame sent to $ok widget${if (ok == 1) "" else "s"}"
                else "Sent to $ok of ${ids.size} — launcher blocked the rest")
            } finally {
                try { bmp.recycle() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            onDone("Push failed: ${e.message ?: "unknown error"}")
        }
    }

    /** Preload frames into memory for the stutter-free vsync preview. */
    suspend fun decodePreviewFrames(id: String, maxSide: Int = 448): List<Bitmap> =
        withContext(Dispatchers.IO) {
            val frame = repo.get(id) ?: return@withContext emptyList()
            val count = frame.frameCount.coerceIn(1, 32)
            (0 until count).mapNotNull { i ->
                try { repo.decodeFrame(id, i, maxSide) } catch (_: Exception) { null }
            }
        }

    fun boundWidgetCount(frameId: String): Int = try {
        repo.boundWidgetIds(frameId).size
    } catch (_: Exception) { 0 }

    /** "Next photo in 12m" for the first bound slideshow widget, if any. */
    fun slideNextLabel(frameId: String): String? = try {
        repo.boundWidgetIds(frameId)
            .mapNotNull { LivingFramesWidgetHelper.slideNextInLabel(context, it) }
            .firstOrNull()
    } catch (_: Exception) { null }

    fun refreshWidgets() {
        startPlayback()
    }

    /** True-color type label for the segmented picker. */
    fun typeLabel(type: LivingFrameType): String = when (type) {
        LivingFrameType.GIF -> "GIF"
        LivingFrameType.VIDEO -> "Video"
        LivingFrameType.PHOTO -> "Photo"
        LivingFrameType.SLIDESHOW -> "Slideshow"
    }
}

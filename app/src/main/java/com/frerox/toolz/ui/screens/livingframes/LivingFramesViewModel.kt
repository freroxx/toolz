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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameCorners
import com.frerox.toolz.data.livingframes.LivingFrameFit
import com.frerox.toolz.data.livingframes.LivingFrameFps
import com.frerox.toolz.data.livingframes.LivingFrameImportResult
import com.frerox.toolz.data.livingframes.LivingFrameImporter
import com.frerox.toolz.data.livingframes.LivingFrameLoopMode
import com.frerox.toolz.data.livingframes.LivingFrameLoopTransition
import com.frerox.toolz.data.livingframes.LivingFrameQuality
import com.frerox.toolz.data.livingframes.LivingFrameResolution
import com.frerox.toolz.data.livingframes.LivingFrameSpeed
import com.frerox.toolz.data.livingframes.LivingFrameTapAction
import com.frerox.toolz.data.livingframes.LivingFrameType
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import com.frerox.toolz.data.livingframes.LivingFramesStorage
import com.frerox.toolz.widget.livingframes.LivingFramesSlideshow
import com.frerox.toolz.widget.livingframes.LivingFramesWidgetHelper
import com.frerox.toolz.worker.LivingFramesRenderWorker
import com.frerox.toolz.worker.LivingFramesWatchdogWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

data class ImportState(val label: String, val progress: Float?)

data class PendingVideo(
    val uri: Uri,
    val durationMs: Long,
    val width: Int,
    val height: Int
)

/** One home-screen instance of the selected set, plus how it was last rendered. */
data class PlacedWidget(
    val widgetId: Int,
    val cells: String,
    val info: LivingFramesWidgetHelper.WidgetInfo?,
    val nextSlideIn: String?
)

data class LivingFramesUiState(
    val frames: List<LivingFrame> = emptyList(),
    val selectedId: String? = null,
    val loaded: Boolean = false,
    val import: ImportState? = null,
    /** True when the user hid the import dialog to keep customizing. */
    val importHidden: Boolean = false,
    val pendingVideo: PendingVideo? = null,
    val storage: LivingFramesStorage? = null,
    val loadFailed: Boolean = false,
    /** Applies to the next import; stored pixels can't be upgraded later. */
    val importQuality: LivingFrameQuality = LivingFrameQuality.DEFAULT
) {
    val selected: LivingFrame? get() = frames.firstOrNull { it.id == selectedId } ?: frames.firstOrNull()
}

@HiltViewModel
class LivingFramesViewModel @Inject constructor(
    private val repo: LivingFramesRepository,
    private val importer: LivingFrameImporter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _ui = MutableStateFlow(LivingFramesUiState())
    val ui: StateFlow<LivingFramesUiState> = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var importJob: Job? = null
    private var retryJob: Job? = null
    private val refreshJobs = HashMap<String, Job>()

    init {
        viewModelScope.launch {
            repo.ensureLoaded()
            _ui.update { it.copy(loadFailed = repo.isLoadFailed()) }
            refreshStorage()
            repo.frames.collect { list ->
                _ui.update {
                    it.copy(
                        frames = list,
                        loaded = true,
                        loadFailed = repo.isLoadFailed(),
                        selectedId = it.selectedId?.takeIf { id -> list.any { f -> f.id == id } }
                            ?: list.firstOrNull()?.id
                    )
                }
            }
        }
        LivingFramesWatchdogWorker.schedule(context)
    }

    /** Widgets of the selected set currently on the home screen, polled lightly while the tool is visible. */
    val placed: StateFlow<List<PlacedWidget>> = combine(
        _ui.map { it.selected?.id }.distinctUntilChanged(),
        flow { while (true) { emit(Unit); delay(2_000L) } }
    ) { id, _ -> id }
        .map { id -> if (id == null) emptyList() else readPlaced(id) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private fun readPlaced(frameId: String): List<PlacedWidget> {
        val live = LivingFramesWidgetHelper.allWidgetIds(context).toSet()
        val frame = repo.get(frameId)
        return repo.boundWidgetIds(frameId).filter { it in live }.sorted().map { id ->
            PlacedWidget(
                widgetId = id,
                cells = LivingFramesWidgetHelper.cellLabel(context, id),
                info = LivingFramesWidgetHelper.readInfo(context, id),
                nextSlideIn = if (frame?.isSlideshow == true) LivingFramesSlideshow.nextLabel(context, id) else null
            )
        }
    }

    // -- selection ----------------------------------------------------------------

    fun select(id: String) = _ui.update { it.copy(selectedId = id) }

    fun coverFile(id: String): File =
        repo.coverFile(id).takeIf { it.isFile } ?: repo.frameFile(id, 0)

    fun refreshStorage() {
        viewModelScope.launch {
            val stats = withContext(Dispatchers.IO) { repo.storage() }
            _ui.update { it.copy(storage = stats) }
        }
    }

    fun cleanupStorage() {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) { repo.cleanupOrphans() }
            refreshStorage()
            _messages.emit(
                if (freed > 0) {
                    "Cleaned up ${LivingFramesRepository.formatBytes(freed)} of unused files."
                } else {
                    "Nothing to clean up."
                }
            )
        }
    }

    fun retryLoad() {
        // A queued duplicate load could overwrite an import started after the
        // first attempt succeeded.
        if (retryJob?.isActive == true) return
        retryJob = viewModelScope.launch {
            repo.load()
            _ui.update {
                it.copy(
                    frames = repo.snapshot(),
                    loaded = true,
                    loadFailed = repo.isLoadFailed(),
                    selectedId = it.selectedId?.takeIf { id -> repo.get(id) != null }
                        ?: repo.snapshot().firstOrNull()?.id
                )
            }
            refreshStorage()
            if (!repo.isLoadFailed()) {
                // Recovery should restore widgets immediately rather than
                // waiting for the next hourly watchdog pass.
                LivingFramesWidgetHelper.allWidgetIdsOrNull(context)?.forEach { id ->
                    LivingFramesRenderWorker.enqueue(context, id)
                }
            }
        }
    }

    // -- import -----------------------------------------------------------------------

    private fun runImport(label: String, block: suspend ((Float) -> Unit) -> LivingFrameImportResult) {
        if (importJob?.isActive == true || _ui.value.import != null) return
        if (!_ui.value.loaded) {
            _messages.tryEmit("The library is still loading. Try again in a moment.")
            return
        }
        if (repo.isLoadFailed()) {
            _messages.tryEmit("The library couldn't be read. Use “Try loading again” before importing.")
            return
        }
        importJob = viewModelScope.launch {
            LivingFramesImportCancellation.reset()
            _ui.update { it.copy(import = ImportState(label, 0f), importHidden = false) }
            LivingFramesImportNotifications.start(context, label)
            var lastNotifiedPct = -1
            try {
                val res = block { p ->
                    // The notification's Cancel action cannot cancel a
                    // coroutine directly; it raises this flag and the next
                    // progress callback aborts through the normal cleanup path.
                    if (LivingFramesImportCancellation.consume()) {
                        throw CancellationException("Import cancelled")
                    }
                    val progress = p.coerceIn(0f, 1f)
                    _ui.update { s ->
                        s.copy(import = s.import?.copy(progress = progress))
                    }
                    val pct = (progress * 100).toInt()
                    if (pct / 5 != lastNotifiedPct / 5) {
                        lastNotifiedPct = pct
                        LivingFramesImportNotifications.update(context, label, progress)
                    }
                }
                _ui.update { it.copy(selectedId = res.frame.id) }
                _messages.emit(res.note ?: "“${res.frame.name}” is ready. Add it to your home screen.")
            } catch (e: CancellationException) {
                if (LivingFramesImportCancellation.consume()) {
                    _messages.tryEmit("Import cancelled.")
                }
                throw e
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                _messages.emit(
                    when (e) {
                        is OutOfMemoryError -> "Not enough memory to import that. Try a shorter video or a smaller photo."
                        else -> e.message ?: "Import failed"
                    }
                )
            } finally {
                LivingFramesImportCancellation.reset()
                LivingFramesImportNotifications.cancel(context)
                _ui.update { it.copy(import = null, importHidden = false) }
                refreshStorage()
            }
        }
    }

    fun hideImport() = _ui.update { it.copy(importHidden = true) }

    fun showImport() = _ui.update { it.copy(importHidden = false) }

    fun cancelImport() {
        LivingFramesImportCancellation.request()
        importJob?.cancel()
    }

    fun setImportQuality(quality: LivingFrameQuality) {
        _ui.update { it.copy(importQuality = quality) }
    }

    fun importGif(uri: Uri) {
        val quality = _ui.value.importQuality
        runImport("Reading GIF") { p -> importer.importGif(uri, quality, p) }
    }

    fun importPhoto(uri: Uri) {
        val quality = _ui.value.importQuality
        runImport("Adding photo") { p -> importer.importPhoto(uri, quality, p) }
    }

    fun importSlideshow(uris: List<Uri>) {
        val quality = _ui.value.importQuality
        runImport("Adding photos") { p -> importer.importSlideshow(uris, quality, p) }
    }

    fun onVideoPicked(uri: Uri) {
        viewModelScope.launch {
            val probe = importer.probeVideo(uri)
            if (probe == null || probe.durationMs <= 0L) {
                _messages.emit("Couldn't read that video.")
            } else {
                _ui.update {
                    it.copy(
                        pendingVideo = PendingVideo(
                            uri = uri,
                            durationMs = probe.durationMs,
                            width = probe.width,
                            height = probe.height
                        )
                    )
                }
            }
        }
    }

    fun cancelVideo() = _ui.update { it.copy(pendingVideo = null) }

    fun confirmVideo(
        startMs: Long,
        lengthMs: Long,
        fps: Int,
        quality: LivingFrameQuality = _ui.value.importQuality
    ) {
        val pv = _ui.value.pendingVideo ?: return
        _ui.update { it.copy(pendingVideo = null) }
        runImport("Decoding video") { p ->
            importer.importVideo(pv.uri, startMs, lengthMs, quality, fps, p)
        }
    }

    suspend fun videoThumb(uri: Uri, atMs: Long): ImageBitmap? =
        importer.videoThumbnail(uri, atMs)?.asImageBitmap()

    // -- editing (instant in memory, debounced to disk and to the widgets) -----------------------

    private fun edit(id: String, pushToWidgets: Boolean = true, transform: (LivingFrame) -> LivingFrame) {
        repo.update(id, transform)
        if (pushToWidgets) scheduleWidgetRefresh(id)
    }

    private fun scheduleWidgetRefresh(id: String) {
        refreshJobs.remove(id)?.cancel()
        refreshJobs[id] = viewModelScope.launch {
            delay(600L)
            LivingFramesWidgetHelper.refreshFrame(context, repo, id)
        }
    }

    fun setFps(id: String, fps: Int) = edit(id) { it.copy(fps = LivingFrameFps.snap(fps)) }

    fun setSpeed(id: String, speed: Float) =
        edit(id) { it.copy(speed = LivingFrameSpeed.snap(speed)) }

    fun setResolution(id: String, percent: Int) =
        edit(id) { it.copy(resolutionPercent = LivingFrameResolution.snap(percent)) }

    fun setLoopMode(id: String, mode: LivingFrameLoopMode) = edit(id) { it.copy(loopMode = mode) }

    fun setLoopTransition(id: String, transition: LivingFrameLoopTransition) =
        edit(id) { it.copy(loopTransition = transition) }
    fun setFit(id: String, fit: LivingFrameFit) = edit(id) { it.copy(fit = fit) }

    fun setTapAction(id: String, action: LivingFrameTapAction) = edit(id) { frame ->
        if (action == LivingFrameTapAction.OPEN_TOOL && frame.tapTargetRoute.isBlank()) {
            frame.copy(
                tapAction = action,
                tapTargetRoute = LivingFrameTools.ALL.first().route
            )
        } else {
            frame.copy(tapAction = action)
        }
    }

    fun setTapTarget(id: String, route: String) = edit(id) { frame ->
        frame.copy(
            tapAction = LivingFrameTapAction.OPEN_TOOL,
            tapTargetRoute = LivingFrameTools.routeOrNull(route) ?: frame.tapTargetRoute
        )
    }
    fun setOpacity(id: String, opacity: Float) = edit(id) { it.copy(opacity = opacity.coerceIn(0.4f, 1f)) }
    fun setCorner(id: String, dp: Int) =
        edit(id) { it.copy(cornerRadiusDp = LivingFrameCorners.snap(dp)) }
    fun setShowCaption(id: String, show: Boolean) = edit(id) { it.copy(showCaption = show) }
    fun setShuffle(id: String, shuffle: Boolean) = edit(id, pushToWidgets = false) { it.copy(shuffle = shuffle) }

    fun setName(id: String, name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        edit(id, pushToWidgets = repo.get(id)?.showCaption == true) { it.copy(name = clean) }
    }

    fun setSlideshowInterval(id: String, mins: Int) {
        val clamped = mins.coerceIn(1, 1440)
        edit(id, pushToWidgets = false) { it.copy(slideshowIntervalMin = clamped) }
        repo.boundWidgetIds(id).forEach { LivingFramesSlideshow.schedule(context, it, clamped) }
    }

    // -- widget actions --------------------------------------------------------------------------------

    fun pinToHome(frame: LivingFrame): Boolean {
        LivingFramesWidgetHelper.setPendingPinFrame(context, frame.id)
        return LivingFramesWidgetHelper.requestPin(context, frame)
    }

    fun refreshWidget(widgetId: Int) {
        viewModelScope.launch {
            val ok = LivingFramesWidgetHelper.refresh(context, repo, widgetId)
            _messages.emit(if (ok) "Widget updated" else "That widget couldn't be updated. See the note under it.")
        }
    }

    fun skipSlide(frameId: String) {
        viewModelScope.launch {
            val ids = repo.boundWidgetIds(frameId)
            if (ids.isEmpty()) {
                _messages.emit("Add the slideshow to your home screen first.")
                return@launch
            }
            ids.forEach { LivingFramesWidgetHelper.advanceSlideshow(context, repo, it) }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val widgetIds = repo.boundWidgetIds(id)
            if (!repo.delete(id)) {
                _messages.emit("Couldn't delete this set. Free some storage and try again.")
                return@launch
            }
            refreshJobs.remove(id)?.cancel()
            widgetIds.forEach { wid ->
                LivingFramesRenderWorker.cancel(context, wid)
                LivingFramesWidgetHelper.pushPlaceholder(context, wid, "This widget was deleted. Long-press to remove it.")
                LivingFramesSlideshow.cancel(context, wid)
                LivingFramesWidgetHelper.clearInfo(context, wid)
            }
            synchronized(previewCache) { previewCache.keys.removeAll { it.startsWith("$id:") } }
            refreshStorage()
            _messages.emit("Widget set deleted")
        }
    }

    // -- in-app preview ------------------------------------------------------------------------------------

    // Only one preview set is visible at a time. Keeping a second animation
    // list can retain tens of megabytes of decoded bitmaps on a large device.
    private val previewCache = object : LinkedHashMap<String, List<ImageBitmap>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ImageBitmap>>?) = size > 1
    }

    /**
     * Frames for the preview. Corners, fit and opacity are applied by Compose at
     * draw time, so sliders respond instantly and no decode happens per tick.
     */
    suspend fun previewFrames(frame: LivingFrame): List<ImageBitmap> {
        val key = "${frame.id}:${frame.createdAt}"
        val cached = synchronized(previewCache) {
            val hit = previewCache[key]
            // The previous set is no longer on screen. Drop it before decoding
            // the next one so switching sets never holds two full previews in
            // memory at the same time.
            if (hit == null) previewCache.clear()
            hit
        }
        if (cached != null) return cached
        val list = withContext(Dispatchers.IO) {
            val count = when {
                frame.isSlideshow -> minOf(frame.frameCount, 8)
                // A video preview shows the first half of the clip. Keeping
                // the whole clip would either take too long to prepare or
                // crowd out the widget's own bitmap budget.
                frame.type == LivingFrameType.VIDEO ->
                    (frame.frameCount / 2).coerceAtLeast(1)
                else -> frame.frameCount
            }
            // Decoding all 900 frames at preview size would exhaust memory.
            // Preview the opening seconds at the real rate; LivePreview notes
            // when it is showing an excerpt, and the widget still plays all
            // stored frames. The byte budget adapts to wide/tall widgets.
            val previewCount = if (frame.isSlideshow) count else minOf(count, MAX_PREVIEW_FRAMES)
            val out = ArrayList<ImageBitmap>(previewCount.coerceAtMost(MAX_PREVIEW_FRAMES))
            var usedBytes = 0L
            for (i in 0 until previewCount) {
                if (out.isNotEmpty() && usedBytes >= PREVIEW_BUDGET_BYTES) break
                val src = repo.decodeAtLeast(frame.id, i, PREVIEW_SIDE) ?: continue
                val bmp = capSide(src, PREVIEW_SIDE + 40)
                usedBytes += bmp.byteCount
                out.add(bmp.asImageBitmap())
            }
            out
        }
        if (list.isNotEmpty()) synchronized(previewCache) { previewCache[key] = list }
        return list
    }

    private fun capSide(bmp: Bitmap, side: Int): Bitmap {
        val longest = maxOf(bmp.width, bmp.height)
        if (longest <= side) return bmp
        val s = side / longest.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            bmp, (bmp.width * s).roundToInt().coerceAtLeast(1), (bmp.height * s).roundToInt().coerceAtLeast(1), true
        )
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }

    private companion object {
        const val PREVIEW_SIDE = 420
        const val MAX_PREVIEW_FRAMES = 90

        /**
         * Keep the whole preview under ~24 MB. The editor only shows one set
         * at a time, and this leaves room for the import pipeline and the
         * launcher bitmaps on mid-range devices.
         */
        const val PREVIEW_BUDGET_BYTES = 24L * 1024 * 1024
    }
}

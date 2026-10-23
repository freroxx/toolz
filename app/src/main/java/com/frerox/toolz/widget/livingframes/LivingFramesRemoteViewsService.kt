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

package com.frerox.toolz.widget.livingframes

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.frerox.toolz.R
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameFps
import com.frerox.toolz.data.livingframes.LivingFrameQuality
import com.frerox.toolz.data.livingframes.LivingFrameRenderer
import com.frerox.toolz.data.livingframes.LivingFrameResolution
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Supplies one frame at a time to an `AdapterViewFlipper` in the launcher.
 *
 * A collection widget sends each frame in its own transaction, so a 900-frame
 * 30 fps clip can render at the widget's native resolution instead of being
 * crushed into the binder's bitmap budget.
 *
 * Smoothness rule: the host asks for a position and immediately shows the
 * result. Decoding synchronously on that call made the widget flash the
 * loading view whenever the launcher ran slightly ahead. This factory
 * prefetches a window ahead, keeps a bounded bitmap cache, and returns the
 * last good frame (never an empty RemoteViews) on a miss.
 */
class LivingFramesRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        LivingFramesFactory(applicationContext, intent)
}

private class LivingFramesFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val repo: LivingFramesRepository? by lazy {
        try {
            EntryPointAccessors.fromApplication(
                context.applicationContext, LivingFramesEntryPoint::class.java
            ).livingFramesRepository()
        } catch (t: Throwable) {
            Log.w(TAG, "Couldn't reach the Living Widgets repository", t)
            null
        }
    }

    @Volatile private var frame: LivingFrame? = null
    @Volatile private var order: IntArray = IntArray(0)
    @Volatile private var outW: Int = 0
    @Volatile private var outH: Int = 0
    @Volatile private var density: Float = 1f
    @Volatile private var rgb565: Boolean = false

    /** Incremented on every data-set change so stale prefetches can stop. */
    @Volatile private var generation: Int = 0

    /**
     * Recently rendered frames, in access order. The launcher may still draw a
     * view after it leaves this cache, so evicted bitmaps are left to the GC
     * rather than recycled underneath it.
     */
    private val recent = LinkedHashMap<Int, Bitmap>(24, 0.75f, true)
    private val pending = HashSet<Int>()

    /** Last frame handed to the launcher; used instead of a blank on a miss. */
    @Volatile private var last: Bitmap? = null

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "living-frames-prefetch").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    private val appWidgetId: Int = run {
        val fromExtra = intent.getIntExtra(
            LivingFramesWidgetHelper.EXTRA_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (fromExtra != AppWidgetManager.INVALID_APPWIDGET_ID) {
            fromExtra
        } else {
            intent.data?.pathSegments?.firstOrNull()?.toIntOrNull()
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
        }
    }

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        generation++
        val currentGeneration = generation
        val repository = repo
        if (repository == null || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            clearFrames()
            return
        }
        // Metadata is small; a bounded synchronous load here is safer than
        // showing a blank widget because a coroutine had not finished yet.
        runBlocking(Dispatchers.IO) { repository.ensureLoaded() }
        val current = repository.boundFrame(appWidgetId)?.let { repository.get(it) }
        clearFrames()
        frame = current
        if (current == null || !current.isAnimated) return

        val displayFps = LivingFrameFps.snap(current.fps)
        order = LivingFrameEngine.displaySequence(
            current.frameCount,
            current.sourceFps,
            displayFps,
            current.speed,
            current.loopMode
        ).toIntArray()

        val mgr = AppWidgetManager.getInstance(context)
        val (w, h) = LivingFramesWidgetHelper.widgetSizePx(context, mgr, appWidgetId)
        val resolution = LivingFrameResolution.snap(current.resolutionPercent)
        outW = (w * resolution / 100f).roundToInt().coerceAtLeast(48)
        outH = (h * resolution / 100f).roundToInt().coerceAtLeast(48)
        density = context.resources.displayMetrics.density
        // 16-bit only for the compact preset; High/Ultra keep full colour.
        rgb565 = current.quality == LivingFrameQuality.COMPACT &&
            LivingFrameRenderer.isOpaque(current)

        // Render frame zero before the host asks. This is the frame the
        // loading view and the first flipper child both use.
        if (order.isNotEmpty()) {
            rendered(current, order[0], currentGeneration)
            prefetch(current, 0, currentGeneration)
        }
    }

    override fun getViewAt(position: Int): RemoteViews {
        val current = frame
        val ord = order
        if (current == null || ord.isEmpty()) {
            return placeholderViews()
        }
        val currentGeneration = generation
        val frameIndex = ord[position % ord.size]
        val bitmap = rendered(current, frameIndex, currentGeneration)
            ?: last
        // Keep the next few frames ready while the launcher is drawing this
        // one. The single worker also prevents a burst of binder decodes.
        prefetch(current, position, currentGeneration)
        return frameViews(bitmap)
    }

    private fun rendered(
        current: LivingFrame,
        frameIndex: Int,
        requestedGeneration: Int
    ): Bitmap? {
        synchronized(recent) { recent[frameIndex] }?.let { return it }
        val repository = repo ?: return null
        val required = LivingFrameEngine.requiredSourceSide(
            outW, outH, current.width, current.height, current.fit
        )
        val src = runBlocking(Dispatchers.IO) {
            repository.decodeAtLeast(current.id, frameIndex, required)
        } ?: return null
        val bmp = try {
            LivingFrameRenderer.render(
                src = src,
                frame = current,
                outW = outW,
                outH = outH,
                density = density,
                rgb565 = rgb565
            )
        } finally {
            runCatching { src.recycle() }
        }
        // A data-set change while this decode was in flight means the bitmap
        // belongs to a stale order; don't poison the new cache with it.
        if (requestedGeneration == generation) remember(frameIndex, bmp)
        return bmp
    }

    private fun remember(frameIndex: Int, bitmap: Bitmap) {
        synchronized(recent) {
            recent[frameIndex] = bitmap
            last = bitmap
            var total = 0L
            recent.values.forEach { total += it.byteCount }
            val oldest = recent.entries.iterator()
            while (recent.size > 1 &&
                (recent.size > MAX_RECENT_FRAMES || total > MAX_RECENT_BYTES) &&
                oldest.hasNext()
            ) {
                val entry = oldest.next()
                total -= entry.value.byteCount
                oldest.remove()
            }
        }
    }

    /**
     * Decode [PREFETCH_AHEAD] positions ahead on a single background thread.
     * If the launcher jumps, the worker simply continues from the new
     * position; the generation check stops work for an obsolete data set.
     */
    private fun prefetch(
        current: LivingFrame,
        startPosition: Int,
        requestedGeneration: Int
    ) {
        val ord = order
        if (ord.isEmpty()) return
        executor.execute {
            for (offset in 1..PREFETCH_AHEAD) {
                if (requestedGeneration != generation) return@execute
                val position = (startPosition + offset) % ord.size
                val frameIndex = ord[position]
                val alreadyQueued = synchronized(recent) {
                    if (recent.containsKey(frameIndex) || pending.contains(frameIndex)) {
                        true
                    } else {
                        pending.add(frameIndex)
                        false
                    }
                }
                if (alreadyQueued) continue
                try {
                    rendered(current, frameIndex, requestedGeneration)
                } finally {
                    synchronized(recent) { pending.remove(frameIndex) }
                }
            }
        }
    }

    private fun frameViews(bitmap: Bitmap?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.living_frames_frame)
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.living_frame_item, bitmap)
        } else {
            // Never hand the host an empty image: a solid tile reads as a
            // deliberate loading state instead of a flicker to transparent.
            views.setInt(
                R.id.living_frame_item,
                "setBackgroundColor",
                PLACEHOLDER_COLOR
            )
        }
        return views
    }

    private fun placeholderViews(): RemoteViews = frameViews(last)

    override fun getCount(): Int = order.size

    override fun getLoadingView(): RemoteViews {
        val ord = order
        val first = if (ord.isNotEmpty()) {
            synchronized(recent) { recent[ord[0]] }
        } else {
            null
        }
        return frameViews(first ?: last)
    }

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    override fun onDestroy() {
        executor.shutdownNow()
        clearFrames()
    }

    private fun clearFrames() {
        synchronized(recent) {
            recent.clear()
            pending.clear()
        }
        last = null
    }

    private companion object {
        const val TAG = "LivingFramesRVS"
        const val PREFETCH_AHEAD = 12
        const val MAX_RECENT_FRAMES = 24
        const val MAX_RECENT_BYTES = 48L * 1024 * 1024
        val PLACEHOLDER_COLOR = Color.rgb(24, 24, 24)
    }
}

/** Kept outside the service so both paths agree on the adapter URI shape. */
internal fun livingFramesAdapterUri(appWidgetId: Int, frameId: String): Uri =
    Uri.parse("toolz://living-frames/$appWidgetId/$frameId")

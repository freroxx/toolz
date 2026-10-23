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
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.frerox.toolz.R
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameQuality
import com.frerox.toolz.data.livingframes.LivingFrameRenderer
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.math.max

/**
 * Supplies one frame at a time to an `AdapterViewFlipper` in the launcher.
 *
 * The old widget put every frame into one RemoteViews, so the binder's bitmap
 * budget (roughly 6 × screen area) forced long clips down to a couple of
 * hundred pixels. A collection widget sends each frame in its own transaction,
 * which removes that ceiling: a 900-frame 30 fps clip can render at the
 * widget's native resolution.
 *
 * Frames are decoded and composited on demand. The host's RemoteViewsAdapter
 * keeps a small window of views around the current position, so playback is
 * smooth without holding the whole sequence in memory.
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

    private var frame: LivingFrame? = null
    private var order: IntArray = IntArray(0)
    private var outW: Int = 0
    private var outH: Int = 0
    private var density: Float = 1f
    private var rgb565: Boolean = false

    /**
     * Recently requested frames. The host may ask for the same position twice
     * while preloading; keeping a handful avoids decoding twice. Evicted
     * bitmaps are left to the GC because the launcher may still be drawing
     * the view that references them. A byte cap matters: a full-screen widget
     * frame can be 10 MB on its own.
     */
    private val recent = LinkedHashMap<Int, Bitmap>(8, 0.75f, true)

    private fun remember(frameIndex: Int, bitmap: Bitmap) {
        synchronized(recent) {
            recent[frameIndex] = bitmap
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
        val repository = repo
        if (repository == null || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            frame = null
            order = IntArray(0)
            return
        }
        // Metadata is small; a bounded synchronous load here is safer than
        // showing a blank widget because a coroutine had not finished yet.
        runBlocking(Dispatchers.IO) { repository.ensureLoaded() }
        val current = repository.boundFrame(appWidgetId)?.let { repository.get(it) }
        frame = current
        if (current == null || !current.isAnimated) {
            order = IntArray(0)
            return
        }
        order = LivingFrameEngine.playbackOrder(current.frameCount, current.loopMode).toIntArray()
        val mgr = AppWidgetManager.getInstance(context)
        val (w, h) = LivingFramesWidgetHelper.widgetSizePx(context, mgr, appWidgetId)
        outW = w.coerceAtLeast(96)
        outH = h.coerceAtLeast(96)
        density = context.resources.displayMetrics.density
        // 16-bit only for the compact preset; High/Ultra keep full colour.
        rgb565 = current.quality == LivingFrameQuality.COMPACT &&
            LivingFrameRenderer.isOpaque(current)
        synchronized(recent) { recent.clear() }
    }

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.living_frames_frame)
        val current = frame ?: return views
        if (order.isEmpty()) return views
        val frameIndex = order[position % order.size]
        val bitmap = rendered(current, frameIndex) ?: return views
        views.setImageViewBitmap(R.id.living_frame_item, bitmap)
        return views
    }

    private fun rendered(current: LivingFrame, frameIndex: Int): Bitmap? {
        synchronized(recent) { recent[frameIndex] }?.let { return it }
        val repository = repo ?: return null
        val required = max(outW, outH)
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
        remember(frameIndex, bmp)
        return bmp
    }

    override fun getCount(): Int = order.size

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    override fun onDestroy() {
        synchronized(recent) { recent.clear() }
    }

    private companion object {
        const val TAG = "LivingFramesRVS"
        const val MAX_RECENT_FRAMES = 6
        const val MAX_RECENT_BYTES = 32L * 1024 * 1024
    }
}

/** Kept outside the service so both paths agree on the adapter URI shape. */
internal fun livingFramesAdapterUri(appWidgetId: Int, frameId: String): Uri =
    Uri.parse("toolz://living-frames/$appWidgetId/$frameId")

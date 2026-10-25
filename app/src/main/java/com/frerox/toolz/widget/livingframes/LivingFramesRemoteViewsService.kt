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
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.frerox.toolz.R
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameFit
import com.frerox.toolz.data.livingframes.LivingFrameLoopTransition
import com.frerox.toolz.data.livingframes.LivingFrameType
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameQuality
import com.frerox.toolz.data.livingframes.LivingFrameRenderer
import com.frerox.toolz.data.livingframes.LivingFrameResolution
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Supplies one frame at a time to an `AdapterViewFlipper` in the launcher.
 *
 * This is the fallback path for loops too long to embed in one RemoteViews.
 * The static path (see [LivingFramesWidgetHelper]) is preferred because it
 * cannot miss a frame; when streaming is unavoidable, the frames are kept
 * small enough for the host's ~2 MB loaded-view cache and a miss reuses the
 * last displayed frame instead of flashing back to frame zero.
 *
 * Playback contract:
 *  - The flipper runs at [LivingFrameEngine.playbackFps]; the order handed
 *    out here is resampled for exactly that interval, so loop length follows
 *    fps and speed.
 *  - Seam blends are ordinary [LivingFrameEngine.DisplayStep] values. Normal
 *    steps share a cached bitmap; blend steps are keyed by their from/to/mix
 *    pair, so the cache still deduplicates repeated normal frames.
 *  - Cache eviction follows the playhead rather than LRU: the frame needed
 *    soonest must survive.
 */
class LivingFramesRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        LivingFramesFactory(applicationContext, intent)
}

/** Playback counters, logged every few seconds when the log tag is DEBUG. */
private class Stats {
    val hit = AtomicInteger()
    val waited = AtomicInteger()
    val inlineMisses = AtomicInteger()
    val stale = AtomicInteger()
    val produced = AtomicInteger()
    val fromProxy = AtomicInteger()
    val produceNanos = AtomicLong()
    val maxProduceMs = AtomicLong()
    @Volatile var lastLogAt = 0L
}

private class Session(
    val id: Int,
    val frame: LivingFrame,
    val steps: Array<LivingFrameEngine.DisplayStep>,
    val outW: Int,
    val outH: Int,
    val densityX: Float,
    val densityY: Float,
    val rgb565: Boolean,
    val resident: Boolean,
    val capacity: Int,
    val ahead: Int,
    /** Widget-size proxy folder, or null when this set cannot be proxied. */
    val proxyDir: File?,
    val proxyWritable: Boolean
) {
    val stats = Stats()
    val lock = ReentrantLock()
    val changed = lock.newCondition()
    val cache = HashMap<LivingFrameEngine.DisplayStep, Bitmap>()
    val pending = HashSet<LivingFrameEngine.DisplayStep>()
    val cursor = AtomicInteger(0)
    val active = AtomicInteger(0)

    /** Resident session with every unique step rendered: nothing left to do. */
    @Volatile var warm = false

    /** Display positions at which each step appears. */
    private val positions: Map<LivingFrameEngine.DisplayStep, IntArray> = run {
        val map = HashMap<LivingFrameEngine.DisplayStep, MutableList<Int>>()
        steps.forEachIndexed { position, step ->
            map.getOrPut(step) { ArrayList(1) }.add(position)
        }
        map.mapValues { it.value.toIntArray() }
    }

    val unique: Int = positions.size

    /** Display ticks until [step] is next needed, from the playhead. */
    fun distance(step: LivingFrameEngine.DisplayStep): Int {
        val n = steps.size
        val c = cursor.get()
        var best = Int.MAX_VALUE
        positions[step]?.forEach { p ->
            val d = (p - c + n) % n
            if (d < best) best = d
        }
        return if (best == Int.MAX_VALUE) n else best
    }

    fun release() = lock.withLock {
        cache.clear()
        pending.clear()
        changed.signalAll()
    }
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

    @Volatile private var session: Session? = null

    /** Last frame handed to the launcher; used instead of frame zero on a miss. */
    @Volatile private var last: Bitmap? = null

    /** Radius the host clips to; updated with the session's frame. */
    @Volatile private var cornerDp: Int = 0

    /**
     * A one-pixel neutral tile, scaled by the host. Drawing it through the
     * rounded outline gives a clean loading state at any widget size without
     * pinning the first video frame into the host's permanent template.
     */
    private val placeholder = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
        eraseColor(PLACEHOLDER_COLOR)
    }

    private val sessionIds = AtomicInteger(0)
    private val proxies = LivingFrameProxyCache(context)
    private val registered = AtomicBoolean(false)

    // Two workers: WebP decode is the bottleneck, and the host's loader
    // thread decodes inline on a miss, so this is the useful parallelism.
    private val executor = Executors.newFixedThreadPool(WORKERS) { runnable ->
        Thread(runnable, "living-frames-decode").apply {
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

    private val startIndex: Int = run {
        val fromExtra = intent.getIntExtra(
            LivingFramesWidgetHelper.EXTRA_START_INDEX, -1
        )
        val fromUri = intent.data?.pathSegments?.getOrNull(2)?.toIntOrNull() ?: 0
        (if (fromExtra >= 0) fromExtra else fromUri).coerceAtLeast(0)
    }

    override fun onCreate() {
        if (registered.compareAndSet(false, true)) ACTIVE_FACTORIES.incrementAndGet()
    }

    override fun onDataSetChanged() {
        val repository = repo
        if (repository == null || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            retire()
            return
        }
        // Metadata is small; a bounded synchronous load here is safer than
        // showing a blank widget because a coroutine had not finished yet.
        runBlocking(Dispatchers.IO) { repository.ensureLoaded() }
        val current = repository.boundFrame(appWidgetId)?.let { repository.get(it) }
        if (current == null || !current.isAnimated) {
            retire()
            return
        }

        val rate = LivingFrameEngine.playbackFps(current)
        val baseSteps = LivingFrameEngine.displaySteps(current, rate)
        val rotated = LivingFrameEngine.rotateSteps(baseSteps, startIndex)
        if (rotated.isEmpty()) {
            retire()
            return
        }
        val steps = rotated.toTypedArray()

        val mgr = AppWidgetManager.getInstance(context)
        val (w, h) = LivingFramesWidgetHelper.widgetSizePx(context, mgr, appWidgetId)
        val resolution = LivingFrameResolution.snap(current.resolutionPercent)
        var outW = (w * resolution / 100f).roundToInt().coerceAtLeast(48)
        var outH = (h * resolution / 100f).roundToInt().coerceAtLeast(48)
        // The launcher's adapter keeps only a small amount of loaded views
        // (about 2 MB in AOSP). Keeping each frame near 2 MB / 6 means it can
        // hold several frames ahead, so a flip almost never finds its view
        // still loading and falls back to the loading view.
        val opaque = LivingFrameRenderer.isOpaqueForWidget(current)
        val compact = opaque && current.quality == LivingFrameQuality.COMPACT
        // Opaque video has no alpha to preserve, so spend the same host bytes
        // on twice the pixels instead of downscaling. Corners are clipped by
        // the launcher, which is why an opaque video with rounded corners can
        // still use RGB_565.
        val targetPixels = outW.toLong() * outH
        val rgb565 = opaque && (compact || targetPixels * 4L > HOST_FRAME_BYTES)
        val hostBpp = if (rgb565) 2L else 4L
        val maxPixels = HOST_FRAME_BYTES / hostBpp
        val pixelCount = targetPixels
        if (pixelCount > maxPixels) {
            val f = sqrt(maxPixels.toDouble() / pixelCount)
            outW = (outW * f).roundToInt().coerceAtLeast(48)
            outH = (outH * f).roundToInt().coerceAtLeast(48)
        }

        // Size the cache from real numbers, not a frame-count guess.
        val budget = budgetBytes()
        val unique = steps.toHashSet().size
        val pixels = outW.toLong() * outH
        val frameBytes = pixels * if (rgb565) 2L else 4L
        val resident = unique * frameBytes <= budget
        val capacity = if (resident) {
            unique
        } else {
            (budget / frameBytes).toInt().coerceIn(MIN_WINDOW, MAX_WINDOW)
        }
        val ahead = if (resident) {
            steps.size
        } else {
            ceil(rate * PREFETCH_SECONDS).toInt()
                .coerceIn(MIN_AHEAD, MAX_AHEAD)
                .coerceAtMost((capacity - 3).coerceAtLeast(2))
        }

        val proxyDir = if (current.type == LivingFrameType.VIDEO && current.fit == LivingFrameFit.FILL) {
            proxies.dirFor(current.id, outW, outH)
        } else {
            null
        }

        val density = context.resources.displayMetrics.density
        cornerDp = current.cornerRadiusDp
        val s = Session(
            id = sessionIds.incrementAndGet(),
            frame = current,
            steps = steps,
            outW = outW,
            outH = outH,
            densityX = LivingFrameEngine.densityScale(density, outW, w),
            densityY = LivingFrameEngine.densityScale(density, outH, h),
            rgb565 = rgb565,
            resident = resident,
            capacity = capacity,
            ahead = ahead,
            proxyDir = proxyDir,
            proxyWritable = proxyDir != null && proxies.hasSpace()
        )
        val old = session
        session = s
        old?.release()

        // The loading view and the first child should be ready before the
        // host asks, then the workers run ahead of the playhead.
        obtain(s, steps[0], FIRST_FRAME_WAIT_MS)?.let { last = it }
        schedule(s)
        if (proxyDir != null) {
            try {
                executor.execute {
                    proxies.prune(current.id, proxyDir) { id -> repository.get(id) != null }
                }
            } catch (_: RejectedExecutionException) {
            }
        }
    }

    override fun getViewAt(position: Int): RemoteViews {
        val s = session ?: return frameViews(last)
        val n = s.steps.size
        if (n == 0) return frameViews(last)
        val pos = ((position % n) + n) % n
        s.cursor.set(pos)
        // Start the workers before we spend time on an inline decode.
        schedule(s)
        val fresh = obtain(s, s.steps[pos], MISS_WAIT_MS)
        if (fresh == null) s.stats.stale.incrementAndGet()
        val bitmap = fresh ?: last
        if (bitmap != null) last = bitmap
        logStats(s)
        return frameViews(bitmap)
    }

    /**
     * Cached frame, the result of an in-flight decode, or a fresh decode on
     * this thread. Exactly one thread decodes a given step at a time.
     */
    private fun obtain(
        s: Session,
        step: LivingFrameEngine.DisplayStep,
        waitMs: Long
    ): Bitmap? {
        s.lock.withLock {
            s.cache[step]?.let {
                s.stats.hit.incrementAndGet()
                return it
            }
            if (!s.pending.add(step)) {
                s.stats.waited.incrementAndGet()
                var nanos = TimeUnit.MILLISECONDS.toNanos(waitMs)
                try {
                    while (step in s.pending && nanos > 0L) {
                        nanos = s.changed.awaitNanos(nanos)
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                return s.cache[step]
            }
        }
        s.stats.inlineMisses.incrementAndGet()
        return complete(s, step)
    }

    /** Decode + render + store. The caller must own `pending[step]`. */
    private fun complete(s: Session, step: LivingFrameEngine.DisplayStep): Bitmap? {
        var bitmap: Bitmap? = null
        val startedAt = SystemClock.elapsedRealtimeNanos()
        try {
            bitmap = produce(s, step)
            val nanos = SystemClock.elapsedRealtimeNanos() - startedAt
            s.stats.produced.incrementAndGet()
            s.stats.produceNanos.addAndGet(nanos)
            s.stats.maxProduceMs.accumulateAndGet(nanos / 1_000_000L) { a, b -> maxOf(a, b) }
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Out of memory rendering a Living Widget frame", e)
            s.lock.withLock { s.cache.clear() }
        } catch (t: Throwable) {
            Log.w(TAG, "Couldn't render a Living Widget frame", t)
        } finally {
            s.lock.withLock {
                if (bitmap != null) store(s, step, bitmap)
                s.pending.remove(step)
                s.changed.signalAll()
            }
        }
        return bitmap
    }

    private fun produce(s: Session, step: LivingFrameEngine.DisplayStep): Bitmap? {
        if (step.blended) return produceBlend(s, step)
        val proxyDir = s.proxyDir
        if (proxyDir != null) {
            // Fast path: the frame already exists at widget size.
            proxies.read(proxyDir, step.from, s.rgb565)?.let {
                s.stats.fromProxy.incrementAndGet()
                return finish(s, it)
            }
        }
        val repository = repo ?: return null
        val required = LivingFrameEngine.requiredSourceSide(
            s.outW, s.outH, s.frame.width, s.frame.height, s.frame.fit
        )
        val src = runBlocking(Dispatchers.IO) {
            repository.decodeAtLeast(s.frame.id, step.from, required)
        } ?: return null
        return try {
            if (proxyDir != null) {
                val base = LivingFrameRenderer.renderBase(src, s.frame, s.outW, s.outH, s.rgb565)
                // Next pass (and the next process start) skips the big decode.
                if (s.proxyWritable) proxies.write(proxyDir, step.from, base)
                finish(s, base)
            } else {
                LivingFrameRenderer.renderWidget(
                    src = src,
                    frame = s.frame,
                    outW = s.outW,
                    outH = s.outH,
                    densityX = s.densityX,
                    densityY = s.densityY,
                    rgb565 = s.rgb565
                )
            }
        } finally {
            runCatching { src.recycle() }
        }
    }

    /** Seam crossfade. Only a handful of steps per loop take this path. */
    private fun produceBlend(s: Session, step: LivingFrameEngine.DisplayStep): Bitmap? {
        val repository = repo ?: return null
        val required = LivingFrameEngine.requiredSourceSide(
            s.outW, s.outH, s.frame.width, s.frame.height, s.frame.fit
        )
        val from = runBlocking(Dispatchers.IO) {
            repository.decodeAtLeast(s.frame.id, step.from, required)
        } ?: return null
        val to = try {
            runBlocking(Dispatchers.IO) {
                repository.decodeAtLeast(s.frame.id, step.to, required)
            }
        } catch (t: Throwable) {
            runCatching { from.recycle() }
            throw t
        } ?: run {
            runCatching { from.recycle() }
            return null
        }
        return try {
            LivingFrameRenderer.renderBlendWidget(
                from = from,
                to = to,
                frame = s.frame,
                outW = s.outW,
                outH = s.outH,
                densityX = s.densityX,
                densityY = s.densityY,
                mix = step.mix,
                rgb565 = s.rgb565,
                soft = s.frame.loopTransition == LivingFrameLoopTransition.BLUR
            )
        } finally {
            runCatching { if (!from.isRecycled) from.recycle() }
            runCatching { if (!to.isRecycled) to.recycle() }
        }
    }

    /** Opacity on top of a widget-size base picture; corners are host-clipped. */
    private fun finish(s: Session, base: Bitmap): Bitmap {
        if (s.frame.opacity >= 0.999f) return base
        return try {
            LivingFrameRenderer.renderWidget(
                src = base,
                frame = s.frame,
                outW = s.outW,
                outH = s.outH,
                densityX = s.densityX,
                densityY = s.densityY,
                rgb565 = s.rgb565
            )
        } finally {
            runCatching { base.recycle() }
        }
    }

    private fun logStats(s: Session) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        val now = SystemClock.elapsedRealtime()
        val st = s.stats
        if (now - st.lastLogAt < 5_000L) return
        st.lastLogAt = now
        val produced = st.produced.get()
        val avgMs = if (produced > 0) st.produceNanos.get() / produced / 1_000_000.0 else 0.0
        Log.d(
            TAG,
            "widget=$appWidgetId hit=${st.hit.get()} waited=${st.waited.get()} " +
                    "inline=${st.inlineMisses.get()} stale=${st.stale.get()} " +
                    "produced=$produced fromProxy=${st.fromProxy.get()} " +
                    "avg=${"%.1f".format(avgMs)}ms max=${st.maxProduceMs.get()}ms " +
                    "resident=${s.resident} proxy=${s.proxyDir != null} " +
                    "out=${s.outW}x${s.outH} rgb565=${s.rgb565} " +
                    "positions=${s.steps.size} unique=${s.unique} cache=${s.cache.size}/${s.capacity}"
        )
    }

    /** Lock held. Evicted bitmaps are left to the GC: the host may still draw them. */
    private fun store(s: Session, step: LivingFrameEngine.DisplayStep, bitmap: Bitmap) {
        s.cache[step] = bitmap
        if (s.resident) {
            if (s.cache.size >= s.unique) s.warm = true
            return
        }
        while (s.cache.size > s.capacity) {
            var victim: LivingFrameEngine.DisplayStep? = null
            var farthest = -1
            for (key in s.cache.keys) {
                if (key == step) continue
                val d = s.distance(key)
                if (d > farthest) {
                    farthest = d
                    victim = key
                }
            }
            if (victim == null) break
            s.cache.remove(victim)
        }
    }

    private fun schedule(s: Session) {
        if (s.warm) return
        while (true) {
            val running = s.active.get()
            if (running >= WORKERS) return
            if (s.active.compareAndSet(running, running + 1)) break
        }
        try {
            executor.execute {
                try {
                    drain(s)
                } finally {
                    s.active.decrementAndGet()
                }
            }
        } catch (_: RejectedExecutionException) {
            s.active.decrementAndGet()
        }
    }

    /**
     * Render frames ahead of the playhead until the window (or, for a
     * resident loop, the whole loop) is full. The playhead is re-read on
     * every iteration, so a newer position naturally supersedes older work
     * and there is no queue to fall behind.
     */
    private fun drain(s: Session) {
        while (session === s && !s.warm) {
            val step = claimNext(s) ?: return
            complete(s, step)
        }
    }

    private fun claimNext(s: Session): LivingFrameEngine.DisplayStep? {
        val n = s.steps.size
        val c = s.cursor.get()
        s.lock.withLock {
            for (offset in 1..s.ahead) {
                val step = s.steps[(c + offset) % n]
                if (!s.cache.containsKey(step) && s.pending.add(step)) {
                    return step
                }
            }
        }
        return null
    }

    private fun frameViews(bitmap: Bitmap?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.living_frames_frame)
        // A miss or the host's loading template gets the neutral tile, never
        // frame zero: the template is created once and reused for the life of
        // the widget, so a first frame here would flash back on every loop.
        views.setImageViewBitmap(R.id.living_frame_item, bitmap ?: placeholder)
        // The bitmap is square; the launcher measures the rounded outline in
        // its own pixels, so the radius is exact at every widget size.
        LivingFramesCorners.apply(views, R.id.living_frame_item, cornerDp)
        return views
    }

    override fun getCount(): Int = session?.steps?.size ?: 0

    /**
     * Neutral, not the last frame: the host fixes this template at metadata
     * load time. Returning [last] there would keep showing that one frame on
     * every later miss, which reads as a jump back to the start of the loop.
     */
    override fun getLoadingView(): RemoteViews = frameViews(null)

    override fun getViewTypeCount(): Int = 1

    /**
     * The session id is part of the id, so a data-set change (new speed, fps,
     * resolution or frames) can never be matched to a stale host-side view
     * that happens to share a position.
     */
    override fun getItemId(position: Int): Long =
        ((session?.id ?: 0).toLong() shl 32) or position.toLong()

    override fun hasStableIds(): Boolean = true

    override fun onDestroy() {
        if (registered.compareAndSet(true, false)) ACTIVE_FACTORIES.decrementAndGet()
        retire()
        executor.shutdownNow()
    }

    private fun retire() {
        val old = session
        session = null
        old?.release()
        last = null
        cornerDp = 0
    }

    private companion object {
        const val TAG = "LivingFramesRVS"
        const val WORKERS = 2
        const val MB = 1024L * 1024L

        /** Total bitmap cache across all Living Widgets, split per widget. */
        const val MAX_TOTAL_CACHE = 64L * MB

        /**
         * Largest frame sent to the launcher's adapter. The AOSP host cache
         * is around 2 MB and holds a window of loaded views; 2 MB / 6 keeps
         * roughly six frames resident so a flip rarely misses.
         */
        const val HOST_FRAME_BYTES = 2L * MB / 6L

        const val MIN_WINDOW = 8
        const val MAX_WINDOW = 64
        const val MIN_AHEAD = 4
        const val MAX_AHEAD = 24
        const val PREFETCH_SECONDS = 0.6

        /** How long a miss waits on an in-flight decode before decoding itself. */
        const val MISS_WAIT_MS = 100L
        const val FIRST_FRAME_WAIT_MS = 2_000L

        val PLACEHOLDER_COLOR = Color.rgb(24, 24, 24)
        val ACTIVE_FACTORIES = AtomicInteger(0)

        fun budgetBytes(): Long {
            val total = minOf(Runtime.getRuntime().maxMemory() / 6L, MAX_TOTAL_CACHE)
            return total / ACTIVE_FACTORIES.get().coerceAtLeast(1)
        }
    }
}

/** Kept outside the service so both paths agree on the adapter URI shape. */
internal fun livingFramesAdapterUri(
    appWidgetId: Int,
    frameId: String,
    startIndex: Int = 0
): Uri = Uri.parse("toolz://living-frames/$appWidgetId/$frameId/${startIndex.coerceAtLeast(0)}")

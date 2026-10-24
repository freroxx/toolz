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

/**
 * Supplies one frame at a time to an `AdapterViewFlipper` in the launcher.
 *
 * Playback contract:
 *  - The flipper runs at [LivingFrameEngine.playbackFps]; the order handed
 *    out here is resampled for exactly that interval, so loop length follows
 *    fps and speed.
 *  - Rendered frames are cached. When the whole loop fits the memory budget
 *    it stays resident and, after the first pass, playback never decodes.
 *    Otherwise a sliding window is kept and frames are evicted by how far
 *    ahead of the playhead they are (the frame just shown is the first to
 *    go). Plain LRU is the worst policy for a cyclic scan: it evicts the
 *    frame needed soonest and misses on every frame.
 *  - A miss never returns a stale frame if it can be avoided. The host
 *    caches whatever it is handed for that position until its window
 *    moves, so a stale frame would repeat for a full pass. A miss waits
 *    briefly for an in-flight decode, or decodes inline on the host's
 *    loader thread, before falling back to the last frame.
 */
class LivingFramesRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        LivingFramesFactory(applicationContext, intent)
}

/**
 * Everything one data-set generation needs. Swapped atomically so
 * getViewAt never sees a half-updated order/size/cache combination, and a
 * decode that finishes after a swap lands in the retired session's cache.
 */
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
    val order: IntArray,
    val outW: Int,
    val outH: Int,
    val density: Float,
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
    val cache = HashMap<Int, Bitmap>()
    val pending = HashSet<Int>()
    val cursor = AtomicInteger(0)
    val active = AtomicInteger(0)

    /** Resident session with every unique frame rendered: nothing left to do. */
    @Volatile var warm = false

    /** Display positions at which each source frame appears. */
    private val positions: Array<IntArray?> = run {
        val size = maxOf(frame.frameCount, (order.maxOrNull() ?: 0) + 1)
        val counts = IntArray(size)
        order.forEach { counts[it]++ }
        val result = arrayOfNulls<IntArray>(size)
        val fill = IntArray(size)
        order.forEachIndexed { position, index ->
            val slots = result[index] ?: IntArray(counts[index]).also { result[index] = it }
            slots[fill[index]++] = position
        }
        result
    }

    val unique: Int = positions.count { it != null }

    /** Display ticks until [frameIndex] is next needed, from the playhead. */
    fun distance(frameIndex: Int): Int {
        val n = order.size
        val c = cursor.get()
        var best = Int.MAX_VALUE
        positions.getOrNull(frameIndex)?.forEach { p ->
            val d = (p - c + n) % n
            if (d < best) best = d
        }
        return best
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

    /** Last frame handed to the launcher; used instead of a blank on a miss. */
    @Volatile private var last: Bitmap? = null

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
        val order = LivingFrameEngine.displaySequence(
            current.frameCount,
            current.sourceFps,
            rate,
            current.speed,
            current.loopMode
        ).toIntArray()
        if (order.isEmpty()) {
            retire()
            return
        }

        val mgr = AppWidgetManager.getInstance(context)
        val (w, h) = LivingFramesWidgetHelper.widgetSizePx(context, mgr, appWidgetId)
        val resolution = LivingFrameResolution.snap(current.resolutionPercent)
        val outW = (w * resolution / 100f).roundToInt().coerceAtLeast(48)
        val outH = (h * resolution / 100f).roundToInt().coerceAtLeast(48)

        // Size the cache from real numbers, not a frame-count guess.
        val budget = budgetBytes()
        val unique = order.toHashSet().size
        val pixels = outW.toLong() * outH
        val opaque = LivingFrameRenderer.isOpaque(current)
        // 16-bit for the compact preset, and also whenever it is the only
        // way an opaque loop stays fully resident.
        val rgb565 = opaque && (
                current.quality == LivingFrameQuality.COMPACT ||
                        (unique * pixels * 4L > budget && unique * pixels * 2L <= budget)
                )
        val frameBytes = pixels * if (rgb565) 2L else 4L
        val resident = unique * frameBytes <= budget
        val capacity = if (resident) {
            unique
        } else {
            (budget / frameBytes).toInt().coerceIn(MIN_WINDOW, MAX_WINDOW)
        }
        val ahead = if (resident) {
            order.size
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

        val s = Session(
            id = sessionIds.incrementAndGet(),
            frame = current,
            order = order,
            outW = outW,
            outH = outH,
            density = context.resources.displayMetrics.density,
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

        // Frame zero is the loading view and the first child; have it ready
        // before the host asks, then let the workers run ahead of it.
        obtain(s, order[0], FIRST_FRAME_WAIT_MS)?.let { last = it }
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
        val n = s.order.size
        if (n == 0) return frameViews(last)
        val pos = ((position % n) + n) % n
        s.cursor.set(pos)
        // Start the workers before we spend time on an inline decode.
        schedule(s)
        val fresh = obtain(s, s.order[pos], MISS_WAIT_MS)
        if (fresh == null) s.stats.stale.incrementAndGet()
        val bitmap = fresh ?: last
        if (bitmap != null) last = bitmap
        logStats(s)
        return frameViews(bitmap)
    }

    /**
     * Cached frame, the result of an in-flight decode, or a fresh decode on
     * this thread. Exactly one thread decodes a given frame at a time.
     */
    private fun obtain(s: Session, frameIndex: Int, waitMs: Long): Bitmap? {
        s.lock.withLock {
            s.cache[frameIndex]?.let {
                s.stats.hit.incrementAndGet()
                return it
            }
            if (!s.pending.add(frameIndex)) {
                s.stats.waited.incrementAndGet()
                var nanos = TimeUnit.MILLISECONDS.toNanos(waitMs)
                try {
                    while (frameIndex in s.pending && nanos > 0L) {
                        nanos = s.changed.awaitNanos(nanos)
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                return s.cache[frameIndex]
            }
        }
        s.stats.inlineMisses.incrementAndGet()
        return complete(s, frameIndex)
    }

    /** Decode + render + store. The caller must own `pending[frameIndex]`. */
    private fun complete(s: Session, frameIndex: Int): Bitmap? {
        var bitmap: Bitmap? = null
        val startedAt = SystemClock.elapsedRealtimeNanos()
        try {
            bitmap = produce(s, frameIndex)
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
                if (bitmap != null) store(s, frameIndex, bitmap)
                s.pending.remove(frameIndex)
                s.changed.signalAll()
            }
        }
        return bitmap
    }

    private fun produce(s: Session, frameIndex: Int): Bitmap? {
        val proxyDir = s.proxyDir
        if (proxyDir != null) {
            // Fast path: the frame already exists at widget size.
            proxies.read(proxyDir, frameIndex, s.rgb565)?.let {
                s.stats.fromProxy.incrementAndGet()
                return finish(s, it)
            }
        }
        val repository = repo ?: return null
        val required = LivingFrameEngine.requiredSourceSide(
            s.outW, s.outH, s.frame.width, s.frame.height, s.frame.fit
        )
        val src = runBlocking(Dispatchers.IO) {
            repository.decodeAtLeast(s.frame.id, frameIndex, required)
        } ?: return null
        return try {
            if (proxyDir != null) {
                val base = LivingFrameRenderer.renderBase(src, s.frame, s.outW, s.outH, s.rgb565)
                // Next pass (and the next process start) skips the big decode.
                if (s.proxyWritable) proxies.write(proxyDir, frameIndex, base)
                finish(s, base)
            } else {
                LivingFrameRenderer.render(
                    src = src,
                    frame = s.frame,
                    outW = s.outW,
                    outH = s.outH,
                    density = s.density,
                    rgb565 = s.rgb565
                )
            }
        } finally {
            runCatching { src.recycle() }
        }
    }

    /** Corners and opacity on top of a widget-size base picture. */
    private fun finish(s: Session, base: Bitmap): Bitmap {
        if (s.frame.cornerRadiusDp == 0 && s.frame.opacity >= 0.999f) return base
        return try {
            LivingFrameRenderer.render(
                src = base,
                frame = s.frame,
                outW = s.outW,
                outH = s.outH,
                density = s.density,
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
                    "positions=${s.order.size} unique=${s.unique} cache=${s.cache.size}/${s.capacity}"
        )
    }

    /** Lock held. Evicted bitmaps are left to the GC: the host may still draw them. */
    private fun store(s: Session, frameIndex: Int, bitmap: Bitmap) {
        s.cache[frameIndex] = bitmap
        if (s.resident) {
            if (s.cache.size >= s.unique) s.warm = true
            return
        }
        while (s.cache.size > s.capacity) {
            var victim = -1
            var farthest = -1
            for (key in s.cache.keys) {
                if (key == frameIndex) continue
                val d = s.distance(key)
                if (d > farthest) {
                    farthest = d
                    victim = key
                }
            }
            if (victim < 0) break
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
            val frameIndex = claimNext(s) ?: return
            complete(s, frameIndex)
        }
    }

    private fun claimNext(s: Session): Int? {
        val n = s.order.size
        val c = s.cursor.get()
        s.lock.withLock {
            for (offset in 1..s.ahead) {
                val frameIndex = s.order[(c + offset) % n]
                if (!s.cache.containsKey(frameIndex) && s.pending.add(frameIndex)) {
                    return frameIndex
                }
            }
        }
        return null
    }

    private fun frameViews(bitmap: Bitmap?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.living_frames_frame)
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.living_frame_item, bitmap)
        } else {
            // Never hand the host an empty image: a solid tile reads as a
            // deliberate loading state instead of a flicker to transparent.
            views.setInt(R.id.living_frame_item, "setBackgroundColor", PLACEHOLDER_COLOR)
        }
        return views
    }

    override fun getCount(): Int = session?.order?.size ?: 0

    override fun getLoadingView(): RemoteViews {
        val s = session
        val first = s?.let { sess ->
            sess.lock.withLock { sess.cache[sess.order[0]] }
        }
        return frameViews(first ?: last)
    }

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
    }

    private companion object {
        const val TAG = "LivingFramesRVS"
        const val WORKERS = 2
        const val MB = 1024L * 1024L

        /** Total bitmap cache across all Living Widgets, split per widget. */
        const val MAX_TOTAL_CACHE = 64L * MB

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
internal fun livingFramesAdapterUri(appWidgetId: Int, frameId: String): Uri =
    Uri.parse("toolz://living-frames/$appWidgetId/$frameId")
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

package com.frerox.toolz.data.livingframes

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File + JSON storage. No Room.
 *  - frames/<id>/frame_XXXX.webp   stored frames
 *  - frames/<id>/cover.webp        small thumbnail for lists
 *  - frames.json                   metadata (written atomically)
 *  - living_frames_bindings prefs  appWidgetId -> frameId
 *
 * Setting edits (sliders etc.) update memory instantly and hit disk through
 * a debounced writer; structural changes (import, delete) flush immediately.
 */
@Singleton
class LivingFramesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val serializer = ListSerializer(LivingFrame.serializer())
    private val root: File get() = File(context.filesDir, "living_frames")
    private val metaFile: File get() = File(root, "frames.json")
    private val bindings: SharedPreferences by lazy {
        context.getSharedPreferences("living_frames_bindings", Context.MODE_PRIVATE)
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val loadLock = Mutex()
    private val persistSignal = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var loaded = false

    private val _frames = MutableStateFlow<List<LivingFrame>>(emptyList())
    val frames: Flow<List<LivingFrame>> = _frames.asStateFlow()

    init {
        ioScope.launch {
            for (signal in persistSignal) {
                delay(300L)
                writeNow()
            }
        }
    }

    // -- loading ---------------------------------------------------------

    /** Loads once per process; every caller (UI, receivers, workers) may call it freely. */
    suspend fun ensureLoaded() {
        if (loaded) return
        load()
    }

    suspend fun load() = loadLock.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (!metaFile.exists()) {
                    _frames.value = emptyList()
                    return@withContext
                }
                val list = json.decodeFromString(serializer, metaFile.readText())
                // Drop sets whose files are gone (cleared storage, partial deletes).
                val (valid, dropped) = list.partition { frameFile(it.id, 0).exists() }
                dropped.forEach { runCatching { frameDir(it.id).deleteRecursively() } }
                _frames.value = valid.sortedByDescending { it.createdAt }
                if (dropped.isNotEmpty()) writeNow()
            } catch (_: Exception) {
                _frames.value = emptyList()
            } finally {
                loaded = true
            }
        }
    }

    fun snapshot(): List<LivingFrame> = _frames.value
    fun get(id: String): LivingFrame? = _frames.value.firstOrNull { it.id == id }

    // -- paths -----------------------------------------------------------

    fun frameDir(id: String): File = File(root, id)
    fun ensureFrameDir(id: String): File = frameDir(id).apply { mkdirs() }
    fun frameFile(id: String, index: Int): File = File(frameDir(id), "frame_%04d.webp".format(index))
    fun coverFile(id: String): File = File(frameDir(id), "cover.webp")

    // -- decoding --------------------------------------------------------

    /**
     * Decodes frame [index] so its longest side is at least [minSide] (when
     * the stored file is that large), never more than 2× that. Using the
     * smallest power-of-two sample that still covers the target keeps widgets
     * sharp without decoding full-size files.
     */
    suspend fun decodeAtLeast(id: String, index: Int, minSide: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val f = frameFile(id, index)
                if (!f.exists()) return@withContext null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, bounds)
                if (bounds.outWidth <= 0) return@withContext null
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                var sample = 1
                while (longest / (sample * 2) >= minSide) sample *= 2
                BitmapFactory.decodeFile(
                    f.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            } catch (_: Exception) { null }
        }

    // -- mutation --------------------------------------------------------

    /** Insert or replace, flushed to disk before returning. */
    suspend fun upsert(frame: LivingFrame) {
        _frames.value = (_frames.value.filterNot { it.id == frame.id } + frame)
            .sortedByDescending { it.createdAt }
        withContext(Dispatchers.IO) { writeNow() }
    }

    /** In-memory immediately, persisted shortly after. Safe to call per slider tick. */
    fun update(id: String, transform: (LivingFrame) -> LivingFrame) {
        _frames.value = _frames.value.map { if (it.id == id) transform(it) else it }
        persistSignal.trySend(Unit)
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            runCatching { frameDir(id).deleteRecursively() }
            runCatching {
                val ed = bindings.edit()
                bindings.all.forEach { (k, v) -> if (v == id) ed.remove(k) }
                ed.apply()
            }
        }
        _frames.value = _frames.value.filterNot { it.id == id }
        withContext(Dispatchers.IO) { writeNow() }
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    private suspend fun writeNow() = writeLock.withLock {
        try {
            root.mkdirs()
            val tmp = File(root, "frames.json.tmp")
            tmp.writeText(json.encodeToString(serializer, _frames.value))
            if (!tmp.renameTo(metaFile)) {
                metaFile.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (_: Exception) {}
    }

    // -- widget bindings ---------------------------------------------------

    fun bindWidget(appWidgetId: Int, frameId: String) {
        bindings.edit().putString("w_$appWidgetId", frameId).apply()
    }

    fun unbindWidget(appWidgetId: Int) {
        bindings.edit().remove("w_$appWidgetId").apply()
    }

    fun boundFrame(appWidgetId: Int): String? = bindings.getString("w_$appWidgetId", null)

    fun boundWidgetIds(frameId: String): List<Int> =
        bindings.all.mapNotNull { (k, v) ->
            if (v == frameId && k.startsWith("w_")) k.removePrefix("w_").toIntOrNull() else null
        }

    /** Drops bindings for widgets the launcher no longer has. */
    fun pruneBindings(liveWidgetIds: Set<Int>): List<Int> {
        val stale = bindings.all.keys
            .filter { it.startsWith("w_") }
            .mapNotNull { it.removePrefix("w_").toIntOrNull() }
            .filter { it !in liveWidgetIds }
        if (stale.isNotEmpty()) {
            val ed = bindings.edit()
            stale.forEach { ed.remove("w_$it") }
            ed.apply()
        }
        return stale
    }

    companion object {
        /** Animated clips are short loops; the launcher holds every frame in memory. */
        const val MAX_FRAMES = 40
        const val MAX_SLIDES = 30
        const val STORE_SIDE_GIF = 540
        const val STORE_SIDE_VIDEO = 720
        const val STORE_SIDE_PHOTO = 1280
        const val COVER_SIDE = 400
    }
}

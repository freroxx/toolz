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
import android.util.Log
import com.frerox.toolz.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Storage accounting shown in the UI and enforced by the importer. */
data class LivingFramesStorage(
    val usedBytes: Long,
    val freeBytes: Long,
    val quotaBytes: Long,
    val setCount: Int,
    val orphanDirs: List<File>,
    val orphanBytes: Long
) {
    val available: Long get() = (quotaBytes - usedBytes).coerceAtLeast(0L)
    val fraction: Float
        get() = if (quotaBytes <= 0) 0f else (usedBytes.toFloat() / quotaBytes).coerceIn(0f, 1f)
}

data class StorageCheck(val ok: Boolean, val message: String? = null)

/**
 * File + JSON storage. No Room.
 *  - living_frames/<id>/frame_XXXX.webp  stored frames
 *  - living_frames/<id>/cover.webp       small thumbnail for lists
 *  - living_frames/frames.json           metadata (written atomically)
 *  - living_frames_bindings prefs        appWidgetId -> frameId
 *
 * Setting edits (sliders etc.) update memory instantly and hit disk through
 * a debounced writer; structural changes (import, delete) flush immediately.
 * A `.bak` copy is kept so a truncated metadata write never loses the
 * library.
 */
@Singleton
class LivingFramesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val serializer = ListSerializer(LivingFrame.serializer())
    private val root: File get() = File(context.filesDir, "living_frames")
    private val metaFile: File get() = File(root, "frames.json")
    private val backupFile: File get() = File(root, "frames.json.bak")
    private val bindings: SharedPreferences by lazy {
        context.getSharedPreferences("living_frames_bindings", Context.MODE_PRIVATE)
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val loadLock = Mutex()

    /**
     * Guards every read-modify-write of [_frames]. Slider edits are
     * synchronous while imports/deletes are suspending, so a Mutex alone
     * cannot cover both; this monitor keeps the in-memory list coherent and
     * gives the writer a consistent snapshot.
     */
    private val stateLock = Any()
    private val persistSignal = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var loaded = false
    @Volatile private var loadFailed = false
    @Volatile private var usedBackup = false

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
        // Double-check inside the lock: two callers that race here must not
        // each perform an initial load, and a load must not overwrite edits
        // made while the second caller was queued.
        loadLock.withLock {
            if (!loaded) loadInternal()
        }
    }

    /** Forced reload, used by the recovery UI after a metadata failure. */
    suspend fun load() = loadLock.withLock { loadInternal() }

    private suspend fun loadInternal() = withContext(Dispatchers.IO) {
        usedBackup = false
        try {
            if (!root.exists() && !root.mkdirs()) {
                throw IOException("Couldn't create the Living Widgets folder")
            }
            if (!root.isDirectory) {
                throw IOException("The Living Widgets path is not a folder")
            }
            runCatching { File(root, ".nomedia").createNewFile() }
            if (!metaFile.exists() && !backupFile.exists()) {
                setFrames(emptyList())
                loaded = true
                loadFailed = false
                return@withContext
            }

            val decoded = readMetadata()
            if (decoded == null) {
                // Do not erase the frame directories: a JSON failure is
                // recoverable, a bulk file delete is not.
                Log.e(TAG, "Living Widgets metadata is unreadable; keeping files on disk")
                loadFailed = true
                setFrames(emptyList())
                loaded = true
                return@withContext
            }

            var repaired = false
            val seen = HashSet<String>()
            val valid = ArrayList<LivingFrame>(decoded.size)
            for (candidate in decoded) {
                val frame = LivingFrameValidation.normalize(candidate) ?: continue
                if (!seen.add(frame.id)) continue
                val actual = repairFrameFiles(frame)
                if (actual == null) {
                    Log.w(TAG, "Dropping metadata for ${frame.id}: no frame files")
                    repaired = true
                    continue
                }
                if (actual != frame) repaired = true
                valid.add(actual)
            }
            if (usedBackup) {
                // A backup is one write behind. A set imported just before the
                // primary JSON was damaged is absent from the backup but its
                // files are still valid. Adopt those directories instead of
                // letting the orphan cleanup delete them.
                val recovered = recoverUnlistedSets(seen)
                if (recovered.isNotEmpty()) {
                    Log.w(TAG, "Recovered ${recovered.size} unlisted frame set(s)")
                    valid.addAll(recovered)
                    repaired = true
                }
            }
            setFrames(valid.sortedByDescending { it.createdAt })
            loaded = true
            // Only a successful read may clear the failure flag. A canceled
            // recovery retry must leave the UI/workers in the safe state.
            loadFailed = false
            if (repaired || usedBackup) writeNow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Living Widgets", e)
            loadFailed = true
            setFrames(emptyList())
            loaded = true
        }
    }

    private fun setFrames(frames: List<LivingFrame>) {
        synchronized(stateLock) { _frames.value = frames }
    }

    private fun readMetadata(): List<LivingFrame>? {
        val primary = runCatching {
            if (metaFile.exists()) json.decodeFromString(serializer, metaFile.readText()) else null
        }.getOrNull()
        if (primary != null) return primary
        val backup = runCatching {
            if (backupFile.exists()) json.decodeFromString(serializer, backupFile.readText()) else null
        }.getOrNull()
        if (backup != null) {
            usedBackup = true
            Log.w(TAG, "Recovered Living Widgets metadata from backup")
        }
        return backup
    }

    /** True when metadata could not be read; callers must not prune bindings in that state. */
    fun isLoadFailed(): Boolean = loadFailed

    /**
     * Directories with valid frames but no entry in the recovered backup are
     * almost always sets imported after the backup snapshot. Recovery cannot
     * know whether they were animations or slideshows, so multi-frame sets
     * become slideshows (all frames preserved); sets over the slideshow limit
     * can only be animations and stay animated. Nothing is deleted.
     */
    private fun recoverUnlistedSets(known: Set<String>): List<LivingFrame> {
        val dirs = root.listFiles { f -> f.isDirectory }.orEmpty()
        val now = System.currentTimeMillis()
        return dirs.mapNotNull { dir ->
            val id = dir.name
            if (id in known || !LivingFrameValidation.isValidId(id) || id.endsWith(".tmp")) {
                return@mapNotNull null
            }
            val contiguous = (0 until MAX_FRAMES).takeWhile { frameFile(id, it).exists() }.size
            if (contiguous <= 0) return@mapNotNull null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { BitmapFactory.decodeFile(frameFile(id, 0).path, bounds) }
            val type = when {
                contiguous == 1 -> LivingFrameType.PHOTO
                contiguous > MAX_SLIDES -> LivingFrameType.GIF
                else -> LivingFrameType.SLIDESHOW
            }
            LivingFrameValidation.normalize(
                LivingFrame(
                    id = id,
                    name = context.getString(R.string.lw_recovered_set),
                    type = type,
                    frameCount = contiguous,
                    width = bounds.outWidth.takeIf { it > 0 } ?: 1,
                    height = bounds.outHeight.takeIf { it > 0 } ?: 1,
                    slideshowIntervalMin = 1,
                    createdAt = dir.lastModified().takeIf { it > 0L } ?: now,
                    sourceLabel = context.getString(R.string.lw_recovered_source)
                )
            )
        }
    }

    /**
     * A killed import or an older write can leave holes in the numbered
     * frames. Compact them instead of deleting the whole set. Returns null
     * only when there is genuinely nothing to draw.
     */
    private fun repairFrameFiles(frame: LivingFrame): LivingFrame? {
        val dir = frameDir(frame.id)
        if (!dir.exists()) return null
        if (!dir.isDirectory) throw IOException("Frame path is not a folder: ${frame.id}")
        // A process kill between the two rename phases below leaves frames
        // under `.repair_N.tmp`. Put them back before listing, otherwise the
        // set would look empty and its metadata would be dropped.
        recoverRepairTemps(dir)
        val files = dir.listFiles { f ->
            f.isFile && f.name.startsWith("frame_") && f.name.endsWith(".webp")
        }?.sortedBy { it.name }
            ?: throw IOException("Couldn't list frames for ${frame.id}")
        if (files.isEmpty()) return null

        val max = when (frame.type) {
            LivingFrameType.SLIDESHOW -> MAX_SLIDES
            LivingFrameType.PHOTO -> 1
            else -> MAX_FRAMES
        }
        val count = files.size.coerceAtMost(max)
        val canonicalNames = (0 until count).map { frameName(it) }
        val needsRename = files.take(count).mapIndexed { i, file -> file.name != canonicalNames[i] }
            .any { it } || files.size > count
        if (!needsRename) return frame.copy(frameCount = count)

        // Rename through a temporary name so a hole cannot overwrite a file.
        val held = ArrayList<Pair<File, File>>(count)
        files.take(count).forEachIndexed { i, file ->
            val temp = File(dir, ".repair_$i.tmp")
            if (file.renameTo(temp)) held.add(temp to File(dir, canonicalNames[i]))
        }
        held.forEach { (temp, target) ->
            // On Unix rename replaces an existing target. If one source failed
            // to move, its file may still sit at this name; do not overwrite it.
            if (!target.exists()) runCatching { temp.renameTo(target) }
        }
        files.drop(count).forEach { runCatching { it.delete() } }
        // Verify the contiguous sequence that decoding will actually use.
        // Counting every frame_* file could overstate the set after a failed
        // rename or a failed delete, leaving metadata pointing at holes.
        val actual = (0 until max).takeWhile { frameFile(frame.id, it).exists() }.size
        if (actual <= 0) return null
        Log.w(TAG, "Compacted ${frame.id} to $actual frames")
        return frame.copy(frameCount = actual)
    }

    /** Restores frames parked by an interrupted compaction. */
    private fun recoverRepairTemps(dir: File) {
        val temps = dir.listFiles { f ->
            f.isFile && f.name.startsWith(".repair_") && f.name.endsWith(".tmp")
        }.orEmpty()
        temps.forEach { temp ->
            val index = temp.name.removePrefix(".repair_").removeSuffix(".tmp")
                .toIntOrNull() ?: return@forEach
            if (index !in 0 until MAX_FRAMES) return@forEach
            val target = File(dir, frameName(index))
            if (!target.exists()) runCatching { temp.renameTo(target) }
        }
    }

    fun snapshot(): List<LivingFrame> = _frames.value
    fun get(id: String): LivingFrame? = _frames.value.firstOrNull { it.id == id }

    // -- paths -----------------------------------------------------------

    fun frameDir(id: String): File = File(root, id)
    fun ensureFrameDir(id: String): File = frameDir(id).apply { mkdirs() }
    fun stagingDir(id: String): File = File(root, "$id.tmp")

    /** Canonical name; legacy files may use locale digits, so check both. */
    fun frameFile(id: String, index: Int): File {
        val canonical = File(frameDir(id), frameName(index))
        if (canonical.exists()) return canonical
        val legacy = File(frameDir(id), legacyFrameName(index))
        return if (legacy.exists()) legacy else canonical
    }

    fun coverFile(id: String): File = File(frameDir(id), "cover.webp")

    private fun frameName(index: Int): String = String.format(Locale.US, "frame_%04d.webp", index)
    private fun legacyFrameName(index: Int): String = "frame_%04d.webp".format(index)

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "Out of memory decoding ${id}/$index")
                null
            } catch (_: Exception) {
                null
            }
        }

    // -- mutation --------------------------------------------------------

    /** Insert or replace, flushed to disk before returning. */
    suspend fun upsert(frame: LivingFrame) {
        val valid = LivingFrameValidation.normalize(frame)
            ?: throw IllegalArgumentException("Invalid Living Widget metadata")
        val replaced = synchronized(stateLock) {
            val previous = _frames.value
            val existing = previous.firstOrNull { it.id == valid.id }
            _frames.value = (previous.filterNot { it.id == valid.id } + valid)
                .sortedByDescending { it.createdAt }
            existing
        }
        val saved = withContext(Dispatchers.IO) { writeNow() }
        if (!saved) {
            // Put back only this frame. Other edits made while the write was
            // in flight must survive; restoring the whole old list would erase
            // them and could resurrect a deleted set.
            synchronized(stateLock) {
                val current = _frames.value
                if (current.firstOrNull { it.id == valid.id } == valid) {
                    val restored = if (replaced == null) {
                        current.filterNot { it.id == valid.id }
                    } else {
                        current.map { if (it.id == valid.id) replaced else it }
                    }
                    _frames.value = restored.sortedByDescending { it.createdAt }
                }
            }
            // A debounced writer may have persisted the rolled-back frame just
            // before this write failed; persist the corrected list too.
            persistSignal.trySend(Unit)
            throw IOException("Couldn't save the Living Widget library")
        }
    }

    /** In-memory immediately, persisted shortly after. Safe to call per slider tick. */
    fun update(id: String, transform: (LivingFrame) -> LivingFrame) {
        val changed = synchronized(stateLock) {
            var edited = false
            val next = _frames.value.map { frame ->
                if (frame.id != id) {
                    frame
                } else {
                    val updated = LivingFrameValidation.normalize(transform(frame)) ?: frame
                    if (updated != frame) edited = true
                    updated
                }
            }
            if (edited) _frames.value = next
            edited
        }
        if (changed) persistSignal.trySend(Unit)
    }

    suspend fun flush() = withContext(Dispatchers.IO) { writeNow() }

    /** Returns false without touching files if the metadata write fails. */
    suspend fun delete(id: String): Boolean {
        if (!LivingFrameValidation.isValidId(id)) return false
        val removed = synchronized(stateLock) {
            val previous = _frames.value
            val existing = previous.firstOrNull { it.id == id }
            _frames.value = previous.filterNot { it.id == id }
            existing
        }
        val saved = withContext(Dispatchers.IO) { writeNow() }
        if (!saved) {
            synchronized(stateLock) {
                // Only restore this set, and only if nothing re-created it
                // while the write was failing. Other edits stay untouched.
                if (removed != null && _frames.value.none { it.id == id }) {
                    _frames.value = (_frames.value + removed)
                        .sortedByDescending { it.createdAt }
                }
            }
            // Correct any debounced save that ran while delete's own write
            // was failing.
            persistSignal.trySend(Unit)
            return false
        }
        markFrameWidgetsDeleted(id)
        withContext(Dispatchers.IO) {
            // Remove after metadata is durable: an interrupted delete leaves
            // an orphan (cleaned later), never a metadata entry without files.
            runCatching { frameDir(id).deleteRecursively() }
        }
        return true
    }

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    private suspend fun writeNow(): Boolean = writeLock.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (!root.exists() && !root.mkdirs()) {
                    throw IOException("Couldn't create the Living Widgets folder")
                }
                if (!root.isDirectory) {
                    throw IOException("The Living Widgets path is not a folder")
                }
                runCatching { File(root, ".nomedia").createNewFile() }
                // Serialise one coherent snapshot. Reading _frames.value mid
                // encode could mix an older list with newer edits and write a
                // document the UI never showed.
                val snapshot = synchronized(stateLock) { _frames.value }
                val payload = json.encodeToString(serializer, snapshot)
                val tmp = File(root, "frames.json.tmp")
                FileOutputStream(tmp).use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                    out.fd.sync()
                }
                // Never overwrite a good backup with the corrupt primary we
                // just recovered from. usedBackup is set by readMetadata() and
                // cleared only after a save succeeds, so this check does not
                // have to re-parse frames.json on every slider tick.
                if (!usedBackup && metaFile.exists()) {
                    val bakTmp = File(root, "frames.json.bak.tmp")
                    runCatching {
                        metaFile.copyTo(bakTmp, overwrite = true)
                        if (!bakTmp.exists() || bakTmp.length() <= 0L) {
                            throw IllegalStateException("Backup copy failed")
                        }
                        FileOutputStream(bakTmp, true).use { it.fd.sync() }
                        if (!replaceAtomically(bakTmp, backupFile)) {
                            throw IOException("Couldn't replace the metadata backup")
                        }
                    }.onFailure {
                        // Leave the previous backup intact.
                        runCatching { bakTmp.delete() }
                    }
                }
                if (!replaceAtomically(tmp, metaFile)) {
                    // Keep the previous primary instead of writing a partial
                    // JSON document that the next launch cannot read.
                    throw IOException("Couldn't replace the metadata file")
                }
                usedBackup = false
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist Living Widgets metadata", e)
                false
            }
        }
    }

    /**
     * Replace [target] with [source] without exposing a half-written file.
     * Internal storage supports atomic moves; renameTo is an atomic replace
     * on the Linux filesystems Android uses. If both fail, the caller keeps
     * the old file rather than falling back to a non-atomic rewrite.
     */
    private fun replaceAtomically(source: File, target: File): Boolean {
        return try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            true
        } catch (_: Exception) {
            runCatching { source.renameTo(target) }.getOrDefault(false)
        }
    }

    // -- storage ---------------------------------------------------------

    /** Snapshot used by the storage UI and by quota checks before an import. */
    fun storage(): LivingFramesStorage {
        val framesNow = synchronized(stateLock) { _frames.value }
        val known = framesNow.map { it.id }.toSet()
        val dirs = root.listFiles { f -> f.isDirectory }.orEmpty()
        // If metadata failed to load, every real frame directory looks like
        // an orphan. Reporting (or deleting) them would be catastrophic.
        val now = System.currentTimeMillis()
        val orphanDirs = if (loadFailed || !loaded) emptyList() else dirs.filter {
            val orphan = it.name.endsWith(".tmp") ||
                it.name.startsWith(".trash-") ||
                (!it.name.startsWith(".") && it.name !in known)
            // Match cleanupOrphans: very recent staging may belong to an
            // import that is still running, so do not offer it for deletion.
            orphan && now - it.lastModified() > ORPHAN_AGE_MS
        }
        return LivingFramesStorage(
            usedBytes = directorySize(root),
            freeBytes = context.filesDir.usableSpace.coerceAtLeast(0L),
            quotaBytes = MAX_STORE_BYTES,
            setCount = framesNow.size,
            orphanDirs = orphanDirs,
            orphanBytes = orphanDirs.sumOf { directorySize(it) }
        )
    }

    /**
     * Cheap used-bytes snapshot for an import. [storage] also builds orphan
     * lists; calling it every few frames made a 360-frame import walk the
     * whole library dozens of times.
     */
    fun usageBytes(): Long = directorySize(root)

    fun checkStorage(extraBytes: Long = 0L): StorageCheck {
        val stats = storage()
        val projected = stats.usedBytes + extraBytes.coerceAtLeast(0L)
        if (projected > MAX_STORE_BYTES) {
            return StorageCheck(
                false,
                context.getString(
                    R.string.lw_storage_full_usage,
                    formatBytes(stats.usedBytes),
                    formatBytes(MAX_STORE_BYTES)
                )
            )
        }
        if (stats.freeBytes - extraBytes < MIN_FREE_BYTES) {
            return StorageCheck(
                false,
                context.getString(
                    R.string.lw_import_need_space,
                    formatBytes(MIN_FREE_BYTES)
                )
            )
        }
        return StorageCheck(true)
    }

    suspend fun cleanupOrphans(): Long = withContext(Dispatchers.IO) {
        if (!loaded || loadFailed) {
            Log.w(TAG, "Skipping orphan cleanup until the library loads successfully")
            return@withContext 0L
        }
        var freed = 0L
        val known = synchronized(stateLock) { _frames.value.map { it.id }.toSet() }
        val now = System.currentTimeMillis()
        root.listFiles()?.forEach { f ->
            if (!f.isDirectory) return@forEach
            val isStaging = f.name.endsWith(".tmp")
            val isTrash = f.name.startsWith(".trash-")
            val isOrphan = f.name !in known && !isStaging && !isTrash && !f.name.startsWith(".")
            if ((isStaging || isTrash || isOrphan) && now - f.lastModified() > ORPHAN_AGE_MS) {
                freed += directorySize(f)
                runCatching { f.deleteRecursively() }
            }
        }
        freed
    }

    // -- import staging --------------------------------------------------

    /** Creates a fresh staging directory. Metadata is only published after every frame is written. */
    suspend fun beginImport(id: String): File = withContext(Dispatchers.IO) {
        val dir = stagingDir(id)
        runCatching { dir.deleteRecursively() }
        dir.mkdirs()
        dir
    }

    /**
     * Moves a fully written staging set into place. When an old set with the
     * same id exists it is parked in `.trash-*` and returned, so the caller
     * can restore it if publishing metadata fails. The caller owns cleanup of
     * the returned folder after a successful metadata write.
     */
    suspend fun publishImport(id: String): File? = withContext(Dispatchers.IO) {
        val staging = stagingDir(id)
        require(staging.isDirectory) { "Import staging folder is missing" }
        require(File(staging, frameName(0)).exists()) { "Import staging folder is incomplete" }
        val target = frameDir(id)
        val trash = File(root, ".trash-$id-${System.nanoTime()}")
        var oldMoved = false
        if (target.exists()) {
            // Never delete the live set before the new one is safely in place.
            if (!target.renameTo(trash)) {
                throw IOException("Couldn't replace the existing Living Widget files")
            }
            oldMoved = true
            // A renamed directory keeps its old mtime. Touch the parked set so
            // a concurrent orphan cleanup cannot delete it before metadata is
            // committed or rolled back.
            runCatching { trash.setLastModified(System.currentTimeMillis()) }
        }
        try {
            if (!staging.renameTo(target)) {
                // Directory rename can fail on some FUSE-backed storage; copy is
                // still safe because old files are in .trash and metadata has
                // not been published yet.
                staging.copyRecursively(target, overwrite = true)
                runCatching { staging.deleteRecursively() }
            }
            require(target.isDirectory && frameFile(id, 0).exists()) {
                "Couldn't finalise the Living Widget files"
            }
            runCatching { target.setLastModified(System.currentTimeMillis()) }
            if (oldMoved) trash else null
        } catch (t: Throwable) {
            // Roll back to the previous set if the new files did not land.
            runCatching { target.deleteRecursively() }
            if (oldMoved && !target.exists()) {
                if (!trash.renameTo(target)) {
                    Log.e(TAG, "Could not restore previous files for $id", t)
                }
            }
            throw t
        }
    }

    suspend fun discardImport(id: String) = withContext(Dispatchers.IO) {
        runCatching { stagingDir(id).deleteRecursively() }
    }

    // -- widget bindings -------------------------------------------------

    fun bindWidget(appWidgetId: Int, frameId: String): Boolean {
        if (appWidgetId <= 0 || !LivingFrameValidation.isValidId(frameId)) return false
        // Configuration finishes immediately after this call; a queued apply()
        // could be lost if the process is killed before the disk write lands.
        return runCatching {
            bindings.edit().putString("w_$appWidgetId", frameId).commit()
        }.getOrDefault(false)
    }

    fun unbindWidget(appWidgetId: Int) {
        if (appWidgetId <= 0) return
        bindings.edit().remove("w_$appWidgetId").apply()
    }

    fun boundFrame(appWidgetId: Int): String? = bindings.getString("w_$appWidgetId", null)

    fun boundWidgetIds(frameId: String): List<Int> =
        bindings.all.mapNotNull { (k, v) ->
            if (v == frameId && k.startsWith("w_")) k.removePrefix("w_").toIntOrNull() else null
        }

    /**
     * Keeps a tombstone instead of dropping the association. Without it the
     * refresh fallback would attach the widget to the only remaining set the
     * next time the watchdog ran, silently replacing the "deleted" card.
     */
    private fun markFrameWidgetsDeleted(frameId: String) {
        val ed = bindings.edit()
        bindings.all.forEach { (k, v) ->
            if (v == frameId && k.startsWith("w_")) ed.putString(k, DELETED_WIDGET_BINDING)
        }
        ed.apply()
    }

    /** Marks one widget as pointing at a set that no longer exists. */
    fun markWidgetDeleted(appWidgetId: Int) {
        if (appWidgetId <= 0) return
        bindings.edit().putString("w_$appWidgetId", DELETED_WIDGET_BINDING).apply()
    }

    /** Drops bindings for widgets the launcher no longer has. */
    fun pruneBindings(liveWidgetIds: Set<Int>): List<Int> {
        // Bindings cannot be validated while the library is unreadable; keep
        // every association so recovery restores the exact widget -> set map.
        if (!loaded || loadFailed) {
            Log.w(TAG, "Skipping binding prune while metadata is unreadable")
            return emptyList()
        }
        // An empty enumeration is ambiguous (no widgets vs. a transient binder
        // failure). onDeleted normally clears bindings, so do not gamble here.
        if (liveWidgetIds.isEmpty() && bindings.all.keys.any { it.startsWith("w_") }) {
            Log.w(TAG, "Skipping binding prune: widget enumeration returned no IDs")
            return emptyList()
        }
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
        private const val TAG = "LivingFramesRepo"

        /** Sentinel binding kept while an orphaned home-screen widget waits to be removed. */
        const val DELETED_WIDGET_BINDING = "!"

        /**
         * Upper bound for stored animation frames. The old 40-frame cap was
         * the reason long clips could not exist. 900 frames is 30 s at 30 fps
         * (or 15 s at 60 fps); the importer lowers the capture rate when a
         * longer window would exceed this, and the launcher still only ever
         * holds a handful of frames at once thanks to streaming playback.
         */
        const val MAX_FRAMES = 900
        const val MAX_SLIDES = 30
        const val MAX_CLIP_MS = 30_000L

        /** Longest edge of the thumbnail kept per set; capture sizes live in [LivingFrameQuality]. */
        const val COVER_SIDE = 400

        const val MAX_STORE_BYTES = 1024L * 1024 * 1024
        const val MIN_FREE_BYTES = 256L * 1024 * 1024
        const val MAX_SET_BYTES = 640L * 1024 * 1024
        private const val ORPHAN_AGE_MS = 60L * 60 * 1000

        fun formatBytes(bytes: Long): String = when {
            bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / (1024f * 1024 * 1024))
            bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024))
            bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
            else -> "$bytes B"
        }

        private fun directorySize(file: File): Long =
            if (!file.exists()) 0L
            else if (file.isFile) file.length()
            else file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}

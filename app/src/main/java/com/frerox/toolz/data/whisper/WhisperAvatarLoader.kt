/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import com.frerox.toolz.data.whisper.ProtocolDiagnostics
import com.frerox.toolz.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V6-R7 AVATARS: resolves encrypted ImgBB-hosted avatars into raw sealed bytes for
 * a given owner public key, with a bounded memory cache and in-flight deduplication
 * (a hub + chat header + sheet all request the same URL on the same frame).
 *
 * The returned bytes are SEALED — callers run [WhisperAvatarCodec.open] with the
 * profile's public key. Key derivation is deterministic, so there is no server
 * round-trip beyond the single image fetch.
 *
 * THREADING: [load] and [prime] are safe to call from the main thread — every disk
 * touch is hopped to [Dispatchers.IO], so a cold-start avatar never blocks a frame.
 */
@Singleton
class WhisperAvatarLoader @Inject constructor(
    // DI seam: interface-typed so an env-switched implementation (staging/test
    // host) can be bound to EncryptedBlobHost via a Hilt @Binds module —
    // WhisperEncryptedImageHost remains the production impl.
    private val host: EncryptedBlobHost,
    @ApplicationScope private val appScope: CoroutineScope,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) {
    // Access-order LRU. The previous implementation declared accessOrder=true but
    // never evicted anything: browsing Discover and then opening many chats kept
    // every sealed avatar resident for the process lifetime (an OOM risk on a
    // large account/hub). Both an entry and a byte budget are enforced because a
    // single encrypted avatar can still be hundreds of KB.
    private val lru = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {}
    private val lruLock = Any()
    private var lruBytes = 0L

    private val inflight = Collections.synchronizedMap(mutableMapOf<String, Deferred<ByteArray?>>())

    /** Bumped on clear() so stale results from a wiped cache are not re-cached. */
    @Volatile private var generation = 0L

    /**
     * Negative cache: a dead avatar URL (deleted ImgBB image is PERMANENT) must
     * not re-hit the network on every scroll for every composition instance.
     * Failures are remembered per URL and re-eligible after 10 minutes — the
     * same window as the verified-signer cert cache — or immediately via
     * [clear] (sign-out) / [prime] (local re-upload).
     */
    private val failedAt = Collections.synchronizedMap(mutableMapOf<String, Long>())
    private val failedRetryMs = 10 * 60_000L

    // V6-R7 (#cache): PERSISTENT layer for SEALED bytes (ciphertext on disk, same
    // posture as chat image cache) — avatars survive process death without re-fetch.
    private val diskDir: java.io.File
        get() = java.io.File(appContext.filesDir, "whisper_avatars").apply { mkdirs() }

    private fun diskFileFor(url: String): java.io.File =
        java.io.File(diskDir, java.security.MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".bin")

    private suspend fun diskGet(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            diskFileFor(url).takeIf { it.exists() }?.readBytes()
        }.getOrNull()
    }

    /**
     * Writes one sealed avatar atomically (temp + same-directory rename) so a
     * process kill can never leave a half-written entry that would be cached and
     * served as a permanently broken image. Then trims the directory by BOTH
     * count and total bytes: the old 64-entry cap alone allowed hundreds of MB.
     */
    private suspend fun diskPut(url: String, sealed: ByteArray) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = diskDir
            val target = diskFileFor(url)
            val tmp = java.io.File.createTempFile("wavatar_", ".part", dir)
            try {
                tmp.writeBytes(sealed)
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                }
            } finally {
                tmp.delete()
            }
            trimDisk()
        }
    }

    private fun trimDisk() {
        runCatching {
            val files = diskDir.listFiles()?.filter { it.isFile } ?: return
            var total = files.sumOf { it.length() }
            var count = files.size
            if (count <= MAX_DISK_ENTRIES && total <= MAX_DISK_BYTES) return
            // Delete oldest first until both budgets are satisfied.
            for (file in files.sortedBy { it.lastModified() }) {
                if (count <= MAX_DISK_ENTRIES && total <= MAX_DISK_BYTES) break
                val size = file.length()
                if (file.delete()) {
                    total -= size
                    count--
                }
            }
        }
    }

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()

    private fun normalizeUrl(url: String): String =
        url.substringBefore("#").substringBefore("?")

    private fun lruGet(key: String): ByteArray? = synchronized(lruLock) {
        lru[key]?.copyOf()
    }

    private fun lruPut(key: String, value: ByteArray) {
        synchronized(lruLock) {
            lru.remove(key)?.let { lruBytes -= it.size.toLong() }
            lru[key] = value
            lruBytes += value.size.toLong()
            // Evict from the eldest (least-recently-used) end until both budgets hold.
            val iterator = lru.entries.iterator()
            while ((lru.size > MAX_MEMORY_ENTRIES || lruBytes > MAX_MEMORY_BYTES) && iterator.hasNext()) {
                val eldest = iterator.next()
                iterator.remove()
                lruBytes -= eldest.value.size.toLong()
            }
            if (lruBytes < 0L) lruBytes = 0L
        }
    }

    /** Prime the memory + disk cache after a local upload so the new avatar renders instantly without a network round-trip. */
    suspend fun prime(urlNoFragment: String, sealed: ByteArray) {
        val key = normalizeUrl(urlNoFragment)
        val genAtStart = generation
        failedAt.remove(key)
        diskPut(key, sealed)
        // A sign-out that raced the upload must not be resurrected by this write.
        if (genAtStart != generation) {
            withContext(Dispatchers.IO) { runCatching { diskFileFor(key).delete() } }
            return
        }
        lruPut(key, sealed.copyOf())
    }

    suspend fun load(urlNoFragment: String, ownerPublicKeyBase64: String): ByteArray? {
        val key = normalizeUrl(urlNoFragment)
        lruGet(key)?.let { return it }
        diskGet(key)?.let { cached ->
            // V6-R7b FIX (double-wrap heal): stale disk entries from the window
            // where upload double-wrapped are raw PNG wrappers, not sealed bytes.
            // A sealed AES-GCM payload never starts with the PNG signature (2^-32
            // collision treated as a harmless refetch), so drop them and refetch.
            if (!isPng(cached)) {
                lruPut(key, cached.copyOf())
                return cached
            }
            withContext(Dispatchers.IO) { runCatching { diskFileFor(key).delete() } }
        }
        // Negative cache (offline/dead URL): serve the silent initials fallback
        // instead of refetching a known-dead image on every recomposition.
        failedAt[key]?.let { failed ->
            if (System.currentTimeMillis() - failed < failedRetryMs) return null
            failedAt.remove(key)
        }
        val deferred: Deferred<ByteArray?> = synchronized(inflight) {
            inflight.getOrPut(key) {
                appScope.async { fetchAndCache(key, ownerPublicKeyBase64) }
            }
        }
        return try {
            deferred.await()
        } finally {
            synchronized(inflight) { inflight.remove(key, deferred) }
        }
    }

    private suspend fun fetchAndCache(url: String, ownerPubB64: String): ByteArray? {
        val genAtStart = generation
        val normalized = normalizeUrl(url)
        val hostRes = host.download(normalized)
        if (hostRes.isFailure) {
            ProtocolDiagnostics.increment("avatar.hostFail")
        }
        val raw = hostRes.getOrNull() ?: run {
            rememberFailure(normalized)
            return null
        }
        // V6-R7 FIX: fail CLOSED — if the PNG transport unwrap fails we must not
        // cache/return the raw wrapper (it decoded as colored static downstream).
        var sealed = withContext(Dispatchers.Default) { WhisperImageCipherTransport.decode(raw) }
        if (sealed == null) {
            ProtocolDiagnostics.increment("avatar.transportDecodeFail")
            rememberFailure(normalized)
            return null
        }
        // V6-R7b FIX (double-wrap heal): legacy avatars uploaded while the repo
        // pre-wrapped AND the host wrapped carry PNG(WZ1(PNG(WZ1(sealed)))).
        // Sealed AES-GCM output never carries a PNG header — unwrap once more
        // when we see it so already-hosted avatars self-heal without re-upload.
        if (isPng(sealed)) {
            sealed = withContext(Dispatchers.Default) { WhisperImageCipherTransport.decode(sealed) } ?: run {
                rememberFailure(normalized)
                return null
            }
        }
        if (genAtStart != generation) return null // cache was cleared mid-flight
        val cacheKey = normalizeUrl(url)
        failedAt.remove(cacheKey)
        diskPut(cacheKey, sealed)
        lruPut(cacheKey, sealed)
        return sealed.copyOf()
    }

    private fun rememberFailure(url: String) {
        synchronized(failedAt) {
            // Bound: a pathological session cannot grow the map unbounded.
            if (failedAt.size >= 128) failedAt.clear()
            failedAt[url] = System.currentTimeMillis()
        }
    }

    /** Called on sign-out / account wipe so one user's avatars never serve the next. */
    fun clear() {
        generation++
        synchronized(lruLock) {
            lru.clear()
            lruBytes = 0L
        }
        synchronized(inflight) { inflight.clear() }
        failedAt.clear()
        // File deletion must never run on the caller (often main during sign-out).
        appScope.launch(Dispatchers.IO) {
            runCatching { diskDir.listFiles()?.forEach { it.delete() } }
        }
    }

    private companion object {
        const val MAX_MEMORY_ENTRIES = 32
        const val MAX_MEMORY_BYTES = 8L * 1024 * 1024
        const val MAX_DISK_ENTRIES = 64
        const val MAX_DISK_BYTES = 16L * 1024 * 1024
    }
}

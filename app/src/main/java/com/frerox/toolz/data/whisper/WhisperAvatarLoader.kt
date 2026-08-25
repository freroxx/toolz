/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import com.frerox.toolz.di.ApplicationScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V6-R7 AVATARS: resolves encrypted ImgBB-hosted avatars into raw sealed bytes for
 * a given owner public key, with an LRU memory cache and in-flight deduplication
 * (a hub + chat header + sheet all request the same URL on the same frame).
 *
 * The returned bytes are SEALED — callers run [WhisperAvatarCodec.open] with the
 * profile's public key. Key derivation is deterministic, so there is no server
 * round-trip beyond the single image fetch.
 */
@Singleton
class WhisperAvatarLoader @Inject constructor(
    private val host: WhisperEncryptedImageHost,
    @ApplicationScope private val appScope: CoroutineScope,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) {
    private val lru = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {}
    private val lruMutex = Mutex()
    private val inflight = Collections.synchronizedMap(mutableMapOf<String, Deferred<ByteArray?>>())
    /** Bumped on clear() so stale results from a wiped cache are not re-cached. */
    private var generation = 0L

    // V6-R7 (#cache): PERSISTENT layer for SEALED bytes (ciphertext on disk, same
    // posture as chat image cache) — avatars survive process death without re-fetch.
    private val diskDir: java.io.File
        get() = java.io.File(appContext.filesDir, "whisper_avatars").apply { mkdirs() }
    private val diskMutex = Mutex()

    private fun diskFileFor(url: String): java.io.File =
        java.io.File(diskDir, java.security.MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".bin")

    private fun diskGet(url: String): ByteArray? = runCatching {
        diskFileFor(url).takeIf { it.exists() }?.readBytes()
    }.getOrNull()

    private fun diskPut(url: String, sealed: ByteArray) {
        runCatching {
            val f = diskFileFor(url)
            f.writeBytes(sealed)
            // Bound the directory: drop oldest beyond 64 entries.
            val files = diskDir.listFiles()?.sortedBy { it.lastModified() } ?: return
            if (files.size > 64) files.take(files.size - 64).forEach { it.delete() }
        }
    }

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()

    private fun normalizeUrl(url: String): String =
        url.substringBefore("#").substringBefore("?")

    /** Prime the memory + disk cache after a local upload so the new avatar renders instantly without a network round-trip. */
    suspend fun prime(urlNoFragment: String, sealed: ByteArray) {
        val key = normalizeUrl(urlNoFragment)
        diskPut(key, sealed)
        lruMutex.withLock { lru[key] = sealed.copyOf() }
    }

    suspend fun load(urlNoFragment: String, ownerPublicKeyBase64: String): ByteArray? {
        val key = normalizeUrl(urlNoFragment)
        lruMutex.withLock { lru[key] }?.let { return it.copyOf() }
        diskGet(key)?.let { cached ->
            // V6-R7b FIX (double-wrap heal): stale disk entries from the window
            // where upload double-wrapped are raw PNG wrappers, not sealed bytes.
            // A sealed AES-GCM payload never starts with the PNG signature (2^-32
            // collision treated as a harmless refetch), so drop them and refetch.
            if (!isPng(cached)) {
                lruMutex.withLock { lru[key] = cached }
                return cached.copyOf()
            }
            runCatching { diskFileFor(key).delete() }
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
        val raw = host.download(normalized).getOrNull() ?: return null
        // V6-R7 FIX: fail CLOSED — if the PNG transport unwrap fails we must not
        // cache/return the raw wrapper (it decoded as colored static downstream).
        var sealed = WhisperImageCipherTransport.decode(raw) ?: return null
        // V6-R7b FIX (double-wrap heal): legacy avatars uploaded while the repo
        // pre-wrapped AND the host wrapped carry PNG(WZ1(PNG(WZ1(sealed)))).
        // Sealed AES-GCM output never carries a PNG header — unwrap once more
        // when we see it so already-hosted avatars self-heal without re-upload.
        if (isPng(sealed)) {
            sealed = WhisperImageCipherTransport.decode(sealed) ?: return null
        }
        if (genAtStart != generation) return null // cache was cleared mid-flight
        val cacheKey = normalizeUrl(url)
        diskPut(cacheKey, sealed)
        lruMutex.withLock { lru[cacheKey] = sealed }
        return sealed.copyOf()
    }

    /** Called on sign-out / account wipe so one user's avatars never serve the next. */
    fun clear() {
        generation++
        synchronized(lru) { lru.clear() }
        synchronized(inflight) { inflight.clear() }
        runCatching { diskDir.listFiles()?.forEach { it.delete() } }
    }
}

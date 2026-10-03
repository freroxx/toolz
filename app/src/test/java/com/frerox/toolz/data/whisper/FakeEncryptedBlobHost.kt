/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.whisper

/**
 * In-memory [EncryptedBlobHost] for unit tests: keeps sealed ciphertext in
 * RAM so tests can round-trip upload → download and observe delete behavior
 * without any network, storage backend, or Supabase client.
 *
 * Uploads are addressable by the returned attachmentId (also encoded as the
 * URL's last path segment), mirroring how the production host hands back a
 * (url, attachmentId) pair.
 */
class FakeEncryptedBlobHost : EncryptedBlobHost {

    private val blobs = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    /** Number of successful uploads (observable in tests). */
    var uploadCount: Int = 0
        private set

    /** Number of delete calls (observable in tests). */
    var deleteCount: Int = 0
        private set

    override suspend fun upload(
        cipherBytes: ByteArray,
        name: String,
        expirationSeconds: Long?,
    ): Result<Pair<String, String?>> {
        if (cipherBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("empty ciphertext"))
        }
        uploadCount++
        val attachmentId = "fake-$uploadCount"
        blobs[attachmentId] = cipherBytes
        return Result.success("https://fake.invalid/$attachmentId" to attachmentId)
    }

    override suspend fun delete(url: String, attachmentId: String?): Result<Unit> {
        deleteCount++
        if (attachmentId != null) blobs.remove(attachmentId)
        return Result.success(Unit)
    }

    override suspend fun download(url: String): Result<ByteArray> {
        val id = url.substringAfterLast("/", "")
        val bytes = blobs[id]
            ?: return Result.failure(NoSuchElementException("no blob for $url"))
        return Result.success(bytes)
    }
}

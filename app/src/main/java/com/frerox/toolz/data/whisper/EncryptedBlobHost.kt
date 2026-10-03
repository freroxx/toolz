/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Contract for encrypted-blob hosts: upload sealed ciphertext to a
 * third-party image host, download it back, and delete it by handle.
 *
 * The production implementation is [WhisperEncryptedImageHost] (authenticated
 * Edge Functions with Supabase Storage fallback). An environment-switched
 * implementation (e.g. a staging or test host) would plug in here via a Hilt
 * `@Binds` module binding this interface to the chosen implementation.
 */
interface EncryptedBlobHost {
    /** Seals and uploads [cipherBytes]; returns Pair(url, attachmentId). */
    suspend fun upload(cipherBytes: ByteArray, name: String, expirationSeconds: Long?): Result<Pair<String, String?>>

    /** Deletes the blob at [url] identified by [attachmentId]; no-op when the handle is null. */
    suspend fun delete(url: String, attachmentId: String?): Result<Unit>

    /** Downloads the raw (still transport-wrapped) bytes hosted at [url]. */
    suspend fun download(url: String): Result<ByteArray>
}

/**
 * Hilt binding: whenever [EncryptedBlobHost] is injected (WhisperRepository,
 * WhisperAvatarLoader), provide the [WhisperEncryptedImageHost] singleton.
 * Dagger cannot auto-bind a concrete class to an interface, so the binding
 * is explicit; it lives in this file to keep the contract and its only
 * production implementation together.
 *
 * R2/B2 (environment-selected host — DOCUMENTED SKELETON, NOT WIRED):
 * to run against a staging/test host, implement [EncryptedBlobHost] and
 * swap the @Binds parameter below (or gate on a BuildConfig flag inside a
 * @Provides); every injection point already targets the interface, so no
 * caller changes are needed. WhisperEncryptedImageHost stays the sole,
 * production binding until R2/B2 lands.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EncryptedBlobHostModule {

    @Binds
    @Singleton
    abstract fun bindEncryptedBlobHost(
        impl: WhisperEncryptedImageHost,
    ): EncryptedBlobHost
}

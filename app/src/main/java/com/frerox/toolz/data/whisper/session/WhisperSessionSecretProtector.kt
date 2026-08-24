/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper.session

import com.frerox.toolz.data.whisper.WhisperCrypto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V6 (planwhisper.md §3.1): seam between ratchet-session persistence and the
 * Android Keystore so the store's secret-handling is unit-testable on the JVM.
 *
 * Production binding wraps/opens bytes with [WhisperCrypto.wrapWithKeystoreAes]
 * (hardware-backed AES-GCM). Tests inject an in-memory fake — the persisted-file
 * layer must never be exercised without it, or plaintext key material could touch
 * disk in a test environment.
 */
interface WhisperSessionSecretProtector {
    /** Protects key material at rest; null means "refuse to persist unprotected". */
    fun wrap(plain: ByteArray): String?

    /** Reverses [wrap]; null on any tamper/unavailability (treated as data loss). */
    fun unwrap(wrappedB64: String): ByteArray?
}

/** Production protector: delegates to the hardware-backed Keystore AES-GCM wrapper. */
@Singleton
class KeystoreSessionSecretProtector @Inject constructor(
    private val crypto: WhisperCrypto,
) : WhisperSessionSecretProtector {
    override fun wrap(plain: ByteArray): String? = crypto.wrapWithKeystoreAes(plain)
    override fun unwrap(wrappedB64: String): ByteArray? = crypto.unwrapWithKeystoreAes(wrappedB64)
}

/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

/**
 * V6 (planwhisper.md): live-transport gating for the Double Ratchet.
 *
 * The ratchet is chaos-proven (80 seeds, zero wrong plaintexts), wired behind the
 * V5 envelope fallback (any handshake failure degrades to envelopes — a message can
 * never be blocked by session problems), and covered by the interop/persistence/
 * reinstall gates in WhisperV3InteropTest + WhisperSessionStoreRecordTest.
 */
object WhisperProtocolConfig {
    const val LIVE_PROTOCOL_VERSION = 2      // v5 multi-key envelopes (fallback path)
    const val RATCHET_PROTOCOL_VERSION = 3   // v3 Double Ratchet frames
    val ratchetEnabled: Boolean get() = true // V6: forward secrecy live (plan §5.6)
}

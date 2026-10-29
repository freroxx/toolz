/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Clipboard sandbox: 30 RAM-only fixture clips (OTP/links/code/pins/etc.).
 * The real clipboard Room table is never read or written while the
 * spoof_clipboard key is active. All mutations below only touch this list.
 */

package com.frerox.toolz.data.developer

import com.frerox.toolz.data.clipboard.ClipAiState
import com.frerox.toolz.data.clipboard.ClipSensitivityReason
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.data.clipboard.ClipboardType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

object FakeClipboardSandbox {

    private val clips = MutableStateFlow(seeded())

    /** Live list the VM serves while sandboxed. */
    fun entries(): MutableStateFlow<List<ClipboardEntry>> = clips

    /** RAM-only delete. */
    fun delete(id: Int) {
        clips.update { list -> list.filterNot { it.id == id } }
    }

    /** RAM-only pin flip. */
    fun togglePin(id: Int) {
        clips.update { list -> list.map { if (it.id == id) it.copy(isPinned = !it.isPinned) else it } }
    }

    /** RAM-only wipe of unpinned fixtures. */
    fun clearUnpinned() {
        clips.update { list ->
            val now = System.currentTimeMillis()
            list.map { if (it.isPinned) it else it.copy(deletedAt = now) }
        }
    }

    /** RAM-only soft delete (restorable from the sandbox trash). */
    fun softDelete(id: Int) {
        clips.update { list ->
            val now = System.currentTimeMillis()
            list.map { if (it.id == id) it.copy(deletedAt = now) else it }
        }
    }

    /** RAM-only restore from the sandbox trash. */
    fun restore(id: Int) {
        clips.update { list -> list.map { if (it.id == id) it.copy(deletedAt = null) else it } }
    }

    /** RAM-only permanent trash purge. */
    fun emptyTrash() {
        clips.update { list -> list.filter { it.deletedAt == null } }
    }

    /** RAM-only lock flip. */
    fun setLocked(id: Int, locked: Boolean) {
        clips.update { list -> list.map { if (it.id == id) it.copy(isLocked = locked) else it } }
    }

    /** RAM-only AI state/summary update. */
    fun setAiResult(id: Int, summary: String?, type: String?) {
        clips.update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(
                        summary = summary ?: it.summary,
                        type = type ?: it.type,
                        isAiProcessed = true,
                        aiState = ClipAiState.DONE,
                        aiError = null,
                    )
                } else {
                    it
                }
            }
        }
    }

    /** RAM-only AI failure marker. */
    fun setAiFailed(id: Int, error: String?) {
        clips.update { list ->
            list.map {
                if (it.id == id) it.copy(aiState = ClipAiState.FAILED, aiError = error) else it
            }
        }
    }

    /** Restore a deleted fixture back into the RAM list. */
    fun insert(entry: ClipboardEntry) {
        clips.update { list ->
            list + entry.copy(
                id = (list.maxOfOrNull { it.id } ?: 0) + 1,
                timestamp = System.currentTimeMillis(),
                deletedAt = null,
            )
        }
    }

    /** Back to the pristine fixture set (sandbox on). */
    fun reset() {
        clips.value = seeded()
    }

    private fun seeded(): List<ClipboardEntry> {
        val now = System.currentTimeMillis()
        val minute = 60 * 1000L

        // (content, type, sourceApp) triples; index i ages by ~7*i minutes.
        val seed = listOf(
            "4 1 8 2 9" to ("OTP" to "WhatsApp"),
            "https://developer.android.com/reference/kotlin" to ("URL" to "Chrome"),
            "fun fakeIt(): Boolean = true" to ("CODE" to "Android Studio"),
            "+1 555 010 0000" to ("PHONE" to "Contacts"),
            "#7C4DFF" to ("COLOR" to "Figma"),
            "rey mercado • 42 seward st, apt 2" to ("PERSONAL" to "Forms"),
            "devnull@toolz.local" to ("EMAIL" to "Gmail"),
            "sqrt(1337) ≈ 36.565" to ("MATHS" to "Calculator"),
            "Nothing real in this list — sandbox fixtures only" to ("TEXT" to "Toolz"),
            "1600 Bit Street, Sandbox, CA 90210" to ("ADDRESS" to "Maps"),
            "https://toolz.app/docs/dev-sandbox" to ("URL" to "Chrome"),
            "SELECT * FROM fake_clips WHERE id = 30;" to ("CODE" to "DB Browser"),
            "7 1 3 4 2" to ("OTP" to "Bank"),
            "Do not ship this device state anywhere" to ("TEXT" to "Notes"),
            "https://github.com/toolz/toolz/pull/5373" to ("URL" to "Chrome"),
            "#00E5FF" to ("COLOR" to "Palette"),
            "Lorem dummy paste for the fixture gallery" to ("TEXT" to "Toolz"),
            "+44 20 7946 0009" to ("PHONE" to "Dialer"),
            "docker compose up -d --build" to ("CODE" to "Terminal"),
            "Kaiserdamm 12, Berlin (fictional)" to ("ADDRESS" to "Maps"),
            "0 2 9 4 7" to ("OTP" to "Authenticator"),
            "Alpha test seed line" to ("TEXT" to "Toolz"),
            "fixture-squad@toolz.local" to ("EMAIL" to "Spark"),
            "42 * 1.618 ≈ 67.956" to ("MATHS" to "Calculator"),
            "git rebase -i HEAD~3" to ("CODE" to "Terminal"),
            "https://nebula.tools/status/12345" to ("URL" to "Nebula"),
            "4242 4242 4242 4242" to ("CARD" to "Checkout"),
            "+65 6000 0100" to ("PHONE" to "Dialer"),
            "#FFB300" to ("COLOR" to "Material"),
            "Pinned fixture — survives clearAll in the sandbox" to ("TEXT" to "Toolz"),
        )

        return seed.mapIndexed { i, (content, meta) ->
            val (rawType, sourceApp) = meta
            val type = when (rawType) {
                "URL" -> ClipboardType.LINK
                "MATHS" -> ClipboardType.MATH
                else -> rawType
            }
            val sensitiveReason = when (type) {
                ClipboardType.OTP -> ClipSensitivityReason.OTP
                ClipboardType.CARD -> ClipSensitivityReason.CARD
                ClipboardType.SECRET -> ClipSensitivityReason.SECRET
                else -> null
            }
            val sensitive = sensitiveReason != null
            val aiProcessed = i % 7 == 3
            ClipboardEntry(
                id = i + 1,
                content = content,
                // First 21 land today, a few yesterday/older so the grouping
                // exercises Today/Yesterday/Older headers.
                timestamp = now - i * 47 * minute - (i / 21) * 26 * 60 * minute,
                type = type,
                isPinned = i == 0 || i == 29,
                sourceApp = sourceApp,
                summary = if (aiProcessed) "Fixture summary — processed locally, not by AI." else null,
                isAiProcessed = aiProcessed,
                contentHash = content.hashCode(),
                source = "Fixture",
                sizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong(),
                isSensitive = sensitive,
                sensitivityReason = sensitiveReason,
                // One fixture demonstrates the biometric lock without touching
                // any real Password Vault row.
                isLocked = type == ClipboardType.CARD,
                passwordVaultMatch = false,
                metadataJson = "{\"sandbox\":true}",
                expiresAt = if (sensitive) null else now + 7L * 24L * 60L * 60L * 1000L,
                aiState = when {
                    sensitive -> ClipAiState.SKIPPED_SENSITIVE
                    aiProcessed -> ClipAiState.DONE
                    else -> ClipAiState.IDLE
                },
            )
        }
    }
}

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

package com.frerox.toolz.data.clipboard

import android.net.Uri

/** Stable type strings. AI may still return arbitrary uppercase categories. */
object ClipboardType {
    const val TEXT = "TEXT"
    const val LINK = "LINK"
    const val URL = "URL" // legacy
    const val EMAIL = "EMAIL"
    const val PHONE = "PHONE"
    const val COLOR = "COLOR"
    const val CODE = "CODE"
    const val OTP = "OTP"
    const val SECRET = "SECRET"
    const val CARD = "CARD"
    const val CRYPTO = "CRYPTO"
    const val ADDRESS = "ADDRESS"
    const val MATH = "MATH"
    const val MATHS = "MATHS" // legacy
    const val PERSONAL = "PERSONAL"
    const val SOCIAL = "SOCIAL"
    const val IMAGE = "IMAGE"
    const val FILE = "FILE"
    const val MULTI = "MULTI"
}

object ClipSensitivityReason {
    const val PASSWORD_VAULT = "PASSWORD_VAULT"
    const val OTP = "OTP"
    const val CARD = "CARD"
    const val SECRET = "SECRET"
    const val LABEL = "LABEL"
    const val EXTRA = "EXTRA"
    const val MANUAL = "MANUAL"
}

object ClipAiState {
    const val IDLE = "IDLE"
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
    const val SKIPPED_SENSITIVE = "SKIPPED_SENSITIVE"
    const val DISABLED = "DISABLED"
}

enum class ClipboardFilter {
    ALL,
    TEXT,
    LINKS,
    CODE,
    OTP,
    IMAGES,
    FILES,
    PINNED,
    SENSITIVE,
}

data class ClipClassification(
    val type: String,
    val isSensitive: Boolean = false,
    val reason: String? = null,
    val passwordVaultMatch: Boolean = false,
)

/** One item inside a system clip. */
data class ClipboardPayloadItem(
    val text: String? = null,
    val html: String? = null,
    val uri: Uri? = null,
    val mimeType: String? = null,
    val fileName: String? = null,
    val isSensitive: Boolean = false,
)

/** Normalized payload shared by the service, popup, share target and manual paste. */
data class ClipboardPayload(
    val items: List<ClipboardPayloadItem>,
    val label: String? = null,
    val source: String,
    val sourcePackage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSensitiveExtra: Boolean = false,
)

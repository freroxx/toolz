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

import java.security.MessageDigest

/** One attachment input to the stable clipboard content hash. */
data class ClipboardHashAttachment(
    val blobId: String,
    val fileName: String? = null,
    val mimeType: String? = null,
)

/**
 * Stable SHA-256 helpers shared by capture, backup and tests.
 *
 * The clipboard hash deliberately includes a format marker and sorts
 * attachments by blob id. Re-copying the same content therefore bumps the
 * existing row instead of creating a duplicate, regardless of the order in
 * which the system exposed multiple items.
 */
object ClipboardHashing {

    const val FORMAT_VERSION = "v2"

    fun sha256(value: String): String =
        sha256(value.toByteArray(Charsets.UTF_8))

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun contentHash(
        content: String,
        html: String? = null,
        attachments: List<ClipboardHashAttachment> = emptyList(),
    ): String = buildString {
        append(FORMAT_VERSION).append('\n')
        append(content).append('\n')
        append(html.orEmpty()).append('\n')
        attachments.sortedBy { it.blobId }.forEach { attachment ->
            append(attachment.blobId).append(':')
                .append(attachment.fileName.orEmpty()).append(':')
                .append(attachment.mimeType.orEmpty()).append('\n')
        }
    }.let(::sha256)
}

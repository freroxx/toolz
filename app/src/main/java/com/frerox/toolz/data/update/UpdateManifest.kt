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

package com.frerox.toolz.data.update

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val changelog: String? = null,
    val isCritical: Boolean? = false,
    /**
     * Anti-mod update-lock: Whisper is blocked while
     * `installedVersionCode < minimumVersionCode`. Null/0 = no floor.
     * Keep in sync with edge secret MIN_VERSION_CODE.
     */
    val minimumVersionCode: Int? = null,
    /**
     * Ed25519 signature (base64, 64 bytes) over the canonical manifest
     * JSON — the manifest with the `signature` and `_comment` fields
     * removed, minified, keys in original order (see
     * [UpdateRepository.verifyManifestSignature]). Null = unsigned legacy
     * manifest. When present, it MUST verify against the public key
     * embedded in the app or the manifest is rejected fail-closed.
     */
    val signature: String? = null,
    val releases: List<UpdateRelease>? = null
)

@JsonClass(generateAdapter = true)
data class UpdateRelease(
    val abi: String,
    val downloadUrl: String,
    val size: Long? = null
)

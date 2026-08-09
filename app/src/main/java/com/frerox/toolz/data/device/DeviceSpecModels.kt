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

package com.frerox.toolz.data.device

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceSpecResponse(
    @SerialName("search_query") val searchQuery: String = "",
    @SerialName("matched_device") val matchedDevice: String = "",
    @SerialName("search_name") val searchName: String = "",   // fallback alias from older cache entries
    @SerialName("source_url") val sourceUrl: String = "",
    @SerialName("image") val image: String = "",
    @SerialName("img") val img: String = "", // Legacy support for old cache entries
    @SerialName("specifications") val specifications: Map<String, Map<String, String>> = emptyMap()
)

// UI-friendly models derived from the raw response
data class DeviceSpecUiModel(
    val name: String,
    val image: String,
    val sourceUrl: String,
    val quickSpecs: List<QuickSpecItem>,
    val categories: List<SpecCategory>,
    val isFromCache: Boolean = false
)

data class QuickSpecItem(
    val name: String,
    val value: String
)

data class SpecCategory(
    val name: String,
    val items: List<SpecDetail>
)

data class SpecDetail(
    val name: String,
    val value: String
)

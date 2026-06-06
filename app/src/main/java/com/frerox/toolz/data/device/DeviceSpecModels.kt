package com.frerox.toolz.data.device

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceSpecResponse(
    @SerialName("search_query") val searchQuery: String = "",
    @SerialName("matched_device") val matchedDevice: String = "",
    @SerialName("source_url") val sourceUrl: String = "",
    @SerialName("img") val img: String = "",
    @SerialName("specifications") val specifications: Map<String, Map<String, String>> = emptyMap()
)

// UI-friendly models derived from the raw response
data class DeviceSpecUiModel(
    val name: String,
    val img: String,
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

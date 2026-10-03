package com.frerox.toolz.data.news

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NewsDto(
    @SerialName("schemaVersion") val schemaVersion: Int = 1,
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("body") val body: String,
    @SerialName("imageUrl") val imageUrl: String? = null,
    @SerialName("actionLabel") val actionLabel: String? = null,
    @SerialName("actionUrl") val actionUrl: String? = null,
    @SerialName("priority") val priority: String = "info",
    @SerialName("status") val status: String = "published",
    @SerialName("pinned") val pinned: Boolean = false,
    @SerialName("publishAt") val publishAt: String? = null,
    @SerialName("expiresAt") val expiresAt: String? = null,
    @SerialName("minAppVersion") val minAppVersion: String? = null,
    @SerialName("maxAppVersion") val maxAppVersion: String? = null,
    @SerialName("onlyVersions") val onlyVersions: List<String> = emptyList(),
    @SerialName("excludedVersions") val excludedVersions: List<String> = emptyList(),
    @SerialName("delaySeconds") val delaySeconds: Int = 0,
    @SerialName("frequency") val frequency: String = "once",
    @SerialName("intervalHours") val intervalHours: Int? = null,
    @SerialName("maxImpressions") val maxImpressions: Int? = null,
    @SerialName("dismissible") val dismissible: Boolean = true,
    @SerialName("showInHistory") val showInHistory: Boolean = true,
    @SerialName("requiresAction") val requiresAction: Boolean = false,
    @SerialName("notify") val notify: Boolean = true,
    @SerialName("disappearing") val disappearing: Boolean = false,
    @SerialName("createdAt") val createdAt: String = "",
    @SerialName("updatedAt") val updatedAt: String = ""
)

@Serializable
data class NewsFeedDto(
    @SerialName("version") val version: Int = 1,
    @SerialName("appVersion") val appVersion: String = "",
    @SerialName("count") val count: Int = 0,
    @SerialName("news") val news: List<NewsDto> = emptyList(),
    @SerialName("removedIds") val removedIds: List<String> = emptyList(),
    @SerialName("v") val feedVersion: Int = -1,
    @SerialName("degraded") val degraded: Boolean = false
)

@Serializable
data class NewsVersionDto(
    @SerialName("v") val v: Int = -1,
    @SerialName("degraded") val degraded: Boolean = false
)

package com.frerox.toolz.data.update

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    @Json(name = "downloadUrl") val apkUrl: String?,
    val changelog: String?,
    val isCritical: Boolean?,
    val sha256: String?
)

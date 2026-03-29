package com.frerox.toolz.data.update

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val changelog: String?,
    val isCritical: Boolean? = false,
    val releases: List<UpdateRelease>?
)

@JsonClass(generateAdapter = true)
data class UpdateRelease(
    val abi: String,
    val downloadUrl: String,
    val size: Long? = null
)

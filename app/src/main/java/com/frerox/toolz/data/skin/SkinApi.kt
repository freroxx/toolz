package com.frerox.toolz.data.skin

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class SkinSubmitRequest(
    val name: String,
    val skin: SkinDto,
)

data class SkinSubmitResponse(
    val ok: Boolean = false,
    val id: String? = null,
    val error: String? = null,
)

interface SkinApi {
    @GET("api/skins")
    suspend fun getSkins(
        @Query("appVersion") appVersion: String,
        @Query("platform") platform: String = "android",
    ): SkinFeedDto

    @POST("api/skins")
    suspend fun submitSkin(@Body body: SkinSubmitRequest): SkinSubmitResponse
}

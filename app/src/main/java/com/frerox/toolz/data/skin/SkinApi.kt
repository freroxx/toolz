package com.frerox.toolz.data.skin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

@Serializable
data class SkinSubmitRequest(
    @SerialName("name") val name: String,
    @SerialName("skin") val skin: SkinDto,
)

@Serializable
data class SkinSubmitResponse(
    @SerialName("ok") val ok: Boolean = false,
    @SerialName("id") val id: String? = null,
    @SerialName("error") val error: String? = null,
)

@Serializable
data class SkinStatusDto(
    @SerialName("id") val id: String,
    @SerialName("status") val status: String,
)

@Serializable
data class SkinStatusFeedDto(
    @SerialName("statuses") val statuses: List<SkinStatusDto> = emptyList(),
    @SerialName("removed") val removed: List<String> = emptyList(),
)

interface SkinApi {
    @GET("api/skins")
    suspend fun getSkins(
        @Query("appVersion") appVersion: String,
        @Query("platform") platform: String = "android",
    ): SkinFeedDto

    @POST("api/skins")
    suspend fun submitSkin(@Body body: SkinSubmitRequest): SkinSubmitResponse

    @GET("api/skins")
    suspend fun getSkinStatuses(
        @Query("status") ids: String,
    ): SkinStatusFeedDto
}

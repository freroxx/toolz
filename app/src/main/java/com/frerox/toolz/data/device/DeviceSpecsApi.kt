package com.frerox.toolz.data.device

import retrofit2.http.GET
import retrofit2.http.Query

interface DeviceSpecsApi {
    @GET("api/specs")
    suspend fun getDeviceSpecs(
        @Query("model") model: String,
    ): DeviceSpecResponse
}

package com.frerox.toolz.data.update

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Url

interface UpdateService {
    @Headers(
        "Accept: application/vnd.github.v3+json",
        "User-Agent: Toolz-App"
    )
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubRelease>

    @GET
    suspend fun getUpdateManifest(@Url url: String): Response<UpdateManifest>
}

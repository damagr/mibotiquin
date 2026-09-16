package com.mibotiquin.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET

interface GitHubApi {
    @GET("repos/damagr/mibotiquin/releases/latest")
    suspend fun getLatestRelease(): Response<GitHubRelease>
}

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("body") val body: String?,
    @SerializedName("assets") val assets: List<GitHubAsset>
)

data class GitHubAsset(
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("name") val name: String
)
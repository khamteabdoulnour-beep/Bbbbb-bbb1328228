package com.hikari.manga.data.network

import com.google.gson.JsonObject
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Url

interface ApiService {

    @GET
    suspend fun getMangaList(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): JsonObject

    @GET
    suspend fun getRawHtml(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): ResponseBody
}

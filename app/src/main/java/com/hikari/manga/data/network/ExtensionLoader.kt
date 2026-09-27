package com.hikari.manga.data.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.hikari.manga.data.model.ApiServerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

data class ExtensionData(
    val baseUrl: String,
    val mangaListUrl: String,
    val mangaDetailUrl: String,
    val chaptersUrl: String,
    val pagesUrl: String,
    val searchUrl: String,
    val headers: Map<String, String>
)

class ExtensionLoader {

    private val TAG = "ExtensionLoader"
    private val gson = Gson()
    private var activeExtension: ExtensionData? = null
    private var activeServer: ApiServerInfo? = null

    suspend fun loadExtension(server: ApiServerInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "تحميل إضافة: ${server.server} من ${server.fileLink}")

                val request = Request.Builder()
                    .url(server.fileLink)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .header("Accept", "application/json, */*")
                    .build()

                val response = NetworkModule.okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e(TAG, "فشل تحميل الإضافة: ${response.code}")
                    return@withContext false
                }

                val rawJson = response.body?.string()
                if (rawJson.isNullOrBlank()) {
                    Log.e(TAG, "ملف الإضافة فارغ")
                    return@withContext false
                }

                val jsonObject = gson.fromJson(rawJson, JsonObject::class.java)
                activeExtension = parseExtensionJson(jsonObject, server.referer)
                activeServer = server
                true

            } catch (e: Exception) {
                Log.e(TAG, "خطأ في تحميل إضافة ${server.server}: ${e.message}")
                false
            }
        }
    }

    private fun parseExtensionJson(json: JsonObject, referer: String): ExtensionData {
        val linksObj = if (json.has("links") && json.get("links").isJsonObject) {
            json.getAsJsonObject("links")
        } else null

        val baseUrl = (
            json.get("baseUrl")?.asString
            ?: json.get("base_url")?.asString
            ?: linksObj?.get("baseUrl")?.asString
            ?: json.get("url")?.asString
            ?: json.get("domain")?.asString
            ?: referer.trimEnd('/')
        ).trimEnd('/')

        fun resolve(path: String): String {
            if (path.isEmpty()) return baseUrl
            return if (path.startsWith("http")) path else "$baseUrl/${path.trimStart('/')}"
        }

        val checkUrl = linksObj?.get("checkUrl")?.asString
        val mangaListUrl = if (!checkUrl.isNullOrEmpty()) {
            checkUrl
        } else {
            resolve(
                json.get("mangaListUrl")?.asString
                ?: json.get("listUrl")?.asString
                ?: json.get("catalogUrl")?.asString
                ?: "/manga"
            )
        }

        val mangaDetailUrl = resolve(
            json.get("mangaDetailUrl")?.asString
            ?: json.get("detailUrl")?.asString
            ?: "/manga/{id}"
        )

        val chaptersUrl = resolve(
            json.get("chaptersUrl")?.asString
            ?: json.get("chapterListUrl")?.asString
            ?: "/manga/{id}/chapters"
        )

        val pagesUrl = resolve(
            json.get("pagesUrl")?.asString
            ?: json.get("pageListUrl")?.asString
            ?: "/chapter/{chapterId}"
        )

        val searchUrl = resolve(
            json.get("searchUrl")?.asString
            ?: json.get("search_url")?.asString
            ?: "/?s={query}&post_type=wp-manga"
        )

        val headers = mutableMapOf(
            "Referer" to if (referer.isNotBlank()) referer else "$baseUrl/",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.7",
            "Accept-Language" to "ar,en;q=0.9"
        )

        if (json.has("headers") && json.get("headers").isJsonObject) {
            json.getAsJsonObject("headers").entrySet().forEach { entry ->
                headers[entry.key] = entry.value.asString
            }
        }

        return ExtensionData(
            baseUrl = baseUrl,
            mangaListUrl = mangaListUrl,
            mangaDetailUrl = mangaDetailUrl,
            chaptersUrl = chaptersUrl,
            pagesUrl = pagesUrl,
            searchUrl = searchUrl,
            headers = headers
        )
    }

    fun getMangaListUrl(): String = activeExtension?.mangaListUrl ?: ""
    fun getMangaDetailUrl(id: String): String = activeExtension?.mangaDetailUrl?.replace("{id}", id) ?: ""
    fun getChaptersUrl(id: String): String = activeExtension?.chaptersUrl?.replace("{id}", id) ?: ""
    fun getPagesUrl(id: String): String = activeExtension?.pagesUrl?.replace("{chapterId}", id) ?: ""
    fun getSearchUrl(query: String): String = activeExtension?.searchUrl?.replace("{query}", java.net.URLEncoder.encode(query, "UTF-8")) ?: ""
    fun getHeaders(): Map<String, String> = activeExtension?.headers ?: emptyMap()
    fun getActiveServer(): ApiServerInfo? = activeServer
    fun isLoaded(): Boolean = activeExtension != null
    fun clear() { activeExtension = null; activeServer = null }
}

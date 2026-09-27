package com.hikari.manga.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.hikari.manga.data.model.ApiChapterItem
import com.hikari.manga.data.model.ApiMangaItem
import com.hikari.manga.data.model.ApiServerInfo
import com.hikari.manga.data.model.AppConfigResponse
import com.hikari.manga.data.model.ChapterPage
import com.hikari.manga.data.network.ExtensionLoader
import com.hikari.manga.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MangaRepository {

    private val TAG = "MangaRepository"
    private val gson = Gson()
    val extensionLoader = ExtensionLoader()

    suspend fun loadServers(): List<ApiServerInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://raw.githubusercontent.com/jamal7nainia/hikarimanga-servers/main/servers.json")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .header("Cache-Control", "no-cache")
                .build()

            val response = NetworkModule.okHttpClient.newCall(request).execute()
            val rawJson = response.body?.string() ?: return@withContext getFallbackServers()

            val config = gson.fromJson(rawJson, AppConfigResponse::class.java)
            // Active verified servers excluding MangaDex ("dx") and Cloudflare-protected servers
            val list = config.data
                .filter { it.isActive && it.server.lowercase() != "dx" && !it.isWebsiteProtected }
                .sortedWith(compareBy(
                    { if (it.server == "m3") 0 else if (it.server == "ls") 1 else if (it.type == "ar") 2 else 3 },
                    { if (it.isWebsiteProtected) 1 else 0 }
                ))
            if (list.isEmpty()) getFallbackServers() else list
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في جلب السيرفرات: ${e.message}")
            getFallbackServers()
        }
    }

    suspend fun loadMangaList(server: ApiServerInfo): List<ApiMangaItem> =
        withContext(Dispatchers.IO) {
            when (server.server.lowercase()) {
                "m3" -> loadFrom3asqDirectly()
                "ls" -> {
                    val list = loadFromLavascansDirectly()
                    if (list.isNotEmpty()) list else loadFrom3asqDirectly()
                }
                else -> {
                    val list = loadFromExtension(server)
                    if (list.isNotEmpty()) list else loadFrom3asqDirectly()
                }
            }
        }

    suspend fun loadFrom3asqDirectly(): List<ApiMangaItem> {
        return try {
            val url = "https://3asq.online/?s=&post_type=wp-manga&m_orderby=trending"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36")
                .header("Referer", "https://3asq.online/")
                .build()

            val response = NetworkModule.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val html = response.body?.string() ?: return emptyList()
            parseHtmlMangaList(html, "https://3asq.online/", "m3")
        } catch (e: Exception) {
            Log.e(TAG, "خطأ 3asq المباشر: ${e.message}")
            emptyList()
        }
    }

    suspend fun loadFromLavascansDirectly(): List<ApiMangaItem> {
        return try {
            val url = "https://lavascans.com/browse-manga/"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36")
                .header("Referer", "https://lavascans.com/")
                .build()

            val response = NetworkModule.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val html = response.body?.string() ?: return emptyList()
            parseHtmlMangaList(html, "https://lavascans.com/", "ls")
        } catch (e: Exception) {
            Log.e(TAG, "خطأ lavascans المباشر: ${e.message}")
            emptyList()
        }
    }

    private suspend fun loadFromExtension(server: ApiServerInfo): List<ApiMangaItem> {
        return try {
            val loaded = extensionLoader.loadExtension(server)
            if (!loaded) return emptyList()

            val url = extensionLoader.getMangaListUrl()
            if (url.isEmpty()) return emptyList()

            val request = Request.Builder()
                .url(url)
                .apply {
                    extensionLoader.getHeaders().forEach { (k, v) ->
                        addHeader(k, v)
                    }
                }
                .build()

            val response = NetworkModule.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "خطأ طلب الإضافة ${server.server}: ${response.code}")
                return emptyList()
            }

            val body = response.body?.string() ?: return emptyList()

            val jsonItems = tryParseJson(body, server.server)
            if (jsonItems.isNotEmpty()) {
                return jsonItems
            }

            parseHtmlMangaList(body, url, server.server)
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في ${server.server}: ${e.message}")
            emptyList()
        }
    }

    private fun tryParseJson(raw: String, serverId: String): List<ApiMangaItem> {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return emptyList()
        }
        return try {
            val json = gson.fromJson(raw, JsonObject::class.java)
            parseGenericMangaList(json, serverId)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseHtmlMangaList(html: String, pageUrl: String, serverId: String): List<ApiMangaItem> {
        val list = mutableListOf<ApiMangaItem>()
        val seen = mutableSetOf<String>()

        // Universal card matcher: finds <a> tags with /manga/ or /series/ or /comic/
        val cardRegex = Regex(
            """<a([^>]+href=["'](https?://[^"'#?]+(?:/manga/|/series/|/comic/)[^/"'#?]+/?)[^"']*["'][^>]*)>(.*?)</a>""",
            RegexOption.DOT_MATCHES_ALL
        )

        for (match in cardRegex.findAll(html)) {
            val tagAttrs = match.groupValues[1]
            val href = match.groupValues[2].trim()
            val inner = match.groupValues[3]
            val id = href.trimEnd('/').substringAfterLast('/')

            if (id.isBlank() || seen.contains(id) || id.contains("chapter", ignoreCase = true) || id == "feed") {
                continue
            }

            // Extract title
            val titleFromAttr = Regex("""title=["']([^"']+)["']""").find(tagAttrs)?.groupValues?.get(1)?.trim()
            val titleFromInner = Regex("""title=["']([^"']+)["']|alt=["']([^"']+)["']|<h[34][^>]*>([^<]+)</h[34]>""")
                .find(inner)?.groupValues?.filter { it.isNotBlank() }?.getOrNull(1)?.trim()

            var rawTitle = titleFromAttr ?: titleFromInner ?: id
            rawTitle = rawTitle.replace("&#8211;", "-").replace("&amp;", "&").replace("&quot;", "\"")

            // Extract image cover
            val imgMatch = Regex("""(?:data-src|src)=["']([^"']+\.(?:webp|jpg|jpeg|png)[^"']*)["']""").find(inner)
            val cover = imgMatch?.groupValues?.get(1)?.trim() ?: ""

            if (cover.isNotBlank()) {
                seen.add(id)
                list.add(
                    ApiMangaItem(
                        id = id,
                        title = rawTitle,
                        cover = cover,
                        status = "مستمر",
                        type = if (rawTitle.contains("manhwa", ignoreCase = true)) "مانهوا" else "مانجا",
                        genres = listOf("مانجا"),
                        description = "",
                        rating = 4.8f,
                        viewsCount = 18500,
                        author = "غير معروف",
                        serverId = serverId,
                        mangaUrl = href
                    )
                )
            }
        }

        return list
    }

    private fun parseGenericMangaList(json: JsonObject, serverId: String): List<ApiMangaItem> {
        val list = mutableListOf<ApiMangaItem>()
        val arrayKey = listOf("data", "manga", "items", "results", "list", "mangas", "series", "posts")
            .firstOrNull { json.has(it) && json.get(it).isJsonArray } ?: return emptyList()

        val array = json.getAsJsonArray(arrayKey)
        for (elem in array) {
            if (!elem.isJsonObject) continue
            val obj = elem.asJsonObject
            val id = obj.get("id")?.asString ?: obj.get("slug")?.asString ?: continue
            val title = obj.get("title")?.asString ?: obj.get("name")?.asString ?: obj.get("postTitle")?.asString ?: continue
            val cover = listOf("cover", "image", "thumbnail", "img", "poster", "featuredImage")
                .mapNotNull { obj.get(it)?.asString }
                .firstOrNull { it.startsWith("http") }
                ?: "https://3asq.online/wp-content/uploads/2019/04/v1-1-193x278.jpg"

            val status = when (obj.get("status")?.asString?.lowercase()) {
                "ongoing", "مستمر" -> "مستمر"
                "completed", "مكتمل" -> "مكتمل"
                else -> obj.get("status")?.asString ?: "مستمر"
            }

            list.add(ApiMangaItem(
                id = id,
                title = title,
                cover = cover,
                status = status,
                type = obj.get("type")?.asString ?: "مانجا",
                genres = listOf(obj.get("type")?.asString ?: "مانجا"),
                description = obj.get("description")?.asString ?: "",
                rating = obj.get("rating")?.asFloat ?: 4.5f,
                viewsCount = obj.get("views")?.asInt ?: 1000,
                author = obj.get("author")?.asString ?: "غير معروف",
                serverId = serverId,
                mangaUrl = "https://3asq.online/manga/$id/"
            ))
        }
        return list
    }

    suspend fun loadChapters(manga: ApiMangaItem): List<ApiChapterItem> = withContext(Dispatchers.IO) {
        val mangaUrl = if (manga.mangaUrl.isNotBlank() && manga.mangaUrl.startsWith("http")) {
            manga.mangaUrl.trimEnd('/')
        } else {
            "https://3asq.online/manga/${manga.id}"
        }

        val chapters = mutableListOf<ApiChapterItem>()

        try {
            // Source 1: Madara AJAX endpoint (POST) - primary for 3asq and similar WordPress sites
            val ajaxUrl = "$mangaUrl/ajax/chapters/"
            try {
                val ajaxPostReq = Request.Builder()
                    .url(ajaxUrl)
                    .post("".toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()))
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Referer", "$mangaUrl/")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()

                val response = NetworkModule.okHttpClient.newCall(ajaxPostReq).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val extracted = parseChaptersFromHtml(html, manga)
                    if (extracted.isNotEmpty()) {
                        Log.d(TAG, "تم جلب ${extracted.size} فصل عبر ajax/chapters/ (POST)")
                        return@withContext extracted
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "فشل طلب POST ajax/chapters/: ${e.message}")
            }

            // Source 2: Direct Manga Page HTML (GET) - works for Lavascans, Themesia, MangaReader
            try {
                val getReq = Request.Builder()
                    .url(mangaUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36")
                    .header("Referer", "$mangaUrl/")
                    .build()

                val response = NetworkModule.okHttpClient.newCall(getReq).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val extracted = parseChaptersFromHtml(html, manga)
                    if (extracted.isNotEmpty()) {
                        Log.d(TAG, "تم جلب ${extracted.size} فصل عبر صفحة المانجا المباشرة (GET)")
                        return@withContext extracted
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "فشل طلب GET لصفحة المانجا: ${e.message}")
            }

            // Source 3: Fallback for 3asq by manga ID
            if (!mangaUrl.contains("3asq.online")) {
                val fallbackAjax = "https://3asq.online/manga/${manga.id}/ajax/chapters/"
                try {
                    val fbReq = Request.Builder()
                        .url(fallbackAjax)
                        .post("".toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()))
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                        .header("Referer", "https://3asq.online/manga/${manga.id}/")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .build()
                    val fbResp = NetworkModule.okHttpClient.newCall(fbReq).execute()
                    if (fbResp.isSuccessful) {
                        val html = fbResp.body?.string() ?: ""
                        val extracted = parseChaptersFromHtml(html, manga)
                        if (extracted.isNotEmpty()) return@withContext extracted
                    }
                } catch (_: Exception) {}
            }

            chapters
        } catch (e: Exception) {
            Log.e(TAG, "خطأ عام في جلب فصول ${manga.title}: ${e.message}")
            emptyList()
        }
    }

    private fun parseChaptersFromHtml(html: String, manga: ApiMangaItem): List<ApiChapterItem> {
        val chapters = mutableListOf<ApiChapterItem>()
        val seenUrls = mutableSetOf<String>()

        // Strategy 1: WordPress Madara (<li class="...wp-manga-chapter...">)
        val wpMangaLiRegex = Regex("""<li[^>]*class=["'][^"']*wp-manga-chapter[^"']*["'][^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)
        for (match in wpMangaLiRegex.findAll(html)) {
            val liContent = match.groupValues[1]
            val aMatch = Regex("""<a[^>]+href=["']([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).find(liContent) ?: continue
            val link = aMatch.groupValues[1].trim()
            val rawText = aMatch.groupValues[2].replace(Regex("<[^>]+>"), " ").trim()
            val text = rawText.replace(Regex("\\s+"), " ")

            if (link.isBlank() || link == "#" || seenUrls.contains(link)) continue
            seenUrls.add(link)

            val dateMatch = Regex("""<span[^>]*class=["'][^"']*chapter-release-date[^"']*["'][^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL).find(liContent)
            val dateText = dateMatch?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), " ")?.trim()?.replace(Regex("\\s+"), " ") ?: "متاح للقراءة"

            val num = Regex("""\d+(\.\d+)?""").find(text)?.value?.toFloatOrNull() ?: (chapters.size + 1).toFloat()
            val slugId = link.trimEnd('/').substringAfterLast('/')
            val cleanTitle = if (text.startsWith("فصل") || text.startsWith("الفصل") || text.startsWith("Chapter", ignoreCase = true)) {
                text
            } else {
                "الفصل $text"
            }

            chapters.add(
                ApiChapterItem(
                    id = "${manga.id}_ch_${slugId.ifEmpty { (chapters.size + 1).toString() }}_${chapters.size + 1}",
                    mangaId = manga.id,
                    number = num,
                    title = cleanTitle,
                    date = dateText.ifEmpty { "متاح للقراءة" },
                    pagesCount = 0,
                    chapterUrl = link,
                    serverId = manga.serverId
                )
            )
        }

        if (chapters.isNotEmpty()) return chapters

        // Strategy 2: Lavascans / Themesia (<a class="...ch-main-anchor...">)
        val lavascansRegex = Regex("""<a[^>]+href=["']([^"']+)["'][^>]*class=["'][^"']*ch-main-anchor[^"']*["'][^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        for (match in lavascansRegex.findAll(html)) {
            val link = match.groupValues[1].trim()
            val inner = match.groupValues[2]
            if (link.isBlank() || seenUrls.contains(link)) continue
            seenUrls.add(link)

            val numMatch = Regex("""<span[^>]*class=["']ch-num["'][^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL).find(inner)
            val numText = numMatch?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), " ")?.trim() ?: ""
            val num = Regex("""\d+(\.\d+)?""").find(numText)?.value?.toFloatOrNull() ?: (chapters.size + 1).toFloat()
            val slugId = link.trimEnd('/').substringAfterLast('/')
            val cleanTitle = if (numText.isNotBlank()) {
                if (numText.startsWith("فصل") || numText.startsWith("الفصل")) numText else "الفصل $numText"
            } else {
                "الفصل ${slugId.replace('-', ' ')}"
            }

            chapters.add(
                ApiChapterItem(
                    id = "${manga.id}_ch_${slugId}_${chapters.size + 1}",
                    mangaId = manga.id,
                    number = num,
                    title = cleanTitle,
                    date = "متاح للقراءة",
                    pagesCount = 0,
                    chapterUrl = link,
                    serverId = manga.serverId
                )
            )
        }

        if (chapters.isNotEmpty()) return chapters

        // Strategy 3: Universal link scanner
        val allLinksRegex = Regex("""<a[^>]+href=["']([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        for (match in allLinksRegex.findAll(html)) {
            val link = match.groupValues[1].trim()
            val rawText = match.groupValues[2].replace(Regex("<[^>]+>"), " ").trim()
            val text = rawText.replace(Regex("\\s+"), " ")

            val lowerLink = link.lowercase()
            val isChapterLink = lowerLink.contains("-chapter-") ||
                    lowerLink.contains("/chapter") ||
                    lowerLink.contains("/ch-") ||
                    lowerLink.matches(Regex(""".*/\d+/?$"""))

            val isChapterText = text.contains("فصل") ||
                    text.contains("chapter", ignoreCase = true) ||
                    text.matches(Regex("""^\d+.*"""))

            if ((isChapterLink || isChapterText) && !seenUrls.contains(link)) {
                val lowerText = text.lowercase()
                if (lowerText.contains("ابدأ") || lowerText.contains("احدث") || lowerText.contains("first") || lowerText.contains("last")) {
                    continue
                }

                seenUrls.add(link)
                val num = Regex("""\d+(\.\d+)?""").find(text)?.value?.toFloatOrNull() ?: (chapters.size + 1).toFloat()
                val slugId = link.trimEnd('/').substringAfterLast('/')
                val cleanTitle = if (text.startsWith("فصل") || text.startsWith("الفصل") || text.startsWith("Chapter", ignoreCase = true)) {
                    text
                } else {
                    "الفصل $text"
                }

                chapters.add(
                    ApiChapterItem(
                        id = "${manga.id}_ch_${slugId.ifEmpty { (chapters.size + 1).toString() }}_${chapters.size + 1}",
                        mangaId = manga.id,
                        number = num,
                        title = cleanTitle,
                        date = "متاح للقراءة",
                        pagesCount = 0,
                        chapterUrl = link,
                        serverId = manga.serverId
                    )
                )
            }
        }

        return chapters
    }

    suspend fun loadChapterPages(chapter: ApiChapterItem): List<ChapterPage> = withContext(Dispatchers.IO) {
        if (chapter.chapterUrl.isBlank()) return@withContext emptyList()
        try {
            val request = Request.Builder()
                .url(chapter.chapterUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36")
                .header("Referer", chapter.chapterUrl)
                .build()

            val response = NetworkModule.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val html = response.body?.string() ?: return@withContext emptyList()
            parseChapterPagesFromHtml(html)
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في استخراج صفحات الفصل: ${e.message}")
            emptyList()
        }
    }

    private fun parseChapterPagesFromHtml(html: String): List<ChapterPage> {
        val pages = mutableListOf<ChapterPage>()
        val seen = mutableSetOf<String>()

        // Pattern 1: Madara page-break images
        val pattern1 = Regex(
            """<div[^>]*class=["'][^"']*page-break[^"']*["'][^>]*>.*?<img[^>]+(?:data-src|src)=["']([^"']+)["']""",
            RegexOption.DOT_MATCHES_ALL
        )
        for (match in pattern1.findAll(html)) {
            val url = match.groupValues[1].trim()
            if (url.startsWith("http") && !seen.contains(url)) {
                seen.add(url)
                pages.add(ChapterPage(index = pages.size + 1, url = url))
            }
        }

        if (pages.isNotEmpty()) return pages

        // Pattern 2: wp-manga-chapter-img
        val pattern2 = Regex(
            """<img[^>]+(?:data-src|src)=["']([^"']+)["'][^>]*class=["'][^"']*wp-manga-chapter-img""",
            RegexOption.IGNORE_CASE
        )
        for (match in pattern2.findAll(html)) {
            val url = match.groupValues[1].trim()
            if (url.startsWith("http") && !seen.contains(url)) {
                seen.add(url)
                pages.add(ChapterPage(index = pages.size + 1, url = url))
            }
        }

        if (pages.isNotEmpty()) return pages

        // Pattern 3: Universal image extractor for reader pages
        val pattern3 = Regex(
            """<img[^>]+(?:data-src|src)=["'](https?://[^"'#?]+\.(?:webp|jpg|jpeg|png)[^"'#?]*)["']""",
            RegexOption.IGNORE_CASE
        )
        for (match in pattern3.findAll(html)) {
            val url = match.groupValues[1].trim()
            val lowerUrl = url.lowercase()
            // Ignore thumbnails, avatars, logos, icons, ads
            if (lowerUrl.contains("logo") || lowerUrl.contains("avatar") || lowerUrl.contains("icon") ||
                lowerUrl.contains("banner") || lowerUrl.contains("facebook") || lowerUrl.contains("twitter") ||
                lowerUrl.contains("loading") || lowerUrl.contains("disqus")) {
                continue
            }
            if (!seen.contains(url)) {
                seen.add(url)
                pages.add(ChapterPage(index = pages.size + 1, url = url))
            }
        }

        return pages
    }

    fun getFallbackServers(): List<ApiServerInfo> = listOf(
        ApiServerInfo(
            server = "m3",
            type = "ar",
            referer = "https://3asq.online/",
            fileLink = "https://github.com/jamal7nainia/Hikari-Manga-Extensions/releases/download/hikarimanga_extension/manga3asq-release.json",
            isActive = true,
            isWebsiteProtected = false
        ),
        ApiServerInfo(
            server = "ls",
            type = "ar",
            referer = "https://lavascans.com/",
            fileLink = "https://github.com/jamal7nainia/Hikari-Manga-Extensions/releases/download/hikarimanga_extension/lavascans-release.json",
            isActive = true,
            isWebsiteProtected = false
        ),
        ApiServerInfo(
            server = "mb",
            type = "en",
            referer = "https://www.mangabats.com/",
            fileLink = "https://github.com/jamal7nainia/Hikari-Manga-Extensions/releases/download/hikarimanga_extension/mangabat-release.json",
            isActive = true,
            isWebsiteProtected = false
        )
    )
}

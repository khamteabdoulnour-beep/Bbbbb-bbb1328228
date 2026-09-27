package com.hikari.manga.data.model

data class ApiServerInfo(
    val server: String = "",
    val type: String = "ar",
    val referer: String = "",
    val fileLink: String = "",
    val isActive: Boolean = true,
    val isWebsiteProtected: Boolean = false
)

data class AppConfigResponse(
    val success: Boolean = false,
    val data: List<ApiServerInfo> = emptyList()
)

data class ApiMangaItem(
    val id: String = "",
    val title: String = "",
    val cover: String = "",
    val status: String = "مستمر",
    val type: String = "مانجا",
    val genres: List<String> = emptyList(),
    val description: String = "",
    val rating: Float = 4.5f,
    val viewsCount: Int = 1000,
    val author: String = "غير معروف",
    val serverId: String = "",
    val mangaUrl: String = ""
)

data class ApiChapterItem(
    val id: String = "",
    val mangaId: String = "",
    val number: Float = 1f,
    val title: String = "",
    val date: String = "",
    val pagesCount: Int = 0,
    val chapterUrl: String = "",
    val serverId: String = ""
)

data class ChapterPage(
    val index: Int = 0,
    val url: String = ""
)

data class LatestChapter(
    val number: Float = 1f,
    val title: String = "",
    val timeAgo: String = ""
)

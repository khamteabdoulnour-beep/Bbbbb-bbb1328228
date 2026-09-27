package com.hikari.manga.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.manga.data.model.ApiChapterItem
import com.hikari.manga.data.model.ApiMangaItem
import com.hikari.manga.data.model.ApiServerInfo
import com.hikari.manga.data.model.ChapterPage
import com.hikari.manga.data.repository.MangaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MangaViewModel : ViewModel() {

    private val repository = MangaRepository()

    private val _servers = MutableStateFlow<List<ApiServerInfo>>(emptyList())
    val servers: StateFlow<List<ApiServerInfo>> = _servers

    private val _mangaList = MutableStateFlow<List<ApiMangaItem>>(emptyList())
    val mangaList: StateFlow<List<ApiMangaItem>> = _mangaList

    private val _isLoadingServers = MutableStateFlow(false)
    val isLoadingServers: StateFlow<Boolean> = _isLoadingServers

    private val _isLoadingManga = MutableStateFlow(false)
    val isLoadingManga: StateFlow<Boolean> = _isLoadingManga

    private val _activeServer = MutableStateFlow<ApiServerInfo?>(null)
    val activeServer: StateFlow<ApiServerInfo?> = _activeServer

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedManga = MutableStateFlow<ApiMangaItem?>(null)
    val selectedManga: StateFlow<ApiMangaItem?> = _selectedManga

    private val _chapters = MutableStateFlow<List<ApiChapterItem>>(emptyList())
    val chapters: StateFlow<List<ApiChapterItem>> = _chapters

    private val _isLoadingChapters = MutableStateFlow(false)
    val isLoadingChapters: StateFlow<Boolean> = _isLoadingChapters

    private val _readingChapter = MutableStateFlow<ApiChapterItem?>(null)
    val readingChapter: StateFlow<ApiChapterItem?> = _readingChapter

    private val _chapterPages = MutableStateFlow<List<ChapterPage>>(emptyList())
    val chapterPages: StateFlow<List<ChapterPage>> = _chapterPages

    private val _isLoadingPages = MutableStateFlow(false)
    val isLoadingPages: StateFlow<Boolean> = _isLoadingPages

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites

    val filteredMangaList: StateFlow<List<ApiMangaItem>> = combine(
        _mangaList,
        _searchQuery
    ) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            val q = query.trim().lowercase()
            list.filter {
                it.title.lowercase().contains(q) ||
                it.type.lowercase().contains(q) ||
                it.status.lowercase().contains(q) ||
                it.author.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadServers()
    }

    fun loadServers() {
        viewModelScope.launch {
            _isLoadingServers.value = true
            val result = repository.loadServers()
            _servers.value = result
            _isLoadingServers.value = false

            // Default to m3 (مانجا العاشق) or the first active server
            val defaultServer = result.firstOrNull { it.server == "m3" } ?: result.firstOrNull()
            defaultServer?.let { loadMangaListForServer(it) }
        }
    }

    fun loadMangaListForServer(server: ApiServerInfo) {
        viewModelScope.launch {
            _activeServer.value = server
            _isLoadingManga.value = true
            _error.value = null
            _mangaList.value = emptyList()

            val result = repository.loadMangaList(server)

            if (result.isEmpty()) {
                _error.value = "تعذر جلب الأعمال من (${getServerDisplayName(server.server)}). تحقق من اتصال الإنترنت أو اختر خادماً آخر"
            } else {
                _mangaList.value = result
            }
            _isLoadingManga.value = false
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectManga(manga: ApiMangaItem?) {
        _selectedManga.value = manga
        _chapters.value = emptyList()
        if (manga != null) {
            loadChapters(manga)
        }
    }

    fun reloadChapters() {
        _selectedManga.value?.let { loadChapters(it) }
    }

    private fun loadChapters(manga: ApiMangaItem) {
        viewModelScope.launch {
            _isLoadingChapters.value = true
            val chs = repository.loadChapters(manga)
            _chapters.value = chs
            _isLoadingChapters.value = false
        }
    }

    fun openReader(chapter: ApiChapterItem) {
        _readingChapter.value = chapter
        _chapterPages.value = emptyList()
        viewModelScope.launch {
            _isLoadingPages.value = true
            val pages = repository.loadChapterPages(chapter)
            _chapterPages.value = pages
            _isLoadingPages.value = false
        }
    }

    fun closeReader() {
        _readingChapter.value = null
        _chapterPages.value = emptyList()
    }

    fun toggleFavorite(mangaId: String) {
        val current = _favorites.value
        _favorites.value = if (current.contains(mangaId)) {
            current - mangaId
        } else {
            current + mangaId
        }
    }

    companion object {
        fun getServerDisplayName(code: String): String = when (code.lowercase()) {
            "m3" -> "مانجا العاشق"
            "ls" -> "لافا سكانز"
            "mb" -> "مانجا بات"
            "az" -> "أزورا مانجا"
            "tx" -> "فريق إكس"
            "dl" -> "ديلار تيوب"
            "hj" -> "هيجالا"
            "km" -> "كورا مانجا"
            "ma" -> "مانجاك"
            "mf" -> "مانجا فوكس"
            "me" -> "مانجا فري"
            "mg" -> "مانجا جي جي"
            "sp" -> "سبارك مانجا"
            "om" -> "اونما"
            else -> code.uppercase()
        }
    }
}

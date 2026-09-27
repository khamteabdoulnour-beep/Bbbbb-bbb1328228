package com.example

import com.hikari.manga.data.model.ApiMangaItem
import com.hikari.manga.data.repository.MangaRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testLoadChapters3asq() = runBlocking {
    val repo = MangaRepository()
    val manga = ApiMangaItem(
      id = "one-piece",
      title = "One Piece",
      serverId = "m3",
      mangaUrl = "https://3asq.online/manga/one-piece/"
    )
    val chapters = repo.loadChapters(manga)
    println(">>> 3ASQ CHAPTERS LOADED: ${chapters.size}")
    if (chapters.isNotEmpty()) {
      println(">>> FIRST CHAPTER: ${chapters.first().title} URL: ${chapters.first().chapterUrl}")
      val pages = repo.loadChapterPages(chapters.first())
      println(">>> CHAPTER PAGES LOADED: ${pages.size}")
      assertTrue("Pages should be extracted", pages.isNotEmpty())
    }
    assertTrue("Chapters should not be empty", chapters.isNotEmpty())
  }

  @Test
  fun testLoadChaptersLavascans() = runBlocking {
    val repo = MangaRepository()
    val manga = ApiMangaItem(
      id = "lord-of-money",
      title = "Lord of Money",
      serverId = "ls",
      mangaUrl = "https://lavascans.com/manga/lord-of-money/"
    )
    val chapters = repo.loadChapters(manga)
    println(">>> LAVASCANS CHAPTERS LOADED: ${chapters.size}")
    if (chapters.isNotEmpty()) {
      println(">>> FIRST CHAPTER: ${chapters.first().title} URL: ${chapters.first().chapterUrl}")
    }
    assertTrue("Lavascans chapters should not be empty", chapters.isNotEmpty())
  }
}

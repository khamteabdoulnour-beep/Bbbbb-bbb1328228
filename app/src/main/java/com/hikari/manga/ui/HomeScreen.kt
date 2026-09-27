package com.hikari.manga.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hikari.manga.data.model.ApiChapterItem
import com.hikari.manga.data.model.ApiMangaItem
import com.hikari.manga.data.model.ApiServerInfo
import com.hikari.manga.data.model.ChapterPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MangaViewModel = viewModel()) {
    val servers by viewModel.servers.collectAsState()
    val mangaList by viewModel.filteredMangaList.collectAsState()
    val isLoadingManga by viewModel.isLoadingManga.collectAsState()
    val isLoadingServers by viewModel.isLoadingServers.collectAsState()
    val activeServer by viewModel.activeServer.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedManga by viewModel.selectedManga.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val isLoadingChapters by viewModel.isLoadingChapters.collectAsState()
    val readingChapter by viewModel.readingChapter.collectAsState()
    val chapterPages by viewModel.chapterPages.collectAsState()
    val isLoadingPages by viewModel.isLoadingPages.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    var showSearch by remember { mutableStateOf(false) }

    // If reading chapter, show Reader Screen
    if (readingChapter != null) {
        BackHandler {
            viewModel.closeReader()
        }
        MangaReaderScreen(
            chapter = readingChapter!!,
            mangaTitle = selectedManga?.title ?: "هيكاري مانجا",
            pages = chapterPages,
            isLoading = isLoadingPages,
            onClose = { viewModel.closeReader() }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .statusBarsPadding()
    ) {
        // App Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "هيكاري مانجا",
                        color = Color(0xFFFFD700),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("⚡", fontSize = 20.sp)
                }
                Text(
                    text = if (activeServer != null)
                        "المصدر: ${MangaViewModel.getServerDisplayName(activeServer!!.server)}"
                    else "تصفح واقرأ المانجا والمانهوا بالعربية",
                    color = Color(0xFF9E9E9E),
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { showSearch = !showSearch },
                    modifier = Modifier.testTag("toggle_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "بحث",
                        tint = if (showSearch) Color(0xFFFFD700) else Color.White
                    )
                }
                IconButton(
                    onClick = { activeServer?.let { viewModel.loadMangaListForServer(it) } },
                    modifier = Modifier.testTag("refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث",
                        tint = Color(0xFFFFD700)
                    )
                }
            }
        }

        // Search Input
        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("ابحث في القائمة...", color = Color(0xFF888888)) },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح", tint = Color.Gray)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD700),
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor = Color(0xFFFFD700),
                    focusedContainerColor = Color(0xFF1A1A1A),
                    unfocusedContainerColor = Color(0xFF141414)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("search_text_field")
            )
        }

        // Servers List Row
        if (isLoadingServers) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color(0xFFFFD700),
                trackColor = Color(0xFF222222)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                items(servers) { server ->
                    ServerChip(
                        server = server,
                        isSelected = activeServer?.server == server.server,
                        onClick = { viewModel.loadMangaListForServer(server) }
                    )
                }
            }
        }

        // Content Area
        when {
            isLoadingManga -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFFFD700),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "جاري جلب المانجا...",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = MangaViewModel.getServerDisplayName(activeServer?.server ?: ""),
                            color = Color(0xFFFFD700),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("⚡", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = error ?: "",
                                color = Color.White,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { activeServer?.let { viewModel.loadMangaListForServer(it) } },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFD700),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("retry_button")
                            ) {
                                Text("إعادة المحاولة 🔄", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            mangaList.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("🔍", fontSize = 44.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "لا توجد نتائج تطابق \"$searchQuery\"" else "لا توجد أعمال متاحة حالياً",
                            color = Color.White,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { activeServer?.let { viewModel.loadMangaListForServer(it) } },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD700),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("تحديث القائمة", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(mangaList, key = { it.id }) { manga ->
                        MangaCard(
                            manga = manga,
                            isFavorite = favorites.contains(manga.id),
                            onClick = { viewModel.selectManga(manga) }
                        )
                    }
                }
            }
        }
    }

    // Manga Detail and Chapters Dialog
    selectedManga?.let { manga ->
        MangaDetailDialog(
            manga = manga,
            chapters = chapters,
            isLoadingChapters = isLoadingChapters,
            isFavorite = favorites.contains(manga.id),
            onToggleFavorite = { viewModel.toggleFavorite(manga.id) },
            onReadChapter = { chapter -> viewModel.openReader(chapter) },
            onReloadChapters = { viewModel.reloadChapters() },
            onDismiss = { viewModel.selectManga(null) }
        )
    }
}

@Composable
fun ServerChip(
    server: ApiServerInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) Color(0xFFFFD700) else Color(0xFF1E1E1E)
    val textColor = if (isSelected) Color.Black else Color.White
    val borderColor = if (isSelected) Color(0xFFFFE066) else Color(0xFF333333)
    val displayName = MangaViewModel.getServerDisplayName(server.server)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("server_chip_${server.server}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = displayName,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            val langBadge = if (server.type == "ar") "عربي" else "EN"
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) Color.Black.copy(alpha = 0.2f) else Color(0xFF333333)
            ) {
                Text(
                    text = langBadge,
                    color = textColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun MangaCard(
    manga: ApiMangaItem,
    isFavorite: Boolean = false,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .testTag("manga_card_${manga.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(manga.cover)
                        .crossfade(true)
                        .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                        .setHeader("Referer", if (manga.mangaUrl.isNotBlank()) manga.mangaUrl else "https://3asq.online/")
                        .build(),
                    contentDescription = manga.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Dark gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xD9000000)),
                                startY = 75f
                            )
                        )
                )

                // Status badge
                if (manga.status.isNotBlank()) {
                    Surface(
                        color = Color(0xCC000000),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = manga.status,
                            color = Color(0xFFFFD700),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // Favorite badge
                if (isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "مفضل",
                        tint = Color(0xFFFF4081),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(16.dp)
                    )
                }

                // Type label at bottom
                Text(
                    text = manga.type,
                    color = Color.LightGray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = manga.title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun MangaDetailDialog(
    manga: ApiMangaItem,
    chapters: List<ApiChapterItem>,
    isLoadingChapters: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onReadChapter: (ApiChapterItem) -> Unit,
    onReloadChapters: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = manga.title,
                        color = Color(0xFFFFD700),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_detail_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Manga info row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(manga.cover)
                            .crossfade(true)
                            .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                            .setHeader("Referer", if (manga.mangaUrl.isNotBlank()) manga.mangaUrl else "https://3asq.online/")
                            .build(),
                        contentDescription = manga.title,
                        modifier = Modifier
                            .width(105.dp)
                            .height(155.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DetailRow(label = "الحالة", value = manga.status)
                        DetailRow(label = "النوع", value = manga.type)
                        DetailRow(label = "المؤلف", value = manga.author)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${manga.rating} / 5",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onToggleFavorite,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFavorite) Color(0xFFE91E63) else Color(0xFF2A2A2A),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag("favorite_button")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isFavorite) "في المفضلة" else "إضافة للمفضلة",
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Description
                if (manga.description.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = manga.description,
                        color = Color(0xFFBBBBBB),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF333333))
                Spacer(Modifier.height(10.dp))

                // Chapters Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (chapters.isNotEmpty()) "الفصول المتاحة (${chapters.size})" else "قائمة الفصول",
                        color = Color(0xFFFFD700),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (chapters.isNotEmpty()) {
                        TextButton(onClick = { onReadChapter(chapters.first()) }) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("اقرأ أحدث فصل", color = Color(0xFFFFD700), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                if (isLoadingChapters) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = Color(0xFFFFD700),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("جاري استخراج قائمة الفصول...", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                } else if (chapters.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "لا تتوفر فصول حالياً لهذا العمل في هذا المصدر",
                                color = Color.Gray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = onReloadChapters,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2A2A2A),
                                    contentColor = Color(0xFFFFD700)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("إعادة المحاولة 🔄", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(chapters, key = { index, ch -> "${ch.id}_$index" }) { index, chapter ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF252528),
                                onClick = { onReadChapter(chapter) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MenuBook,
                                            contentDescription = null,
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = chapter.title,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (chapter.date.isNotBlank()) {
                                                Text(
                                                    text = chapter.date,
                                                    color = Color.Gray,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "قراءة",
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MangaReaderScreen(
    chapter: ApiChapterItem,
    mangaTitle: String,
    pages: List<ChapterPage>,
    isLoading: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val refererHeader = if (chapter.chapterUrl.isNotBlank()) {
        chapter.chapterUrl
    } else {
        "https://3asq.online/"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .statusBarsPadding()
    ) {
        // Reader Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = Color(0xFFFFD700)
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mangaTitle,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (pages.isNotEmpty()) "${chapter.title} • ${pages.size} صفحة" else chapter.title,
                    color = Color(0xFFFFD700),
                    fontSize = 11.sp
                )
            }
        }

        // Reader Content
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFFFD700))
                    Spacer(Modifier.height(12.dp))
                    Text("جاري استخراج صفحات الفصل...", color = Color.White, fontSize = 14.sp)
                }
            }
        } else if (pages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📖", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "تعذر تحميل صفحات هذا الفصل من المصدر حالياً",
                        color = Color.White,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD700),
                            contentColor = Color.Black
                        )
                    ) {
                        Text("العودة للقائمة", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(pages, key = { it.index }) { page ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(page.url)
                                .crossfade(true)
                                .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                                .setHeader("Referer", refererHeader)
                                .build(),
                            contentDescription = "صفحة ${page.index}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            contentScale = ContentScale.FillWidth
                        )
                        // Page number indicator badge
                        Surface(
                            shape = CircleShape,
                            color = Color(0x99000000),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "${page.index}",
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            color = Color(0xFF9E9E9E),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value.ifEmpty { "غير متوفر" },
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

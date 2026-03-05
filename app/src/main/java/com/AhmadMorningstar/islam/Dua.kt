package com.AhmadMorningstar.islam

import android.content.Context
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ---------------------------------------------------------------------------
// DATA MODELS
// ---------------------------------------------------------------------------

data class DuaCategory(
    val id: String,
    val title: String,
    val duaCount: Int = 0,
    val isFavoriteFolder: Boolean = false
)

data class DuaItem(
    val id: String,
    val title: String,
    val arabic: String,
    val translation: String,
    val transliteration: String,
    val virtue: String,
    val targetCount: Int,
    val sourceUrl: String,
    val audioFileName: String
)

enum class DuaScreenState { CATEGORIES, DUA_LIST, FLASHCARD }

// ---------------------------------------------------------------------------
// MAIN DUA UI
// ---------------------------------------------------------------------------

@Composable
fun DuaUI(theme: CompassTheme, lang: String) {
    val context = LocalContext.current
    val duaPrefs = remember { DuaPreferences(context) }
    val coroutineScope = rememberCoroutineScope()
    // Navigation state
    var screenState by remember { mutableStateOf(DuaScreenState.CATEGORIES) }
    var selectedCategory by remember { mutableStateOf<DuaCategory?>(null) }
    var selectedDuaIndex by remember { mutableStateOf(0) }
    var categoryDuas by remember { mutableStateOf<List<DuaItem>>(emptyList()) }

    // Categories — reloads whenever refreshTrigger increments
    var refreshTrigger by remember { mutableStateOf(0) }
    var categories by remember { mutableStateOf<List<DuaCategory>>(emptyList()) }

    // Update dialog state
    var showUpdateDialog by remember { mutableStateOf(false) }
    var categoriesToUpdate by remember { mutableStateOf<List<String>>(emptyList()) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0 to 0) }
    var downloadFailed by remember { mutableStateOf(false) }

    // Load categories on first launch and on refresh
    LaunchedEffect(refreshTrigger) {
        categories = withContext(Dispatchers.IO) {
            val rawCats = DuaContentManager.loadCategories(context, lang)
            val favCount = duaPrefs.getFavorites().size
            listOf(DuaCategory("fav", "Favorites ❤️", favCount, true)) + rawCats
        }
    }

    // Check GitHub for content updates on every app launch
    LaunchedEffect(Unit) {
        val updates = withContext(Dispatchers.IO) {
            DuaContentManager.checkForUpdates(context)
        }
        if (updates.isNotEmpty()) {
            categoriesToUpdate = updates
            showUpdateDialog = true
        }
    }

    // ---------------------------------------------------------------------------
    // DOWNLOAD DIALOG
    // ---------------------------------------------------------------------------

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDownloading) showUpdateDialog = false },
            containerColor = theme.backgroundColor,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "New Content Available",
                    color = theme.textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                when {
                    isDownloading -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Downloading ${downloadProgress.first} of ${downloadProgress.second} files...",
                                color = theme.textColor.copy(0.7f),
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (downloadProgress.second > 0)
                                        downloadProgress.first.toFloat() / downloadProgress.second
                                    else 0f
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = theme.needleAlignedColor,
                                trackColor = theme.textColor.copy(0.1f)
                            )
                        }
                    }
                    downloadFailed -> {
                        Text(
                            text = "Download failed. Please check your connection and try again.",
                            color = theme.maliciousColor.copy(0.85f),
                            fontSize = 14.sp
                        )
                    }
                    else -> {
                        Text(
                            text = "Updated Supplications & Adhkar are available, including new duas and audio files.",
                            color = theme.textColor.copy(0.65f),
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (!isDownloading) {
                    Button(
                        onClick = {
                            isDownloading = true
                            downloadFailed = false
                            downloadProgress = 0 to 0
                            coroutineScope.launch(Dispatchers.IO) {
                                val success = DuaContentManager.downloadAll(
                                    context = context,
                                    categoriesToUpdate = categoriesToUpdate,
                                    onProgress = { done, total ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            downloadProgress = done to total
                                        }
                                    }
                                )
                                withContext(Dispatchers.Main) {
                                    isDownloading = false
                                    if (success) {
                                        showUpdateDialog = false
                                        refreshTrigger++
                                    } else {
                                        downloadFailed = true
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.needleAlignedColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (downloadFailed) "Retry" else "Download",
                            color = if (theme.isDark) theme.backgroundColor else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("Later", color = theme.textColor.copy(0.45f))
                    }
                }
            }
        )
    }

    // ---------------------------------------------------------------------------
    // BACK HANDLING
    // ---------------------------------------------------------------------------

    BackHandler(enabled = screenState != DuaScreenState.CATEGORIES) {
        screenState = when (screenState) {
            DuaScreenState.FLASHCARD -> DuaScreenState.DUA_LIST
            else -> DuaScreenState.CATEGORIES
        }
    }

    // ---------------------------------------------------------------------------
    // SCREEN NAVIGATION
    // ---------------------------------------------------------------------------

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .padding(top = 40.dp, bottom = 80.dp)
    ) {
        when (screenState) {

            // ----------------------------------------------------------------
            // SCREEN 1: CATEGORY LIST
            // ----------------------------------------------------------------
            DuaScreenState.CATEGORIES -> {
                Text(
                    text = "Supplications",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.textColor,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                )
                LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                    itemsIndexed(categories) { _, category ->
                        CategoryCard(category = category, theme = theme) {
                            selectedCategory = category
                            coroutineScope.launch {
                                categoryDuas = withContext(Dispatchers.IO) {
                                    if (category.isFavoriteFolder) {
                                        DuaContentManager.loadFavorites(
                                            context, duaPrefs.getFavorites(), lang
                                        )
                                    } else {
                                        DuaContentManager.loadDuas(context, category.id, lang)
                                    }
                                }
                                screenState = DuaScreenState.DUA_LIST
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            // ----------------------------------------------------------------
            // SCREEN 2: DUA TITLE LIST
            // ----------------------------------------------------------------
            DuaScreenState.DUA_LIST -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { screenState = DuaScreenState.CATEGORIES }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = theme.textColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Text(
                        text = selectedCategory?.title ?: "",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textColor
                    )
                }

                if (categoryDuas.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No supplications found",
                            color = theme.textColor.copy(0.4f),
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                        itemsIndexed(categoryDuas) { index, dua ->
                            DuaTitleCard(dua = dua, index = index, theme = theme) {
                                selectedDuaIndex = index
                                screenState = DuaScreenState.FLASHCARD
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }

            // ----------------------------------------------------------------
            // SCREEN 3: FLASHCARD PAGER
            // ----------------------------------------------------------------
            DuaScreenState.FLASHCARD -> {
                if (categoryDuas.isEmpty()) {
                    screenState = DuaScreenState.DUA_LIST
                    return@Column
                }

                val pagerState = rememberPagerState(
                    initialPage = selectedDuaIndex,
                    pageCount = { categoryDuas.size }
                )

                val currentDua = categoryDuas[pagerState.currentPage]
                var isFav by remember(currentDua.id) {
                    mutableStateOf(duaPrefs.isFavorite(currentDua.id))
                }

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { screenState = DuaScreenState.DUA_LIST }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = theme.textColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Text(
                        text = "${pagerState.currentPage + 1} / ${categoryDuas.size}",
                        color = theme.textColor.copy(0.45f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    IconButton(onClick = {
                        duaPrefs.toggleFavorite(currentDua.id)
                        isFav = duaPrefs.isFavorite(currentDua.id)
                        refreshTrigger++ // update fav count on category card
                    }) {
                        Text(
                            text = if (isFav) "❤️" else "🤍",
                            fontSize = 22.sp
                        )
                    }
                }

                // Swipeable pager — each page is independently scrollable vertically
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    FlashcardView(
                        dua = categoryDuas[page],
                        categoryId = selectedCategory?.id ?: "",
                        theme = theme
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// CATEGORY CARD  — title on left, count badge on right
// ---------------------------------------------------------------------------

@Composable
fun CategoryCard(category: DuaCategory, theme: CompassTheme, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        color = theme.surfaceColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (category.isFavoriteFolder) theme.needleAlignedColor.copy(0.4f)
            else theme.textColor.copy(0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = category.title,
                color = theme.textColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            // Count badge with accent background
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = theme.needleAlignedColor.copy(0.15f)
            ) {
                Text(
                    text = "${category.duaCount}",
                    color = theme.needleAlignedColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// DUA TITLE CARD  — numbered list item
// ---------------------------------------------------------------------------

@Composable
fun DuaTitleCard(dua: DuaItem, index: Int, theme: CompassTheme, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = theme.textColor.copy(0.03f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, theme.textColor.copy(0.06f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Number badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = theme.needleAlignedColor.copy(0.12f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${index + 1}",
                        color = theme.needleAlignedColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = dua.title,
                color = theme.textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// FLASHCARD VIEW  — scrollable, Arabic + translation always visible
// ---------------------------------------------------------------------------

@Composable
fun FlashcardView(
    dua: DuaItem,
    categoryId: String,
    theme: CompassTheme
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // Counter state — resets whenever we land on a new dua (keyed by id)
    var count by remember(dua.id) { mutableStateOf(0) }
    val isComplete = count >= dua.targetCount

    // Audio state — all keyed to dua.id so they reset on swipe
    var mediaPlayer: MediaPlayer? by remember { mutableStateOf(null) }
    var isPlaying by remember(dua.id) { mutableStateOf(false) }
    var isAudioReady by remember(dua.id) { mutableStateOf(false) }
    var isAudioMissing by remember(dua.id) { mutableStateOf(false) }
    var audioDuration by remember(dua.id) { mutableStateOf("") }

    // ---------------------------------------------------------------------------
    // MEDIA PLAYER LIFECYCLE
    // A fresh player is created for each dua.id and fully released on dispose.
    // This prevents crashes from fast swiping or concurrent playback.
    // ---------------------------------------------------------------------------
    DisposableEffect(dua.id) {
        if (dua.audioFileName.isEmpty()) {
            return@DisposableEffect onDispose { }
        }

        val player = MediaPlayer()
        mediaPlayer = player

        val audioFile = File(
            context.filesDir,
            "Dua/content/audio/$categoryId/${dua.audioFileName}"
        )

        if (audioFile.exists()) {
            try {
                player.setDataSource(audioFile.absolutePath)
                player.prepareAsync()
                player.setOnPreparedListener { mp ->
                    isAudioReady = true
                    val secs = mp.duration / 1000
                    audioDuration = "${secs / 60}:${String.format("%02d", secs % 60)}"
                }
                player.setOnCompletionListener {
                    isPlaying = false
                }
                player.setOnErrorListener { _, _, _ ->
                    isAudioMissing = true
                    false
                }
            } catch (e: Exception) {
                isAudioMissing = true
                player.release()
                mediaPlayer = null
            }
        } else {
            // File not yet downloaded
            isAudioMissing = true
            player.release()
            mediaPlayer = null
        }

        onDispose {
            player.release()
            mediaPlayer = null
        }
    }

    // ---------------------------------------------------------------------------
    // FLASHCARD CONTENT
    // ---------------------------------------------------------------------------

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Arabic Text Box
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.surfaceColor,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, theme.textColor.copy(0.07f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = dua.title,
                    color = theme.needleAlignedColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = dua.arabic,
                    color = theme.textColor,
                    fontSize = 26.sp,
                    lineHeight = 42.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Translation — always visible, never collapsible
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.needleAlignedColor.copy(0.05f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, theme.needleAlignedColor.copy(0.12f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "TRANSLATION",
                    color = theme.needleAlignedColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Text(
                    text = dua.translation,
                    color = theme.textColor.copy(0.85f),
                    fontSize = 15.sp,
                    lineHeight = 24.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Audio + Counter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Audio Player
            if (dua.audioFileName.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 20.dp)
                ) {
                    when {
                        isAudioMissing -> {
                            Surface(
                                shape = CircleShape,
                                color = theme.textColor.copy(0.05f),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("—", color = theme.textColor.copy(0.3f), fontSize = 20.sp)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Not downloaded",
                                color = theme.textColor.copy(0.3f),
                                fontSize = 10.sp
                            )
                        }
                        !isAudioReady -> {
                            Surface(
                                shape = CircleShape,
                                color = theme.textColor.copy(0.05f),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = theme.needleAlignedColor,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                        else -> {
                            Surface(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clickable {
                                        val player = mediaPlayer ?: return@clickable
                                        if (isPlaying) {
                                            player.pause()
                                            isPlaying = false
                                        } else {
                                            player.start()
                                            isPlaying = true
                                        }
                                    },
                                shape = CircleShape,
                                color = theme.needleAlignedColor.copy(0.15f),
                                border = BorderStroke(1.dp, theme.needleAlignedColor.copy(0.3f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (isPlaying) "⏸" else "▶",
                                        color = theme.needleAlignedColor,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                            if (audioDuration.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = audioDuration,
                                    color = theme.textColor.copy(0.4f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Counter — tap to count, shows ✓ + reset when complete
            if (isComplete) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = theme.needleAlignedColor,
                        border = BorderStroke(2.dp, theme.needleAlignedColor)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "✓",
                                color = if (theme.isDark) theme.backgroundColor else Color.White,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Surface(
                        modifier = Modifier.clickable { count = 0 },
                        shape = RoundedCornerShape(10.dp),
                        color = theme.textColor.copy(0.07f),
                        border = BorderStroke(1.dp, theme.textColor.copy(0.1f))
                    ) {
                        Text(
                            text = "Reset",
                            color = theme.textColor.copy(0.55f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .clickable { count++ },
                    shape = CircleShape,
                    color = theme.surfaceColor,
                    border = BorderStroke(2.dp, theme.needleAlignedColor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$count",
                                color = theme.textColor,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "/ ${dua.targetCount}",
                                color = theme.textColor.copy(0.4f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Expandable sections
        if (dua.transliteration.isNotEmpty()) {
            DuaExpandableSection("Transliteration", dua.transliteration, theme)
        }
        if (dua.virtue.isNotEmpty()) {
            DuaExpandableSection("Virtue", dua.virtue, theme)
        }

        Spacer(Modifier.height(8.dp))

        // Verify authenticity link
        if (dua.sourceUrl.isNotEmpty()) {
            TextButton(
                onClick = {
                    try { uriHandler.openUri(dua.sourceUrl) } catch (e: Exception) { }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Verify Authenticity ↗",
                    color = theme.textColor.copy(0.4f),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ---------------------------------------------------------------------------
// EXPANDABLE SECTION
// ---------------------------------------------------------------------------

@Composable
fun DuaExpandableSection(title: String, content: String, theme: CompassTheme) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = theme.textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = if (expanded) "−" else "+",
                color = theme.needleAlignedColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light
            )
        }

        AnimatedVisibility(visible = expanded) {
            Text(
                text = content,
                color = theme.textColor.copy(0.75f),
                fontSize = 14.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            thickness = 0.5.dp,
            color = theme.textColor.copy(0.08f)
        )
    }
}

// ---------------------------------------------------------------------------
// DUA CONTENT MANAGER
// Handles version checking, downloading, and loading all dua content.
//
// File layout in app internal storage (survives reboot, deleted on uninstall):
//   filesDir/
//     Dua/
//       dua_version.json         ← local copy; compared against GitHub
//       categories.json          ← downloaded list of all categories
//       favorites.json           ← user favorites (managed by DuaPreferences)
//       content/
//         morning_adhkar.json
//         evening_adhkar.json
//         audio/
//           morning_adhkar/
//             m_1_ayat-kursi.mp3
//           evening_adhkar/
//             ...
//
// dua_version.json format on GitHub (per-category versioning):
//   { "morning_adhkar": 2, "evening_adhkar": 1 }
// ---------------------------------------------------------------------------

object DuaContentManager {

    private const val BASE_URL =
        "https://raw.githubusercontent.com/AhmadMorningstar/Islam/refs/heads/main/app/src/main/assets/Dua"

    /**
     * Compares local dua_version.json with the one on GitHub.
     * Returns the list of category IDs that are outdated or not yet downloaded.
     * Returns empty list if already up to date or if offline.
     */
    suspend fun checkForUpdates(context: Context): List<String> = withContext(Dispatchers.IO) {
        try {
            val remoteJson = downloadText("$BASE_URL/dua_version.json")
                ?: return@withContext emptyList()
            val remoteVersions = JSONObject(remoteJson)

            val localFile = File(context.filesDir, "Dua/dua_version.json")
            val localVersions = if (localFile.exists()) {
                try { JSONObject(localFile.readText()) } catch (e: Exception) { JSONObject() }
            } else {
                JSONObject()
            }

            val outdated = mutableListOf<String>()
            val keys = remoteVersions.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val remoteV = remoteVersions.getInt(key)
                val localV = localVersions.optInt(key, -1)
                if (remoteV > localV) outdated.add(key)
            }
            outdated
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Downloads all content for the given categories:
     *   1. categories.json
     *   2. Each category's JSON file
     *   3. All audio files referenced in those JSONs (skips already cached)
     *   4. Saves dua_version.json last to mark completion
     *
     * Returns true on success, false if a critical step fails.
     */
    suspend fun downloadAll(
        context: Context,
        categoriesToUpdate: List<String>,
        onProgress: (done: Int, total: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val duaDir = File(context.filesDir, "Dua")
            val contentDir = File(duaDir, "content")
            contentDir.mkdirs()

            var done = 0
            // Rough initial total — will expand once we know audio count
            var total = 1 + categoriesToUpdate.size
            onProgress(done, total)

            // Step 1: categories.json
            val catSuccess = downloadToFile(
                "$BASE_URL/categories.json",
                File(duaDir, "categories.json")
            )
            done++
            onProgress(done, total)
            if (!catSuccess) return@withContext false

            // Step 2: Each category JSON
            val downloadedCategories = mutableListOf<String>()
            for (categoryId in categoriesToUpdate) {
                val success = downloadToFile(
                    "$BASE_URL/content/$categoryId.json",
                    File(contentDir, "$categoryId.json")
                )
                if (success) downloadedCategories.add(categoryId)
                done++
                onProgress(done, total)
            }

            // Step 3: Scan JSONs for audio filenames
            val audioJobs = mutableListOf<Pair<String, File>>()
            for (categoryId in downloadedCategories) {
                val jsonFile = File(contentDir, "$categoryId.json")
                if (!jsonFile.exists()) continue
                try {
                    val array = JSONArray(jsonFile.readText())
                    val audioDir = File(contentDir, "audio/$categoryId").also { it.mkdirs() }
                    for (i in 0 until array.length()) {
                        val audioFileName = array.getJSONObject(i).optString("aud", "")
                        if (audioFileName.isNotEmpty()) {
                            val localAudio = File(audioDir, audioFileName)
                            if (!localAudio.exists()) {
                                val url = "$BASE_URL/content/audio/$categoryId/$audioFileName"
                                audioJobs.add(url to localAudio)
                            }
                        }
                    }
                } catch (e: Exception) { /* skip malformed JSON */ }
            }

            // Update total now that we know audio count (+1 for version file at the end)
            total = done + audioJobs.size + 1
            onProgress(done, total)

            // Step 4: Download audio files
            for ((url, file) in audioJobs) {
                downloadToFile(url, file)
                done++
                onProgress(done, total)
            }

            // Step 5: Save version file last — this marks the download as complete
            val remoteVersionJson = downloadText("$BASE_URL/dua_version.json")
            if (remoteVersionJson != null) {
                File(duaDir, "dua_version.json").writeText(remoteVersionJson)
            }
            done++
            onProgress(done, total)

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Loads category list. Checks internal storage first, falls back to app assets.
     * Also fetches the dua count for each category to show the badge.
     */
    fun loadCategories(context: Context, lang: String): List<DuaCategory> {
        return try {
            val file = File(context.filesDir, "Dua/categories.json")
            val json = if (file.exists()) file.readText()
            else context.assets.open("Dua/categories.json").bufferedReader().use { it.readText() }

            val array = JSONArray(json)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                DuaCategory(
                    id = id,
                    title = if (obj.has(lang)) obj.getString(lang) else obj.getString("en"),
                    duaCount = getDuaCount(context, id)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getDuaCount(context: Context, categoryId: String): Int {
        return try {
            val file = File(context.filesDir, "Dua/content/$categoryId.json")
            val json = if (file.exists()) file.readText()
            else context.assets.open("Dua/content/$categoryId.json").bufferedReader().use { it.readText() }
            JSONArray(json).length()
        } catch (e: Exception) { 0 }
    }

    /**
     * Loads all duas for a category. Internal storage first, assets as fallback.
     */
    fun loadDuas(context: Context, categoryId: String, lang: String): List<DuaItem> {
        return try {
            val file = File(context.filesDir, "Dua/content/$categoryId.json")
            val json = if (file.exists()) file.readText()
            else context.assets.open("Dua/content/$categoryId.json").bufferedReader().use { it.readText() }

            val array = JSONArray(json)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                DuaItem(
                    id = obj.getString("id"),
                    title = obj.getJSONObject("title").let {
                        if (it.has(lang)) it.getString(lang) else it.getString("en")
                    },
                    arabic = obj.getString("ar"),
                    translation = obj.getJSONObject("trans").let {
                        if (it.has(lang)) it.getString(lang) else it.getString("en")
                    },
                    transliteration = obj.getJSONObject("lat").let {
                        if (it.has(lang)) it.getString(lang) else it.getString("en")
                    },
                    virtue = obj.optJSONObject("virt")?.let {
                        if (it.has(lang)) it.optString(lang, "") else ""
                    } ?: "",
                    targetCount = obj.getInt("cnt"),
                    sourceUrl = obj.optString("url", ""),
                    audioFileName = obj.optString("aud", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Loads favorited duas. Auto-scans all categories from categories.json —
     * no hardcoded list, so adding new categories never breaks this.
     */
    fun loadFavorites(context: Context, favoriteIds: Set<String>, lang: String): List<DuaItem> {
        if (favoriteIds.isEmpty()) return emptyList()
        return try {
            val catFile = File(context.filesDir, "Dua/categories.json")
            val catJson = if (catFile.exists()) catFile.readText()
            else context.assets.open("Dua/categories.json").bufferedReader().use { it.readText() }

            val allDuas = mutableListOf<DuaItem>()
            val catArray = JSONArray(catJson)
            for (i in 0 until catArray.length()) {
                val categoryId = catArray.getJSONObject(i).getString("id")
                allDuas.addAll(loadDuas(context, categoryId, lang))
            }
            allDuas.filter { it.id in favoriteIds }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun downloadText(urlStr: String): String? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) { null }
    }

    private fun downloadToFile(urlStr: String, target: File): Boolean {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            if (conn.responseCode != 200) return false
            target.parentFile?.mkdirs()
            conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
            target.exists() && target.length() > 0
        } catch (e: Exception) { false }
    }
}
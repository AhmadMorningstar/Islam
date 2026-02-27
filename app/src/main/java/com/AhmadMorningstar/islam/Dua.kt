package com.AhmadMorningstar.islam

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import java.io.File

// --- DATA MODELS ---
data class DuaCategory(
    val id: String,
    val title: String,
    val isFavoriteFolder: Boolean = false
)

data class DuaItem(
    val id: String,
    val title: String,
    val arabic: String,
    val translation: String,
    val transliteration: String,
    val virtue: String,
    val explanation: String,
    val targetCount: Int,
    val sourceUrl: String,
    val audioUrl: String
)

// --- STATE ENUM ---
enum class DuaScreenState { CATEGORIES, DUA_LIST, FLASHCARD }
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DuaUI(theme: CompassTheme) {
    var refreshTrigger by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val prefs = remember { ThemePreferences(context) }
    val coroutineScope = rememberCoroutineScope()
    // Navigation State
    var currentScreenState by remember { mutableStateOf(DuaScreenState.CATEGORIES) }
    var selectedCategory by remember { mutableStateOf<DuaCategory?>(null) }
    var selectedDuaIndex by remember { mutableStateOf(0) }
    var categoryDuas by remember { mutableStateOf<List<DuaItem>>(emptyList()) }
    val userLang = prefs.getSavedLanguage();

    // Hardcoded categories for now (can be moved to JSON later)
    val categories = remember(userLang, refreshTrigger) {
        listOf(DuaCategory("fav", "Favorited ❤️", true)) + loadCategories(context, userLang)
    }

    var showDownloadDialog by remember {
        mutableStateOf(!File(context.filesDir, "Dua/downloaded.lock").exists())
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        DuaDownloader.downloadContent(context, categories)
                        File(context.filesDir, "Dua/downloaded.lock").createNewFile()
                        refreshTrigger++ // This forces the UI to reload the new JSON files
                        showDownloadDialog = false
                    }
                }) { Text("Download") }
            },
            title = { Text("Update Content") },
            text = { Text("Would you like to download the latest Supplications and Adhkar?") }
        )
    }

    // Back button handling
    BackHandler(enabled = currentScreenState != DuaScreenState.CATEGORIES) {
        currentScreenState = if (currentScreenState == DuaScreenState.FLASHCARD) DuaScreenState.DUA_LIST else DuaScreenState.CATEGORIES
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .padding(top = 40.dp, bottom = 80.dp) // Leave room for bottom nav
    ) {
        when (currentScreenState) {
            DuaScreenState.CATEGORIES -> {
                Text(
                    text = "Supplications",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.textColor,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                )

                LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                    items(categories) { category ->
                        CategoryCard(category, theme) {
                            selectedCategory = category
                            categoryDuas = if (category.id == "fav") {
                                // Logic: Load all JSONs and filter by Favorite IDs
                                loadAllFavorites(context, prefs.getFavorites(), userLang)
                            } else {
                                loadDuasFromJson(context, category.id, userLang)
                            }
                            currentScreenState = DuaScreenState.DUA_LIST
                        }
                    }
                }
            }

            DuaScreenState.DUA_LIST -> {
                Row(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentScreenState = DuaScreenState.CATEGORIES }) {
                        Text("X", color = theme.textColor, fontSize = 24.sp)
                    }
                    Text(
                        text = selectedCategory?.title ?: "",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textColor
                    )
                }

                LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp)) {
                    items(categoryDuas.indices.toList()) { index ->
                        DuaTitleCard(categoryDuas[index], theme) {
                            selectedDuaIndex = index
                            currentScreenState = DuaScreenState.FLASHCARD
                        }
                    }
                }
            }

            DuaScreenState.FLASHCARD -> {
                val pagerState = rememberPagerState(initialPage = selectedDuaIndex, pageCount = { categoryDuas.size })
                val currentDua = categoryDuas[pagerState.currentPage]

                // Top Bar with Favorite Toggle
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { currentScreenState = DuaScreenState.DUA_LIST }) { Text("✕", color = theme.textColor) }

                    var isFav by remember(currentDua.id) { mutableStateOf(prefs.isFavorite(currentDua.id)) }
                    IconButton(onClick = {
                        prefs.toggleFavorite(currentDua.id)
                        isFav = prefs.isFavorite(currentDua.id)
                    }) {
                        Text(if (isFav) "❤️" else "♡", color = theme.textColor, fontSize = 24.sp)
                    }
                }

                HorizontalPager(state = pagerState) { page ->
                    FlashcardView(dua = categoryDuas[page],
                        theme = theme,
                        category = selectedCategory,
                        onSourceClick = { uriHandler.openUri(categoryDuas[page].sourceUrl) }
                    )
                }
            }
        }
    }
}

// Helper to load only favorited Duas
fun loadAllFavorites(context: Context, favoriteIds: Set<String>, lang: String): List<DuaItem> {
    val allCategories = listOf("morning_adhkar", "evening_adhkar", "before_sleep", "salah")
    return allCategories.flatMap { loadDuasFromJson(context, it, lang) }
        .filter { favoriteIds.contains(it.id) }
}

@Composable
fun CategoryCard(category: DuaCategory, theme: CompassTheme, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        color = theme.surfaceColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (category.isFavoriteFolder) theme.needleAlignedColor else theme.textColor.copy(0.1f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.title,
                color = theme.textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DuaTitleCard(dua: DuaItem, theme: CompassTheme, onClick: () -> Unit) {
    val typography = LocalAppTypography.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        color = theme.textColor.copy(0.03f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = dua.title,
            modifier = Modifier.padding(16.dp),
            color = theme.textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun FlashcardView(
    dua: DuaItem,
    theme: CompassTheme,
    category: DuaCategory?,
    onSourceClick: () -> Unit
) {
    var count by remember(dua.id) { mutableStateOf(0) }
    val isComplete = count >= dua.targetCount
    val context = LocalContext.current
    val mediaPlayer = remember { android.media.MediaPlayer() }
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf("0:00") }

    // Remove the backslashes!
    val audioUrl = "https://raw.githubusercontent.com/AhmadMorningstar/Islam/main/app/src/main/assets/Dua/content/audio/${category?.id}/${dua.audioUrl}"

    LaunchedEffect(dua.id) {
        isPlaying = false
        isPrepared = false
        duration = "Loading..."
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(audioUrl)

            mediaPlayer.setOnPreparedListener { mp ->
                isPrepared = true
                val totalSeconds = mp.duration / 1000
                duration = String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60)
            }

            mediaPlayer.setOnCompletionListener {
                isPlaying = false
            }

            mediaPlayer.setOnErrorListener { _, _, _ ->
                duration = "Error"
                false
            }

            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            duration = "Error"
        }
    }

    // Stop and Release logic (Same as before)
    DisposableEffect(dua.id) {
        onDispose { if (mediaPlayer.isPlaying) mediaPlayer.stop() }
    }
    DisposableEffect(Unit) {
        onDispose { mediaPlayer.release() }
    }


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
            border = BorderStroke(1.dp, theme.textColor.copy(0.05f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = dua.title,
                    color = theme.needleAlignedColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = dua.arabic,
                    color = theme.textColor,
                    fontSize = 28.sp,
                    lineHeight = 44.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- MINI PLAYER & COUNTER ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Play Button
            Surface(
                modifier = Modifier
                    .size(60.dp)
                    .clickable {
                        try {
                            if (isPlaying) {
                                mediaPlayer.pause()
                            } else {
                                mediaPlayer.start()
                            }
                            isPlaying = !isPlaying
                        } catch (e: Exception) { /* Handle player not ready */ }
                    },
                shape = CircleShape,
                color = theme.needleAlignedColor.copy(0.15f),
                border = BorderStroke(1.dp, theme.needleAlignedColor.copy(0.3f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (isPlaying) "⏸" else "▶", color = theme.needleAlignedColor, fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Main Counter (With long-press to reset)
            Surface(
                modifier = Modifier
                    .size(90.dp)
                    .clickable(enabled = !isComplete) { count++ },
                shape = CircleShape,
                color = if (isComplete) theme.needleAlignedColor else theme.surfaceColor,
                border = BorderStroke(3.dp, theme.needleAlignedColor)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isComplete) "✓" else "$count / ${dua.targetCount}",
                        color = if (isComplete) theme.backgroundColor else theme.textColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Text(
            text = "Duration: $duration",
            color = theme.textColor.copy(0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- SECTIONS ---
        ExpandableSection("Translation", dua.translation, theme)
        ExpandableSection("Transliteration", dua.transliteration, theme)
        if (dua.virtue.isNotEmpty()) ExpandableSection("Virtue", dua.virtue, theme)
        if (dua.explanation.isNotEmpty()) ExpandableSection("Explanation", dua.explanation, theme)

        Spacer(modifier = Modifier.height(32.dp))

        // Bottom Action Buttons (Source & Audio)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = onSourceClick, colors = ButtonDefaults.buttonColors(containerColor = theme.textColor.copy(0.1f))) {
                Text("Verify Authenticity", color = theme.textColor)
            }

            // Audio Button showing Duration
            Button(
                onClick = { /* Already handled by the Play button above */ },
                colors = ButtonDefaults.buttonColors(containerColor = theme.needleAlignedColor.copy(0.1f))
            ) {
                Text("Audio ($duration)", color = theme.needleAlignedColor)
            }
        }
    }
}

@Composable
fun ExpandableSection(title: String, content: String, theme: CompassTheme) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { expanded = !expanded },
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, color = theme.textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(if (expanded) "−" else "+", color = theme.needleAlignedColor, fontSize = 20.sp)
            }

            AnimatedVisibility(visible = expanded) {
                Text(
                    text = content,
                    color = theme.textColor.copy(0.8f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Divider(color = theme.textColor.copy(0.05f), modifier = Modifier.padding(top = 16.dp))
        }
    }
}

// Updated to check Internal Storage first, then Assets
fun loadCategories(context: Context, lang: String): List<DuaCategory> {
    return try {
        val file = File(context.filesDir, "Dua/categories.json")
        val jsonString = if (file.exists()) {
            file.readText()
        } else {
            context.assets.open("Dua/categories.json").bufferedReader().use { it.readText() }
        }

        val array = JSONArray(jsonString)
        List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            DuaCategory(
                id = obj.getString("id"),
                title = if (obj.has(lang)) obj.getString(lang) else obj.getString("en")
            )
        }
    } catch (e: Exception) { emptyList() }
}

object DuaDownloader {
    // Updated to your specific raw path
    private const val BASE_URL = "https://github.com/AhmadMorningstar/Islam/raw/refs/heads/main/app/src/main/assets/Dua"

    suspend fun downloadContent(context: Context, categories: List<DuaCategory>) {
        val catFile = File(context.filesDir, "Dua/categories.json")
        catFile.parentFile?.mkdirs()
        downloadFile("$BASE_URL/categories.json", catFile)

        categories.filter { !it.isFavoriteFolder }.forEach { cat ->
            val targetFile = File(context.filesDir, "Dua/content/${cat.id}.json")
            targetFile.parentFile?.mkdirs()
            downloadFile("$BASE_URL/content/${cat.id}.json", targetFile)
        }
    }

    private fun downloadFile(url: String, target: File) {
        try {
            java.net.URL(url).openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun loadDuasFromJson(context: Context, categoryId: String, lang: String): List<DuaItem> {
    return try {
        val file = File(context.filesDir, "Dua/content/$categoryId.json")
        val jsonString = if (file.exists()) {
            file.readText()
        } else {
            // Ensure this is called correctly
            context.assets.open("Dua/content/$categoryId.json").bufferedReader().use { it.readText() }
        }

        val array = JSONArray(jsonString)
        val list = mutableListOf<DuaItem>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                DuaItem(
                    id = obj.getString("id"),
                    title = obj.getJSONObject("title").optString(lang, obj.getJSONObject("title").getString("en")),
                    arabic = obj.getString("ar"),
                    translation = obj.getJSONObject("trans").optString(lang, obj.getJSONObject("trans").getString("en")),
                    transliteration = obj.getJSONObject("lat").optString(lang, obj.getJSONObject("lat").getString("en")),
                    virtue = obj.getJSONObject("virt").optString(lang, ""),
                    explanation = "",
                    targetCount = obj.getInt("cnt"),
                    sourceUrl = obj.getString("url"),
                    audioUrl = obj.optString("aud", "") // Use optString to avoid crashes if missing
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

fun playAudio(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "audio/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback: Open in Browser if no player found
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(browserIntent)
    }
}
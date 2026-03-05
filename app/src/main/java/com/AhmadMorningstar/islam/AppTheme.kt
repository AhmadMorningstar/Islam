package com.AhmadMorningstar.islam

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import java.io.File

// ---------------------------------------------------------------------------
// THEME DATA MODEL
// ---------------------------------------------------------------------------

data class CompassTheme(
    val name: String,
    val isDark: Boolean,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val tickColor: Color,
    val northColor: Color,
    val needleDefaultColor: Color,
    val needleAlignedColor: Color,
    val textColor: Color,
    val compassIconColor: Color,
    val settingsIconColor: Color,
    val verifiedColor: Color,
    val maliciousColor: Color,
)

// ---------------------------------------------------------------------------
// THEME DEFINITIONS
// ---------------------------------------------------------------------------

object AppThemes {
    val PureLight = CompassTheme(
        "Pure Light",
        false,
        Color(0xFFF5F5F7),
        Color.Black.copy(0.05f),
        Color.DarkGray, Color.Red,
        Color(0xFF2C3E50),
        Color(0xFF27AE60),
        Color(0xFF1C1C1E),
        compassIconColor = Color(0xFFFFA000),
        settingsIconColor = Color(0xFF5D4037),
        verifiedColor = Color(0xFF27AE60),
        maliciousColor = Color(0xFFD32F2F)
    )

    val DesertGold = CompassTheme(
        "Desert Gold Light",
        false,
        Color(0xFFFFF8E1),
        Color(0xFF795548).copy(0.1f),
        Color(0xFF5D4037),
        Color.Red, Color(0xFF8D6E63),
        Color(0xFFFFA000),
        Color(0xFF3E2723),
        compassIconColor = Color(0xFFFFA000),
        settingsIconColor = Color(0xFF5D4037),
        verifiedColor = Color(0xFF388E3C),
        maliciousColor = Color(0xFFC62828)
    )

    val EmeraldNight = CompassTheme(
        "Emerald Dark", true,
        Color(0xFF06120E),
        Color.White.copy(0.03f),
        Color(0xFF81C784),
        Color.Red,
        Color(0xFF4DB6AC),
        Color(0xFF00E676),
        Color.White,
        compassIconColor = Color(0xFFFFA000),
        settingsIconColor = Color(0xFF5D4037),
        verifiedColor = Color(0xFF00E676),
        maliciousColor = Color(0xFFFF5252)
    )

    val Obsidian = CompassTheme(
        "Obsidian Dark",
        true,
        Color(0xFF0A0E12),
        Color.White.copy(0.05f),
        Color.White, Color.Red,
        Color(0xFFFF3D00),
        Color(0xFF00FF88),
        Color.White,
        compassIconColor = Color(0xFF00FF88),
        settingsIconColor = Color.White.copy(alpha = 0.7f),
        verifiedColor = Color(0xFF00FF88),
        maliciousColor = Color(0xFFFF3D00)
    )

    val OceanDeep = CompassTheme(
        name = "Ocean Deep Dark",
        isDark = true,
        backgroundColor = Color(0xFF010B13),
        surfaceColor = Color(0xFF0A1929),
        tickColor = Color(0xFF64B5F6),
        northColor = Color(0xFFFF5252),
        needleDefaultColor = Color(0xFF00B0FF),
        needleAlignedColor = Color(0xFF00E5FF),
        textColor = Color(0xFFE3F2FD),
        compassIconColor = Color(0xFF00FF88),
        settingsIconColor = Color.White.copy(alpha = 0.7f),
        verifiedColor = Color(0xFF00E5FF),
        maliciousColor = Color(0xFFFF5252)
    )

    val allThemes = listOf(PureLight, DesertGold, Obsidian, EmeraldNight, OceanDeep)
}

// ---------------------------------------------------------------------------
// THEME PERSISTENCE
// ---------------------------------------------------------------------------

class ThemePreferences(context: Context) {
    private val sharedPrefs = context.getSharedPreferences("qibla_prefs", Context.MODE_PRIVATE)

    // --- THEME ---
    fun saveTheme(themeName: String) {
        sharedPrefs.edit().putString("selected_theme", themeName).apply()
    }

    fun getSavedThemeName(): String? {
        return sharedPrefs.getString("selected_theme", "Obsidian Dark")
    }

    // --- VIBRATION MASTER TOGGLE ---
    fun saveVibrationEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("vib_enabled", enabled).apply()
    }

    fun isVibrationEnabled(): Boolean {
        return sharedPrefs.getBoolean("vib_enabled", false)
    }

    // --- VIBRATION CUSTOMIZATION ---
    fun saveVibrationSettings(strength: Int, speed: Long) {
        sharedPrefs.edit()
            .putInt("vib_strength", strength)
            .putLong("vib_speed", speed)
            .apply()
    }

    fun saveTimezone(tzId: String) {
        sharedPrefs.edit().putString("selected_timezone", tzId).apply()
    }

    fun getSavedTimezone(): String {
        return sharedPrefs.getString("selected_timezone", java.util.TimeZone.getDefault().id)
            ?: "GMT"
    }

    fun getVibStrength(): Int = sharedPrefs.getInt("vib_strength", 255)
    fun getVibSpeed(): Long = sharedPrefs.getLong("vib_speed", 50L)

    fun saveRegion(regionId: String) {
        sharedPrefs.edit().putString("selected_region", regionId).apply()
    }

    fun getSavedRegion(): String {
        return sharedPrefs.getString("selected_region", "erbil") ?: "erbil"
    }
}
// ---------------------------------------------------------------------------
// DUA PREFERENCES
// Stored separately from ThemePreferences to keep dua concerns isolated.
// Favorites are persisted as a JSON file in internal storage —
// survives reboots, gets deleted on uninstall, never loses data on crash.
// ---------------------------------------------------------------------------

class DuaPreferences(context: Context) {
    private val sharedPrefs = context.getSharedPreferences("dua_prefs", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    // --- LANGUAGE ---
    // "en" = English, "ku" = Kurdish Sorani — matches your JSON field names
    fun getSavedLanguage(): String = sharedPrefs.getString("language", "en") ?: "en"
    fun saveLanguage(lang: String) {
        sharedPrefs.edit().putString("language", lang).apply()
    }

    // --- FAVORITES (favorites.json in internal storage) ---
    private fun favoritesFile() = File(appContext.filesDir, "Dua/favorites.json")

    fun getFavorites(): Set<String> {
        val file = favoritesFile()
        if (!file.exists()) return emptySet()
        return try {
            val array = JSONArray(file.readText())
            LinkedHashSet<String>().apply {
                for (i in 0 until array.length()) add(array.getString(i))
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun saveFavorites(favorites: Set<String>) {
        val file = favoritesFile()
        file.parentFile?.mkdirs()
        file.writeText(JSONArray(favorites.toList()).toString())
    }

    fun isFavorite(id: String): Boolean = id in getFavorites()

    fun toggleFavorite(id: String) {
        val favs = getFavorites().toMutableSet()
        if (id in favs) favs.remove(id) else favs.add(id)
        saveFavorites(favs)
    }
}
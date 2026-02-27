package com.AhmadMorningstar.islam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// THE DATA

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

object AppThemes {
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
    val EmeraldNight = CompassTheme(
        "Emerald Night", true,
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
    val DesertGold = CompassTheme(
        "Desert Gold",
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

    val OceanDeep = CompassTheme(
        name = "Ocean Deep",
        isDark = true,
        backgroundColor = Color(0xFF010B13), // Very dark navy
        surfaceColor = Color(0xFF0A1929),    // Lighter navy surface
        tickColor = Color(0xFF64B5F6),       // Light blue ticks
        northColor = Color(0xFFFF5252),      // Bright coral north
        needleDefaultColor = Color(0xFF00B0FF), // Vivid blue needle
        needleAlignedColor = Color(0xFF00E5FF), // Cyan alignment
        textColor = Color(0xFFE3F2FD),        // Soft blue-white text
        compassIconColor = Color(0xFF00FF88), // Greenish
        settingsIconColor = Color.White.copy(alpha = 0.7f), // Soft white
        verifiedColor = Color(0xFF00E5FF),
        maliciousColor = Color(0xFFFF5252)
    )

    val allThemes = listOf(Obsidian, PureLight, EmeraldNight, DesertGold, OceanDeep)
}
// --- 2. TYPOGRAPHY SYSTEM ---

data class AppTypography(
    val scale: Float,
    val countdownRemaining: TextStyle,
    val navButton: TextStyle,
    val standardBody: TextStyle,
    val qiblaScreen: TextStyle
)

val LocalAppTypography = staticCompositionLocalOf<AppTypography> {
    error("No AppTypography provided")
}

@Composable
fun rememberAppTypography(currentLanguage: String): AppTypography {
    val isKurdish = currentLanguage == "ku"
    val isArabic = currentLanguage == "ar"

    return remember(currentLanguage) {
        AppTypography(
            scale = if (isKurdish || isArabic) 1.20f else 1.0f,
            countdownRemaining = TextStyle(
                fontSize = if (isKurdish) 18.sp else 10.sp,
                fontWeight = if (isKurdish) FontWeight.ExtraBold else FontWeight.Black
            ),
            navButton = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            standardBody = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            qiblaScreen = TextStyle(
                fontSize = if (isKurdish || isArabic) 17.sp else 14.sp,
                fontWeight = if (isKurdish) FontWeight.ExtraBold else FontWeight.Bold
            )
        )
    }
}

// --- 3. THEME WRAPPER ---

@Composable
fun AhmadMorningstarTheme(
    theme: CompassTheme,
    fontScale: Float,
    language: String,
    content: @Composable () -> Unit
) {
    val layoutDirection = if (language == "ar" || language == "ku") {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    val typography = rememberAppTypography(language)
    val currentDensity = LocalDensity.current
    val languageMultiplier = if (language == "ar" || language == "ku") 1.15f else 1.0f

    val customDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * fontScale * languageMultiplier
    )

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalDensity provides customDensity,
        LocalAppTypography provides typography
    ) {
        content()
    }
}
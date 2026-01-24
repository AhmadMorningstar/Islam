package com.AhmadMorningstar.islam

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.AhmadMorningstar.islam.security.SignatureVerifier
import java.util.TimeZone
@Composable
fun SettingsUI(
    theme: CompassTheme,
    onThemeSelected: (CompassTheme) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { ThemePreferences(context) }

    // State to track navigation between "MAIN" and "REGION"
    var currentSubPage by remember { mutableStateOf("MAIN") }

    if (currentSubPage == "REGION") {
        RegionSettingsUI(
            theme = theme,
            prefs = prefs,
            onBack = { currentSubPage = "MAIN" }
        )
    } else {
        SettingsMainContent(
            theme = theme,
            prefs = prefs,
            onThemeSelected = onThemeSelected,
            onOpenRegion = { currentSubPage = "REGION" }
        )
    }
}

@Composable
fun SettingsMainContent(
    theme: CompassTheme,
    prefs: ThemePreferences,
    onThemeSelected: (CompassTheme) -> Unit,
    onOpenRegion: () -> Unit
) {
    val context = LocalContext.current

    val isSignatureValid = remember { SignatureVerifier.isSignatureValid(context) }
    var isVibEnabled by remember { mutableStateOf(prefs.isVibrationEnabled()) }
    var currentStrength by remember { mutableStateOf(prefs.getVibStrength().toFloat()) }
    var currentSpeed by remember { mutableStateOf(prefs.getVibSpeed().toFloat()) }

    // Timezone Logic (Highly Optimized)
    var selectedTz by remember { mutableStateOf(prefs.getSavedTimezone()) }
    var searchQuery by remember { mutableStateOf("") }

    val allTzIds = remember { TimeZone.getAvailableIDs().toList() }
    val filteredTz = remember(searchQuery) {
        if (searchQuery.isEmpty()) emptyList()
        else allTzIds.filter { it.contains(searchQuery, ignoreCase = true) }.take(5)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 40.dp, bottom = 100.dp)
    ) {
        Text(
            text = "Settings",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = theme.textColor,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // --- TIMEZONE SECTION (FIXED & NON-LAGGY) ---
        SettingSectionHeader("Localization", theme)
        SettingsCard(theme) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRegion() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Region & Calculation", color = theme.textColor, fontWeight = FontWeight.Bold)
                    Text("Methods, Cities, and Adjustments", color = theme.textColor.copy(0.5f), fontSize = 12.sp)
                }
                Icon(painterResource(id = R.drawable.ic_verified), contentDescription = null, tint = theme.needleAlignedColor)
            }
        }
        SettingSectionHeader("Synchronization", theme)
        SettingsCard(theme) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("App Timezone", color = theme.textColor, fontWeight = FontWeight.Bold)
                Text("Active: $selectedTz", color = theme.needleAlignedColor, fontSize = 12.sp)

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search city (e.g. Asia/Baghdad)", color = theme.textColor.copy(0.4f)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textColor,
                        unfocusedTextColor = theme.textColor,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        focusedBorderColor = theme.needleAlignedColor,
                        unfocusedBorderColor = theme.textColor.copy(0.2f),
                        cursorColor = theme.needleAlignedColor
                    )
                )

                // Results appear right below the text field, no floating menu
                if (filteredTz.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    filteredTz.forEach { tz ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    selectedTz = tz
                                    searchQuery = ""
                                    prefs.saveTimezone(tz)
                                },
                            color = theme.textColor.copy(0.05f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = tz,
                                color = theme.textColor,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // --- HAPTIC FEEDBACK SECTION ---
        SettingSectionHeader("Haptic Feedback", theme)
        SettingsCard(theme) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Vibration", color = theme.textColor, fontWeight = FontWeight.Bold)
                        Text("Vibrate when facing Kaaba", color = theme.textColor.copy(0.5f), fontSize = 12.sp)
                    }
                    Switch(
                        checked = isVibEnabled,
                        onCheckedChange = {
                            isVibEnabled = it
                            prefs.saveVibrationEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.needleAlignedColor,
                            checkedTrackColor = theme.needleAlignedColor.copy(0.3f),
                            uncheckedBorderColor = theme.textColor.copy(0.3f)
                        )
                    )
                }

                if (isVibEnabled) {
                    HorizontalDivider(Modifier.padding(vertical = 16.dp), 1.dp, theme.textColor.copy(0.05f))

                    Text("Strength: ${currentStrength.toInt()}", color = theme.textColor, fontSize = 14.sp)
                    Slider(
                        value = currentStrength,
                        onValueChange = {
                            currentStrength = it
                            prefs.saveVibrationSettings(it.toInt(), currentSpeed.toLong())
                        },
                        valueRange = 50f..255f,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.needleAlignedColor,
                            activeTrackColor = theme.needleAlignedColor,
                            inactiveTrackColor = theme.textColor.copy(0.1f)
                        )
                    )

                    Text("Pulse Speed: ${currentSpeed.toInt()}ms", color = theme.textColor, fontSize = 14.sp)
                    Slider(
                        value = currentSpeed,
                        onValueChange = {
                            currentSpeed = it
                            prefs.saveVibrationSettings(currentStrength.toInt(), it.toLong())
                        },
                        valueRange = 20f..200f,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.needleAlignedColor,
                            activeTrackColor = theme.needleAlignedColor,
                            inactiveTrackColor = theme.textColor.copy(0.1f)
                        )
                    )

                    Button(
                        onClick = { testVibration(context, currentStrength.toInt(), currentSpeed.toLong()) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.needleAlignedColor.copy(0.1f))
                    ) {
                        Text("Test Haptics", color = theme.needleAlignedColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // --- APPEARANCE SECTION ---
        SettingSectionHeader("App Appearance", theme)
        AppThemes.allThemes.forEach { item ->
            ThemeOptionRow(
                targetTheme = item,
                isSelected = item.name == theme.name,
                currentTheme = theme,
                onClick = { onThemeSelected(item) }
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(32.dp))

        SettingSectionHeader("Security", theme)

        SettingsCard(theme) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Check Signature",
                        color = theme.textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Ensures the app is official",
                        color = theme.textColor.copy(0.5f),
                        fontSize = 12.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isSignatureValid) R.drawable.ic_verified else R.drawable.ic_malicious
                        ),
                        contentDescription = if (isSignatureValid) "Verified" else "Malicious",
                        tint = if (isSignatureValid) theme.verifiedColor else theme.maliciousColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isSignatureValid) "Verified" else "Malicious",
                        color = if (isSignatureValid) theme.verifiedColor else theme.maliciousColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

    }
}

val worldData = mapOf(
    "United Kingdom" to listOf("London", "Manchester", "Birmingham", "Glasgow"),
    "USA" to listOf("New York", "Los Angeles", "Chicago", "Houston", "Washington DC"),
    "Saudi Arabia" to listOf("Makkah", "Medina", "Riyadh", "Jeddah"),
    "Turkey" to listOf("Istanbul", "Ankara", "Izmir", "Bursa"),
    "UAE" to listOf("Dubai", "Abu Dhabi", "Sharjah")
)


@Composable
fun RegionSettingsUI(
    theme: CompassTheme,
    onBack: () -> Unit,
    prefs: ThemePreferences
) {
    // Local state for UI responsiveness
    var autoRegion by remember { mutableStateOf(prefs.isAutoRegionEnabled()) }
    var calcMethod by remember { mutableStateOf(prefs.getCalculationMethod()) }
    var asrMethod by remember { mutableStateOf(prefs.getAsrMethod()) }
    var highLat by remember { mutableStateOf(prefs.getHighLatMethod()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Back Button & Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(id = R.drawable.ic_malicious), // Reusing your icon as requested
                    contentDescription = "Back",
                    tint = theme.textColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Region & Calculation", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = theme.textColor)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 1. Automatic Toggle
        SettingsCard(theme) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Automatic Selection", color = theme.textColor, fontWeight = FontWeight.Bold)
                    Text("Use GPS and Internet for location", color = theme.textColor.copy(0.5f), fontSize = 12.sp)
                }
                Switch(
                    checked = autoRegion,
                    onCheckedChange = {
                        autoRegion = it
                        prefs.setAutoRegion(it)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = theme.needleAlignedColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Manual Sections - Disabled if Auto is ON
        val manualAlpha = if (autoRegion) 0.4f else 1f

        Box(modifier = Modifier.alpha(manualAlpha)) {
            Column {
                SettingSectionHeader("Manual Selection", theme)

                ExpandableSelectionCard(
                    title = "Iraq Regions",
                    selectedItem = prefs.getCityName(),
                    options = listOf("Erbil", "Sulaymaniyah", "Kirkuk", "Baghdad", "Basra"),
                    theme = theme,
                    enabled = !autoRegion,
                    onItemSelected = { city ->
                        val coords = when(city) {
                            "Sulaymaniyah" -> Pair(35.56, 45.42)
                            "Kirkuk" -> Pair(35.46, 44.39)
                            "Baghdad" -> Pair(33.31, 44.36)
                            "Basra" -> Pair(30.50, 47.78)
                            else -> Pair(36.19, 44.01) // Erbil default
                        }
                        prefs.saveLocation(coords.first, coords.second, city)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExpandableSelectionCard(
                    title = "World Regions",
                    selectedItem = "Search Country...",
                    options = listOf("United Kingdom", "USA", "Saudi Arabia", "Turkey", "UAE", "Malaysia"),
                    theme = theme,
                    enabled = !autoRegion,
                    onItemSelected = { /* Implement Country Picker Logic */ }
                )
            }
            // Transparent overlay to prevent clicks when auto is on
            if (autoRegion) {
                Box(modifier = Modifier.matchParentSize().clickable(enabled = false) {})
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Calculation Settings (Always accessible)
        SettingSectionHeader("Calculation Standards", theme)

        ExpandableSelectionCard(
            title = "Calculation Method",
            selectedItem = calcMethod,
            options = listOf("MAKKAH", "MWL", "ISNA", "KARACHI", "EGYPTIAN", "JAFRI", "TEHRAN"),
            theme = theme,
            onItemSelected = {
                calcMethod = it
                prefs.saveCalculationMethod(it)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ExpandableSelectionCard(
            title = "Asr Method",
            selectedItem = asrMethod,
            options = listOf("SHAFI", "HANAFI"),
            theme = theme,
            onItemSelected = {
                asrMethod = it
                prefs.saveAsrMethod(it)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ExpandableSelectionCard(
            title = "Higher Latitude",
            selectedItem = highLat,
            options = listOf("NONE", "MID_NIGHT", "ONE_SEVEN", "TWILIGHT"),
            theme = theme,
            onItemSelected = {
                highLat = it
                prefs.saveHighLatMethod(it)
            }
        )

        Spacer(modifier = Modifier.height(120.dp)) // Extra scroll space
    }
}

@Composable
fun ExpandableSelectionCard(
    title: String,
    selectedItem: String,
    options: List<String>,
    theme: CompassTheme,
    enabled: Boolean = true,
    onItemSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = theme.textColor.copy(0.03f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, theme.textColor.copy(0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, color = theme.textColor, fontSize = 14.sp)
                    Text(selectedItem, color = theme.needleAlignedColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Icon(
                    painter = painterResource(id = if (expanded) R.drawable.ic_verified else R.drawable.ic_prayer_times),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).alpha(0.5f),
                    tint = theme.textColor
                )
            }

            if (expanded && enabled) {
                HorizontalDivider(color = theme.textColor.copy(0.05f))
                options.forEach { option ->
                    Text(
                        text = option,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onItemSelected(option)
                                expanded = false
                            }
                            .padding(16.dp),
                        color = if (option == selectedItem) theme.needleAlignedColor else theme.textColor,
                        fontWeight = if (option == selectedItem) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun SelectionRow(label: String, current: String, theme: CompassTheme) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = theme.textColor)
        Text(current, color = theme.needleAlignedColor, fontWeight = FontWeight.Bold)
    }
}

// Ensure these supporting composables remain in your file
@Composable
fun ThemeOptionRow(targetTheme: CompassTheme, isSelected: Boolean, currentTheme: CompassTheme, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = if (isSelected) targetTheme.surfaceColor else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(2.dp, targetTheme.needleAlignedColor) else BorderStroke(1.dp, currentTheme.textColor.copy(0.1f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(28.dp).background(targetTheme.needleAlignedColor, RoundedCornerShape(14.dp)))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(targetTheme.name, color = if (isSelected) targetTheme.textColor else currentTheme.textColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp)
                Text(if (targetTheme.isDark) "Dark Mode" else "Light Mode", color = (if (isSelected) targetTheme.textColor else currentTheme.textColor).copy(0.5f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SettingsCard(theme: CompassTheme, content: @Composable () -> Unit) {
    Surface(color = theme.textColor.copy(0.03f), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, theme.textColor.copy(0.08f)), modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
fun SettingSectionHeader(title: String, theme: CompassTheme) {
    Text(text = title.uppercase(), color = theme.needleAlignedColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
}

private fun testVibration(context: Context, strength: Int, speed: Long) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // API 26+ logic
        val effect = VibrationEffect.createWaveform(
            longArrayOf(0, speed, speed, speed),
            intArrayOf(0, strength, 0, strength),
            -1
        )
        vibrator.vibrate(effect)
    } else {
        // API 23-25 logic: Standard vibration pattern (strength cannot be controlled on older APIs)
        @Suppress("DEPRECATION")
        vibrator.vibrate(longArrayOf(0, speed, speed, speed), -1)
    }
}
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
    onThemeSelected: (CompassTheme) -> Unit,
    currentRegion: String,
    onRegionSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val regions = listOf(
        "akre", "duhok", "erbil", "halabja", "jalawla", "khanaqin",
        "kirkuk", "qara_hanjir", "shekhan", "sulaymaniyah", "taqtaq",
        "tuz_khurma", "zakho"
    )
    var showRegionDialog by remember { mutableStateOf(false) }

    val isSignatureValid = remember {
        SignatureVerifier.isSignatureValid(context)
    }

    val prefs = remember { ThemePreferences(context) }

    var isVibEnabled by remember { mutableStateOf(prefs.isVibrationEnabled()) }
    var currentStrength by remember { mutableStateOf(prefs.getVibStrength().toFloat()) }
    var currentSpeed by remember { mutableStateOf(prefs.getVibSpeed().toFloat()) }

    // Timezone Logic (Highly Optimized)
    var selectedTz by remember { mutableStateOf(prefs.getSavedTimezone()) }
    var searchQuery by remember { mutableStateOf("") }

    val allTzIds = remember { TimeZone.getAvailableIDs().toList() }
    val filteredTz = remember(searchQuery) {
        if (searchQuery.isEmpty()) emptyList() // Don't show anything if not searching
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


        // --- REGION SECTION ---
        SettingSectionHeader("Location", theme)
        SettingsCard(theme) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRegionDialog = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Prayer Region", color = theme.textColor, fontWeight = FontWeight.Bold)
                    Text(
                        text = currentRegion.replace("_", " ").uppercase(),
                        color = theme.needleAlignedColor,
                        fontSize = 12.sp
                    )
                }
                Text("Change", color = theme.needleAlignedColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
    // Region Selection Dialog
    // Region Selection Dialog inside SettingsUI
    if (showRegionDialog) {
        AlertDialog(
            onDismissRequest = { showRegionDialog = false },
            title = {
                Text(
                    "Select Region",
                    color = theme.textColor,
                    fontWeight = FontWeight.Bold
                )
            },
            containerColor = theme.backgroundColor,
            // The Fix: Provide a TextButton instead of an empty lambda {}
            confirmButton = {
                TextButton(onClick = { showRegionDialog = false }) {
                    Text("CANCEL", color = theme.needleAlignedColor, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                // Box with fixed height prevents the dialog from jumping
                // and manages the scroll area better
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        regions.forEach { regionId ->
                            val displayName = regionId.replace("_", " ").uppercase()
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onRegionSelected(regionId)
                                        showRegionDialog = false
                                    }
                                    .padding(vertical = 2.dp),
                                color = if (regionId == currentRegion) theme.needleAlignedColor.copy(0.1f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = displayName,
                                    color = if (regionId == currentRegion) theme.needleAlignedColor else theme.textColor,
                                    modifier = Modifier.padding(16.dp),
                                    fontWeight = if (regionId == currentRegion) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        )
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
package com.AhmadMorningstar.islam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.HighLatitudeRule
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.text.SimpleDateFormat
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.*

// Data model to hold the times
data class PrayerTimeData(
    val name: String,
    val time: String,
    val icon: Int,
    val isNext: Boolean = false,
    val targetMillis: Long = 0L,
    val isMuted: Boolean = false
)

@Composable
fun PrayerTimesUI(theme: CompassTheme) {
    val context = LocalContext.current
    val prefs = remember { ThemePreferences(context) }

    // --- CRITICAL: INSTANT UPDATE LOGIC ---
    // We read the values from prefs. If these change in Settings.kt,
    // the UI will see the change and re-calculate because they are keys in 'remember'
    val calendar = Calendar.getInstance()
    val lat by remember { mutableStateOf(prefs.getLat()) }
    val lng by remember { mutableStateOf(prefs.getLng()) }
    val calcMethod = prefs.getCalculationMethod()
    val asrMethod = prefs.getAsrMethod()
    val highLat = prefs.getHighLatMethod()
    val city = prefs.getCityName()

    // This block re-runs INSTANTLY when any key (lat, lng, method) changes
    val prayerTimes = remember(calendar.get(Calendar.MINUTE), lat, lng, calcMethod, asrMethod, highLat) {
        val coordinates = Coordinates(lat, lng)
        calculateRealPrayerTimes(coordinates, calendar, prefs)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 40.dp, bottom = 120.dp)
    ) {
        // Header Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "Prayer Times",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.textColor
                )
                Text(
                    text = "$city, ${if (prefs.getCountry() == "IQ") "Iraq" else "Global"}",
                    fontSize = 14.sp,
                    color = theme.needleAlignedColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date()),
                color = theme.textColor.copy(0.5f),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        val nextPrayer = prayerTimes.find { it.isNext } ?: prayerTimes.first()
        NextPrayerCard(theme, nextPrayer)

        Spacer(modifier = Modifier.height(32.dp))

        SettingSectionHeader("Today's Schedule", theme)

        Surface(
            color = theme.textColor.copy(0.03f),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.textColor.copy(0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                prayerTimes.forEachIndexed { index, prayer ->
                    PrayerRow(theme, prayer)
                    if (index < prayerTimes.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            thickness = 1.dp,
                            color = theme.textColor.copy(0.05f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatCountdown(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

private fun calculateRealPrayerTimes(
    coordinates: Coordinates,
    calendar: Calendar,
    prefs: ThemePreferences
): List<PrayerTimeData> {

    // 1. SELECT THE ACTUAL METHOD (No longer placeholders)
    val method = prefs.getCalculationMethod()
    val params: CalculationParameters = when (method) {
        "MAKKAH" -> CalculationMethod.UMM_AL_QURA.parameters
        "MWL" -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
        "ISNA" -> CalculationMethod.NORTH_AMERICA.parameters
        "KARACHI" -> CalculationMethod.KARACHI.parameters
        "EGYPTIAN" -> CalculationMethod.EGYPTIAN.parameters
        "TEHRAN" -> CalculationParameters(17.7, 14.0).apply {
            methodAdjustments.maghrib = 4
        }
        "JAFRI" -> CalculationParameters(16.0, 14.0).apply {
            methodAdjustments.maghrib = 4
        }
        else -> CalculationMethod.EGYPTIAN.parameters
    }

    // 2. SET ASR METHOD (MADHAB)
    params.madhab = if (prefs.getAsrMethod() == "HANAFI") Madhab.HANAFI else Madhab.SHAFI

    // 3. SET HIGH LATITUDE RULE
    params.highLatitudeRule = when(prefs.getHighLatMethod()) {
        "MID_NIGHT" -> HighLatitudeRule.MIDDLE_OF_THE_NIGHT
        "ONE_SEVEN" -> HighLatitudeRule.SEVENTH_OF_THE_NIGHT
        "ANGLE_BASED" -> HighLatitudeRule.TWILIGHT_ANGLE
        else -> HighLatitudeRule.TWILIGHT_ANGLE
    }

    // 4. IRAQ SPECIFIC ACCURACY ADJUSTMENTS
    // If the user is in Iraq, we apply these standard local offsets
    if (prefs.getCountry() == "IQ") {
        params.methodAdjustments.fajr = 18
        params.methodAdjustments.sunrise = 5
        params.methodAdjustments.dhuhr = 9
        params.methodAdjustments.asr = 1
        params.methodAdjustments.maghrib = 6
        params.methodAdjustments.isha = 0
    }

    val date = DateComponents.from(calendar.time)
    val adhanTimes = PrayerTimes(coordinates, date, params)

    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    val next = adhanTimes.nextPrayer()

    return listOf(
        PrayerTimeData("Fajr", timeFormatter.format(adhanTimes.fajr), R.drawable.ic_prayer_times, next == com.batoulapps.adhan.Prayer.FAJR, adhanTimes.fajr.time),
        PrayerTimeData("Sunrise", timeFormatter.format(adhanTimes.sunrise), R.drawable.ic_prayer_times, next == com.batoulapps.adhan.Prayer.SUNRISE, adhanTimes.sunrise.time),
        PrayerTimeData("Dhuhr", timeFormatter.format(adhanTimes.dhuhr), R.drawable.ic_prayer_times, next == com.batoulapps.adhan.Prayer.DHUHR, adhanTimes.dhuhr.time),
        PrayerTimeData("Asr", timeFormatter.format(adhanTimes.asr), R.drawable.ic_prayer_times, next == com.batoulapps.adhan.Prayer.ASR, adhanTimes.asr.time),
        PrayerTimeData("Maghrib", timeFormatter.format(adhanTimes.maghrib), R.drawable.ic_prayer_times, next == com.batoulapps.adhan.Prayer.MAGHRIB, adhanTimes.maghrib.time),
        PrayerTimeData("Isha", timeFormatter.format(adhanTimes.isha), R.drawable.ic_prayer_times, next == com.batoulapps.adhan.Prayer.ISHA, adhanTimes.isha.time)
    )
}

// Keeping your UI components below...
@Composable
fun NextPrayerCard(theme: CompassTheme, prayer: PrayerTimeData) {
    var countdownText by remember(prayer.targetMillis) { mutableStateOf("") }

    LaunchedEffect(prayer.targetMillis) {
        while (true) {
            val remaining = prayer.targetMillis - System.currentTimeMillis()
            if (remaining > 0) {
                countdownText = formatCountdown(remaining)
            } else {
                countdownText = "00:00:00"
            }
            kotlinx.coroutines.delay(1000) // Update every second
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(165.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(theme.needleAlignedColor.copy(0.2f), Color.Transparent)
                ),
                shape = RoundedCornerShape(28.dp)
            )
            .background(theme.textColor.copy(0.05f), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(theme.needleAlignedColor.copy(0.1f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = prayer.icon),
                    contentDescription = null,
                    tint = theme.needleAlignedColor,
                    modifier = Modifier.size(75.dp)
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(
                    "UP NEXT",
                    color = theme.needleAlignedColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    prayer.name,
                    color = theme.textColor,
                    fontSize = 35.sp,
                    fontWeight = FontWeight.Bold
                )


                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Small dot indicator
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(theme.needleAlignedColor, androidx.compose.foundation.shape.CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "After ",
                        color = theme.textColor.copy(0.7f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = countdownText,
                        color = theme.needleAlignedColor,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace // Keeps it from wobbling
                    )
                }
            }

        }
    }
}

@Composable
fun PrayerRow(theme: CompassTheme, prayer: PrayerTimeData) {
    val contentAlpha = if (prayer.isNext) 1f else 0.6f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = prayer.icon),
                contentDescription = null,
                tint = if (prayer.isNext) theme.needleAlignedColor else theme.textColor.copy(contentAlpha),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = prayer.name,
                color = theme.textColor.copy(contentAlpha),
                fontSize = 25.sp,
                fontWeight = if (prayer.isNext) FontWeight.Bold else FontWeight.Medium
            )
        }
        Text(
            text = prayer.time,
            color = if (prayer.isNext) theme.needleAlignedColor else theme.textColor.copy(contentAlpha),
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
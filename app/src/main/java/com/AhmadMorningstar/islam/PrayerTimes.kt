package com.AhmadMorningstar.islam

import androidx.compose.animation.animateColorAsState
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
import org.json.JSONArray
import org.json.JSONObject
import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.res.stringResource
import java.util.*

// --- DATA MODEL Internal use
data class PrayerTimeItem(val name: String, val time: String, val icon: Int)

@Composable
fun PrayerTimesUI(theme: CompassTheme, region: String) {
    val context = LocalContext.current
    val prayerNames = listOf("Fajr",
        "Sunrise",
        "Dhuhr",
        "Asr",
        "Maghrib",
        "Isha"
    )
    val prayerIcons = listOf(
        R.drawable.ic_prayer_times, R.drawable.ic_prayer_times,
        R.drawable.ic_prayer_times, R.drawable.ic_prayer_times,
        R.drawable.ic_prayer_times, R.drawable.ic_prayer_times
    )

    var allDaysJson by remember { mutableStateOf<JSONArray?>(null) }
    var todayPrayers by remember { mutableStateOf<List<PrayerTimeItem>>(emptyList()) }
    var nextPrayerName by remember { mutableStateOf("Loading...") }
    var nextPrayerIndex by remember { mutableStateOf(-1) }
    var countdownText by remember { mutableStateOf("00:00:00") }

    // This reloads the file whenever the 'region' changes in Settings
    LaunchedEffect(region) {
        try {
            val jsonString = context.assets.open("Prayer_Times/$region.json")
                .bufferedReader().use { it.readText() }
            allDaysJson = JSONArray(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(allDaysJson) {
        while (true) {
            val data = allDaysJson
            if (data == null) {
                kotlinx.coroutines.delay(1000)
                continue
            }

            val now = Calendar.getInstance()
            val currentDay = now.get(Calendar.DAY_OF_MONTH)
            val currentMonth = now.get(Calendar.MONTH) + 1

            // Part A: Update Schedule
            val todayObj = findEntryForDate(data, currentDay, currentMonth)
            todayObj?.let {
                val timesArray = it.getJSONArray("time")
                val items = mutableListOf<PrayerTimeItem>()
                for (j in 0 until timesArray.length()) {
                    items.add(PrayerTimeItem(prayerNames[j], timesArray.getString(j), prayerIcons[j]))
                }
                todayPrayers = items
            }

            // Part B: Find Next Prayer
            var targetTime: Calendar? = null
            var foundName = ""
            var foundIdx = -1

            todayPrayers.forEachIndexed { index, prayer ->
                val pTime = prayer.time.split(":")
                val pCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, pTime[0].toInt())
                    set(Calendar.MINUTE, pTime[1].toInt())
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (pCal.after(now) && targetTime == null) {
                    targetTime = pCal
                    foundName = prayer.name
                    foundIdx = index
                }
            }

            // Midnight Logic (Tomorrow's Fajr)
            if (targetTime == null) {
                val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                val tomorrowObj = findEntryForDate(data, tomorrow.get(Calendar.DAY_OF_MONTH), tomorrow.get(Calendar.MONTH) + 1)
                tomorrowObj?.let {
                    val timesArray = it.getJSONArray("time")
                    val fajrTime = timesArray.getString(0).split(":")
                    targetTime = Calendar.getInstance().apply {
                        time = tomorrow.time
                        set(Calendar.HOUR_OF_DAY, fajrTime[0].toInt())
                        set(Calendar.MINUTE, fajrTime[1].toInt())
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    foundName = "Fajr (Tomorrow)"
                    foundIdx = -1
                }
            }

            // Part C: Update UI
            targetTime?.let {
                val diff = it.timeInMillis - now.timeInMillis
                countdownText = formatMillis(diff)
                nextPrayerName = foundName
                nextPrayerIndex = foundIdx
            }

            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 60.dp, bottom = 120.dp)
    ) {
        CountdownCard(nextName = nextPrayerName, countdown = countdownText, theme = theme)
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(id = R.string.todays_schedule_label),
            color = theme.needleAlignedColor,
            style = LocalAppTypography.current.qiblaScreen.copy(
                letterSpacing = 2.sp
            ),
            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
        )

        Surface(
            color = theme.textColor.copy(0.03f),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.textColor.copy(0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                todayPrayers.forEachIndexed { index, prayer ->
                    PrayerRow(
                        prayer = prayer,
                        isNext = index == nextPrayerIndex,
                        theme = theme,
                        isLast = index == todayPrayers.size - 1
                    )
                }
            }
        }
    }
}

@Composable
fun CountdownCard(nextName: String, countdown: String, theme: CompassTheme) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = theme.surfaceColor,
        shape = RoundedCornerShape(32.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.textColor.copy(0.1f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(theme.needleAlignedColor.copy(0.15f), Color.Transparent)
                    )
                )
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.up_next_label, nextName),
                color = theme.textColor.copy(0.6f),
                style = LocalAppTypography.current.qiblaScreen.copy(
                    letterSpacing = 1.5.sp
                )
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = countdown,
                    color = theme.textColor,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp
                )
            }

            Text(
                text = stringResource(id = R.string.remaining_label),
                color = theme.needleAlignedColor,
                style = LocalAppTypography.current.qiblaScreen

            )
        }
    }
}

@Composable
fun PrayerRow(
    prayer: PrayerTimeItem,
    isNext: Boolean,
    theme: CompassTheme,
    isLast: Boolean
) {
    val rowBg by animateColorAsState(
        if (isNext) theme.needleAlignedColor.copy(0.1f) else Color.Transparent, label = ""
    )

    // NEW: Get the context and format the time for display
    val context = LocalContext.current
    val displayTime = remember(prayer.time) {
        formatTimeForDevice(context, prayer.time)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = prayer.icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isNext) theme.needleAlignedColor else theme.textColor.copy(0.5f)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = prayer.name,
                color = if (isNext) theme.textColor else theme.textColor.copy(0.8f),
                fontSize = 18.sp,
                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium
            )
        }

        Text(
            text = displayTime,
            color = if (isNext) theme.needleAlignedColor else theme.textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }

    if (!isLast) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            thickness = 0.5.dp,
            color = theme.textColor.copy(0.05f)
        )
    }
}

// Helper to find the specific JSON object for a given day/month
private fun findEntryForDate(data: JSONArray, day: Int, month: Int): JSONObject? {
    for (i in 0 until data.length()) {
        val obj = data.getJSONObject(i)
        if (obj.getInt("day") == day && obj.getInt("month") == month) return obj
    }
    return null
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

private fun formatTimeForDevice(context: Context, rawTime: String): String {
    val is24Hour = DateFormat.is24HourFormat(context)
    if (is24Hour) return rawTime // JSON is already 24h, so return as-is

    return try {
        // Parse the 24h string and format it to 12h with AM/PM
        val sdf24 = SimpleDateFormat("H:mm", Locale.getDefault())
        val sdf12 = SimpleDateFormat("h:mm a", Locale.getDefault())
        val date = sdf24.parse(rawTime)
        if (date != null) sdf12.format(date) else rawTime
    } catch (e: Exception) {
        rawTime
    }
}
package com.AhmadMorningstar.islam

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

// ---------------------------------------------------------------------------
// CONSTANTS — process-lifetime singletons, allocated exactly once, never on UI
// ---------------------------------------------------------------------------

private val PRAYER_NAMES_EN = arrayOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
private val PRAYER_NAMES_KU = arrayOf(
    "بەیانی",
    "خۆرهەڵاتن",
    "نیوەڕۆ",
    "عەسر",
    "ئێوارە",
    "ئیشا",
)
private const val TOMORROW_FAJR_EN = "Fajr (Tomorrow)"
private const val TOMORROW_FAJR_KU = "فەجر (سبەی)"
// ---------------------------------------------------------------------------
// DATA MODELS
// ---------------------------------------------------------------------------

data class PrayerTimeItem(val name: String, val time: String, val icon: Int)

private class DaySchedule(
    val prayers: List<PrayerTimeItem>,
    val prayerEpochMs: LongArray,
    val tomorrowFajrMs: Long,
    val tomorrowFajrLabel: String,
)

// ---------------------------------------------------------------------------
// HELPERS — pick the right array / label for the active language
// ---------------------------------------------------------------------------

private fun namesFor(language: String): Array<String> =
    if (language == "ku") PRAYER_NAMES_KU else PRAYER_NAMES_EN

private fun tomorrowLabelFor(language: String): String =
    if (language == "ku") TOMORROW_FAJR_KU else TOMORROW_FAJR_EN

// ---------------------------------------------------------------------------
// MAIN COMPOSABLE
// ---------------------------------------------------------------------------

@Composable
fun PrayerTimesUI(theme: CompassTheme, region: String, language: String = "en") {
    val context = LocalContext.current

    val is24h = remember { DateFormat.is24HourFormat(context) }

    var schedule        by remember { mutableStateOf<DaySchedule?>(null) }
    var nextPrayerName  by remember { mutableStateOf("...") }
    var nextPrayerIndex by remember { mutableStateOf(-1) }
    var countdownText   by remember { mutableStateOf("--:--:--") }

    LaunchedEffect(region, language) {
        schedule = null
        val names         = namesFor(language)
        val tomorrowLabel = tomorrowLabelFor(language)
        schedule = withContext(Dispatchers.IO) {
            buildDaySchedule(context, region, names, tomorrowLabel)
        }
    }

    // ── TICK LOOP ────────────────────────────────────────────────────────────
    // Keyed on schedule: cancels & restarts the moment new data arrives.
    // Hot path: only Long subtraction + formatMillis. Zero alloc per tick.
    LaunchedEffect(schedule) {
        val s = schedule ?: return@LaunchedEffect

        while (true) {
            val now = System.currentTimeMillis()

            var foundIdx = -1
            var foundMs  = 0L
            for (i in s.prayerEpochMs.indices) {
                if (s.prayerEpochMs[i] > now) {
                    foundIdx = i
                    foundMs  = s.prayerEpochMs[i]
                    break
                }
            }

            if (foundIdx == -1) {
                nextPrayerName  = s.tomorrowFajrLabel
                nextPrayerIndex = -1
                val raw = formatMillis((s.tomorrowFajrMs - now).coerceAtLeast(0))
                countdownText   = if (language == "ku") toArabicNumerals(raw) else raw
            } else {
                nextPrayerName  = s.prayers[foundIdx].name
                nextPrayerIndex = foundIdx
                val raw = formatMillis((foundMs - now).coerceAtLeast(0))
                countdownText   = if (language == "ku") toArabicNumerals(raw) else raw
            }

            kotlinx.coroutines.delay(1000)
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 60.dp, bottom = 120.dp),
    ) {
        CountdownCard(nextName = nextPrayerName, countdown = countdownText, theme = theme)

        Spacer(Modifier.height(32.dp))

        Text(
            text          = stringResource(id = R.string.todays_schedule_label),
            color         = theme.needleAlignedColor,
            fontSize      = 12.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier      = Modifier.padding(start = 8.dp, bottom = 16.dp),
        )

        Surface(
            color    = theme.textColor.copy(0.03f),
            shape    = RoundedCornerShape(24.dp),
            border   = BorderStroke(1.dp, theme.textColor.copy(0.08f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                val prayers = schedule?.prayers ?: emptyList()
                val lastIdx = prayers.lastIndex
                prayers.forEachIndexed { index, prayer ->
                    PrayerRow(
                        prayer   = prayer,
                        isNext   = index == nextPrayerIndex,
                        theme    = theme,
                        isLast   = index == lastIdx,
                        is24h    = is24h,
                        language = language,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// CHILD COMPOSABLES
// ---------------------------------------------------------------------------

@Composable
fun CountdownCard(nextName: String, countdown: String, theme: CompassTheme) {

    val gradient = remember(theme.needleAlignedColor) {
        Brush.verticalGradient(listOf(theme.needleAlignedColor.copy(0.15f), Color.Transparent))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = theme.surfaceColor,
        shape    = RoundedCornerShape(32.dp),
        border   = BorderStroke(1.dp, theme.textColor.copy(0.1f)),
    ) {
        Column(
            modifier = Modifier
                .background(gradient)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text          = stringResource(id = R.string.up_next_label) + nextName,
                color         = theme.textColor.copy(0.6f),
                fontSize      = 14.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text          = countdown,
                    color         = theme.textColor,
                    fontSize      = 48.sp,
                    fontWeight    = FontWeight.Light,
                    letterSpacing = 2.sp,
                )
            }
            Text(
                text       = stringResource(id = R.string.remaining_label),
                color      = theme.needleAlignedColor,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
fun PrayerRow(
    prayer   : PrayerTimeItem,
    isNext   : Boolean,
    theme    : CompassTheme,
    isLast   : Boolean,
    is24h    : Boolean,
    language : String = "en",
) {
    val rowBg by animateColorAsState(
        targetValue = if (isNext) theme.needleAlignedColor.copy(0.1f) else Color.Transparent,
        label       = "prayerRowBg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter            = painterResource(id = prayer.icon),
                contentDescription = null,
                modifier           = Modifier.size(24.dp),
                tint               = if (isNext) theme.needleAlignedColor else theme.textColor.copy(0.5f),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text       = prayer.name,
                color      = if (isNext) theme.textColor else theme.textColor.copy(0.8f),
                fontSize   = 18.sp,
                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
            )
        }

        Text(
            text       = formatPrayerTime(prayer.time, is24h, language),
            color      = if (isNext) theme.needleAlignedColor else theme.textColor,
            fontSize   = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

    if (!isLast) {
        HorizontalDivider(
            modifier  = Modifier.padding(horizontal = 24.dp),
            thickness = 0.5.dp,
            color     = theme.textColor.copy(0.05f),
        )
    }
}

// ---------------------------------------------------------------------------
// BACKGROUND WORK — always called from Dispatchers.IO, never from Main
// ---------------------------------------------------------------------------

private fun buildDaySchedule(
    context          : Context,
    region           : String,
    localizedNames   : Array<String>,
    tomorrowFajrLabel: String,
): DaySchedule? {
    return try {
        val jsonText = context.assets.open("Prayer_Times/$region.json")
            .bufferedReader().use { it.readText() }
        val data = JSONArray(jsonText)

        val now   = Calendar.getInstance()
        val day   = now.get(Calendar.DAY_OF_MONTH)
        val month = now.get(Calendar.MONTH) + 1

        val todayObj   = findEntryForDate(data, day, month) ?: return null
        val timesArray = todayObj.getJSONArray("time")
        val count      = minOf(timesArray.length(), localizedNames.size)

        val prayers = ArrayList<PrayerTimeItem>(count)
        val epochMs = LongArray(count)

        for (j in 0 until count) {
            val timeStr = timesArray.getString(j)
            val colon   = timeStr.indexOf(':')
            val hour    = timeStr.substring(0, colon).toInt()
            val minute  = timeStr.substring(colon + 1).toInt()

            epochMs[j] = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE,      minute)
                set(Calendar.SECOND,      0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            prayers.add(
                PrayerTimeItem(
                    name = localizedNames[j],
                    time = timeStr,
                    icon = R.drawable.ic_prayer_times,
                )
            )
        }
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowFajrMs: Long = findEntryForDate(
            data,
            tomorrow.get(Calendar.DAY_OF_MONTH),
            tomorrow.get(Calendar.MONTH) + 1,
        )?.let { tObj ->
            val fStr = tObj.getJSONArray("time").getString(0)
            val fc   = fStr.indexOf(':')
            Calendar.getInstance().apply {
                time = tomorrow.time
                set(Calendar.HOUR_OF_DAY, fStr.substring(0, fc).toInt())
                set(Calendar.MINUTE,      fStr.substring(fc + 1).toInt())
                set(Calendar.SECOND,      0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } ?: tomorrow.timeInMillis

        DaySchedule(prayers, epochMs, tomorrowFajrMs, tomorrowFajrLabel)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// ---------------------------------------------------------------------------
// PURE UTILITY FUNCTIONS — no side effects, safe on any thread
// ---------------------------------------------------------------------------

private fun findEntryForDate(data: JSONArray, day: Int, month: Int): JSONObject? {
    for (i in 0 until data.length()) {
        val obj = data.getJSONObject(i)
        if (obj.getInt("day") == day && obj.getInt("month") == month) return obj
    }
    return null
}

private fun formatMillis(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}

private fun toArabicNumerals(str: String): String {
    val arabic = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    return buildString(str.length) {
        for (ch in str) append(if (ch in '0'..'9') arabic[ch - '0'] else ch)
    }
}

private fun formatPrayerTime(rawTime: String, is24h: Boolean, language: String = "en"): String {
    return try {
        val colon  = rawTime.indexOf(':')
        val hour   = rawTime.substring(0, colon).toInt()
        val minute = rawTime.substring(colon + 1).toInt()

        val formatted = if (is24h) {
            String.format(Locale.US, "%02d:%02d", hour, minute)
        } else {
            val period = when {
                language == "ku" -> if (hour < 12) "ص" else "م"
                else             -> if (hour < 12) "AM" else "PM"
            }
            val hour12 = when {
                hour == 0  -> 12
                hour > 12  -> hour - 12
                else       -> hour
            }
            String.format(Locale.US, "%d:%02d %s", hour12, minute, period)
        }

        if (language == "ku") toArabicNumerals(formatted) else formatted
    } catch (e: Exception) {
        rawTime
    }
}
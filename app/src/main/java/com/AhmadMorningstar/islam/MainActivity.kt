package com.AhmadMorningstar.islam

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest

// ---------------------------------------------------------------------------
// THEME CONFIGURATION
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
)

object AppThemes {
    val Obsidian = CompassTheme(
        "Obsidian Dark", true, Color(0xFF0A0E12), Color.White.copy(0.05f),
        Color.White, Color.Red, Color(0xFFFF3D00), Color(0xFF00FF88), Color.White
    )
    val PureLight = CompassTheme(
        "Pure Light", false, Color(0xFFF5F5F7), Color.Black.copy(0.05f),
        Color.DarkGray, Color.Red, Color(0xFF2C3E50), Color(0xFF27AE60), Color(0xFF1C1C1E)
    )
    val EmeraldNight = CompassTheme(
        "Emerald Night", true, Color(0xFF06120E), Color.White.copy(0.03f),
        Color(0xFF81C784), Color.Red, Color(0xFF4DB6AC), Color(0xFF00E676), Color.White
    )
    val DesertGold = CompassTheme(
        "Desert Gold", false, Color(0xFFFFF8E1), Color(0xFF795548).copy(0.1f),
        Color(0xFF5D4037), Color.Red, Color(0xFF8D6E63), Color(0xFFFFA000), Color(0xFF3E2723)
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
        textColor = Color(0xFFE3F2FD)        // Soft blue-white text
    )

    val allThemes = listOf(Obsidian, PureLight, EmeraldNight, DesertGold, OceanDeep)
}

enum class Screen { Home, Settings }

class MainActivity : ComponentActivity() {

    private val updateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            // If the update was cancelled or failed, check again to "lock" the app
            checkForUpdates()
        }
    }
    private lateinit var themePrefs: ThemePreferences
    private val currentScreen = mutableStateOf(Screen.Home)
    private val currentThemeState = mutableStateOf(AppThemes.Obsidian)
    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val kaabaLat = 21.422487
    private val kaabaLon = 39.826206

    private var smoothedNorth = 0f
    private val smoothingFactor = 0.15f
    private var lastDeclination = 0f

    private val isDeviceFlatState = mutableStateOf(true)
    private val locationState = mutableStateOf<Location?>(null)
    private val sensorAccuracyState = mutableStateOf(SensorManager.SENSOR_STATUS_UNRELIABLE)
    private val northDirectionState = mutableStateOf(0f)
    private val magneticStrengthState = mutableStateOf(0f)

    val sunAzimuthState = derivedStateOf {
        locationState.value?.let { loc -> calculateSunAzimuth(loc.latitude, loc.longitude) }
    }

    private val qiblaDirectionState = derivedStateOf {
        locationState.value?.let { loc -> calculateQibla(loc.latitude, loc.longitude) }
    }

    private val distanceToKaabaState = derivedStateOf {
        locationState.value?.let { loc -> calculateDistance(loc.latitude, loc.longitude) }
    }

    private fun checkForUpdates() {
        val appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                // Launch the "Force Update" screen
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    updateResultLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                )
            }
        }
    }

    private fun calculateSunAzimuth(lat: Double, lon: Double): Double {
        // 1. Load the saved timezone
        val tzId = themePrefs.getSavedTimezone()
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(tzId))

        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)

        val dayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val totalHours = hour + (minute / 60.0)

        // ... rest of your existing math ...
        val decl = 23.45 * kotlin.math.sin(Math.toRadians(360.0 / 365.0 * (dayOfYear - 81)))
        val latRad = Math.toRadians(lat)
        val declRad = Math.toRadians(decl)
        val hourAngle = Math.toRadians(15.0 * (totalHours - 12.0))
        val y = -kotlin.math.sin(hourAngle)
        val x = (kotlin.math.cos(latRad) * kotlin.math.tan(declRad)) -
                (kotlin.math.sin(latRad) * kotlin.math.cos(hourAngle))

        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360
    }

    private fun calculateMoonAzimuth(lat: Double, lon: Double): Double {
        val tzId = themePrefs.getSavedTimezone()
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(tzId))

        val dayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val totalHours = hour + (minute / 60.0)

        // The Moon moves approx 13.17° per day relative to stars
        // This is a robust approximation for visual calibration
        val moonPhaseAngle = (dayOfYear % 29.5) * (360 / 29.5)
        val hourAngle = Math.toRadians(15.0 * (totalHours - 12.0) - moonPhaseAngle)

        val latRad = Math.toRadians(lat)
        val y = -kotlin.math.sin(hourAngle)
        val x = (kotlin.math.cos(latRad) * 0.3) - (kotlin.math.sin(latRad) * kotlin.math.cos(hourAngle))

        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360
    }

    private fun verifyAppAuthenticity() {
        val integrityManager = IntegrityManagerFactory.create(applicationContext)

        // Nonce should be a random secure string (unique per session)
        val nonce = "AhmadMorningstar_" + System.currentTimeMillis()

        val integrityTokenRequest = IntegrityTokenRequest.builder()
            .setNonce(nonce)
            .build()

        integrityManager.requestIntegrityToken(integrityTokenRequest)
            .addOnSuccessListener { response ->
                val token = response.token()
                // Log this or send to your server.
                // If the token is invalid, the user is likely using a pirated/modded version.
            }
            .addOnFailureListener { e ->
                // This happens if the device is rooted, no Play Services, or tampered.
                // You can choose to show a "Security Alert" dialog here.
            }
    }

    // Add this state variable in MainActivity along with the others
    val moonAzimuthState = derivedStateOf {
        locationState.value?.let { loc -> calculateMoonAzimuth(loc.latitude, loc.longitude) }
    }

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)

                var rawDegree = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (rawDegree < 0) rawDegree += 360f

                val correctedDegree = (rawDegree + lastDeclination + 360) % 360
                smoothedNorth =
                    smoothedNorth + smoothingFactor * shortestAngle(smoothedNorth, correctedDegree)
                northDirectionState.value = (smoothedNorth + 360) % 360

                isDeviceFlatState.value = abs(Math.toDegrees(orientation[1].toDouble())) < 25.0 &&
                        abs(Math.toDegrees(orientation[2].toDouble())) < 25.0
            }

            if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                val values = event.values
                val magnitude =
                    sqrt((values[0] * values[0] + values[1] * values[1] + values[2] * values[2]).toDouble()).toFloat()
                magneticStrengthState.value = magnitude
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            sensorAccuracyState.value = accuracy
        }
    }

    data class VibrationSettings(
        val strength: Int = 255, // 1 to 255
        val speed: Long = 100,    // Delay in milliseconds
    )

    override fun onCreate(savedInstanceState: Bundle?) {

        verifyAppAuthenticity()
        checkForUpdates()

        super.onCreate(savedInstanceState)
        themePrefs = ThemePreferences(this)
        val savedName = themePrefs.getSavedThemeName()
        val themeToLoad = AppThemes.allThemes.find { it.name == savedName } ?: AppThemes.Obsidian
        currentThemeState.value = themeToLoad
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            val screen by currentScreen
            val theme by currentThemeState

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.backgroundColor)
            ) {
                when (screen) {
                    Screen.Home -> {
                        QiblaCompassUI(
                            theme = theme,
                            qiblaDirection = qiblaDirectionState.value,
                            northDirection = northDirectionState.value,
                            distance = distanceToKaabaState.value,
                            sensorAccuracy = sensorAccuracyState.value,
                            magneticStrength = magneticStrengthState.value,
                            location = locationState.value?.let { Pair(it.latitude, it.longitude) },
                            isDeviceFlat = isDeviceFlatState.value
                        )
                    }

                    Screen.Settings -> {
                        SettingsUI(
                            theme = theme,
                            onThemeSelected = { newTheme ->
                                currentThemeState.value = newTheme
                                themePrefs.saveTheme(newTheme.name)
                            }
                        )
                    }
                }

                ModernBottomNav(screen, { currentScreen.value = it }, theme)
            }

            DisposableEffect(Unit) {
                val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
                sensorManager.registerListener(
                    sensorEventListener,
                    rotationSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
                sensorManager.registerListener(
                    sensorEventListener,
                    magSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
                onDispose { sensorManager.unregisterListener(sensorEventListener) }
            }
        }
    }




    private fun calculateDistance(lat1: Double, lon1: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(kaabaLat - lat1)
        val dLon = Math.toRadians(kaabaLon - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(kaabaLat)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun calculateQibla(userLat: Double, userLon: Double): Double {
        val deltaLon = Math.toRadians(kaabaLon - userLon)
        val lat1 = Math.toRadians(userLat)
        val lat2 = Math.toRadians(kaabaLat)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    private fun updateLocationAndDeclination(loc: Location) {
        locationState.value = loc
        lastDeclination = GeomagneticField(
            loc.latitude.toFloat(),
            loc.longitude.toFloat(),
            loc.altitude.toFloat(),
            System.currentTimeMillis()
        ).declination
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { updateLocationAndDeclination(it) }
        }
    }

    private fun checkLocationAndStartUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) startLocationUpdates()
        else requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startLocationUpdates() }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener {
            it?.let {
                updateLocationAndDeclination(
                    it
                )
            }
        }
        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    override fun onResume() {
        super.onResume(); checkLocationAndStartUpdates()
    }

    override fun onPause() {
        super.onPause(); fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

// ---------------------------------------------------------------------------
// GLOBAL UTILITIES
// ---------------------------------------------------------------------------
fun shortestAngle(from: Float, to: Float): Float {
    var diff = to - from
    while (diff < -180) diff += 360
    while (diff > 180) diff -= 360
    return diff
}

// ---------------------------------------------------------------------------
// UI COMPONENTS
// ---------------------------------------------------------------------------

@Composable
fun ModernBottomNav(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    theme: CompassTheme,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = if (!theme.isDark) Color.White else Color(0xFF1A1F24).copy(0.95f),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, theme.textColor.copy(alpha = 0.1f)),
            modifier = Modifier
                .height(72.dp)
                .fillMaxWidth(0.95f),
            shadowElevation = if (!theme.isDark) 12.dp else 0.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavButton(
                    "Qibla",
                    Screen.Home,
                    currentScreen == Screen.Home,
                    theme
                ) { onScreenSelected(Screen.Home) }
                NavButton(
                    "Settings",
                    Screen.Settings,
                    currentScreen == Screen.Settings,
                    theme
                ) { onScreenSelected(Screen.Settings) }
            }
        }
    }
}

@Composable
fun NavButton(
    label: String,
    screen: Screen,
    isSelected: Boolean,
    theme: CompassTheme,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(if (isSelected) 1f else 0.4f, label = "")

    Column(
        modifier = Modifier
            .padding(8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    if (isSelected) theme.needleAlignedColor.copy(0.1f) else Color.Transparent,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) { }

        Spacer(Modifier.height(4.dp))

        Text(
            text = label,
            color = if (isSelected) theme.needleAlignedColor else theme.textColor.copy(alpha = alpha),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }

}

@Composable
fun QiblaCompassUI(
    theme: CompassTheme,
    qiblaDirection: Double?,
    northDirection: Float,
    distance: Double?,
    sensorAccuracy: Int,
    magneticStrength: Float,
    location: Pair<Double, Double>?,
    isDeviceFlat: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? MainActivity
    val sunAngle = activity?.sunAzimuthState?.value?.toFloat()
    val moonAngle = activity?.moonAzimuthState?.value?.toFloat()
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val isDaytime = hour in 6..18
    val themePrefs = remember { ThemePreferences(context) }

    // --- ADD THESE THREE LINES TO FIX THE ERRORS ---
    var hasVibrated by remember { mutableStateOf(false) }
    val vibStrength = themePrefs.getVibStrength() // Loads from SharedPreferences
    val vibSpeed = themePrefs.getVibSpeed()       // Loads from SharedPreferences
    // -----------------------------------------------

    val qiblaAngle = qiblaDirection?.toFloat() ?: 0f
    val angleDifference = shortestAngle(northDirection, qiblaAngle)
    val isAligned = qiblaDirection != null && abs(angleDifference) < 3

    LaunchedEffect(isAligned) {
        if (isAligned && !hasVibrated && themePrefs.isVibrationEnabled()) {
            // Use the base class 'Vibrator' to maintain compatibility across API levels
            val vibrator: android.os.Vibrator =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val vibratorManager =
                        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                }

            if (vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, vibSpeed, vibSpeed, vibSpeed)
                    val amplitudes = intArrayOf(0, vibStrength, 0, vibStrength)
                    vibrator.vibrate(
                        android.os.VibrationEffect.createWaveform(
                            timings,
                            amplitudes,
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(vibSpeed * 2)
                }
            }
            hasVibrated = true
        } else if (!isAligned) {
            hasVibrated = false
        }
    }

    val currentAccentColor by animateColorAsState(
        targetValue = if (isAligned) theme.needleAlignedColor else theme.needleDefaultColor,
        animationSpec = tween(500), label = ""
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(400.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            currentAccentColor.copy(0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        CalibrationMeter(
            magneticStrength,
            sensorAccuracy,
            theme,
            Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (location == null) "SEARCHING GPS..." else "GPS ACTIVE",
                color = if (location == null) Color.Yellow.copy(0.8f) else theme.textColor.copy(0.4f),
                fontSize = 10.sp,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(20.dp))

            Box(modifier = Modifier.size(320.dp), contentAlignment = Alignment.Center) {
                CompassFace(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(-northDirection),
                    qiblaAngle = qiblaAngle,
                    sunAngle = sunAngle,
                    moonAngle = moonAngle,
                    theme = theme
                )
                FixedTopNeedle(currentAccentColor, theme)
            }

            Spacer(Modifier.height(30.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isAligned) "ALIGNED WITH KAABA" else "ROTATE TO ALIGN",
                    color = if (isAligned) theme.needleAlignedColor else theme.textColor.copy(0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                if (qiblaDirection != null) {
                    Text(
                        "${abs(angleDifference).toInt()}°",
                        color = if (isAligned) theme.needleAlignedColor else theme.textColor,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.ExtraLight
                    )
                }

                if (distance != null) {
                    Surface(
                        color = theme.textColor.copy(0.05f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "${distance.toInt()} KM TO MECCA",
                            color = theme.textColor.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        if (sensorAccuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) CalibrationPrompt(theme)
        else if (!isDeviceFlat) DeviceNotFlatPrompt(theme)
    }

}

@Composable
fun CalibrationMeter(strength: Float, accuracy: Int, theme: CompassTheme, modifier: Modifier) {
    val isInterfered = strength > 150f || strength < 15f
    val statusColor = when {
        accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH && !isInterfered -> Color(0xFF00FF88)
        accuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> Color.Yellow
        else -> Color.Red
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "COMPASS ACCURACY",
                color = theme.textColor.copy(0.4f),
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(8.dp)
                    .background(statusColor, RoundedCornerShape(50))
            )
        }
        if (isInterfered) {
            Text(
                "METAL INTERFERENCE",
                color = Color.Red,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CompassFace(
    modifier: Modifier = Modifier,
    qiblaAngle: Float,
    sunAngle: Float?,
    moonAngle: Float?,
    theme: CompassTheme,
) {
    val kaabaBitmap = ImageBitmap.imageResource(id = R.drawable.kaaba)
    val textPaint = remember(theme) {
        Paint().apply {
            isAntiAlias = true
            textSize = 42f
            textAlign = Paint.Align.CENTER
            color = theme.textColor.toArgb() // DYNAMIC TEXT COLOR
        }
    }

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2.2f
        val center = this.center
        drawCircle(theme.surfaceColor, radius = radius)
        drawCircle(
            theme.textColor.copy(0.1f),
            radius = radius,
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
        )

        for (angle in 0 until 360 step 2) {
            val isMajor = angle % 30 == 0
            val isCardinal = angle % 90 == 0
            rotate(angle.toFloat()) {
                drawLine(
                    color = theme.textColor.copy(alpha = if (isMajor) 0.6f else 0.15f),
                    start = Offset(center.x, center.y - radius),
                    end = Offset(
                        center.x,
                        center.y - radius + (if (isCardinal) 20.dp.toPx() else 8.dp.toPx())
                    ),
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                )
                if (isCardinal) {
                    val label = when (angle) {
                        0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> ""
                    }
                    // North stays Red, others follow theme text color
                    textPaint.color =
                        if (angle == 0) android.graphics.Color.RED else theme.textColor.toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        center.x,
                        center.y - radius + 70f,
                        textPaint
                    )
                }
            }
        }

        sunAngle?.let { angle ->
            rotate(angle) {
                // Draw a yellow "Sun" circle or icon at the edge of the compass
                drawCircle(
                    color = Color(0xFFFFD700), // Gold/Yellow
                    radius = 8.dp.toPx(),
                    center = Offset(center.x, center.y - radius + 15.dp.toPx())
                )
            }
        }

        moonAngle?.let { angle ->
            rotate(angle) {
                // Outer glow for the moon
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = 10.dp.toPx(),
                    center = Offset(center.x, center.y - radius + 15.dp.toPx())
                )
                // The Moon itself
                drawCircle(
                    color = Color(0xFFE0E0E0), // Silver/Moonlight
                    radius = 6.dp.toPx(),
                    center = Offset(center.x, center.y - radius + 15.dp.toPx())
                )
            }
        }



        rotate(qiblaAngle) {
            val iconSize = 48.dp.toPx()
            drawImage(
                kaabaBitmap,
                dstOffset = IntOffset(
                    (center.x - iconSize / 2).toInt(),
                    (center.y - radius - iconSize / 2).toInt()
                ),
                dstSize = IntSize(iconSize.toInt(), iconSize.toInt())
            )
        }
    }


}

@Composable
fun FixedTopNeedle(accentColor: Color, theme: CompassTheme) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val path = Path().apply {
            moveTo(center.x, 15.dp.toPx())
            lineTo(center.x - 12.dp.toPx(), 45.dp.toPx())
            lineTo(center.x + 12.dp.toPx(), 45.dp.toPx())
            close()
        }
        drawPath(path, color = accentColor)
        drawCircle(theme.backgroundColor, radius = 6f, center = center) // Match center dot to bg
    }
}

@Composable
fun CalibrationPrompt(theme: CompassTheme) {
    Box(
        Modifier
            .fillMaxSize()
            .background(theme.backgroundColor.copy(0.95f)), Alignment.Center
    ) {
        Text("Move Phone in ∞ Shape", color = theme.textColor)
    }
}

@Composable
fun DeviceNotFlatPrompt(theme: CompassTheme) {
    Box(
        Modifier
            .fillMaxSize()
            .background(theme.backgroundColor.copy(0.95f)), Alignment.Center
    ) {
        Text("Hold Phone Level", color = theme.textColor)
    }
}

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
        return sharedPrefs.getString("selected_timezone", java.util.TimeZone.getDefault().id) ?: "GMT"
    }

    fun getVibStrength(): Int = sharedPrefs.getInt("vib_strength", 255)
    fun getVibSpeed(): Long = sharedPrefs.getLong("vib_speed", 50L)
}
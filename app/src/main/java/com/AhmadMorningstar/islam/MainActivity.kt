package com.AhmadMorningstar.islam

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import androidx.appcompat.app.AppCompatActivity
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.AhmadMorningstar.islam.security.SignatureVerifier
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import org.json.JSONObject
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.activity.SystemBarStyle
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import io.github.cosinekitty.astronomy.*
import java.util.Calendar
import java.util.TimeZone

// ---------------------------------------------------------------------------
// THEME CONFIGURATION
// ---------------------------------------------------------------------------

fun Context.findActivity(): MainActivity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is MainActivity) return context
        context = context.baseContext
    }
    return null
}

enum class Screen { Home, Prayer, Dua, Settings }

class MainActivity : AppCompatActivity() {

    private val updateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            // If the update was cancelled or failed, check again to "lock" the app
            checkForUpdates()
        }
    }

    private val currentRegionState = mutableStateOf("erbil")
    private lateinit var themePrefs: ThemePreferences
    private val currentScreen = mutableStateOf(Screen.Prayer)
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

    val moonAzimuthState = derivedStateOf {
        locationState.value?.let { loc -> calculateMoonAzimuth(loc.latitude, loc.longitude) }
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
                && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                // Launch flexible update
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    updateResultLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                )
            }
        }
            .addOnFailureListener {
                // Offline or network error - just log or optionally show a toast
                // Do NOT block app usage
                println("Update check failed: ${it.localizedMessage}")
            }
    }

    private fun calculateSunAzimuth(lat: Double, lon: Double): Double {
        try {
            val observer = Observer(lat, lon, 0.0)

            // Get timezone from preferences (if you want custom timezone)
            val tzId = themePrefs.getSavedTimezone()
            val calendar = Calendar.getInstance(TimeZone.getTimeZone(tzId))

            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val second = calendar.get(Calendar.SECOND).toDouble()

            val time = Time(year, month, day, hour, minute, second)

            val equ = equator(Body.Sun, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
            val hor = horizon(time, observer, equ.ra, equ.dec, Refraction.Normal)

            return hor.azimuth
        } catch (e: Exception) {
            android.util.Log.e("SunAzimuth", "Error: ${e.message}")
            return 0.0
        }
    }

    private fun calculateMoonAzimuth(lat: Double, lon: Double): Double {
        try {
            val observer = Observer(lat, lon, 0.0)

            val tzId = themePrefs.getSavedTimezone()
            val calendar = Calendar.getInstance(TimeZone.getTimeZone(tzId))

            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val second = calendar.get(Calendar.SECOND).toDouble()

            val time = Time(year, month, day, hour, minute, second)

            val equ = equator(Body.Moon, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
            val hor = horizon(time, observer, equ.ra, equ.dec, Refraction.Normal)

            return hor.azimuth
        } catch (e: Exception) {
            android.util.Log.e("MoonAzimuth", "Error: ${e.message}")
            return 0.0
        }
    }
    private fun isGpsEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun requestGpsEnable() {
        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    // This triggers the modern Google Play Services "Turn on GPS?" dialog
                    exception.startResolutionForResult(this, 12345)

                } catch (sendEx: Exception) {
                    // Ignore
                }
            }
        }
    }
    private fun checkAppSecurity() {
        val prefs = getSharedPreferences("security_prefs", MODE_PRIVATE)
        val lastCheck = prefs.getLong("last_integrity_check", 0L)
        val currentTime = System.currentTimeMillis()

        // Skip check if verified within the last 24 hours to save battery/quota
        if (currentTime - lastCheck < 86400000) return

        FirebaseAppCheck.getInstance().getAppCheckToken(false)
            .addOnSuccessListener {
                prefs.edit().putLong("last_integrity_check", currentTime).apply()
                android.util.Log.d("Security", "Integrity Check Passed!")
            }
            .addOnFailureListener { e ->
                val errorMsg = e.message ?: "Unknown Error"
                android.util.Log.e("Security", "App Check failed: $errorMsg")

                // 1. Ignore Network Issues (Don't annoy users if they are offline)
                if (errorMsg.contains("network", true) || errorMsg.contains("timeout", true)) {
                    return@addOnFailureListener
                }

                // 2. Handle Configuration Errors (Developer side - SHA-256 missing in Firebase)
                if (errorMsg.contains("403") || errorMsg.contains("not found")) {
                    // We don't show the "Malware" warning here because this is usually
                    // a developer configuration mistake, not a user security risk.
                    return@addOnFailureListener
                }

                // 3. THE "TAMPERED / UNAUTHORIZED" WARNING
                // Triggered by: ERROR_APP_NOT_OWNED (-10), Rooted devices, or modified APKs
                if (errorMsg.contains("-10") ||
                    errorMsg.contains("integrity", true) ||
                    errorMsg.contains("attestation", true)) {

                    showOfficialStoreRedirect(
                        title = "Security Risk Detected",
                        message = "This app version was not recognized by Google Play. It may have been modified by a third party and could contain malware. \n\nFor your safety, please uninstall this and download the official version."
                    )
                }
            }
    }

    private fun showOfficialStoreRedirect(title: String, message: String) {
        runOnUiThread {
            val appPackageName = packageName
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false) // User MUST take action
                .setPositiveButton("Get Official App") { _, _ ->
                    try {
                        // Try to open the Play Store app first
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
                    } catch (e: Exception) {
                        // Fallback to browser if Play Store app is missing
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
                    }
                }
                .setNegativeButton("Exit App") { _, _ ->
                    finishAffinity() // Close the app entirely
                }
                .show()
        }
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

    override fun onCreate(savedInstanceState: Bundle?) {

        val signatureStatus = mutableStateOf("Checking...")

        signatureStatus.value = if (SignatureVerifier.isSignatureValid(this)) {
            "Verified ✅"
        } else {
            "Not Verified ❌"
        }

        if (!SignatureVerifier.isSignatureValid(this)) {
            finishAffinity()
            Runtime.getRuntime().exit(0)
        }

        FirebaseApp.initializeApp(this)

        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )


        // 2. Run the check
        checkAppSecurity()

        AppUpdateChecker.fetchConfig { config ->
            if (config == null) return@fetchConfig

            val currentVersion = if (android.os.Build.VERSION.SDK_INT >= 28) {
                packageManager.getPackageInfo(packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
            }
            val forceExpired =
                currentVersion < config.min_version && AppUpdateChecker.isForceExpired(config.force_after)
            val country = AppUpdateChecker.getCountryCode()

            runOnUiThread {
                when {
                    currentVersion < config.min_version && forceExpired -> {
                        // Force update after grace period
                        showForceUpdateDialog(this, config.message)
                    }

                    currentVersion < config.latest_version && config.regions_optional.contains(
                        country
                    ) -> {
                        // Optional update for users in certain countries
                        showOptionalUpdateDialog(this, config.message)
                    }
                }
            }
        }

        // Edge-to-edge setup compatible with SDK 21-36+
        when {
            // Android 15+ (API 35+): Use modern edge-to-edge API
            android.os.Build.VERSION.SDK_INT >= 35 -> {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                )
            }
            // Android 5.0+ (API 21-34): Legacy method
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP -> {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            // Below Android 5.0 (shouldn't happen with minSdk 21, but safe fallback)
            else -> {
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
        }
        checkForUpdates()

        super.onCreate(savedInstanceState)
        val currentLocale = AppCompatDelegate.getApplicationLocales().get(0)?.language
        if (currentLocale == null) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }
        themePrefs = ThemePreferences(this)
        currentRegionState.value = themePrefs.getSavedRegion()
        val savedName = themePrefs.getSavedThemeName()
        val themeToLoad = AppThemes.allThemes.find { it.name == savedName } ?: AppThemes.Obsidian
        currentThemeState.value = themeToLoad

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        setContent {
            val screen by currentScreen
            val theme by currentThemeState
            var currentLanguage by remember { mutableStateOf(themePrefs.getSavedLanguage()) }
            val typography = rememberAppTypography(currentLanguage)
            var gpsEnabled by remember { mutableStateOf(isGpsEnabled()) }
            var permissionGranted by remember {
                mutableStateOf(
                    ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }
            // --- THE LIFECYCLE OBSERVER (Keep this here) ---
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        gpsEnabled = isGpsEnabled()
                        permissionGranted = ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val context = LocalContext.current
            val configuration = LocalConfiguration.current

            val localizedContext = remember(currentLanguage, configuration) {
                val locale = Locale(currentLanguage)
                Locale.setDefault(locale)

                // Create a copy of the current configuration
                val configCopy = android.content.res.Configuration(configuration)
                configCopy.setLocale(locale)
                configCopy.setLayoutDirection(locale)

                context.createConfigurationContext(configCopy)
            }

            val layoutDirection = if (currentLanguage == "ar" || currentLanguage == "ku") {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }
            // --- THE CLEANED UP UI STRUCTURE ---
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalAppTypography provides typography,
                LocalLayoutDirection provides if (currentLanguage == "ar" || currentLanguage == "ku")
                    LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(theme.backgroundColor)
                ) {
                    // LAYER 1: Your Main App Content
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (screen) {
                            Screen.Home -> {
                                // WRAPPER BOX: Holds Compass + Overlay
                                Box(modifier = Modifier.fillMaxSize()) {

                                    // 1. The Compass UI (Always renders underneath)
                                    QiblaCompassUI(
                                        theme = theme,
                                        qiblaDirection = qiblaDirectionState.value,
                                        northDirection = northDirectionState.value,
                                        distance = distanceToKaabaState.value,
                                        sensorAccuracy = sensorAccuracyState.value,
                                        magneticStrength = magneticStrengthState.value,
                                        location = locationState.value?.let {
                                            Pair(
                                                it.latitude,
                                                it.longitude
                                            )
                                        },
                                        isDeviceFlat = isDeviceFlatState.value,
                                        sunAngle = sunAzimuthState.value?.toFloat(),
                                        moonAngle = moonAzimuthState.value?.toFloat()
                                    )

                                    // 2. The GPS Overlay (Only shows HERE, on top of compass)
                                    if (!permissionGranted || !gpsEnabled) {
                                        LocationRequiredOverlay(
                                            isGpsOff = !gpsEnabled,
                                            isPermissionDenied = !permissionGranted,
                                            onActionClick = {
                                                if (!permissionGranted) {
                                                    val intent =
                                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                            data = Uri.fromParts(
                                                                "package",
                                                                packageName,
                                                                null
                                                            )
                                                        }
                                                    startActivity(intent)
                                                } else {
                                                    requestGpsEnable()
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            Screen.Prayer -> {
                                PrayerTimesUI(
                                    theme = theme,
                                    region = currentRegionState.value
                                )
                            }

                            Screen.Dua -> {
                                DuaUI(theme = theme)
                            }

                            Screen.Settings -> {
                                SettingsUI(
                                    theme = theme,
                                    onThemeSelected = { newTheme ->
                                        currentThemeState.value = newTheme
                                        themePrefs.saveTheme(newTheme.name)
                                    },
                                    currentRegion = currentRegionState.value,
                                    onRegionSelected = { newRegion ->
                                        currentRegionState.value = newRegion
                                        themePrefs.saveRegion(newRegion)
                                    },
                                    // ADD THESE TWO LINES:
                                    currentLanguage = currentLanguage,
                                    onLanguageSelected = { newLang ->
                                        currentLanguage = newLang
                                        themePrefs.saveLanguage(newLang)
                                    }
                                )
                            }
                        }
                    }

                    // LAYER 2: Your Navigation (stays on top of the content)
                    ModernBottomNav(screen, { currentScreen.value = it }, theme)
                }
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
        super.onResume()
        // 1. Restart Sensors
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

        // 2. Restart Location
        checkLocationAndStartUpdates()
    }

    override fun onPause() {
        super.onPause()
        // 1. Stop Sensors immediately to save CPU/Battery
        sensorManager.unregisterListener(sensorEventListener)

        // 2. Stop Location
        fusedLocationClient.removeLocationUpdates(locationCallback)
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
// --------------------------------------------------------------------------
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
                    stringResource(id = R.string.navbutton_qibla_label),
                    Screen.Home,
                    iconRes = R.drawable.ic_compass,
                    iconColor = theme.compassIconColor,
                    currentScreen == Screen.Home,
                    theme
                ) { onScreenSelected(Screen.Home) }
                NavButton(
                    stringResource(id = R.string.navbutton_prayers_label),
                    Screen.Prayer,
                    iconRes = R.drawable.ic_prayer_times,
                    iconColor = theme.needleAlignedColor,
                    currentScreen == Screen.Prayer,
                    theme
                ) { onScreenSelected(Screen.Prayer) }
                NavButton(
                    stringResource(id = R.string.navbutton_duas_and_dhikr_label),
                    Screen.Dua,
                    R.drawable.ic_dua,
                    theme.verifiedColor,
                    currentScreen == Screen.Dua,
                    theme
                ) { onScreenSelected(Screen.Dua) }
                NavButton(
                    stringResource(id = R.string.settings_label),
                    Screen.Settings,
                    iconRes = R.drawable.ic_settings,
                    iconColor = theme.settingsIconColor,
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
    iconRes: Int,
    iconColor: Color,
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
        ) {
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(id = iconRes),
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                // Uses your custom theme color when selected, otherwise fades
                tint = if (isSelected) iconColor else theme.textColor.copy(alpha = alpha)
            )
        }

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
    sunAngle: Float?,
    moonAngle: Float?,

) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val themePrefs = remember { ThemePreferences(context) }
    var hasVibrated by remember { mutableStateOf(false) }
    val vibStrength = themePrefs.getVibStrength()
    val vibSpeed = themePrefs.getVibSpeed()
    // -----------------------------------------------

    val qiblaAngle = qiblaDirection?.toFloat() ?: 0f
    val angleDifference = shortestAngle(northDirection, qiblaAngle)
    val isAligned = qiblaDirection != null && abs(angleDifference) < 3

    val formattedDistance = remember(distance) {
        val distInt = distance?.toInt() ?: 0
        val nf = java.text.NumberFormat.getIntegerInstance(java.util.Locale.getDefault())
        nf.format(distInt)
    }
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
                .padding(
                    top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 20.dp,
                    end = 20.dp
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (location == null) {
                    stringResource(id = R.string.searching_gps_label)
                }
                else {
                    stringResource(id = R.string.gps_active_label)
                },
                color = if (location == null) Color.Yellow.copy(0.8f) else theme.textColor.copy(0.4f),
                style = LocalAppTypography.current.qiblaScreen.copy(
                    letterSpacing = 2.sp
                )
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
                    text = if (isAligned) {
                        stringResource(id = R.string.aligned_kaaba_label)
                    } else {
                        stringResource(id = R.string.rotate_align_label)
                    },
                    color = if (isAligned) theme.needleAlignedColor else theme.textColor.copy(0.6f),
                    style = LocalAppTypography.current.qiblaScreen,
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
                            text = stringResource(id = R.string.distance_to_mecca_label, formattedDistance),
                            color = theme.textColor.copy(alpha = 0.7f),
                            style = LocalAppTypography.current.qiblaScreen,
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
                stringResource(id = R.string.qibla_accuracy_label),
                color = theme.textColor.copy(0.4f),
                style = LocalAppTypography.current.qiblaScreen.copy(
                    letterSpacing = 1.sp
                )
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
                stringResource(id = R.string.metal_interference),
                color = Color.Red,
                style = LocalAppTypography.current.qiblaScreen
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
    val sunPainter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_sun)
    val moonPainter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_moon)
    val textPaint = remember(theme) {
        Paint().apply {
            isAntiAlias = true
            textSize = 42f
            textAlign = Paint.Align.CENTER
            color = theme.textColor.toArgb()
        }
    }

    val labelN = stringResource(id = R.string.cardinal_directions_n)
    val labelE = stringResource(id = R.string.cardinal_directions_e)
    val labelS = stringResource(id = R.string.cardinal_directions_s)
    val labelW = stringResource(id = R.string.cardinal_directions_w)

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
                        0   -> labelN
                        90  -> labelE
                        180 -> labelS
                        270 -> labelW
                        else -> ""
                    }
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
                val iconSizePx = 24.dp.toPx() // Increased slightly for visibility
                val xOffset = center.x - (iconSizePx / 2)
                val yOffset = center.y - radius + 10.dp.toPx()

                translate(left = xOffset, top = yOffset) {
                    with(sunPainter) {
                        draw(size = androidx.compose.ui.geometry.Size(iconSizePx, iconSizePx))
                    }
                }
            }
        }

        moonAngle?.let { angle ->
            rotate(angle) {
                val iconSizePx = 22.dp.toPx()
                val xOffset = center.x - (iconSizePx / 2)
                val yOffset = center.y - radius + 10.dp.toPx()

                translate(left = xOffset, top = yOffset) {
                    with(moonPainter) {
                        draw(size = androidx.compose.ui.geometry.Size(iconSizePx, iconSizePx))
                    }
                }
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
        Text(stringResource(id = R.string.move_phone_label),
            color = theme.textColor,
            style = LocalAppTypography.current.qiblaScreen,
        )
    }
}

@Composable
fun DeviceNotFlatPrompt(theme: CompassTheme) {
    Box(
        Modifier
            .fillMaxSize()
            .background(theme.backgroundColor.copy(0.95f)), Alignment.Center
    ) {
        Text(stringResource(id = R.string.phone_level_label),
            color = theme.textColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

class ThemePreferences(context: Context) {
    private val sharedPrefs = context.getSharedPreferences("qibla_prefs", Context.MODE_PRIVATE)

    // --- LANGUAGE ---
    fun saveLanguage(langCode: String) {
        sharedPrefs.edit().putString("selected_lang", langCode).apply()
    }

    fun getSavedLanguage(): String {
        return sharedPrefs.getString("selected_lang", "en") ?: "en"
    }

    // --- FAVORITES ---
    fun toggleFavorite(duaId: String) {
        val favorites = getFavorites().toMutableSet()
        if (favorites.contains(duaId)) favorites.remove(duaId)
        else favorites.add(duaId)
        sharedPrefs.edit().putStringSet("dua_favorites", favorites).apply()
    }

    fun isFavorite(duaId: String): Boolean {
        return getFavorites().contains(duaId)
    }

    fun getFavorites(): Set<String> {
        return sharedPrefs.getStringSet("dua_favorites", emptySet()) ?: emptySet()
    }

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
@Composable
fun LocationRequiredOverlay(
    isGpsOff: Boolean,
    isPermissionDenied: Boolean,
    onActionClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isGpsOff) "GPS is Disabled" else "Location Permission Required",
                style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 20.sp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isGpsOff) "To find the Qibla, your phone's GPS must be on." else "The app needs permission to know where you are.",
                style = androidx.compose.ui.text.TextStyle(color = Color.Gray),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onActionClick) {
                Text(if (isGpsOff) "Enable GPS" else "Grant Permission")
            }
        }
    }
}

data class DuaMetadata(
    val latestVersion: Int,
    val message: String
)

data class UpdateConfig(
    val min_version: Long,
    val latest_version: Long,
    val force_after: String,
    val message: String,
    val regions_optional: List<String>,
)

object AppUpdateChecker {

    fun fetchConfig(onResult: (UpdateConfig?) -> Unit) {
        thread {
            try {
                val jsonStr =
                    URL("https://raw.githubusercontent.com/AhmadMorningstar/Islam/main/update_config.json")
                        .readText()
                val obj = JSONObject(jsonStr)
                val regions = mutableListOf<String>()
                if (obj.has("regions_optional")) {
                    val arr = obj.getJSONArray("regions_optional")
                    for (i in 0 until arr.length()) regions.add(arr.getString(i))
                }

                val config = UpdateConfig(
                    min_version = obj.getLong("min_version"),
                    latest_version = obj.getLong("latest_version"),
                    force_after = obj.getString("force_after"),
                    message = obj.getString("message"),
                    regions_optional = regions
                )
                onResult(config)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun isForceExpired(forceAfter: String): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Use java.time for API 26+
                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a VV")
                val deadline = java.time.ZonedDateTime.parse(forceAfter, formatter)
                java.time.ZonedDateTime.now()
                    .isAfter(deadline.withZoneSameInstant(java.time.ZoneId.systemDefault()))
            } else {
                // Use SimpleDateFormat for API 21-25
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US)
                val deadline = sdf.parse(forceAfter.substringBeforeLast(" "))
                val now = java.util.Date()
                now.after(deadline)
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getCountryCode(): String {
        return Locale.getDefault().country.uppercase(Locale.US)
    }
}

fun showForceUpdateDialog(context: Context, message: String) {
    androidx.appcompat.app.AlertDialog.Builder(context)
        .setTitle(context.getString(R.string.forced_update_dialog))
        .setMessage(message)
        .setCancelable(false)
        .setPositiveButton(context.getString(R.string.optional_update_btn_update_label)) { _, _ ->
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
            )
            if (context is Activity) context.finishAffinity() // closes all activities
            Runtime.getRuntime().exit(0) // ensure process exits
        }
        .setOnDismissListener {
            // Prevent dialog dismissal
            if (context is Activity) {
                context.finishAffinity()
                Runtime.getRuntime().exit(0)
            }
        }
        .show()
}


fun showOptionalUpdateDialog(context: Context, message: String) {
    androidx.appcompat.app.AlertDialog.Builder(context)
        .setTitle(context.getString(R.string.title_update_available_label))
        .setMessage(message)
        .setCancelable(true)
        .setPositiveButton(
            context.getString(R.string.optional_update_btn_update_label)
        )
        { _, _ ->
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
            )
        }
        .setNegativeButton(
            context.getString(R.string.optional_update_btn_update_later_label),
            null)
        .show()
}
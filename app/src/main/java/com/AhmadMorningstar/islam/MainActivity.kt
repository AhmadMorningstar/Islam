package com.AhmadMorningstar.islam

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.activity.SystemBarStyle



enum class Screen { Home, Prayer, Dua, Settings }

class MainActivity : ComponentActivity() {

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

    // Pre-allocated to avoid FloatArray GC pressure inside onSensorChanged (~30-50x/sec)
    private val rotationMatrix = FloatArray(9)
    private val orientationArr = FloatArray(3)


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
        // 1. Load the saved timezone
        val tzId = themePrefs.getSavedTimezone()
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(tzId))

        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)

        val dayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val totalHours = hour + (minute / 60.0)

        // ... rest of your existing math ...
        val decl = 23.45 * sin(Math.toRadians(360.0 / 365.0 * (dayOfYear - 81)))
        val latRad = Math.toRadians(lat)
        val declRad = Math.toRadians(decl)
        val hourAngle = Math.toRadians(15.0 * (totalHours - 12.0))
        val y = -sin(hourAngle)
        val x = (cos(latRad) * kotlin.math.tan(declRad)) -
                (sin(latRad) * cos(hourAngle))

        return (Math.toDegrees(atan2(y, x)) + 360) % 360
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
        val y = -sin(hourAngle)
        val x = (cos(latRad) * 0.3) - (sin(latRad) * cos(hourAngle))

        return (Math.toDegrees(atan2(y, x)) + 360) % 360
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

        // Battery Saver: Only check once every 24 hours
        // (86400000 milliseconds = 24 hours)
        if (currentTime - lastCheck < 86400000) {
            return
        }

        FirebaseAppCheck.getInstance().getAppCheckToken(false)
            .addOnSuccessListener {
                // Update the timestamp on success
                prefs.edit().putLong("last_integrity_check", currentTime).apply()
                android.util.Log.d("Security", "Integrity Check Passed!")
            }
            .addOnFailureListener { e ->

                val errorMsg = e.message ?: ""
                val isNetworkError = errorMsg.contains("network", ignoreCase = true) ||
                        errorMsg.contains("connection", ignoreCase = true) ||
                        errorMsg.contains("timeout", ignoreCase = true)

                if (!isNetworkError) {
                    showIntegrityWarning()
                }
            }
    }

    private fun showIntegrityWarning() {
        runOnUiThread {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Security Warning")
                .setMessage("Your device or app may be tampered. For Security Concerns Please only download from Play Store.")
                .setCancelable(false)
                .setPositiveButton("Continue") { dialog, _ -> dialog.dismiss() }
                .setNegativeButton("Go to Play Store") { dialog, _ ->
                    val appPackageName = packageName
                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=$appPackageName")
                            )
                        )
                    } catch (anfe: android.content.ActivityNotFoundException) {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")
                            )
                        )
                    }
                    dialog.dismiss()
                }
                .show()
        }
    }

    val moonAzimuthState = derivedStateOf {
        locationState.value?.let { loc -> calculateMoonAzimuth(loc.latitude, loc.longitude) }
    }

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationArr)
                val orientation = orientationArr

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
        super.onCreate(savedInstanceState)

        // Call once — isSignatureValid does crypto work, no reason to call it twice
        val isSignatureValid = SignatureVerifier.isSignatureValid(this)
        if (!isSignatureValid) {
            finishAffinity()
            Runtime.getRuntime().exit(0)
        }

        val duaPrefs = DuaPreferences(this)
        val currentLangState = mutableStateOf(duaPrefs.getSavedLanguage())

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

            // --- THE CLEANED UP UI STRUCTURE ---
            // We use ONE root Box to keep everything layered correctly
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
                                    isDeviceFlat = isDeviceFlatState.value
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
                                                        data = Uri.fromParts("package", packageName, null)
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
                            DuaUI(theme = theme, lang = currentLangState.value)
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
                                currentLang = currentLangState.value,
                                onLangSelected = { lang ->
                                    currentLangState.value = lang
                                    duaPrefs.saveLanguage(lang)
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
                    iconRes = R.drawable.ic_compass,
                    iconColor = theme.compassIconColor,
                    currentScreen == Screen.Home,
                    theme
                ) { onScreenSelected(Screen.Home) }
                NavButton(
                    "Prayers",
                    Screen.Prayer,
                    iconRes = R.drawable.ic_prayer_times,
                    iconColor = theme.needleAlignedColor,
                    currentScreen == Screen.Prayer,
                    theme
                ) { onScreenSelected(Screen.Prayer) }
                NavButton(
                    "Dua & Dhikr",
                    Screen.Dua,
                    iconRes = R.drawable.ic_dua,
                    iconColor = theme.needleAlignedColor,
                    currentScreen == Screen.Dua,
                    theme
                ) { onScreenSelected(Screen.Dua) }
                NavButton(
                    "Settings",
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
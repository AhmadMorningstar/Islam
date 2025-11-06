package com.AhmadMorningstar.islam

import android.graphics.Paint
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlin.math.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize

class MainActivity : ComponentActivity() {
    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val kaabaLat = 21.422487
    private val kaabaLon = 39.826206

    private val isDeviceFlatState = mutableStateOf(true)

    private val locationState = mutableStateOf<Location?>(null)
    private val sensorAccuracyState = mutableStateOf(SensorManager.SENSOR_STATUS_UNRELIABLE)
    private val northDirectionState = mutableStateOf(0f)
    private val qiblaDirectionState = derivedStateOf {
        locationState.value?.let { loc ->
            calculateQibla(loc.latitude, loc.longitude)
        }
    }

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)

                // orientation[0] is Azimuth (North direction) in radians.
                var degree = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (degree < 0) degree += 360f
                northDirectionState.value = degree

                // --- NEW CODE STARTS HERE ---
                // orientation[1] is Pitch (tilt forward/backward) in radians.
                // orientation[2] is Roll (tilt left/right) in radians.
                val pitch = Math.toDegrees(orientation[1].toDouble())
                val roll = Math.toDegrees(orientation[2].toDouble())

                // Define a threshold. If tilt exceeds this, we consider it "not flat".
                val flatThreshold = 25.0 // in degrees

                // Update the state based on whether pitch and roll are within the threshold.
                isDeviceFlatState.value = abs(pitch) < flatThreshold && abs(roll) < flatThreshold
                // --- NEW CODE ENDS HERE ---
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            sensorAccuracyState.value = accuracy
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                locationState.value = loc
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            val qiblaDirection = qiblaDirectionState.value
            val northDirection = northDirectionState.value
            val sensorAccuracy = sensorAccuracyState.value
            val location = locationState.value
            val isDeviceFlat = isDeviceFlatState.value

            QiblaCompassUI(
                qiblaDirection = qiblaDirection,
                northDirection = northDirection,
                sensorAccuracy = sensorAccuracy,
                location = location?.let { Pair(it.latitude, it.longitude) },
                isDeviceFlat = isDeviceFlat
            )

            DisposableEffect(Unit) {
                val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_UI)
                onDispose {
                    sensorManager.unregisterListener(sensorEventListener)
                }
            }
        }
    }

    private fun checkLocationAndStartUpdates() {
        if (!isLocationEnabled()) {
            promptToEnableLocation()
        } else {
            requestLocationPermission()
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startLocationUpdates()
        }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                locationState.value = location
            }
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun promptToEnableLocation() {
        AlertDialog.Builder(this)
            .setMessage("Your location service is disabled. Please enable it to find the Qibla direction.")
            .setPositiveButton("Enable Location") { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun calculateQibla(userLat: Double, userLon: Double): Double {
        val deltaLon = Math.toRadians(kaabaLon - userLon)
        val lat1 = Math.toRadians(userLat)
        val lat2 = Math.toRadians(kaabaLat)

        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        val bearing = (Math.toDegrees(atan2(y, x)) + 360) % 360

        return bearing
    }

    override fun onResume() {
        super.onResume()
        checkLocationAndStartUpdates()
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

@Composable
fun CompassFace(
    modifier: Modifier = Modifier,
    qiblaAngle: Float? // <-- Add qiblaAngle as a parameter
) {
    // Load the kaaba image as a bitmap, which is efficient for canvas drawing
    val kaabaBitmap = ImageBitmap.imageResource(id = R.drawable.kaaba)

    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textSize = 40f
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
        }
    }

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2.2f
        val center = this.center

        // Draw the outer circle
        drawCircle(
            color = Color.White.copy(alpha = 0.5f),
            radius = radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )

        // Draw tick marks and labels
        for (angle in 0 until 360 step 15) {
            // ... (the existing code for drawing ticks and labels remains the same)
            val isCardinal = angle % 90 == 0
            val isMajor = angle % 45 == 0

            val lineLength = when {
                isCardinal -> 20.dp.toPx()
                isMajor -> 15.dp.toPx()
                else -> 10.dp.toPx()
            }
            val strokeWidth = when {
                isCardinal -> 3.dp.toPx()
                isMajor -> 2.dp.toPx()
                else -> 1.dp.toPx()
            }

            rotate(degrees = angle.toFloat(), pivot = center) {
                // Draw the tick line
                drawLine(
                    color = Color.White,
                    start = Offset(center.x, center.y - radius),
                    end = Offset(center.x, center.y - radius + lineLength),
                    strokeWidth = strokeWidth
                )

                // Draw labels for cardinal points (N, E, S, W)
                if (isCardinal) {
                    val label = when (angle) {
                        0 -> "N"
                        90 -> "E"
                        180 -> "S"
                        270 -> "W"
                        else -> ""
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        center.x,
                        center.y - radius + lineLength + 40f,
                        textPaint
                    )
                }
            }
        }

        // --- NEW CODE TO DRAW THE KAABA ICON ---
        if (qiblaAngle != null) {
            // We rotate the entire canvas to the Qibla direction
            rotate(degrees = qiblaAngle, pivot = center) {
                val iconSizePx = 25.dp.toPx() // The size of our icon in pixels
                val iconRadius = radius - 35.dp.toPx() // How far from the center to draw it

                // Calculate the top-left position to center the icon correctly
                val topLeft = Offset(
                    x = center.x - iconSizePx / 2,
                    y = center.y - iconRadius - iconSizePx / 2
                )

                // Draw the Kaaba bitmap onto the canvas
                drawImage(
                    image = kaabaBitmap,
                    dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                    dstSize = IntSize(iconSizePx.roundToInt(), iconSizePx.roundToInt())
                )
            }
        }
    }
}

@Composable
fun DeviceNotFlatPrompt() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_screen_rotation), // You'll need to add this icon
                contentDescription = "Phone orientation icon",
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Please Lay Phone Flat",
                color = Color.White,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "For an accurate compass reading, the phone should be held parallel to the ground.",
                color = Color.LightGray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun QiblaCompassUI(
    qiblaDirection: Double?,
    northDirection: Float,
    sensorAccuracy: Int,
    location: Pair<Double, Double>?,
    isDeviceFlat: Boolean // <-- Add the new parameter here
) {
    val qiblaAngle = qiblaDirection?.toFloat() ?: 0f

    val animatedNeedleRotation by animateFloatAsState(
        targetValue = qiblaAngle - northDirection,
        label = "CompassNeedleRotation",
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    )

    val isAligned = qiblaDirection != null && abs((qiblaAngle - northDirection + 360) % 360) < 5

    val bgColor by animateColorAsState(
        targetValue = if (isAligned) Color(0xFF0C4B0C) else Color(0xFF101820),
        label = "BackgroundColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        // The main container for the compass face and the needle
        Box(
            modifier = Modifier.size(300.dp),
            contentAlignment = Alignment.Center
        ) {
            // The custom-drawn compass face that rotates to point North
            CompassFace(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(-northDirection),
                qiblaAngle = qiblaDirection?.toFloat() // <-- PASS THE ANGLE HERE
            )

            // The needle that points towards the Qibla
            Canvas(modifier = Modifier.fillMaxSize()) {
                rotate(degrees = animatedNeedleRotation, pivot = center) {
                    val path = Path().apply {
                        moveTo(center.x, center.y - 120)
                        lineTo(center.x - 20, center.y)
                        lineTo(center.x + 20, center.y)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = if (isAligned) Color.Yellow else Color(0xFFFF5722)
                    )
                }
                // Center circle on top of the needle
                drawCircle(color = Color.White, radius = 15f, center = center)
            }
        }

        // Overlay for status text
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AccuracyAndLocationStatus(sensorAccuracy, location)

            if (isAligned) {
                Text(
                    text = "You are facing the Qibla",
                    color = Color.White,
                    fontSize = 24.sp,
                )
            } else {
                Spacer(modifier = Modifier.height(0.dp))
            }
        }

        // --- UPDATED LOGIC FOR PROMPTS ---
        // The calibration prompt has higher priority.
        if (sensorAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || sensorAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            CalibrationPrompt(sensorAccuracy = sensorAccuracy)
        } else if (!isDeviceFlat) {
            // If calibration is OK, but the phone is not flat, show this prompt.
            DeviceNotFlatPrompt()
        }
    }
}

@Composable
fun CalibrationPrompt(sensorAccuracy: Int) {
    if (sensorAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || sensorAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                // Replace the static image with our new animation!
                Figure8Animation(modifier = Modifier.padding(bottom = 32.dp))

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Compass Interference",
                    color = Color.White,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "For an accurate direction, please move your phone in a figure-8 motion to calibrate the compass. Avoid magnetic or metal objects.",
                    color = Color.LightGray,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun Figure8Animation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "Figure8Transition")

    // Animate the angle from 0 to 360 degrees over 4 seconds for the path
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AngleAnimation"
    )

    // Animate the rotation from -30 to 30 degrees and back for a tilting effect
    val rotation by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RotationAnimation"
    )

    // This phone-like shape will be the object we animate
    Surface(
        modifier = modifier
            .size(width = 50.dp, height = 100.dp)
            .offset {
                // This is the magic: A parametric equation for a figure-8 (Lissajous curve)
                // We convert the animated angle to radians for trigonometric functions
                val angleRad = Math.toRadians(angle.toDouble())
                val radius = 80f // The size of the figure-8 loop

                // sin(angle) controls the X movement, sin(2 * angle) controls the Y
                // This makes the Y-axis move twice as fast, creating the figure-8 shape
                val offsetX = radius * sin(angleRad)
                val offsetY = radius * sin(2 * angleRad) / 2 // Divide by 2 to make the loop wider

                IntOffset(offsetX.roundToInt(), offsetY.roundToInt())
            }
            .rotate(rotation), // Apply the tilting rotation
        shape = RoundedCornerShape(12.dp),
        color = Color.DarkGray,
        border = BorderStroke(2.dp, Color.LightGray)
    ) {
        // A simple representation of a screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp))
        )
    }
}

@Composable
fun AccuracyAndLocationStatus(sensorAccuracy: Int, location: Pair<Double, Double>?) {
    val locationText = if (location == null) "Acquiring location..." else ""
    if (locationText.isNotEmpty()) {
        Text(
            text = locationText,
            color = Color.Yellow,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp)
        )
    }
}
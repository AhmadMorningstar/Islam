package com.AhmadMorningstar.islam

import android.content.Context
import android.graphics.Paint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.hardware.SensorManager
import androidx.compose.material3.Button
import kotlin.math.abs
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// ACTIVITY HELPER
// ---------------------------------------------------------------------------

fun Context.findActivity(): MainActivity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is MainActivity) return context
        context = context.baseContext
    }
    return null
}

// ---------------------------------------------------------------------------
// LOCATION OVERLAY
// ---------------------------------------------------------------------------

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
                text = if (isGpsOff) "To find the Qibla, your phone's GPS must be on."
                else "The app needs permission to know where you are.",
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

// ---------------------------------------------------------------------------
// UTILITY
// ---------------------------------------------------------------------------

fun shortestAngle(from: Float, to: Float): Float {
    var diff = to - from
    while (diff < -180) diff += 360
    while (diff > 180) diff -= 360
    return diff
}

// ---------------------------------------------------------------------------
// MAIN QIBLA COMPASS SCREEN
// ---------------------------------------------------------------------------

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

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val sunAngle = activity?.sunAzimuthState?.value?.toFloat()
    val moonAngle = activity?.moonAzimuthState?.value?.toFloat()
    val themePrefs = remember { ThemePreferences(context) }

    var hasVibrated by remember { mutableStateOf(false) }
    val vibStrength = themePrefs.getVibStrength()
    val vibSpeed = themePrefs.getVibSpeed()

    val qiblaAngle = qiblaDirection?.toFloat() ?: 0f
    val angleDifference = shortestAngle(northDirection, qiblaAngle)
    val isAligned = qiblaDirection != null && abs(angleDifference) < 3

    LaunchedEffect(isAligned) {
        if (isAligned && !hasVibrated && themePrefs.isVibrationEnabled()) {
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
                        android.os.VibrationEffect.createWaveform(timings, amplitudes, -1)
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
        // Radial glow behind compass
        Box(
            modifier = Modifier
                .size(400.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(currentAccentColor.copy(0.15f), Color.Transparent)
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

// ---------------------------------------------------------------------------
// COMPASS SUB-COMPONENTS
// ---------------------------------------------------------------------------

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
            color = theme.textColor.toArgb()
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
                drawCircle(
                    color = Color(0xFFFFD700),
                    radius = 8.dp.toPx(),
                    center = Offset(center.x, center.y - radius + 15.dp.toPx())
                )
            }
        }

        moonAngle?.let { angle ->
            rotate(angle) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = 10.dp.toPx(),
                    center = Offset(center.x, center.y - radius + 15.dp.toPx())
                )
                drawCircle(
                    color = Color(0xFFE0E0E0),
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
fun FixedTopNeedle(accentColor: Color, theme: CompassTheme) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val path = Path().apply {
            moveTo(center.x, 15.dp.toPx())
            lineTo(center.x - 12.dp.toPx(), 45.dp.toPx())
            lineTo(center.x + 12.dp.toPx(), 45.dp.toPx())
            close()
        }
        drawPath(path, color = accentColor)
        drawCircle(theme.backgroundColor, radius = 6f, center = center)
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
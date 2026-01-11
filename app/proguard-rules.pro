# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 1. Keep App Check so Firebase can still find it
-keep class com.google.firebase.appcheck.** { *; }

# 2. Keep your BuildConfig (Very important for your DEBUG checks!)
-keep class com.AhmadMorningstar.islam.BuildConfig { *; }

# 3. Protect your Sensor code (Compass)
-keepclassmembers class * extends android.hardware.SensorEventListener {
    public void onSensorChanged(android.hardware.SensorEvent);
    public void onAccuracyChanged(android.hardware.Sensor, int);
}

# 4. Keep Jetpack Compose from crashing
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# 5. Keep Google Play Services (Location & Updates)
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.play.core.** { *; }

# 6. Keep Firebase and App Check internal models
-keep class com.google.firebase.** { *; }

# 7. Preserve Annotations and Signatures for Kotlin/Compose
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
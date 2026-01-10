plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.AhmadMorningstar.islam"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.AhmadMorningstar.islam"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildFeatures {
            compose = true
        }

    }

    buildTypes {
        release {
            ndk {
                debugSymbolLevel = "FULL" // generates native debug symbols
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {

    // Core Android KTX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat) // For compatibility
    implementation(libs.material) // Material Components for Views (if you use any XML layouts)

    // Jetpack Compose
    // Using the latest versions you have declared
    implementation(libs.activity.compose) // This is likely the newer version (e.g., 1.11.0)
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.ui.tooling) // Correct debug implementation for tooling

    // Lifecycle for Compose
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Location & Maps
    // Use the single, newer version of the location library
    implementation(libs.gms.play.services.location) // This is likely the newer version (e.g., 21.3.0)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Latest In-App Update
    implementation(libs.app.update)
    implementation(libs.play.app.update.ktx)

    // Play Integrity API for Authenticity Checks
    implementation(libs.integrity)
}
/*
dependencies {

    // Jetpack Compose core
    implementation(libs.androidx.activity.compose.v192)
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling.v175)

    // Lifecycle for recomposition
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Location
    implementation(libs.gms.play.services.location)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location.v2101)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Jetpack Compose
    implementation(libs.activity.compose)
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.ui.tooling)
}
*/
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.AhmadMorningstar.islam"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.AhmadMorningstar.islam"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {

        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))

    // TODO: Add the dependencies for Firebase products you want to use
    // When using the BoM, don't specify versions in Firebase dependencies
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")


    // Add the dependencies for any other desired Firebase products
    // https://firebase.google.com/docs/android/setup#available-libraries

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
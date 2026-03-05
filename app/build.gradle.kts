plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.AhmadMorningstar.islam"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.AhmadMorningstar.islam"
        minSdk = 23
        targetSdk = 36
        versionCode = 9
        versionName = "9.0"

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildToolsVersion = "36.0.0"
    ndkVersion = "29.0.14206865"

    androidResources {
        // Corrected: added '=' and used a pattern to target the specific folder
        ignoreAssetsPattern = "audio"
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Import Firebase BoM via version catalog
    implementation(platform(libs.firebase.bom))

    // Firebase Analytics with exclusions
    implementation(libs.firebase.analytics) {
        exclude(group = "com.google.android.gms", module = "play-services-ads")
        exclude(group = "com.google.android.gms", module = "play-services-basement")
    }
    // App Check Play Integrity
    implementation(libs.firebase.appcheck.playintegrity)

    implementation("io.github.cosinekitty:astronomy:2.1.19")

    implementation("androidx.compose.material:material-icons-core")


    // Add the dependencies for any other desired Firebase products
    // https://firebase.google.com/docs/android/setup#available-libraries

    // Core Android KTX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Jetpack Compose
    implementation(libs.activity.compose)
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.text)
    debugImplementation(libs.androidx.compose.ui.ui.tooling)

    // Lifecycle for Compose
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Location & Maps
    // Use the single, newer version of the location library
    implementation(libs.gms.play.services.location)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)

    // Library for Athan prayer times
    implementation(libs.adhan)

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

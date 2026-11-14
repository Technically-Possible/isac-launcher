import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.isac.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.isac.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        // The map library ships native code; keep only the phone (arm64) and emulator (x86_64) builds.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")
    // Exploration map. Tiles from OpenFreeMap (OpenStreetMap data), no account or key needed.
    implementation("org.maplibre.gl:android-sdk:12.3.1")
    // Offline voice commands (Vosk). The phone has no speech recogniser, so ISAC brings its own.
    // 0.3.70+ is built for 16 KB memory pages (Android 15+ on newer Pixels).
    implementation("com.alphacephei:vosk-android:0.3.70@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}

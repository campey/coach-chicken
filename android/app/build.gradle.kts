plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi") // headless screenshots of the screens: ./gradlew :app:testDebugUnitTest, images in app/build/reports/paparazzi
}

// Release signing comes from the environment (CI decodes a keystore secret). Without it the release build falls back
// to the debug key, which is fine for a local build but means updates across machines need an uninstall first.
val releaseKeystore: String? = System.getenv("RELEASE_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.coachchicken"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.coachchicken"
        minSdk = 26
        targetSdk = 35
        // CI passes these from the git tag (-PversionName=0.3.0 -PversionCode=<run number>)
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 3
        versionName = (project.findProperty("versionName") as String?) ?: "0.3.0"
        // MediaPipe ships big native libs; arm64 covers every modern phone (no x86 emulator support).
        ndk { abiFilters += "arm64-v8a" }
    }

    androidResources { noCompress += "task" } // the pose model is memory-mapped

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // MediaPipe and CameraX use reflection; a 33 MB sideloaded APK is fine
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.mediapipe:tasks-vision:0.10.21")
}

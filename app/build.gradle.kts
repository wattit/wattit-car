plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Server address. Override with `-PwattitApiBaseUrl=https://...` or in ~/.gradle/gradle.properties.
val apiBaseUrl: String = (findProperty("wattitApiBaseUrl") as String?) ?: "https://kinetic-ev.duckdns.org"

// Release shrinking (R8, shrink only: no obfuscation or optimization). `-PwattitMinify=false` turns it off.
val minifyRelease: Boolean = (findProperty("wattitMinify") as String?)?.toBooleanStrict() ?: true

// Release signing key lives outside the repository. Without these variables the release build is unsigned
// and debug builds are unaffected.
val releaseKeystore: String? = System.getenv("WATTIT_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "app.wattit.car"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "app.wattit.car"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")

        // Real cars run arm64; the Pleos Connect emulator image (API 34) is arm64 or x86_64 depending on the host.
        // 32-bit and MIPS copies of the SDK's native libraries are left out.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("WATTIT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WATTIT_KEY_ALIAS") ?: "wattit"
                keyPassword = System.getenv("WATTIT_KEY_PASSWORD") ?: System.getenv("WATTIT_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = minifyRelease
            isShrinkResources = minifyRelease
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Store native libraries compressed in the APK (the SDK's libuacore is 3.5-4 MB per ABI and compresses to
    // about half); they are extracted at install time.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Android Automotive standard Car API (CarPropertyManager) for battery capacity.
    useLibrary("android.car")

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Pleos Connect SDK
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

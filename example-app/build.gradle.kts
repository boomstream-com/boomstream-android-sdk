import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Read from local.properties (local builds) or CI environment variables.
// Never commit actual key values — local.properties is git-ignored.
val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use(props::load)
}
val boomstreamApiKey: String = System.getenv("BOOMSTREAM_API_KEY")
    ?: localProps["BOOMSTREAM_API_KEY"]?.toString()
    ?: ""
val boomstreamApiUrl: String = System.getenv("BOOMSTREAM_API_URL")
    ?: localProps["BOOMSTREAM_API_URL"]?.toString()
    ?: "https://boomstream.com/"
val boomstreamConfigUrl: String = System.getenv("BOOMSTREAM_CONFIG_URL")
    ?: localProps["BOOMSTREAM_CONFIG_URL"]?.toString()
    ?: "https://play.boomstream.com/"
val allowedUaKeyToPlayAes: String = System.getenv("ALLOWED_UA_KEY_TO_PLAY_AES")
    ?: localProps["ALLOWED_UA_KEY_TO_PLAY_AES"]?.toString()
    ?: ""

android {
    namespace = "com.boomstream.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.boomstream.example"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "BOOMSTREAM_API_KEY", "\"$boomstreamApiKey\"")
        buildConfigField("String", "BOOMSTREAM_API_URL", "\"$boomstreamApiUrl\"")
        buildConfigField("String", "BOOMSTREAM_CONFIG_URL", "\"$boomstreamConfigUrl\"")
        buildConfigField("String", "ALLOWED_UA_KEY_TO_PLAY_AES", "\"$allowedUaKeyToPlayAes\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Demo-only: use debug signing so CI produces an installable APK without a
            // production keystore. This module is never published to the Play Store.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
        buildConfig = true
        compose = true
    }

    lint {
        // lintVitalRelease hangs under the CI runner's Kotlin-1.9 lint tool when analysing
        // modules compiled with Kotlin 2.1 metadata. Disabled for example-app only —
        // SDK module lint is exercised by the separate `lint` CI stage.
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":api-sdk"))
    implementation(project(":player-sdk"))
    implementation(project(":offline-sdk"))

    implementation(libs.android.core)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coil.compose)
}

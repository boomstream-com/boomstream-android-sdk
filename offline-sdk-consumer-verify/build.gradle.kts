/**
 * Synthetic compile-only consumer module.
 *
 * Verifies that offline-sdk exposes media3-datasource (SimpleCache) as api() so that
 * consumers depending only on offline-sdk — without player-sdk — can resolve SimpleCache
 * on their compile classpath.
 *
 * This module is intentionally minimal: no tests, no publishing, no flavors.
 * It must NOT depend on :player-sdk.
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.boomstream.verify.offline"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Only offline-sdk — no player-sdk. If SimpleCache is not on classpath, compilation fails.
    implementation(project(":offline-sdk"))
}

import java.util.Base64

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
}

android {
    namespace = "com.boomstream.sdk.player"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        val sdkVersion = providers.gradleProperty("boomstream.sdk.version").orElse("1.0.0-SNAPSHOT").get()
        buildConfigField("String", "SDK_VERSION", "\"$sdkVersion\"")
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // Opt in to AGP's auto-generated sources jar attached to components["release"].
    // Without this call AGP still produces releaseSourcesElements, but making the opt-in
    // explicit documents the intent and is the canonical AGP 8.x pattern. Manually adding
    // a second `androidSourcesJar` Jar task on top of this attaches a second artifact with
    // classifier=sources at the same path (build/libs/<module>-sources.jar) → the publication
    // becomes invalid with "multiple artifacts with identical extension and classifier".
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // api-sdk is api() so ConfigResponse, MediaData, BoomstreamConfigClient surface in our API.
    api(project(":api-sdk"))

    // Media3 — most are implementation(); ExoPlayer does NOT appear in our public API (CSO constraint #1).
    // media3-datasource is api() because BoomstreamOfflineCache.provideCache() returns SimpleCache,
    // which lives in media3-datasource. Consumers wiring offline caches need SimpleCache on classpath.
    api(libs.media3.datasource)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)

    // Compose (version resolved via BOM)
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle (LocalLifecycleOwner, collectAsStateWithLifecycle)
    implementation(libs.lifecycle.runtime.compose)

    // Image loading for poster fallback — both View-based (ImageView.load) and Compose (AsyncImage)
    implementation(libs.coil)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}

// Option B (release-only publishing, per-module guard):
// Only the "release" component is registered for publishing.
// The "debug" component is intentionally NOT registered — debug AAR ships without
// certificate pinning and must never reach the remote Maven registry.
// Root build.gradle.kts carries the Option A taskGraph guard as additional defense-in-depth.
afterEvaluate {
    // Dokka V2 (V2EnabledWithHelpers): dokkaGenerateModuleHtml produces per-module HTML
    // docs in build/dokka-module/html/. Bundled as -javadoc.jar per Maven Central requirement
    // (an empty javadoc.jar — just MANIFEST.MF — is silently produced if the source path is
    // wrong, then rejected by Sonatype on the publish-central tag flow).
    val dokkaJar by tasks.registering(Jar::class) {
        archiveClassifier.set("javadoc")
        dependsOn("dokkaGenerateModuleHtml")
        from(layout.buildDirectory.dir("dokka-module/html"))
    }

    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "com.boomstream"
                artifactId = "player-sdk"
                version = providers.gradleProperty("boomstream.sdk.version").orElse("1.0.0-SNAPSHOT").get()
                // components["release"] already carries the AAR + the AGP-generated sources jar
                // (enabled by `android { publishing { singleVariant("release") { withSourcesJar() } } }`
                // above). Only the dokka-backed javadoc jar needs to be attached separately.
                from(components["release"])
                artifact(dokkaJar)

                pom {
                    name.set("Boomstream Player SDK")
                    description.set("Media3/ExoPlayer Composable + View player with Boomstream config integration")
                    url.set("https://github.com/boomstream-com/boomstream-android-sdk")
                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("boomstream")
                            name.set("Boomstream")
                            url.set("https://boomstream.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/boomstream-com/boomstream-android-sdk.git")
                        developerConnection.set("scm:git:ssh://github.com/boomstream-com/boomstream-android-sdk.git")
                        url.set("https://github.com/boomstream-com/boomstream-android-sdk")
                    }
                }
            }
            // debug publication deliberately absent — see comment above
        }

        repositories {
            maven {
                name = "GitLabMaven"
                // CI_JOB_TOKEN has write_package_registry on the project automatically.
                url = uri(System.getenv("GITLAB_MAVEN_REPO_URL")
                    ?: "")
                credentials(HttpHeaderCredentials::class) {
                    name = "Job-Token"
                    value = System.getenv("CI_JOB_TOKEN") ?: ""
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
            }
            // Local staging dir: CI zips this tree and uploads to Sonatype Central Portal API.
            // All three modules write to the same root-level path so one bundle covers all artifacts.
            maven {
                name = "staging"
                url = uri("${rootDir}/build/staging-deploy")
            }
        }
    }

    signing {
        // Sign release artifacts via the gpg binary (useGpgCmd) instead of in-memory PGP keys.
        // The in-memory path uses Bouncy Castle, which rejects some valid GnuPG-exported keys
        // ("Could not read PGP secret key"). The release pipeline imports the key into the gpg
        // keyring beforehand and passes signing.gnupg.keyName / .passphrase as -P properties.
        // Active only on tag builds (release); branch builds (snapshot) are not signed.
        val isTagBuild = System.getenv("CI_COMMIT_TAG")?.isNotEmpty() == true
        if (isTagBuild) {
            useGpgCmd()
            sign(publishing.publications["release"])
        }
    }

}

dokka {
    moduleName.set("player-sdk")
}

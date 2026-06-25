import java.util.Base64

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
}

android {
    namespace = "com.boomstream.sdk.api"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        // Consumers set BOOMSTREAM_API_KEY in local.properties or CI env.
        // Never hardcode the key value in source — see BoomstreamConfig.
        buildConfigField(
            "String", "BOOMSTREAM_API_KEY",
            "\"${project.findProperty("boomstream.api.key") ?: System.getenv("BOOMSTREAM_API_KEY") ?: ""}\""
        )

        consumerProguardFiles("consumer-rules.pro")
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
    api(libs.kotlin.stdlib)
    api(libs.coroutines.core)
    api(libs.serialization.json)
    api(libs.retrofit)
    // okhttp is api() because Interceptor appears in BoomstreamOptions.additionalInterceptors
    api(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.retrofit.kotlinx.json)
    implementation(libs.android.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockito.core)
}

// Release-only publishing (Option B per-module guard):
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
                artifactId = "api-sdk"
                version = providers.gradleProperty("boomstream.sdk.version").orElse("1.0.0-SNAPSHOT").get()
                // components["release"] already carries the AAR + the AGP-generated sources jar
                // (enabled by `android { publishing { singleVariant("release") { withSourcesJar() } } }`
                // above). Only the dokka-backed javadoc jar needs to be attached separately.
                from(components["release"])
                artifact(dokkaJar)

                pom {
                    name.set("Boomstream API SDK")
                    description.set("Type-safe Kotlin client for the Boomstream API and config endpoint")
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
                // No deploy token required. Set GITLAB_MAVEN_REPO_URL in CI variables
                // to the project-scoped URL: .../projects/<PROJECT_ID>/packages/maven
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
        val raw = System.getenv("SIGNING_KEY").orEmpty().trim()
        // Support both plain ASCII-armored and base64-encoded keys. CI variables often strip
        // newlines from multi-line PGP blocks; storing as base64 (single line) avoids that.
        // getMimeDecoder is tolerant of embedded whitespace/linebreaks in the base64 payload.
        val signingKey = when {
            raw.startsWith("-----BEGIN") -> raw
            raw.isNotEmpty() -> String(Base64.getMimeDecoder().decode(raw))
            else -> ""
        }
        val signingPassword = System.getenv("SIGNING_PASSWORD") ?: ""
        val signingKeyId = System.getenv("SIGNING_KEY_ID") ?: ""
        // Signing is only active on tag builds (publish-central). CI_COMMIT_TAG is null on
        // branch builds (develop publish-snapshot), preventing signReleasePublication from
        // being created even when the protected SIGNING_KEY variable is visible.
        val isTagBuild = System.getenv("CI_COMMIT_TAG")?.isNotEmpty() == true
        if (isTagBuild && signingKey.isNotEmpty()) {
            useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
            sign(publishing.publications["release"])
        }
    }

}

dokka {
    moduleName.set("api-sdk")
}

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.dokka) apply false
}

// Enable Gradle dependency locking so CI can generate lockfiles for dependency scanning
// without committing them. Generated fresh per pipeline run via `./gradlew :api-sdk:dependencies
// --write-locks` (see sca-osv job in .gitlab-ci.yml).
// Regular build/test jobs are unaffected — lock verification is not activated.
subprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

// Hard guard — refuse non-Release publish (CSO security constraint #3).
//
// Threat: debug AAR ships without certificate pinning. If a debug-variant
// publication task enters the task graph (e.g. misconfigured target, generic publish-all
// added by a new team member), this guard aborts the build with an explicit error.
//
// Architecture: Belt + Suspenders
//   Belt   (Option A, here): taskGraph.whenReady blocks any Debug publish task at graph-eval time.
//   Suspenders (Option B, per-module): each SDK module only registers the "release" component
//              in its publishing {} block, so debug publication tasks are never created.
gradle.taskGraph.whenReady {
    val publishTasks = allTasks.filter { task ->
        task.name.startsWith("publish") && !task.name.contains("ToMavenLocal")
    }
    if (publishTasks.isNotEmpty()) {
        val debugPublish = publishTasks.find { task ->
            task.name.contains("Debug", ignoreCase = true) &&
                !task.name.contains("Release", ignoreCase = true)
        }
        require(debugPublish == null) {
            "Refusing to publish non-Release artifact (task: ${debugPublish?.name}). " +
                "Debug builds ship without certificate pinning " +
                "— never publish debug AAR to remote registry."
        }
    }
}

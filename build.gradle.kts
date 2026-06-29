plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.dokka) apply false
}

// Enable Gradle dependency locking for CI SCA scanning.
// lockAllConfigurations() captures the full dep graph in generated lockfiles.
// CI (sca-osv job in .gitlab-ci.yml) then SPLITS the lockfile into two scoped files:
//
//   gradle-runtime.lockfile  — releaseRuntimeClasspath only
//                              → gate scan (HIGH/CRITICAL blocks release/*)
//   gradle-tooling.lockfile  — Dokka build-time classpath (jackson/woodstox/etc.)
//                              → informational scan (warn-only, never blocks)
//
// This ensures Dokka's CVEs are visible in the CI report without blocking releases;
// they are NOT in published AAR artifacts (confirmed: `dependencyInsight
// --configuration releaseRuntimeClasspath --dependency jackson` → "No dependencies
// matching"). Lock verification is NOT activated for normal build/test tasks — CI
// passes --write-locks explicitly, transparent to developers.
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

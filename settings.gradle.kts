pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "boomstream-android-sdk"

include(":api-sdk")
include(":player-sdk")
include(":offline-sdk")
include(":offline-sdk-consumer-verify")
include(":example-app")

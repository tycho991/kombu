pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kombu"

// Pure Kotlin/JVM modules (no Android dependency)
include(":ingredient-parser")
include(":duration-parser")
include(":recipe-import")
include(":cookbook-client")
include(":nextcloud-auth")

// Android modules
include(":data")
include(":sync")

// Build tooling (custom static-analysis rules)
include(":detekt-rules")
include(":lint-rules")

// App
include(":app")

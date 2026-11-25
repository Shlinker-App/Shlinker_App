pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        // CI sets MAVEN_CENTRAL_MIRROR to avoid Maven Central rate limits (see docs/maintainers/repo-workflow.md).
        System.getenv("MAVEN_CENTRAL_MIRROR")?.takeIf { it.isNotBlank() }?.let { maven(it) } ?: mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // CI sets MAVEN_CENTRAL_MIRROR to avoid Maven Central rate limits (see docs/maintainers/repo-workflow.md).
        System.getenv("MAVEN_CENTRAL_MIRROR")?.takeIf { it.isNotBlank() }?.let { maven(it) } ?: mavenCentral()
    }
}

rootProject.name = "Shlinker"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:model")
include(":core:ui")
include(":core:network")
include(":core:data")
include(":core:datastore")
include(":feature:servers")
include(":feature:shorturls")

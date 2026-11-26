dependencyResolutionManagement {
    repositories {
        google()
        // CI sets MAVEN_CENTRAL_MIRROR to avoid Maven Central rate limits (see docs/maintainers/repo-workflow.md).
        // The mirror lags behind, so Maven Central still serves artifacts it does not have yet.
        System.getenv("MAVEN_CENTRAL_MIRROR")?.takeIf { it.isNotBlank() }?.let { maven(it) }
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")

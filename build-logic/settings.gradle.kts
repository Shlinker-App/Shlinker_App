dependencyResolutionManagement {
    repositories {
        google()
        // CI sets MAVEN_CENTRAL_MIRROR to avoid Maven Central rate limits (see docs/maintainers/repo-workflow.md).
        System.getenv("MAVEN_CENTRAL_MIRROR")?.takeIf { it.isNotBlank() }?.let { maven(it) } ?: mavenCentral()
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

plugins {
    `kotlin-dsl`
}

group = "app.shlinker.buildlogic"

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.hilt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.shlinker.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.shlinker.android.library.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.shlinker.android.compose.get().pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = libs.plugins.shlinker.hilt.get().pluginId
            implementationClass = "HiltConventionPlugin"
        }
    }
}

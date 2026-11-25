import java.util.Properties

plugins {
    alias(libs.plugins.shlinker.android.application)
    alias(libs.plugins.shlinker.android.compose)
    alias(libs.plugins.shlinker.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Configure signing per channel via untracked signing/<channel>.properties or CI
// environment variables with the SHLINKER_<CHANNEL>_ prefix. Without a key, the release APK is unsigned.
val channels = listOf("devs", "nightly", "stable")

fun signingProperties(channel: String): Properties? {
    val file = rootProject.file("signing/$channel.properties")
    if (file.exists()) return Properties().apply { file.inputStream().use(::load) }
    val prefix = "SHLINKER_${channel.uppercase()}_"
    val storeFile = System.getenv("${prefix}STORE_FILE") ?: return null
    return Properties().apply {
        setProperty("storeFile", storeFile)
        setProperty("storePassword", System.getenv("${prefix}STORE_PASSWORD").orEmpty())
        setProperty("keyAlias", System.getenv("${prefix}KEY_ALIAS").orEmpty())
        setProperty("keyPassword", System.getenv("${prefix}KEY_PASSWORD").orEmpty())
    }
}

android {
    namespace = "app.shlinker"

    defaultConfig {
        applicationId = "app.shlinker"
        versionCode = 25
        versionName = "0.3.1"
    }

    signingConfigs {
        channels.forEach { channel ->
            val props = signingProperties(channel) ?: return@forEach
            create(channel) {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    flavorDimensions += "channel"
    productFlavors {
        create("devs") {
            dimension = "channel"
            signingConfigs.findByName("devs")?.let { signingConfig = it }
            applicationIdSuffix = ".devs"
            versionNameSuffix = "-devs"
        }
        create("nightly") {
            dimension = "channel"
            signingConfigs.findByName("nightly")?.let { signingConfig = it }
            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-nightly"
        }
        create("stable") {
            dimension = "channel"
            signingConfigs.findByName("stable")?.let { signingConfig = it }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                output.versionName.zip(output.versionCode) { name, code ->
                    "shlinker-$name-vc$code.apk"
                },
            )
        }
    }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.data)
    implementation(projects.feature.servers)
    implementation(projects.feature.shorturls)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.reorderable)
    testImplementation(libs.junit)
    testImplementation(projects.core.datastore)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

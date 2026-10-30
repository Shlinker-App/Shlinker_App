plugins {
    alias(libs.plugins.shlinker.android.library)
    alias(libs.plugins.shlinker.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "at.awit.shlinker.core.network"
}

dependencies {
    implementation(projects.core.model)
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}

plugins {
    alias(libs.plugins.linkwerk.android.library)
    alias(libs.plugins.linkwerk.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "at.awit.linkwerk.core.network"
}

dependencies {
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}

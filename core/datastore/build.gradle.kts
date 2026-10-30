plugins {
    alias(libs.plugins.shlinker.android.library)
    alias(libs.plugins.shlinker.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "at.awit.shlinker.core.datastore"
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

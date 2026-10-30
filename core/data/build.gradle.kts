plugins {
    alias(libs.plugins.shlinker.android.library)
    alias(libs.plugins.shlinker.hilt)
}

android {
    namespace = "at.awit.shlinker.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.network)
    implementation(projects.core.datastore)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

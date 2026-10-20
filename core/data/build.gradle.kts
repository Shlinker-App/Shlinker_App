plugins {
    alias(libs.plugins.linkwerk.android.library)
    alias(libs.plugins.linkwerk.hilt)
}

android {
    namespace = "at.awit.linkwerk.core.data"
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

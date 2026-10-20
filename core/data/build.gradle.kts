plugins {
    alias(libs.plugins.linkwerk.android.library)
    alias(libs.plugins.linkwerk.hilt)
}

android {
    namespace = "at.awit.linkwerk.core.data"
}

dependencies {
    api(projects.core.network)
    implementation(projects.core.datastore)
    implementation(libs.kotlinx.coroutines.android)
}

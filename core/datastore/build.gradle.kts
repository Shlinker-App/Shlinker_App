plugins {
    alias(libs.plugins.linkwerk.android.library)
    alias(libs.plugins.linkwerk.hilt)
}

android {
    namespace = "at.awit.linkwerk.core.datastore"
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
}

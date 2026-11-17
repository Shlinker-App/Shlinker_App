plugins {
    alias(libs.plugins.shlinker.android.library)
    alias(libs.plugins.shlinker.android.compose)
}

android {
    namespace = "app.shlinker.core.ui"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
}

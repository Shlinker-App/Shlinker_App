plugins {
    alias(libs.plugins.shlinker.android.library)
}

android {
    namespace = "app.shlinker.core.model"
}

dependencies {
    testImplementation(libs.junit)
}

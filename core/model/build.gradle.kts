plugins {
    alias(libs.plugins.shlinker.android.library)
}

android {
    namespace = "at.awit.shlinker.core.model"
}

dependencies {
    testImplementation(libs.junit)
}

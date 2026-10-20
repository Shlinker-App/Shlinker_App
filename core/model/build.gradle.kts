plugins {
    alias(libs.plugins.linkwerk.android.library)
}

android {
    namespace = "at.awit.linkwerk.core.model"
}

dependencies {
    testImplementation(libs.junit)
}

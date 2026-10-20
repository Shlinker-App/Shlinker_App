plugins {
    alias(libs.plugins.linkwerk.android.library)
    alias(libs.plugins.linkwerk.android.compose)
    alias(libs.plugins.linkwerk.hilt)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "at.awit.linkwerk.feature.servers"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.data)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal fun Project.configureKotlinAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig.minSdk = libs.version("minSdk").toInt()
        defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        compileOptions.sourceCompatibility = JavaVersion.VERSION_21
        compileOptions.targetCompatibility = JavaVersion.VERSION_21
        // Robolectric downloads its Android framework jars itself, bypassing Gradle's repositories.
        providers.environmentVariable("MAVEN_CENTRAL_MIRROR").orNull?.takeIf { it.isNotBlank() }?.let { mirror ->
            testOptions.unitTests.all { it.systemProperty("robolectric.dependency.repo.url", mirror) }
        }
    }

    // AGP 9 has built-in Kotlin support; no separate kotlin-android plugin is needed.
    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            allWarningsAsErrors.set(false)
        }
    }
}

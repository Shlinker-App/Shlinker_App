package app.shlinker.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.shlinker.core.ui.theme.ShlinkerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp-xxhdpi")
class AboutScreenTest {
    @get:Rule val compose = createComposeRule()
    private val opened = mutableListOf<String>()
    private var back = false

    private fun screen(devsBuild: Boolean) {
        compose.setContent {
            ShlinkerTheme(dynamicColor = false) {
                AboutScreen("0.3.2-devs", 26, devsBuild, onBack = { back = true }, onOpenLink = { opened += it })
            }
        }
    }

    @Test fun showsVersionAndOpensForgejoIssueAndLicenseLinks() {
        screen(devsBuild = false)
        compose.onNodeWithText("Version 0.3.2-devs (26)").assertIsDisplayed()
        compose.onNodeWithText("DEVS build").assertDoesNotExist()
        compose.onNodeWithText("Source code on Forgejo").performScrollTo().performClick()
        compose.onNodeWithText("Report a bug or request a feature").performScrollTo().performClick()
        compose.onNodeWithText("License: GPL-3.0").performScrollTo().performClick()
        assertEquals(listOf(REPOSITORY_URL, ISSUES_URL, LICENSE_URL), opened)
        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(back)
    }

    @Test fun devsBuildShowsTheConstructionSiteWarning() {
        screen(devsBuild = true)
        compose.onNodeWithText("DEVS build").assertIsDisplayed()
        compose.onNodeWithText(
            "Handle this version with care. No sudden movements, and don’t forget your hard hat.",
        ).assertIsDisplayed()
    }
}

package app.shlinker

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.shlinker.core.ui.theme.ShlinkerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class DevsBuildBannerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyDevsBuildsGetALabelWithTheVersion() {
        assertEquals("DEVS build v0.3.2", devsBuildLabel("devs", "0.3.2-devs"))
        assertNull(devsBuildLabel("nightly", "0.3.2-nightly"))
        assertNull(devsBuildLabel("stable", "0.3.2"))
    }

    @Test fun bannerSitsBelowTheScreenContent() {
        compose.setContent {
            ShlinkerTheme(dynamicColor = false) {
                DevsBuildFrame("DEVS build v0.3.2") { Text("Screen") }
            }
        }
        val banner = compose.onNodeWithText("DEVS build v0.3.2").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val screen = compose.onNodeWithText("Screen").fetchSemanticsNode().boundsInRoot
        assertTrue(screen.bottom <= banner.top)
    }

    @Test fun otherChannelsShowOnlyTheContent() {
        compose.setContent {
            ShlinkerTheme(dynamicColor = false) {
                DevsBuildFrame(null) { Text("Screen") }
            }
        }
        compose.onNodeWithText("Screen").assertIsDisplayed()
        compose.onNodeWithText("DEVS build", substring = true).assertDoesNotExist()
    }
}

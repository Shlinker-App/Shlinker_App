package at.awit.shlinker.feature.shorturls

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import at.awit.shlinker.core.ui.theme.ShlinkerTheme
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
class ShlinkQrDialogTest {
    @get:Rule val compose = createComposeRule()
    private val url = "https://custom.example/Grüße?x=%26&case=AbC"
    private var closed = false

    private fun screen(value: String = url, dark: Boolean = false) {
        compose.setContent {
            ShlinkerTheme(darkTheme = dark, dynamicColor = false) {
                ShlinkQrDialog(value) { closed = true }
            }
        }
    }

    private fun assertRenderedCodeDecodes() {
        val label = "QR code for $url"
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        }
        val bitmap = compose.onNodeWithContentDescription(label).performScrollTo()
            .assertIsDisplayed().captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertEquals(url, decodeQrPixels(bitmap.width, bitmap.height, pixels))
        compose.onNodeWithText("Close").assertIsDisplayed().performClick()
        assertTrue(closed)
    }

    @Test fun displayedQrIsScannableAndCloseRemainsAccessible() {
        screen()
        assertRenderedCodeDecodes()
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp-mdpi")
    fun narrowPhoneDarkModeStillRendersBlackOnWhiteScannableCode() {
        screen(dark = true)
        assertRenderedCodeDecodes()
    }

    @Test
    @Config(qualifiers = "en-w891dp-h411dp-mdpi")
    fun phoneLandscapeKeepsCodeScrollableAndCloseVisible() {
        screen()
        assertRenderedCodeDecodes()
    }

    @Test
    @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun tabletLandscapeKeepsScannableDialogWithinWindow() {
        screen()
        assertRenderedCodeDecodes()
    }

    @Test
    @Config(qualifiers = "en-w891dp-h411dp-mdpi")
    fun veryDenseCodeInSmallWindowShowsResizeHintInsteadOfBlankOrClippedCode() {
        val longUrl = "https://s.example/" + "a".repeat(2200)
        assertTrue(generateShlinkQrCode(longUrl) is ShlinkQrCode.Ready)
        screen(longUrl)
        val message = "This window is too small to display the complete QR code. " +
            "Rotate your device or enlarge the window."
        compose.waitUntil(5_000) { compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("QR code for $longUrl").assertDoesNotExist()
        compose.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test fun invalidUrlShowsVisibleFailureWithoutFakeCode() {
        screen("not a URL")
        val message = "Cannot generate a QR code: the short URL is not a valid HTTP or HTTPS address."
        compose.waitUntil(5_000) { compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithContentDescription("QR code for not a URL").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()
        assertTrue(closed)
    }
}

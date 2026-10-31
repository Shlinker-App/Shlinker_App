package at.awit.shlinker.feature.shorturls

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.ui.theme.ShlinkerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-w411dp-h891dp-xxhdpi")
class CreateShortUrlScreenTest {
    @get:Rule val compose = createComposeRule()
    private var submitted: CreateShortUrlState? = null
    private var copied: String? = null
    private var back = false

    private fun screen(initial: CreateShortUrlState) {
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            ShlinkerTheme(dynamicColor = false) {
                CreateShortUrlScreen(
                    state,
                    { state = it },
                    { submitted = state },
                    {},
                    { back = true },
                    { copied = it },
                )
            }
        }
    }

    @Test fun formCapturesAllApprovedFieldsAndSubmits() {
        screen(CreateShortUrlState(serverName = "Home", loading = false))
        compose.onNodeWithText("Destination URL").performTextReplacement("https://example.org")
        compose.onNodeWithText("Title (optional)").performTextReplacement("Example")
        compose.onNodeWithText("Custom slug (optional)").performScrollTo().performTextReplacement("slug")
        compose.onNodeWithText("Domain (optional)").performScrollTo().performTextReplacement("s.example")
        compose.onNodeWithText("Tags (optional)").performScrollTo().performTextReplacement("one, two")
        compose.onNodeWithText("Create").performScrollTo().performClick()
        assertEquals("https://example.org", submitted?.longUrl)
        assertEquals("Example", submitted?.title)
        assertEquals("slug", submitted?.slug)
        assertEquals("s.example", submitted?.domain)
        assertEquals("one, two", submitted?.tags)
    }

    @Test fun successOffersCopyAndDoneWithoutAnotherCreateButton() {
        screen(
            CreateShortUrlState(
                serverName = "Home",
                loading = false,
                created = ShortUrl("abc", "https://s.example/abc", "https://example.org", null, emptyList()),
            ),
        )
        compose.onNodeWithText("Shlink created").assertIsDisplayed()
        compose.onNodeWithText("Create").assertDoesNotExist()
        compose.onNodeWithText("Copy shlink").performClick()
        assertEquals("https://s.example/abc", copied)
        compose.onNodeWithText("Done").performClick()
        assertEquals(true, back)
    }

    @Test fun inFlightRequestDisablesSubmitAndBack() {
        screen(CreateShortUrlState(serverName = "Home", loading = false, longUrl = "https://example.org", busy = true))
        compose.onNodeWithText("Create").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Back to shlinks").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Destination URL").performScrollTo().assertIsNotEnabled()
    }

    @Test fun uncertainResultWarnsUserToCheckRefreshedList() {
        screen(
            CreateShortUrlState(
                serverName = "Home",
                loading = false,
                longUrl = "https://example.org",
                error = CreateError.UNCERTAIN,
            ),
        )
        compose.onNodeWithText(
            "The result could not be confirmed. The shlink may already have been created. Go back and check the refreshed list before trying again.",
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Create").performScrollTo().assertIsNotEnabled()
    }
}

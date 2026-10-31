package at.awit.shlinker.feature.shorturls

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.model.ShortUrlDetails
import at.awit.shlinker.core.network.ConnectionError
import at.awit.shlinker.core.network.ShortUrlRequestException
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
class ShortUrlDetailsScreenTest {
    @get:Rule val compose = createComposeRule()
    private var refreshed = false
    private var back = false
    private val full = ShortUrlDetails(
        url = ShortUrl(
            "abc",
            "https://custom.example/abc",
            "https://example.org/long",
            "Example title",
            listOf("one", "two"),
            visits = 12345678901L,
            domain = "custom.example",
        ),
        dateCreated = "2026-10-31T12:00:00+02:00",
        validSince = "2026-11-01T10:00:00Z",
        validUntil = "2026-12-01T10:00:00Z",
        maxVisits = 99999999999L,
        metaAvailable = true,
        crawlable = false,
        forwardQuery = true,
        hasRedirectRules = true,
        nonBotVisits = 12345678900L,
        botVisits = 1L,
    )

    private fun screen(state: ShortUrlDetailsState) {
        compose.setContent {
            ShlinkerTheme(dynamicColor = false) {
                ShortUrlDetailsScreen(state, { back = true }, { refreshed = true })
            }
        }
    }

    @Test fun fullReadOnlyLayoutIncludesEveryReturnedOptionAndExplainsCreationOnlyFields() {
        screen(ShortUrlDetailsState(serverName = "Home", loading = false, details = full))
        compose.onNodeWithText("Server: Home").assertIsDisplayed()
        for (text in listOf(
            "Example title", "https://custom.example/abc", "https://example.org/long",
            "abc", "custom.example", "one, two",
            "2026-10-31T12:00:00+02:00", "12345678901", "12345678900", "1",
            "2026-11-01T10:00:00Z", "2026-12-01T10:00:00Z", "99999999999",
            "Crawlable", "Forward query parameters", "Has redirect rules",
            "Current short code length", "3", "Use existing URL if found", "Creation-only option — not stored",
        )) {
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithContentDescription("Refresh").performClick()
        assertEquals(true, refreshed)
        compose.onNodeWithContentDescription("Back to shlinks").performClick()
        assertEquals(true, back)
    }

    @Test fun missingOptionalDataIsNotConfusedWithDisabledFlagsOrUnlimitedVisits() {
        screen(
            ShortUrlDetailsState(
                loading = false,
                details = ShortUrlDetails(full.url.copy(title = null, domain = null, visits = null)),
            ),
        )
        compose.onNodeWithText("Not set").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Default domain").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No limit").assertDoesNotExist()
        compose.onNodeWithText("No").assertDoesNotExist()
        compose.onAllNodesWithText("Not provided by server")[0].performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun tabletLandscapeCanReadAccessLimitsAndExplicitlyUnsetValues() {
        screen(
            ShortUrlDetailsState(
                serverName = "Home",
                loading = false,
                details = full.copy(validSince = null, validUntil = null, maxVisits = null),
            ),
        )
        compose.onNodeWithText("Access limits").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No limit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Behavior").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No").performScrollTo().assertIsDisplayed()
    }

    @Test fun notFoundShowsErrorAndRetryWithoutPresentingEmptyDetails() {
        screen(
            ShortUrlDetailsState(
                loading = false,
                error = ShortUrlRequestException(ConnectionError.HttpError(404, null)),
            ),
        )
        compose.onNodeWithText(
            "This shlink no longer exists or is not accessible for this domain.",
        ).assertIsDisplayed()
        compose.onNodeWithText("General").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(true, refreshed)
    }
}

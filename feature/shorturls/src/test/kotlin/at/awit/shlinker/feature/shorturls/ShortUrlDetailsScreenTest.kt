package at.awit.shlinker.feature.shorturls

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.model.ShortUrlDetails
import at.awit.shlinker.core.network.ConnectionError
import at.awit.shlinker.core.network.ShortUrlRequestException
import at.awit.shlinker.core.network.UpdateShortUrlException
import at.awit.shlinker.core.network.UpdateShortUrlFailure
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
    private var submitted: ShortUrlEditForm? = null
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
                ShortUrlDetailsScreen(state, { back = true }, { refreshed = true }, {}, {}, {}, {})
            }
        }
    }

    @Test fun detailLayoutIncludesReturnedFieldsAndEditButNoCreationOnlySection() {
        screen(ShortUrlDetailsState(serverName = "Home", loading = false, details = full))
        compose.onNodeWithText("Server: Home").assertIsDisplayed()
        for (text in listOf(
            "Example title", "https://custom.example/abc", "https://example.org/long",
            "abc", "custom.example", "one, two",
            "2026-10-31T12:00:00+02:00", "12345678901", "12345678900", "1",
            "2026-11-01T10:00:00Z", "2026-12-01T10:00:00Z", "99999999999",
            "Crawlable", "Forward query parameters", "Has redirect rules",
        )) {
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.onNodeWithText("Creation options").assertDoesNotExist()
        compose.onNodeWithText("Use existing URL if found").assertDoesNotExist()
        compose.onNodeWithText("Edit").performScrollTo().assertIsDisplayed()
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

    private fun editableScreen(saving: Boolean = false, uncertain: Boolean = false) {
        compose.setContent {
            var state by remember {
                mutableStateOf(
                    ShortUrlDetailsState(
                        loading = false,
                        details = full,
                        form = ShortUrlEditForm.from(full),
                        saving = saving,
                        needsReload = uncertain,
                        error = if (uncertain) UpdateShortUrlException(UpdateShortUrlFailure.UNCERTAIN) else null,
                    ),
                )
            }
            ShlinkerTheme(dynamicColor = false) {
                ShortUrlDetailsScreen(
                    state,
                    { back = true },
                    { refreshed = true },
                    {},
                    { state = state.copy(form = it) },
                    { submitted = state.form },
                    { state = state.copy(form = null) },
                )
            }
        }
    }

    @Test fun editModeUpdatesDestinationTitleTagsAndLimitsWithExplicitSave() {
        editableScreen()
        compose.onNodeWithText("Edit shlink").assertIsDisplayed()
        compose.onNodeWithText("Destination URL").performScrollTo().performTextReplacement("https://new.example")
        compose.onNodeWithText("Title (optional)").performScrollTo().performTextReplacement("New title")
        compose.onNodeWithText("Remove tag one").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove tag one").performScrollTo().performClick()
        compose.onNodeWithText("Tags (optional)").performScrollTo().performTextReplacement("three")
        compose.onNodeWithText("Tags (optional)").performImeAction()
        compose.onNodeWithText("Enabled until").performScrollTo().performTextReplacement("")
        compose.onNodeWithText("Maximum visits allowed").performScrollTo().performTextReplacement("")
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        assertEquals("https://new.example", submitted?.longUrl)
        assertEquals("New title", submitted?.title)
        assertEquals(listOf("two", "three"), submitted?.tags)
        assertEquals("", submitted?.validUntil)
        assertEquals("", submitted?.maxVisits)
        compose.onNodeWithText("Has redirect rules").assertDoesNotExist()
        compose.onNodeWithText("Domain (optional)").assertDoesNotExist()
        compose.onNodeWithText("Custom slug (optional)").assertDoesNotExist()
    }

    @Test fun cancelAsksBeforeDiscardAndReturnsToUnchangedDetailsWithoutSave() {
        editableScreen()
        compose.onNodeWithText("Title (optional)").performScrollTo().performTextReplacement("Draft")
        compose.onNodeWithText("Cancel editing").performScrollTo().performClick()
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Draft").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel editing").performClick()
        compose.onNodeWithText("Discard").performClick()
        compose.onNodeWithText("Edit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Example title").performScrollTo().assertIsDisplayed()
        assertEquals(null, submitted)
        assertEquals(false, back)
    }

    @Test fun savingDisablesSaveCancelRefreshAndBack() {
        editableScreen(saving = true)
        compose.onNodeWithContentDescription("Cancel editing").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Refresh").assertIsNotEnabled()
        compose.onNodeWithText("Destination URL").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Save changes").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Cancel editing").performScrollTo().assertIsNotEnabled()
    }

    @Test fun uncertainOutcomeRequiresReloadInsteadOfAnotherSave() {
        editableScreen(uncertain = true)
        compose.onNodeWithText("Save changes").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Reload details").performScrollTo().performClick()
        assertEquals(true, refreshed)
        assertEquals(null, submitted)
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

package at.awit.shlinker.feature.shorturls

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import at.awit.shlinker.core.model.VisitPeriod
import at.awit.shlinker.core.model.VisitStatistics
import at.awit.shlinker.core.model.window
import at.awit.shlinker.core.network.ConnectionError
import at.awit.shlinker.core.network.InvalidVisitStatisticsException
import at.awit.shlinker.core.network.ShortUrlRequestException
import at.awit.shlinker.core.ui.theme.ShlinkerTheme
import java.time.Instant
import java.time.ZoneId
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
class VisitStatisticsScreenTest {
    @get:Rule val compose = createComposeRule()
    private var selected: VisitPeriod? = null
    private var refreshes = 0
    private var back = false
    private val zone = ZoneId.of("Europe/Vienna")
    private val state = VisitStatisticsState(
        loading = false,
        serverName = "First",
        counts = VisitStatistics(12345678901, 12345678899),
        zone = zone,
        window = VisitPeriod.LAST_30_DAYS.window(Instant.parse("2026-10-31T04:30:00Z"), zone),
    )

    private fun screen(value: VisitStatisticsState = state, domain: String? = "custom.example") {
        compose.setContent {
            ShlinkerTheme(dynamicColor = false) {
                VisitStatisticsScreen(value, "same", domain, { back = true }, { selected = it }, { refreshes++ })
            }
        }
    }

    @Test fun countsIdentityWindowTimezoneAndPeriodSelectionAreExplicit() {
        screen()
        compose.onNodeWithText("Server: First").assertIsDisplayed()
        compose.onNodeWithText("Short code: same").assertIsDisplayed()
        compose.onNodeWithText("Domain: custom.example").assertIsDisplayed()
        compose.onNodeWithText("Last 30 days").assertIsSelected()
        compose.onNodeWithText(
            "From: 2026 / 10 / 02 00:00:00 CEST\nUntil: 2026 / 10 / 31 05:30:00 CET\nTime zone: Europe/Vienna",
        ).performScrollTo().assertIsDisplayed()
        for (text in listOf(
            "Total visits",
            "12345678901",
            "Non-bot visits",
            "12345678899",
            "Potential bot visits",
            "2",
        )) {
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("All time").performScrollTo().performClick()
        assertEquals(VisitPeriod.ALL_TIME, selected)
        compose.onNodeWithContentDescription("Refresh").performClick()
        assertEquals(1, refreshes)
        compose.onNodeWithContentDescription("Back to shlink details").performClick()
        assertEquals(true, back)
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp-mdpi")
    fun narrowPhoneCanReachAllPeriodOptionsAndCounts() {
        screen()
        for (period in listOf("Today", "Last 7 days", "Last 30 days", "Last 90 days", "All time")) {
            compose.onNodeWithText(period).performScrollTo().assertIsDisplayed().performClick()
        }
        compose.onNodeWithText("Potential bot visits").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun tabletLandscapeKeepsSummaryAndActionsAccessible() {
        screen()
        compose.onNodeWithText("12345678901").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("All time").performScrollTo().performClick()
        assertEquals(VisitPeriod.ALL_TIME, selected)
    }

    @Test fun loadingShowsNoInventedCountsAndAllowsBackAndChangingPeriod() {
        screen(state.copy(loading = true, counts = null))
        compose.onNodeWithText("12345678901").assertDoesNotExist()
        compose.onNodeWithText("0").assertDoesNotExist()
        compose.onNodeWithContentDescription("Refresh").assertIsNotEnabled()
        compose.onNodeWithText("Today").performClick()
        assertEquals(VisitPeriod.TODAY, selected)
        compose.onNodeWithContentDescription("Back to shlink details").performClick()
        assertEquals(true, back)
    }

    @Test fun invalidCountsShowRetryWithoutPartialCounts() {
        screen(state.copy(counts = null, error = InvalidVisitStatisticsException()))
        compose.onNodeWithText(
            "The server returned missing, invalid or inconsistent visit counts. " +
                "Refresh to try again; no zero values were assumed.",
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("12345678901").assertDoesNotExist()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        assertEquals(1, refreshes)
    }

    @Test fun missingDomainUsesDefaultDomainLabelAnd404IsVisible() {
        screen(
            state.copy(
                period = VisitPeriod.ALL_TIME,
                counts = null,
                error = ShortUrlRequestException(ConnectionError.HttpError(404, null)),
                window = state.window?.copy(since = null),
            ),
            domain = null,
        )
        compose.onNodeWithText("Domain: Default domain").assertIsDisplayed()
        compose.onNodeWithText("All time").assertIsSelected()
        compose.onNodeWithText(
            "From: Beginning of recorded visits\nUntil: 2026 / 10 / 31 05:30:00 CET\nTime zone: Europe/Vienna",
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("This shlink no longer exists or is not accessible for this domain.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Try again").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0").assertDoesNotExist()
    }

    @Test fun realEmptyPeriodShowsAllThreeZeroCounts() {
        screen(state.copy(counts = VisitStatistics(0, 0)))
        compose.onAllNodesWithText("0").assertCountEquals(3)
        compose.onNodeWithText("Potential bot visits").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test fun insufficientApiPermissionsAreVisibleAndRetryable() {
        screen(state.copy(counts = null, error = ShortUrlRequestException(ConnectionError.InvalidApiKey)))
        compose.onNodeWithText("The API key is invalid or does not have the required permissions.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        assertEquals(1, refreshes)
        compose.onNodeWithText("0").assertDoesNotExist()
    }
}

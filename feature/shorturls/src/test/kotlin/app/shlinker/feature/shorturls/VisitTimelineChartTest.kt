package app.shlinker.feature.shorturls

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.shlinker.core.model.VisitStatistics
import app.shlinker.core.model.VisitTimelineBucket
import app.shlinker.core.ui.theme.ShlinkerTheme
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
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp-xxhdpi")
class VisitTimelineChartTest {
    @get:Rule val compose = createComposeRule()
    private val restore = StateRestorationTester(compose)
    private val start = Instant.parse("2026-10-31T00:00:00Z")
    private val buckets = listOf(
        VisitTimelineBucket(start, start.plusSeconds(3599), false, VisitStatistics(10, 7)),
        VisitTimelineBucket(start.plusSeconds(3599), start.plusSeconds(7199), true, VisitStatistics(5, 1)),
        VisitTimelineBucket(start.plusSeconds(7199), start.plusSeconds(10799), true, VisitStatistics(0, 0)),
    )
    private var nonBotColor = Color.Unspecified
    private var botColor = Color.Unspecified

    private fun screen(values: List<VisitTimelineBucket> = buckets, dark: Boolean = false) {
        restore.setContent {
            ShlinkerTheme(darkTheme = dark, dynamicColor = false) {
                nonBotColor = MaterialTheme.colorScheme.primary
                botColor = MaterialTheme.colorScheme.tertiary
                VisitTimelineChart(values, ZoneId.of("Europe/Vienna"))
            }
        }
    }

    @Test fun renderedStackProportionsAndColorsMatchNonBotAndBotCounts() {
        screen()
        val bar = compose.onNodeWithContentDescription("Interval 1 · Non-bot visits: 7 · Potential bot visits: 3")
        val bitmap = bar.captureToImage().asAndroidBitmap()
        val center = bitmap.width / 2
        assertEquals(botColor.toArgb(), bitmap.getPixel(center, bitmap.height / 10))
        assertEquals(nonBotColor.toArgb(), bitmap.getPixel(center, bitmap.height / 2))
        compose.onNodeWithText("Visits per interval · scale: 0–10").assertIsDisplayed()
        bar.performClick().assertIsSelected()
        compose.onNodeWithText("Total: 10 · Non-bots: 7 · Potential bots: 3").assertIsDisplayed()
        compose.onNodeWithText(
            "Interval 1\nFrom (inclusive): 2026 / 10 / 31 01:00:00 CET\n" +
                "Until (inclusive): 2026 / 10 / 31 01:59:59 CET",
        ).assertIsDisplayed()
    }

    @Test fun selectionRestoresAndZeroBucketHasNoInventedVisits() {
        screen()
        val first = "Interval 1 · Non-bot visits: 7 · Potential bot visits: 3"
        compose.onNodeWithContentDescription(first).performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription(first).assertIsSelected()
        compose.onNodeWithContentDescription("Interval 3 · Non-bot visits: 0 · Potential bot visits: 0").performClick()
        compose.onNodeWithText("Total: 0 · Non-bots: 0 · Potential bots: 0").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp-mdpi")
    fun all7BarsRemainSelectableOnNarrowPhoneWithLongCountsInDarkMode() {
        val values = (0..6).map { index ->
            VisitTimelineBucket(
                start.plusSeconds(index * 60L),
                start.plusSeconds(index * 60L + 59),
                index > 0,
                VisitStatistics(Long.MAX_VALUE, Long.MAX_VALUE - 3),
            )
        }
        screen(values, dark = true)
        for (index in 1..7) {
            compose.onNodeWithContentDescription(
                "Interval $index · Non-bot visits: ${Long.MAX_VALUE - 3} · Potential bot visits: 3",
            ).performClick().assertIsSelected()
        }
    }

    @Test fun allTimeFirstBucketExplicitlyStartsAtBeginningAndNewDataResetsSelection() {
        val state = mutableStateOf(
            buckets.mapIndexed { index, bucket ->
                if (index == 0) bucket.copy(since = null) else bucket
            },
        )
        compose.setContent {
            ShlinkerTheme(dynamicColor = false) { VisitTimelineChart(state.value, ZoneId.of("UTC")) }
        }
        compose.onNodeWithContentDescription("Interval 1 · Non-bot visits: 7 · Potential bot visits: 3").performClick()
        compose.onNodeWithText(
            "Interval 1\nFrom (inclusive): Beginning of recorded visits\nUntil (inclusive): 2026 / 10 / 31 00:59:59 UTC",
        ).assertIsDisplayed()
        compose.runOnIdle { state.value = listOf(buckets.first().copy(counts = VisitStatistics(1, 0))) }
        compose.onNodeWithContentDescription(
            "Interval 1 · Non-bot visits: 0 · Potential bot visits: 1",
        ).assertIsSelected()
        compose.onNodeWithText("Total: 1 · Non-bots: 0 · Potential bots: 1").assertIsDisplayed()
    }
}

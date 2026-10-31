package at.awit.shlinker.feature.shorturls

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.awit.shlinker.core.model.parseShlinkDate
import at.awit.shlinker.core.ui.theme.ShlinkerTheme
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp-xxhdpi")
class ShlinkDateTimeFieldTest {
    @get:Rule val compose = createComposeRule()
    private var selected: String? = null
    private val original = "2026-11-01T10:00:00Z"
    private lateinit var previousZone: TimeZone

    @Before fun setZone() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Vienna"))
    }

    @After fun resetZone() = TimeZone.setDefault(previousZone)

    private fun screen(initial: String = original, enabled: Boolean = true) {
        compose.setContent {
            var value by remember { mutableStateOf(initial) }
            ShlinkerTheme(dynamicColor = false) {
                ShlinkDateTimeField(R.string.details_valid_since, value, enabled, false) {
                    selected = it
                    value = it
                }
            }
        }
    }

    @Test fun dateAndTimeDisplaysOpenTheirOwnDialogsAndPreserveUnchangedValue() {
        screen()
        compose.onNodeWithText("2026 / 11 / 01").assertIsDisplayed().performClick()
        assertEquals(null, selected)
        compose.onNodeWithText("Next: time").assertDoesNotExist()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(original, selected)
        compose.onNodeWithText("11:00 CET").assertIsDisplayed().performClick()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(original, selected)
        assertNotNull(parseShlinkDate(requireNotNull(selected)))
    }

    @Test fun cancelAtEitherStageNeverChangesValueAndClearIsExplicit() {
        screen()
        compose.onNodeWithContentDescription("Choose date for Enabled since").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(null, selected)
        compose.onNodeWithContentDescription("Choose time for Enabled since").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(null, selected)
        compose.onNodeWithText("Clear Enabled since").performClick()
        assertEquals("", selected)
    }

    @Test
    @Config(qualifiers = "en-w891dp-h411dp-mdpi")
    fun phoneLandscapeCanConfirmBothDialogs() {
        screen()
        compose.onNodeWithContentDescription("Choose date for Enabled since").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithContentDescription("Choose time for Enabled since").performClick()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(original, selected)
    }

    @Test
    @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun tabletLandscapeCanCancelClockWithoutChangingDate() {
        screen()
        compose.onNodeWithContentDescription("Choose time for Enabled since").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(null, selected)
    }

    @Test fun clockStageSurvivesSavedStateRestorationWithoutApplyingEarly() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ShlinkerTheme(dynamicColor = false) {
                ShlinkDateTimeField(R.string.details_valid_since, original, true, false) { selected = it }
            }
        }
        compose.onNodeWithContentDescription("Choose time for Enabled since").performClick()
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(null, selected)
        compose.onNodeWithText("Apply").performClick()
        assertEquals(original, selected)
    }

    @Test fun emptyDateStartsAtMidnightAndClearRestoresBothPlaceholders() {
        val today = LocalDate.now(ZoneId.of("Europe/Vienna"))
        screen("")
        compose.onNodeWithText("YYYY / MM / DD").performClick()
        compose.onNodeWithText("Apply").performClick()
        val result = requireNotNull(parseShlinkDate(requireNotNull(selected)))
        assertEquals(today, result.toLocalDate())
        assertEquals(0, result.hour)
        assertEquals(0, result.minute)
        compose.onNodeWithText("Clear Enabled since").performClick()
        compose.onNodeWithText("YYYY / MM / DD").assertIsDisplayed()
        compose.onNodeWithText("HH:mm").assertIsDisplayed()
        assertEquals("", selected)
    }

    @Test fun emptyTimeOpensClockDirectlyAndUsesToday() {
        val today = LocalDate.now(ZoneId.of("Europe/Vienna"))
        screen("")
        compose.onNodeWithText("HH:mm").performClick()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(today, requireNotNull(parseShlinkDate(requireNotNull(selected))).toLocalDate())
    }

    @Test fun summerDateUsesAndroidSummerTimeAbbreviation() {
        screen("2026-07-01T10:00:00Z")
        compose.onNodeWithText("2026 / 07 / 01").assertIsDisplayed()
        compose.onNodeWithText("12:00 CEST").assertIsDisplayed()
    }

    @Test fun disabledControlsCannotOpenOrClearPickers() {
        screen(enabled = false)
        compose.onNodeWithContentDescription("Choose date for Enabled since").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Choose time for Enabled since").assertIsNotEnabled()
        compose.onNodeWithText("Clear Enabled since").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "en-w320dp-h700dp-mdpi")
    fun narrowPhoneCanOpenClockFromPlaceholderAndCalendarFromDate() {
        screen("")
        compose.onNodeWithText("HH:mm").assertIsDisplayed().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("YYYY / MM / DD").assertIsDisplayed().performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithContentDescription("Choose date for Enabled since").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose time for Enabled since").assertIsDisplayed()
        assertNotNull(selected)
    }

    @Test fun invalidStartDoesNotMarkEndFieldAsError() {
        val full = at.awit.shlinker.core.model.ShortUrlDetails(
            at.awit.shlinker.core.model.ShortUrl("a", "https://s.example/a", "https://example.org", null, emptyList()),
        )
        compose.setContent {
            ShlinkerTheme(dynamicColor = false) {
                androidx.compose.foundation.layout.Column {
                    ShortUrlEditFields(
                        ShortUrlEditForm.from(full).copy(validSince = "invalid"),
                        true,
                        EditValidation.SINCE,
                        {},
                        TagSuggestionsState(),
                        {},
                    )
                }
            }
        }
        compose.onNodeWithText("Enabled since").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Error),
        )
        compose.onNodeWithText("Enabled until").assert(
            SemanticsMatcher.keyNotDefined(SemanticsProperties.Error),
        )
    }
}

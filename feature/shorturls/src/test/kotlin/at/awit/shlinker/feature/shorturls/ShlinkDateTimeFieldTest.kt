package at.awit.shlinker.feature.shorturls

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.awit.shlinker.core.model.parseShlinkDate
import at.awit.shlinker.core.ui.theme.ShlinkerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    private fun screen() {
        compose.setContent {
            var value by remember { mutableStateOf(original) }
            ShlinkerTheme(dynamicColor = false) {
                ShlinkDateTimeField(R.string.details_valid_since, value, true, false) {
                    selected = it
                    value = it
                }
            }
        }
    }

    @Test fun calendarThenClockAppliesOnlyAfterBothStepsAndUnchangedSelectionPreservesValue() {
        screen()
        compose.onNodeWithText("Choose Enabled since").performClick()
        compose.onNodeWithText("Next: time").performClick()
        assertEquals(null, selected)
        compose.onNodeWithText("Apply").performClick()
        assertEquals(original, selected)
        assertNotNull(parseShlinkDate(requireNotNull(selected)))
    }

    @Test fun cancelAtEitherStageNeverChangesValueAndClearIsExplicit() {
        screen()
        compose.onNodeWithText("Choose Enabled since").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(null, selected)
        compose.onNodeWithText("Choose Enabled since").performClick()
        compose.onNodeWithText("Next: time").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(null, selected)
        compose.onNodeWithText("Clear Enabled since").performClick()
        assertEquals("", selected)
    }

    @Test
    @Config(qualifiers = "en-w891dp-h411dp-mdpi")
    fun phoneLandscapeCanConfirmBothDialogs() {
        screen()
        compose.onNodeWithText("Choose Enabled since").performClick()
        compose.onNodeWithText("Next: time").performClick()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(original, selected)
    }

    @Test
    @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun tabletLandscapeCanCancelClockWithoutChangingDate() {
        screen()
        compose.onNodeWithText("Choose Enabled since").performClick()
        compose.onNodeWithText("Next: time").performClick()
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
        compose.onNodeWithText("Choose Enabled since").performClick()
        compose.onNodeWithText("Next: time").performClick()
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(null, selected)
        compose.onNodeWithText("Apply").performClick()
        assertEquals(original, selected)
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

package at.awit.shlinker.feature.shorturls

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import at.awit.shlinker.core.model.RedirectCondition
import at.awit.shlinker.core.model.RedirectConditionType
import at.awit.shlinker.core.model.RedirectRule
import at.awit.shlinker.core.model.RedirectRules
import at.awit.shlinker.core.network.RedirectRulesException
import at.awit.shlinker.core.network.RedirectRulesFailure
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
class RedirectRulesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val rules = RedirectRules(
        "https://default.example",
        listOf(RedirectRule("https://android.example", listOf(RedirectCondition("device", null, "android")))),
    )
    private var state by mutableStateOf(RedirectRulesState(serverName = "First", loading = false, original = rules))
    private var back = 0
    private var refreshed = 0
    private var saved = 0
    private var neutral = Color.Unspecified

    private fun screen(dark: Boolean = false) {
        compose.setContent {
            ShlinkerTheme(dynamicColor = false, darkTheme = dark) {
                neutral = MaterialTheme.colorScheme.onSurfaceVariant
                RedirectRulesScreen(
                    state, "same", "custom.example", { back++ }, { refreshed++ },
                    { state = state.copy(draft = rules.rules) },
                    { state = state.copy(draft = it) },
                    { state = state.copy(draft = null) },
                    { state = state.copy(confirmSave = true) },
                    { state = state.copy(confirmSave = false) },
                    { saved++ },
                )
            }
        }
    }

    @Test fun showsIdentityFallbackPriorityAndUnknownConditionsWithoutEnablingEdit() {
        state = state.copy(
            original = rules.copy(
                rules = listOf(
                    RedirectRule("https://future.example", listOf(RedirectCondition("future-type", "a", "b"))),
                ),
            ),
        )
        screen()
        compose.onNodeWithText("Server: First").assertIsDisplayed()
        compose.onNodeWithText("Domain: custom.example").assertIsDisplayed()
        compose.onNodeWithText("https://default.example").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("future-type · Parameter: a · Value: b").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Edit rules").performScrollTo().assertIsNotEnabled()
    }

    @Test fun unchangedCancelHasNoUnsavedDialogButDirtyBackDoes() {
        screen()
        compose.onNodeWithText("Edit rules").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Cancel editing").performScrollTo().performClick()
        compose.onNodeWithText("Edit rules").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Edit rules").performClick()
        compose.onNodeWithText("Add rule").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back to shlink details").performClick()
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        assertEquals(0, back)
        compose.onNodeWithText("Discard").performClick()
        compose.onNodeWithText("Edit rules").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp-mdpi")
    fun removingLastRuleRequiresConfirmationAndSeparateSaveConfirmation() {
        screen()
        compose.onNodeWithText("Edit rules").performScrollTo().performClick()
        compose.onNodeWithText("Remove rule").performScrollTo().performClick()
        assertEquals(1, state.draft?.size)
        compose.onNode(hasText("Remove rule") and hasAnyAncestor(isDialog()) and hasClickAction()).performClick()
        assertEquals(0, state.draft?.size)
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.onNodeWithText("Save redirect rules?").assertIsDisplayed()
        assertEquals(0, saved)
        compose.onNode(hasText("Save changes") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(1, saved)
    }

    @Test fun reorderAndMultipleConditionsStayInDraft() {
        state = state.copy(draft = rules.rules + RedirectRule("https://second.example", emptyList()))
        screen()
        compose.onAllNodesWithText("Move down")[0].performScrollTo().performClick()
        assertEquals("https://second.example", state.draft?.first()?.longUrl)
        compose.onAllNodesWithText("Add condition (AND)")[0].performScrollTo().performClick()
        compose.onAllNodesWithText("Type: Device")[0].performScrollTo().performClick()
        compose.onNodeWithText("Query parameter exists").performScrollTo().performClick()
        assertEquals("any-value-query-param", state.draft?.first()?.conditions?.single()?.type)
        compose.onNodeWithText("Query parameter name").performScrollTo().assertIsDisplayed()
        assertEquals(0, saved)
    }

    @Test fun uncertainSaveAllowsOnlyReloadAndKeepsDraftVisible() {
        state = state.copy(
            draft = rules.rules,
            needsReload = true,
            error = RedirectRulesException(RedirectRulesFailure.UNCERTAIN),
        )
        screen()
        compose.onNodeWithText("Reload from server").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, refreshed)
        compose.onNodeWithText("Add rule").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Cancel editing").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Save changes").performScrollTo().assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "en-w891dp-h411dp-mdpi")
    fun allConditionTypesAreSelectableAndSaveDialogRemainsUsableInLandscape() {
        state = state.copy(draft = rules.rules)
        screen()
        val labels = listOf(
            "Device", "Language", "Query parameter equals", "Query parameter exists",
            "Query parameter has no value", "IP address", "Country", "City", "Before date", "After date", "Browser",
        )
        for ((index, label) in labels.withIndex()) {
            compose.onNodeWithText("Type: ${labels[maxOf(0, index - 1)]}").performScrollTo().performClick()
            compose.onNodeWithText(label).performScrollTo().performClick()
            assertEquals(
                RedirectConditionType.entries[index].apiValue,
                state.draft?.single()?.conditions?.single()?.type,
            )
        }
        compose.runOnIdle { state = state.copy(confirmSave = true) }
        compose.onNode(hasText("Save changes") and hasAnyAncestor(isDialog())).assertIsDisplayed().performClick()
        assertEquals(1, saved)
    }

    @Test fun savingLocksEditsRefreshBackAndDuplicateSubmission() {
        state = state.copy(draft = rules.rules, saving = true)
        screen()
        compose.onNodeWithContentDescription("Refresh").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back to shlink details").assertIsNotEnabled()
        compose.onNodeWithText("Add rule").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Cancel editing").performScrollTo().assertIsNotEnabled()
        assertEquals(0, back)
        assertEquals(0, saved)
    }

    @Test fun lightStatusBannersUseNeutralEditingAndReadableGreenSuccessWithoutClickActions() {
        verifyStatusColors(dark = false)
    }

    @Test fun darkStatusBannersUseNeutralEditingAndReadableGreenSuccessWithoutClickActions() {
        verifyStatusColors(dark = true)
    }

    private fun verifyStatusColors(dark: Boolean) {
        state = state.copy(draft = rules.rules)
        screen(dark)
        val editing = compose.onNodeWithText("Editing redirect rules · changes are not yet saved")
            .performScrollTo().assert(hasClickAction().not())
        val editBitmap = editing.captureToImage().asAndroidBitmap()
        val editPixels = IntArray(editBitmap.width * editBitmap.height)
        editBitmap.getPixels(editPixels, 0, editBitmap.width, 0, 0, editBitmap.width, editBitmap.height)
        assertTrue(editPixels.any { it == neutral.toArgb() })
        compose.runOnIdle { state = state.copy(draft = null, saved = true) }
        val success = compose.onNodeWithText("Redirect rules saved").performScrollTo().assert(hasClickAction().not())
        val foreground = if (dark) Color(0xFF9CE5A9) else Color(0xFF125322)
        val background = if (dark) Color(0xFF163922) else Color(0xFFD5E8D4)
        val bitmap = success.captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.any { it == foreground.toArgb() })
        val contrast = (maxOf(foreground.luminance(), background.luminance()) + 0.05f) /
            (minOf(foreground.luminance(), background.luminance()) + 0.05f)
        assertTrue("Success text contrast must be at least 4.5:1", contrast >= 4.5f)
    }
}

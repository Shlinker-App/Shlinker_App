package at.awit.shlinker.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import at.awit.shlinker.core.model.AppTagSettings
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
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private var saved: AppTagSettings? = null
    private var retried = false
    private var back = false

    private fun screen(initial: SettingsState = SettingsState(loading = false)) {
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            ShlinkerTheme(dynamicColor = false) {
                SettingsScreen(
                    state,
                    { state = state.copy(appTag = it) },
                    { saved = state.appTag },
                    { retried = true },
                    { back = true },
                )
            }
        }
    }

    @Test fun defaultTagCanBeRenamedDisabledAndSaved() {
        screen()
        compose.onNodeWithContentDescription("Add app tag").assertIsOn().performClick()
        compose.onNodeWithContentDescription("Add app tag").assertIsOff()
        compose.onNodeWithText("App tag name").performScrollTo().performTextReplacement("custom")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals(AppTagSettings(false, "custom"), saved)
        compose.onNodeWithContentDescription("Back to server overview").performClick()
        assertEquals(true, back)
    }

    @Test fun loadErrorOffersRetryInsteadOfSavingDefaultValues() {
        screen(SettingsState(loading = false, loadFailed = true))
        compose.onNodeWithText("Could not load settings. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(true, retried)
    }

    @Test fun saveErrorIsVisibleWithDraftAndNoSuccessMessage() {
        screen(SettingsState(appTag = AppTagSettings(name = "custom"), loading = false, saveFailed = true))
        compose.onNodeWithText("custom").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Could not save settings. Your changes have not been saved. Please try again.",
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Settings saved").assertDoesNotExist()
    }

    @Test fun savingDisablesFormSubmitAndBack() {
        screen(SettingsState(loading = false, saving = true))
        compose.onNodeWithContentDescription("Add app tag").assertIsNotEnabled()
        compose.onNodeWithText("App tag name").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back to server overview").assertIsNotEnabled()
    }
}

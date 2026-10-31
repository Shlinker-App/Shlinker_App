package at.awit.shlinker.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import at.awit.shlinker.core.model.ServerProfile
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
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val server = ServerProfile("1", "Home", "https://s.example", 1)
    private var confirmed = 0
    private var added = false
    private var name: String? = null

    private fun screen(empty: Boolean = false, initialAction: ServerActionState = ServerActionState()) {
        compose.setContent {
            var action by remember { mutableStateOf(initialAction) }
            ShlinkerTheme(dynamicColor = false) {
                HomeContent(
                    state = if (empty) HomeUiState() else HomeUiState(listOf(server), server.id),
                    action = action,
                    onAddServer = { added = true },
                    onShortUrls = {},
                    onSelect = {},
                    onRename = { action = ServerActionState(server = it, name = it.name) },
                    onDelete = { action = ServerActionState(server = it, deleting = true) },
                    onNameChange = {
                        name = it
                        action = action.copy(name = it)
                    },
                    onConfirm = {
                        confirmed++
                        action = ServerActionState()
                    },
                    onDismiss = { action = ServerActionState() },
                )
            }
        }
    }

    @Test fun renameMenuOpensEditableDialog() {
        screen()
        compose.onNodeWithText("Server Overview").assertIsDisplayed()
        compose.onNodeWithContentDescription("Actions for Home").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithText("Rename server").assertIsDisplayed()
        compose.onNodeWithText("Name").performTextReplacement("Personal")
        compose.onNodeWithText("Save").performClick()
        assertEquals("Personal", name)
        assertEquals(1, confirmed)
    }

    @Test fun deletionNeedsConfirmationAndCanBeCancelled() {
        screen()
        compose.onNodeWithContentDescription("Actions for Home").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete server?").assertIsDisplayed()
        compose.onNodeWithText(
            "Remove “Home” and its saved API key from this device? Shlinks on the server will not be deleted.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, confirmed)
        compose.onNodeWithContentDescription("Actions for Home").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(1, confirmed)
    }

    @Test fun emptyOverviewAllowsAddingButNotOpeningShlinks() {
        screen(empty = true)
        compose.onNodeWithText("No servers configured. Add a server to get started.").assertIsDisplayed()
        compose.onNodeWithText("Show shlinks").assertIsNotEnabled()
        compose.onNodeWithText("Add server", useUnmergedTree = true).performClick()
        assertEquals(true, added)
    }

    @Test fun renameConflictIsVisibleAndBusyActionsAreDisabled() {
        screen(
            initialAction = ServerActionState(
                server = server,
                name = server.name,
                busy = true,
                error = ServerActionError.NAME_CONFLICT,
            ),
        )
        compose.onNodeWithText(
            "A profile with this name already exists for this server address. Please choose a different name.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithText("Name").assertIsNotEnabled()
    }
}

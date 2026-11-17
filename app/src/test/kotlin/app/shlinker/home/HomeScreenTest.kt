package app.shlinker.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import app.shlinker.core.model.ServerProfile
import app.shlinker.core.model.ServerSort
import app.shlinker.core.ui.theme.ShlinkerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private var sorted: ServerSort? = null
    private var reordered: List<String>? = null
    private var selected: String? = null
    private var edited: String? = null
    private var settings = false

    private fun screen(
        empty: Boolean = false,
        initialAction: ServerActionState = ServerActionState(),
        profiles: List<ServerProfile> = listOf(server),
        failReorder: Boolean = false,
    ) {
        compose.setContent {
            var action by remember { mutableStateOf(initialAction) }
            var state by remember {
                mutableStateOf(HomeUiState(if (empty) emptyList() else profiles, if (empty) null else server.id))
            }
            ShlinkerTheme(dynamicColor = false) {
                HomeContent(
                    state = state,
                    action = action,
                    onAddServer = { added = true },
                    onShortUrls = {},
                    onSelect = { selected = it },
                    onEditServer = { edited = it },
                    onSettings = { settings = true },
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
                    onSort = {
                        sorted = it
                        state = state.copy(sort = it)
                    },
                    onReorder = { ids ->
                        reordered = ids
                        if (failReorder) {
                            action = ServerActionState(error = ServerActionError.STORAGE)
                        } else {
                            state = state.copy(servers = ids.map { id -> profiles.first { it.id == id } })
                        }
                    },
                )
            }
        }
    }

    @Test fun settingsActionDoesNotChangeTheSelectedServer() {
        screen()
        compose.onNodeWithContentDescription("Settings").performClick()
        assertTrue(settings)
        assertNull(selected)
    }

    @Test fun editMenuPassesProfileIdWithoutChangingSelection() {
        screen()
        compose.onNodeWithContentDescription("Actions for Home").performClick()
        compose.onNodeWithText("Edit server").performClick()
        assertEquals(server.id, edited)
        assertNull(selected)
        assertEquals(0, confirmed)
    }

    @Test fun sortMenuOffersAllDirectionsAndOnlyManualModeShowsDragHandles() {
        screen()
        compose.onNodeWithContentDescription("Drag to reorder Home").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sort servers").performClick()
        compose.onNodeWithText("Name (A–Z)").assertIsDisplayed()
        compose.onNodeWithText("Name (Z–A)").assertIsDisplayed()
        compose.onNodeWithText("Hostname (A–Z)").assertIsDisplayed()
        compose.onNodeWithText("Hostname (Z–A)").performClick()
        assertEquals(ServerSort.HOSTNAME_DESC, sorted)
        compose.onNodeWithContentDescription("Drag to reorder Home").assertDoesNotExist()
        compose.onNodeWithContentDescription("Sort servers").performClick()
        compose.onNodeWithText("Manual", useUnmergedTree = true).performClick()
        assertEquals(ServerSort.MANUAL, sorted)
        compose.onNodeWithContentDescription("Drag to reorder Home").assertIsDisplayed()
    }

    @Test fun draggingHandleChangesOrderAndSubmitsOnRelease() {
        val second = server.copy(id = "2", name = "Work")
        val third = server.copy(id = "3", name = "Dev")
        screen(profiles = listOf(server, second, third))
        val handle = compose.onNodeWithContentDescription("Drag to reorder Home", useUnmergedTree = true)
        val target = compose.onNodeWithContentDescription("Drag to reorder Dev", useUnmergedTree = true)
        val distance =
            target.fetchSemanticsNode().boundsInRoot.center.y -
                handle.fetchSemanticsNode().boundsInRoot.center.y
        handle.performTouchInput {
            swipe(center, center + Offset(0f, distance), durationMillis = 800)
        }
        compose.waitForIdle()
        assertEquals(listOf("2", "3", "1"), reordered)
        assertNull(selected)
        compose.onNodeWithContentDescription("active").assertIsDisplayed()
    }

    @Test fun accessibilityCanMoveServersWithoutDragging() {
        val second = server.copy(id = "2", name = "Work")
        screen(profiles = listOf(server, second))
        val actions = compose.onNodeWithText("Home").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            actions.single { it.label == "Move down" }.action()
        }
        compose.waitForIdle()
        assertEquals(listOf("2", "1"), reordered)
    }

    @Test fun draggingAtEdgeScrollsAnElevenProfileList() {
        val profiles = listOf(server) + (2..11).map { server.copy(id = "$it", name = "Server $it") }
        screen(profiles = profiles)
        val handle = compose.onNodeWithContentDescription("Drag to reorder Home", useUnmergedTree = true)
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val start = handle.fetchSemanticsNode().boundsInRoot
        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, 60f))
            moveTo(Offset(center.x, root.bottom - start.top - 80f))
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(11, reordered?.size)
        assertEquals("1", reordered?.last())
    }

    @Test fun failedDropShowsErrorAndRestoresPersistedOrder() {
        val second = server.copy(id = "2", name = "Work")
        screen(profiles = listOf(server, second), failReorder = true)
        val handle = compose.onNodeWithContentDescription("Drag to reorder Home", useUnmergedTree = true)
        val target = compose.onNodeWithContentDescription("Drag to reorder Work", useUnmergedTree = true)
        val distance =
            target.fetchSemanticsNode().boundsInRoot.center.y - handle.fetchSemanticsNode().boundsInRoot.center.y
        handle.performTouchInput { swipe(center, center + Offset(0f, distance), durationMillis = 800) }
        compose.onNodeWithText("Could not save your changes. Please try again.").assertIsDisplayed()
        val homeY = compose.onNodeWithText("Home").fetchSemanticsNode().boundsInRoot.top
        val workY = compose.onNodeWithText("Work").fetchSemanticsNode().boundsInRoot.top
        assertTrue(homeY < workY)
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

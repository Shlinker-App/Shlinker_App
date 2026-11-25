package app.shlinker.feature.shorturls

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import app.shlinker.core.model.ServerDomain
import app.shlinker.core.model.ServerProfile
import app.shlinker.core.model.ShortUrl
import app.shlinker.core.ui.theme.ShlinkerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp-xxhdpi")
class SharedCreateScreenTest {
    @get:Rule val compose = createComposeRule()
    private val profiles = listOf(
        ServerProfile("1", "First", "https://first.example", 0),
        ServerProfile("2", "Second", "https://second.example", 1),
    )
    private var form by mutableStateOf(
        CreateShortUrlState(
            profileId = "2",
            serverName = "Second",
            loading = false,
            longUrl = "https://destination.example/a?b=1#part",
            domain = "s.example",
            domains = listOf(ServerDomain("s.example", true)),
        ),
    )
    private var servers by mutableStateOf(SharedCreateState(loading = false, profiles = profiles, selectedId = "2"))
    private var submitted: CreateShortUrlState? = null
    private var copied: String? = null
    private var shared: String? = null
    private var closed = 0
    private var opened = 0

    private var onSurface = Color.Unspecified

    private fun screen(interrupted: Boolean = false, invalid: Boolean = false, dark: Boolean = false) {
        compose.setContent {
            ShlinkerTheme(darkTheme = dark, dynamicColor = false) {
                onSurface = MaterialTheme.colorScheme.onSurface
                SharedCreateScreen(
                    servers, form, interrupted, invalid, null,
                    onChange = { form = it },
                    onSelectServer = {
                        servers = servers.copy(selectedId = it)
                        form = form.copy(profileId = it)
                    },
                    onCreate = { submitted = form },
                    onRetryDomains = {}, onRetryProfiles = {}, onRetryForm = {},
                    onClose = { closed++ }, onOpenApp = { opened++ },
                    onCopy = { copied = it }, onShare = { shared = it },
                )
            }
        }
    }

    @Test fun prefilledUrlOptionalSlugTitleAndServerSelectionWaitForExplicitCreate() {
        screen()
        compose.onNodeWithText("https://destination.example/a?b=1#part").assertIsDisplayed()
        assertNull(submitted)
        compose.onNodeWithText("second.example").assertIsDisplayed()
        compose.onNodeWithText("Server: Second").performClick()
        compose.onNodeWithText("first.example").assertIsDisplayed()
        compose.onNodeWithText("First").performClick()
        compose.onNodeWithText("Custom slug (optional)").performScrollTo().performTextReplacement("custom")
        compose.onNodeWithText("Title (optional)").performScrollTo().performTextReplacement("Title")
        compose.onNodeWithText("Create").performScrollTo().performClick()
        assertEquals("1", submitted?.profileId)
        assertEquals("custom", submitted?.slug)
        assertEquals("Title", submitted?.title)
        assertEquals("https://destination.example/a?b=1#part", submitted?.longUrl)
    }

    @Test fun successHasLabeledCopyShareIconsQrCodeAndDoneWithoutAutomaticSharing() {
        form = form.copy(created = ShortUrl("abc", "https://s.example/abc", form.longUrl, null, emptyList()))
        screen()
        compose.onNodeWithText("Shlink created").assertIsDisplayed()
        compose.onNodeWithText("Create").assertDoesNotExist()
        assertNull(copied)
        assertNull(shared)
        compose.onNodeWithContentDescription("Share https://s.example/abc").performClick()
        assertEquals("https://s.example/abc", shared)
        assertNull(copied)
        compose.onNodeWithContentDescription("Copy https://s.example/abc").performClick()
        assertEquals("https://s.example/abc", copied)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription("QR code for https://s.example/abc")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Done").performScrollTo().performClick()
        assertEquals(1, closed)
    }

    @Test fun darkThemeUsesReadableTextColors() {
        form = form.copy(created = ShortUrl("abc", "https://s.example/abc", form.longUrl, null, emptyList()))
        screen(dark = true)
        val texts = listOf(
            "Create shared shlink",
            "Server: Second",
            "second.example",
            "Shlink created",
            "https://s.example/abc",
        )
        for (text in texts) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(text, onSurface, layouts.single().layoutInput.style.color)
        }
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp-mdpi")
    fun interruptedOrBusyFormCannotWriteSwitchServerOrCloseDuringRequest() {
        form = form.copy(busy = true)
        screen()
        compose.onNodeWithText("Server: Second").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("Create").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performScrollTo().assertIsNotEnabled()
        assertNull(submitted)
        assertEquals(0, closed)
    }

    @Test fun restoredUncertainAttemptShowsCheckAppAndNeverEnablesCreate() {
        screen(interrupted = true)
        compose.onNodeWithText("Open Shlinker to check").performScrollTo().performClick()
        assertEquals(1, opened)
        compose.onNodeWithText("Create").performScrollTo().assertIsNotEnabled()
    }

    @Test fun invalidSharedTextCanBeReplacedWithAnExplicitDestination() {
        form = form.copy(longUrl = "")
        screen(invalid = true)
        compose.onNodeWithText("Create").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Destination URL").performScrollTo().performTextReplacement("https://corrected.example")
        compose.onNodeWithText("Create").performScrollTo().performClick()
        assertEquals("https://corrected.example", submitted?.longUrl)
    }

    @Test fun missingProfilesOffersSetupWithoutCreating() {
        servers = servers.copy(profiles = emptyList(), selectedId = null)
        screen(invalid = true)
        compose.onNodeWithText("Set up a server").performScrollTo().performClick()
        assertEquals(1, opened)
        compose.onNodeWithText("Create").performScrollTo().assertIsNotEnabled()
    }
}

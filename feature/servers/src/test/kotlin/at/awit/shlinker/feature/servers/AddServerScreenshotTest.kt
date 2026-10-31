package at.awit.shlinker.feature.servers

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import at.awit.shlinker.core.model.ServerUrl
import at.awit.shlinker.core.network.CertificateProblem
import at.awit.shlinker.core.network.ConnectionError
import at.awit.shlinker.core.ui.theme.ShlinkerTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot-Tests (JVM, ohne Emulator). Bilder: feature/servers/build/outputs/roborazzi/
 * Aufnehmen: ./gradlew :feature:servers:recordRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AddServerScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, dark: Boolean = false, state: AddServerUiState) {
        compose.setContent { Themed(dark) { Screen(state) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun leer() = shot("add_server_empty", state = AddServerUiState())

    @Test fun leerDunkel() = shot("add_server_empty_dark", dark = true, state = AddServerUiState())

    @Test fun vorschau() = shot(
        "add_server_preview",
        state = AddServerUiState(
            url = "S.Example.org/rest/v3",
            apiKey = "abc",
            normalizedUrlPreview = "https://s.example.org",
        ),
    )

    @Test fun zertifikatsfehler() = shot(
        "add_server_cert_error",
        state = AddServerUiState(
            url = "shlink.home.lan",
            apiKey = "abc",
            connectionError = ConnectionError.Certificate(CertificateProblem.UNTRUSTED),
        ),
    )

    @Test fun httpFehlerDunkel() = shot(
        "add_server_http_error_dark",
        dark = true,
        state = AddServerUiState(
            url = "http://s.example.org",
            urlError = ServerUrl.Reason.INSECURE_HTTP,
            apiKeyMissing = true,
        ),
    )

    @Test fun verbinden() = shot(
        "add_server_connecting",
        state = AddServerUiState(url = "s.example.org", apiKey = "abc", isConnecting = true),
    )

    @Test
    @Config(qualifiers = "de-w411dp-h891dp-xxhdpi")
    fun englishFormIsUsedOnGermanDevice() {
        compose.setContent { Themed(false) { Screen(AddServerUiState()) } }
        compose.onNodeWithText("Add server").assertIsDisplayed()
        compose.onNodeWithText("Name (optional)").assertIsDisplayed()
        compose.onNodeWithText("Server address").assertIsDisplayed()
        compose.onAllNodesWithText("API key")[0].assertIsDisplayed()
        compose.onNodeWithText("Connect & save").assertIsDisplayed()
    }

    @Test fun duplicateMessageIsVisible() {
        compose.setContent {
            Themed(false) {
                Screen(AddServerUiState(saveError = ServerSaveError.DUPLICATE))
            }
        }
        compose.onNodeWithText(
            "This server is already configured with this API key.",
        ).assertIsDisplayed()
    }

    @Test fun nameConflictMessageIsVisible() {
        compose.setContent {
            Themed(false) {
                Screen(AddServerUiState(saveError = ServerSaveError.NAME_CONFLICT))
            }
        }
        compose.onNodeWithText(
            "A profile with this name already exists for this server address. Please choose a different name.",
        ).assertIsDisplayed()
    }

    @Test fun pasteReadsOnlyOnClickAndKeepsKeyMasked() {
        val clipboard = TestClipboard(ClipData.newPlainText("test", "test-api-key"))
        var pasted = ""
        keyField(clipboard) { pasted = it }
        assertEquals(0, clipboard.reads)
        compose.onNodeWithText("Paste").performClick()
        compose.waitForIdle()
        assertEquals("test-api-key", pasted)
        assertEquals(1, clipboard.reads)
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithContentDescription("Show API key").assertIsDisplayed()
    }

    @Test fun emptyOrNonTextClipboardDoesNotReplaceExistingKey() {
        val clipboard = TestClipboard()
        var changes = 0
        keyField(clipboard) { changes++ }
        val nonText = ClipData(
            ClipDescription("image", arrayOf("image/png")),
            ClipData.Item(android.net.Uri.parse("content://example/image")),
        )
        for (clip in listOf(null, ClipData.newPlainText("blank", "  "), nonText)) {
            clipboard.clip = clip
            compose.onNodeWithText("Paste").performClick()
            compose.onNodeWithText("No text in the clipboard. Copy your API key first.").assertIsDisplayed()
            assertEquals(0, changes)
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("typed-key")
        compose.onNodeWithText("No text in the clipboard. Copy your API key first.").assertDoesNotExist()
        assertEquals(1, changes)
    }

    @Test fun clipboardDenialIsVisibleAndPasteCanBeRetried() {
        val clipboard = TestClipboard(ClipData.newPlainText("test", "retry-key"))
        clipboard.denied = true
        var pasted = ""
        keyField(clipboard) { pasted = it }
        compose.onNodeWithText("Paste").performClick()
        compose.onNodeWithText(
            "Could not read the clipboard. Please try again or paste using the text menu.",
        ).assertIsDisplayed()
        assertEquals("", pasted)
        clipboard.denied = false
        compose.onNodeWithText("Paste").performClick()
        compose.waitForIdle()
        assertEquals("retry-key", pasted)
        compose.onNodeWithText(
            "Could not read the clipboard. Please try again or paste using the text menu.",
        ).assertDoesNotExist()
    }

    @Test fun pasteAndVisibilityAreDisabledWhileConnecting() {
        val clipboard = TestClipboard(ClipData.newPlainText("test", "test-api-key"))
        keyField(clipboard, connecting = true)
        compose.onNodeWithText("Paste").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Show API key").assertIsNotEnabled()
        assertEquals(0, clipboard.reads)
    }

    private fun keyField(clipboard: Clipboard, connecting: Boolean = false, onChange: (String) -> Unit = {}) {
        compose.setContent {
            var key by remember { mutableStateOf("existing-key") }
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                Themed(false) {
                    ApiKeyField(
                        state = AddServerUiState(apiKey = key, isConnecting = connecting),
                        onApiKeyChange = {
                            key = it
                            onChange(it)
                        },
                        onSubmit = {},
                    )
                }
            }
        }
    }

    private class TestClipboard(var clip: ClipData? = null) : Clipboard {
        var reads = 0
        var denied = false

        override suspend fun getClipEntry(): ClipEntry? {
            reads++
            if (denied) throw SecurityException("Clipboard access denied")
            return clip?.let(::ClipEntry)
        }

        override suspend fun setClipEntry(clipEntry: ClipEntry?) = error("Paste must not modify the clipboard")
    }

    @Composable
    private fun Themed(dark: Boolean, content: @Composable () -> Unit) =
        ShlinkerTheme(darkTheme = dark, dynamicColor = false, content = content)

    @Composable
    private fun Screen(state: AddServerUiState) = AddServerScreen(
        state = state,
        onNameChange = {},
        onUrlChange = {},
        onUrlFocusLost = {},
        onApiKeyChange = {},
        onSubmit = {},
        onCancel = {},
        onBack = {},
    )
}

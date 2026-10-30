package at.awit.shlinker.feature.servers

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import at.awit.shlinker.core.model.ServerUrl
import at.awit.shlinker.core.network.CertificateProblem
import at.awit.shlinker.core.network.ConnectionError
import at.awit.shlinker.core.ui.theme.ShlinkerTheme
import com.github.takahirom.roborazzi.captureRoboImage
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

    @Test fun duplicateMessageIsVisible() {
        compose.setContent {
            Themed(false) {
                Screen(AddServerUiState(saveError = ServerSaveError.DUPLICATE))
            }
        }
        compose.onNodeWithText(
            "Dieser Server ist mit demselben API-Key bereits eingerichtet. " +
                "Wähle das vorhandene Profil in der Serverübersicht. Ein anderer Name erstellt kein neues Profil.",
        ).assertIsDisplayed()
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

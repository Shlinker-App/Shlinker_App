package app.shlinker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val DevsBannerRed = Color(0xFFB3261E)

/** Label for the bottom banner in DEVS builds, or `null` for all other channels. */
internal fun devsBuildLabel(flavor: String = BuildConfig.FLAVOR, versionName: String = BuildConfig.VERSION_NAME) =
    if (flavor == "devs") "DEVS build v${versionName.removeSuffix("-devs")}" else null

/** Shows [content] above a full-width red banner when [label] is set; the banner covers the navigation bar. */
@Composable
internal fun DevsBuildFrame(label: String?, content: @Composable () -> Unit) {
    if (label == null) {
        content()
        return
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)) { content() }
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(DevsBannerRed)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 4.dp),
        )
    }
}

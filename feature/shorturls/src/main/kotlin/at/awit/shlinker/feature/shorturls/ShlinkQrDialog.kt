package at.awit.shlinker.feature.shorturls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ShlinkQrDialog(shortUrl: String, onDismiss: () -> Unit) {
    val code by produceState<ShlinkQrCode>(ShlinkQrCode.Loading, shortUrl) {
        value = ShlinkQrCode.Loading
        value = withContext(Dispatchers.Default) { generateShlinkQrCode(shortUrl) }
    }
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.65f }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.qr_title)) },
        text = {
            Column(
                Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SelectionContainer { Text(shortUrl) }
                when (val result = code) {
                    ShlinkQrCode.Loading -> CircularProgressIndicator()
                    ShlinkQrCode.InvalidUrl -> Text(
                        stringResource(R.string.qr_invalid),
                        color = MaterialTheme.colorScheme.error,
                    )
                    ShlinkQrCode.EncodingFailed -> Text(
                        stringResource(R.string.qr_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                    is ShlinkQrCode.Ready -> {
                        val description = stringResource(R.string.qr_description, shortUrl)
                        BoxWithConstraints(
                            Modifier.align(Alignment.CenterHorizontally)
                                .widthIn(max = maxHeight * 0.6f).fillMaxWidth(),
                        ) {
                            val modules = result.modules
                            if (constraints.maxWidth < modules.width) {
                                Text(
                                    stringResource(R.string.qr_window_small),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                Canvas(
                                    Modifier.fillMaxWidth().aspectRatio(1f)
                                        .semantics { contentDescription = description },
                                ) {
                                    drawRect(Color.White)
                                    // Whole physical pixels keep edges sharp, including the quiet zone.
                                    val scale = floor(size.minDimension / modules.width)
                                    val left = floor((size.width - modules.width * scale) / 2)
                                    val top = floor((size.height - modules.height * scale) / 2)
                                    for (y in 0 until modules.height) {
                                        for (x in 0 until modules.width) {
                                            if (modules[x, y]) {
                                                drawRect(
                                                    Color.Black,
                                                    Offset(left + x * scale, top + y * scale),
                                                    Size(scale, scale),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text(stringResource(R.string.qr_hint))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.qr_close)) }
        },
    )
}

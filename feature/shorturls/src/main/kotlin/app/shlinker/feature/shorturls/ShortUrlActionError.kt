package app.shlinker.feature.shorturls

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI
import java.net.URISyntaxException

internal enum class ShortUrlActionError { INVALID_URL, NO_HANDLER, DENIED }

internal fun openShortUrl(context: Context, url: String): ShortUrlActionError? {
    val uri = try {
        URI(url)
    } catch (_: URISyntaxException) {
        return ShortUrlActionError.INVALID_URL
    }
    if (uri.scheme?.lowercase() !in listOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
        return ShortUrlActionError.INVALID_URL
    }
    return launchShortUrlIntent(
        context,
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE),
    )
}

internal fun shareShortUrl(context: Context, url: String): ShortUrlActionError? = launchShortUrlIntent(
    context,
    Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        },
        null,
    ),
)

private fun launchShortUrlIntent(context: Context, intent: Intent): ShortUrlActionError? = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    null
} catch (_: ActivityNotFoundException) {
    ShortUrlActionError.NO_HANDLER
} catch (_: SecurityException) {
    ShortUrlActionError.DENIED
}

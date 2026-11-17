package app.shlinker

import android.content.Intent
import android.os.BadParcelableException
import app.shlinker.core.model.extractSharedUrl

internal data class IncomingShare(val url: String?, val title: String = "")

internal fun readIncomingShare(intent: Intent): IncomingShare {
    if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return IncomingShare(null)
    return try {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val title = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty()
        if (text == null || text.length > 65_536) {
            IncomingShare(null)
        } else {
            IncomingShare(extractSharedUrl(text), title.take(500))
        }
    } catch (_: BadParcelableException) {
        IncomingShare(null)
    } catch (_: ClassCastException) {
        IncomingShare(null)
    }
}

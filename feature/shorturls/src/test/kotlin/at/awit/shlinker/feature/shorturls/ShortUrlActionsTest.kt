package at.awit.shlinker.feature.shorturls

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShortUrlActionsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun openDispatchesExactShortUrlToBrowserWithoutSharingOrSecrets() {
        val url = "https://s.example/custom/path?x=1#section"
        assertNull(openShortUrl(context, url))
        val intent = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(url, intent.dataString)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertNull(intent.extras)
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test fun shareDispatchesPlainShortUrlToChooserWithoutOpeningOrCopying() {
        val url = "https://s.example/abc"
        assertNull(shareShortUrl(context, url))
        val chooser = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val intent = requireNotNull(chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(url, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(setOf(Intent.EXTRA_TEXT), intent.extras?.keySet())
        assertNull(intent.data)
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test fun invalidOrNonWebUrlsNeverStartAnActivity() {
        for (url in listOf(
            "",
            "not a url",
            "javascript:alert(1)",
            "intent://example.org",
            "file:///tmp/test",
            "https://",
            "https://user:password@s.example/path",
        )) {
            assertEquals(ShortUrlActionError.INVALID_URL, openShortUrl(context, url))
        }
        assertNull(shadowOf(context).nextStartedActivity)
        assertNull(openShortUrl(context, "http://s.example/abc"))
    }

    @Test fun missingHandlersAndAndroidRestrictionsAreReportedForOpenAndShare() {
        for ((exception, expected) in listOf(
            ActivityNotFoundException("missing") to ShortUrlActionError.NO_HANDLER,
            SecurityException("restricted") to ShortUrlActionError.DENIED,
        )) {
            val unavailable = object : ContextWrapper(context) {
                override fun startActivity(intent: Intent): Unit = throw exception
            }
            assertEquals(expected, openShortUrl(unavailable, "https://s.example/abc"))
            assertEquals(expected, shareShortUrl(unavailable, "https://s.example/abc"))
        }
    }
}

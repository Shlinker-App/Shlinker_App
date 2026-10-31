package at.awit.shlinker

import android.content.Intent
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IncomingShareTest {
    private fun share(text: String) = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

    @Test fun plainTextShareParsesOneExactUrlAndOptionalSubject() {
        assertEquals(
            IncomingShare("https://example.com/a?x=1#frag", "Article"),
            readIncomingShare(
                share("Article\nhttps://example.com/a?x=1#frag").putExtra(Intent.EXTRA_SUBJECT, "Article"),
            ),
        )
    }

    @Test fun unsupportedActionsStreamsAmbiguousOversizedAndWrongExtrasAreNotAcceptedAsUrls() {
        for (intent in listOf(
            Intent(Intent.ACTION_VIEW).setData(android.net.Uri.parse("https://example.com")),
            share("https://example.com").setType("image/png"),
            share("https://example.com").setAction(Intent.ACTION_SEND_MULTIPLE),
            share("https://one.example https://two.example"),
            share("https://example.com " + "x".repeat(65_536)),
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, 42),
        )) {
            assertNull(readIncomingShare(intent).url)
        }
    }

    @Test fun coldShareEntrySurvivesRecreationAndSingleTopDoesNotOverwriteExistingDraft() {
        val controller = Robolectric.buildActivity(ShareActivity::class.java, share("https://example.com")).setup()
        controller.recreate()
        assertEquals("https://example.com", readIncomingShare(controller.get().intent).url)
        controller.newIntent(share("https://second.example").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        val next = shadowOf(controller.get()).nextStartedActivity
        assertEquals(ShareActivity::class.java.name, next.component?.className)
        assertEquals(0, next.flags)
        assertEquals("https://second.example", readIncomingShare(next).url)
        assertEquals("https://example.com", readIncomingShare(controller.get().intent).url)
        controller.pause().stop().destroy()
    }

    @Test fun manifestOffersExportedShareEntryForPlainTextButNotImagesOrViewLinks() {
        val context = RuntimeEnvironment.getApplication()
        val pm = context.packageManager
        val receivers = pm.queryIntentActivities(share("https://example.com").setPackage(context.packageName), 0)
        assertTrue(receivers.any { it.activityInfo.name == ShareActivity::class.java.name && it.activityInfo.exported })
        val images = pm.queryIntentActivities(
            share("https://example.com").setType("image/png").setPackage(context.packageName),
            0,
        )
        assertTrue(images.none { it.activityInfo.name == ShareActivity::class.java.name })
        val info = pm.getActivityInfo(
            android.content.ComponentName(context, ShareActivity::class.java),
            PackageManager.GET_META_DATA,
        )
        assertEquals(android.content.pm.ActivityInfo.LAUNCH_MULTIPLE, info.launchMode)
    }
}

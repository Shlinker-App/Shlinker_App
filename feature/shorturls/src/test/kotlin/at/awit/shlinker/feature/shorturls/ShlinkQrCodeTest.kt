package at.awit.shlinker.feature.shorturls

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShlinkQrCodeTest {
    @Test fun encodesExactShortUrlIncludingCustomDomainPathCaseQueryAndUnicodeWithoutNormalization() {
        for (url in listOf(
            "https://short.example/AbC",
            "http://custom.example:8080/base/a%2Fb?keep=Case&x=%26#fragment",
            "https://xn--bcher-kva.example/Grüße/日本語",
        )) {
            val modules = (generateShlinkQrCode(url) as ShlinkQrCode.Ready).modules
            val scale = 8
            val size = modules.width * scale
            val pixels = IntArray(size * size) { index ->
                if (modules[(index % size) / scale, (index / size) / scale]) 0xff000000.toInt() else -1
            }
            assertEquals(url, decodeQrPixels(size, size, pixels))
            for (y in 0 until modules.height) {
                for (x in 0 until modules.width) {
                    if (x < 4 || y < 4 || x >= modules.width - 4 || y >= modules.height - 4) {
                        assertFalse("Four-module quiet zone", modules[x, y])
                    }
                }
            }
            assertTrue(modules.width > 8)
        }
    }

    @Test fun invalidOrCredentialBearingUrlsAreExplicitFailures() {
        for (url in listOf("", "not a URL", "javascript:alert(1)", "https://user:secret@example.org/a", "https:///a")) {
            assertEquals(ShlinkQrCode.InvalidUrl, generateShlinkQrCode(url))
        }
    }

    @Test fun tooLongUrlIsReportedInsteadOfTruncatedOrEncodedAsDestination() {
        assertEquals(ShlinkQrCode.EncodingFailed, generateShlinkQrCode("https://s.example/" + "a".repeat(5000)))
    }
}

internal fun decodeQrPixels(width: Int, height: Int, pixels: IntArray): String = QRCodeReader().decode(
    BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels))),
).text

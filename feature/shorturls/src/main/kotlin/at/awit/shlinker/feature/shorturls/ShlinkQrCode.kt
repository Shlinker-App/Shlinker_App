package at.awit.shlinker.feature.shorturls

import at.awit.shlinker.core.model.isValidDestinationUrl
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

internal sealed interface ShlinkQrCode {
    data object Loading : ShlinkQrCode
    data object InvalidUrl : ShlinkQrCode
    data object EncodingFailed : ShlinkQrCode
    data class Ready(val modules: BitMatrix) : ShlinkQrCode
}

internal fun generateShlinkQrCode(shortUrl: String): ShlinkQrCode {
    if (!isValidDestinationUrl(shortUrl)) return ShlinkQrCode.InvalidUrl
    return try {
        ShlinkQrCode.Ready(
            QRCodeWriter().encode(
                shortUrl,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to 4,
                ),
            ),
        )
    } catch (_: WriterException) {
        ShlinkQrCode.EncodingFailed
    }
}

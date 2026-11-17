package app.shlinker.core.model

import java.net.URI
import java.net.URISyntaxException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

data class ShortUrlEdit(
    val original: ShortUrlDetails,
    val longUrl: String = original.url.longUrl,
    val title: String? = original.url.title,
    val tags: List<String> = original.url.tags,
    val validSince: String? = original.validSince,
    val validUntil: String? = original.validUntil,
    val maxVisits: Long? = original.maxVisits,
    val crawlable: Boolean? = original.crawlable,
    val forwardQuery: Boolean? = original.forwardQuery,
) {
    val isValid: Boolean
        get() {
            if (!isValidDestinationUrl(longUrl) || (maxVisits != null && maxVisits < 1)) return false
            val since = validSince?.let { parseShlinkDate(it) ?: return false }
            val until = validUntil?.let { parseShlinkDate(it) ?: return false }
            return since == null || until == null || until.isAfter(since)
        }
}

fun isValidDestinationUrl(value: String): Boolean {
    val uri = try {
        URI(value)
    } catch (_: URISyntaxException) {
        return false
    }
    return uri.scheme?.lowercase() in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
}

fun parseShlinkDate(value: String): OffsetDateTime? = try {
    OffsetDateTime.parse(value)
} catch (_: DateTimeParseException) {
    null
}

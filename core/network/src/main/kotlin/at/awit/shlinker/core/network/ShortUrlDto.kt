package at.awit.shlinker.core.network

import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.model.ShortUrlDetails
import kotlinx.serialization.Serializable

@Serializable
internal data class ShortUrlDto(
    val shortCode: String,
    val shortUrl: String,
    val longUrl: String,
    val title: String? = null,
    val tags: List<String> = emptyList(),
    val domain: String? = null,
    val visitsSummary: VisitsSummaryDto? = null,
    val visitsCount: Long? = null,
    val dateCreated: String? = null,
    val meta: ShortUrlMetaDto? = null,
    val crawlable: Boolean? = null,
    val forwardQuery: Boolean? = null,
    val hasRedirectRules: Boolean? = null,
) {
    fun toModel(): ShortUrl {
        val counts =
            listOf(visitsSummary?.total, visitsSummary?.nonBots, visitsSummary?.bots, visitsCount, meta?.maxVisits)
        if (shortCode.isBlank() || shortUrl.isBlank() || longUrl.isBlank() || counts.any { it != null && it < 0 }) {
            throw InvalidShortUrlResponseException()
        }
        return ShortUrl(shortCode, shortUrl, longUrl, title, tags, visitsSummary?.total ?: visitsCount, domain)
    }

    fun toDetails(): ShortUrlDetails = ShortUrlDetails(
        url = toModel(),
        dateCreated = dateCreated,
        validSince = meta?.validSince,
        validUntil = meta?.validUntil,
        maxVisits = meta?.maxVisits,
        metaAvailable = meta != null,
        crawlable = crawlable,
        forwardQuery = forwardQuery,
        hasRedirectRules = hasRedirectRules,
        nonBotVisits = visitsSummary?.nonBots,
        botVisits = visitsSummary?.bots,
    )
}

@Serializable
internal data class VisitsSummaryDto(val total: Long? = null, val nonBots: Long? = null, val bots: Long? = null)

@Serializable
internal data class ShortUrlMetaDto(
    val validSince: String? = null,
    val validUntil: String? = null,
    val maxVisits: Long? = null,
)

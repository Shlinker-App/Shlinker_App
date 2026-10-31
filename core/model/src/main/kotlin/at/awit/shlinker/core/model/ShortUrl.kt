package at.awit.shlinker.core.model

data class ShortUrl(
    val shortCode: String,
    val shortUrl: String,
    val longUrl: String,
    val title: String?,
    val tags: List<String>,
    val visits: Long? = null,
    val domain: String? = null,
)

data class ShortUrlPage(val items: List<ShortUrl>, val currentPage: Int, val pagesCount: Int)

data class ShortUrlDetails(
    val url: ShortUrl,
    val dateCreated: String? = null,
    val validSince: String? = null,
    val validUntil: String? = null,
    val maxVisits: Long? = null,
    val metaAvailable: Boolean = false,
    val crawlable: Boolean? = null,
    val forwardQuery: Boolean? = null,
    val hasRedirectRules: Boolean? = null,
    val nonBotVisits: Long? = null,
    val botVisits: Long? = null,
)

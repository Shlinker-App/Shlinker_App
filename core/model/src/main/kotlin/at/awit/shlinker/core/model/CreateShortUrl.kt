package at.awit.shlinker.core.model

data class CreateShortUrl(
    val longUrl: String,
    val title: String? = null,
    val customSlug: String? = null,
    val domain: String? = null,
    val tags: List<String> = emptyList(),
    val forwardQuery: Boolean? = null,
    val crawlable: Boolean? = null,
)

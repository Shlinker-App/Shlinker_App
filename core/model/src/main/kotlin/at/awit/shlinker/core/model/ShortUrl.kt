package at.awit.shlinker.core.model

data class ShortUrl(
    val shortCode: String,
    val shortUrl: String,
    val longUrl: String,
    val title: String?,
    val tags: List<String>,
)

data class ShortUrlPage(val items: List<ShortUrl>, val currentPage: Int, val pagesCount: Int)

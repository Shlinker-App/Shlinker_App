package at.awit.shlinker.feature.shorturls

import at.awit.shlinker.core.model.ShortUrlDetails
import at.awit.shlinker.core.model.ShortUrlEdit
import at.awit.shlinker.core.model.parseTagInput

data class ShortUrlEditForm(
    val longUrl: String,
    val title: String,
    val tags: List<String>,
    val tagInput: String = "",
    val validSince: String,
    val validUntil: String,
    val maxVisits: String,
    val crawlable: Boolean?,
    val forwardQuery: Boolean?,
) {
    fun toInput(original: ShortUrlDetails) = ShortUrlEdit(
        original = original,
        longUrl = longUrl.trim(),
        title = if (title == original.url.title.orEmpty()) original.url.title else title.trim().ifEmpty { null },
        tags = if (tagInput.isBlank()) tags else (tags + parseTagInput(tagInput)).distinct(),
        validSince = validSince.trim().ifEmpty { null },
        validUntil = validUntil.trim().ifEmpty { null },
        maxVisits = maxVisits.trim().takeIf { it.isNotEmpty() }?.toLongOrNull(),
        crawlable = crawlable,
        forwardQuery = forwardQuery,
    )

    companion object {
        fun from(details: ShortUrlDetails) = ShortUrlEditForm(
            longUrl = details.url.longUrl,
            title = details.url.title.orEmpty(),
            tags = details.url.tags,
            validSince = details.validSince.orEmpty(),
            validUntil = details.validUntil.orEmpty(),
            maxVisits = details.maxVisits?.toString().orEmpty(),
            crawlable = details.crawlable,
            forwardQuery = details.forwardQuery,
        )
    }
}

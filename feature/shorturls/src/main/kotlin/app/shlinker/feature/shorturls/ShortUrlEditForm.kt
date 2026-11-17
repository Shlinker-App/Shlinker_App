package app.shlinker.feature.shorturls

import app.shlinker.core.model.ShortUrlDetails
import app.shlinker.core.model.ShortUrlEdit
import app.shlinker.core.model.parseShlinkDate
import app.shlinker.core.model.parseTagInput

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
    fun hasChanges(original: ShortUrlDetails): Boolean {
        val initial = from(original)
        // Include unfinished/invalid drafts, but stop warning once all edits have been reverted.
        return copy(tagInput = "") != initial || tagInput.isNotBlank()
    }

    fun dateValidation(original: ShortUrlDetails): EditValidation? {
        val since = validSince.trim().takeIf { it.isNotEmpty() }?.let {
            parseShlinkDate(it) ?: return EditValidation.SINCE
        }
        val until = validUntil.trim().takeIf { it.isNotEmpty() }?.let {
            parseShlinkDate(it) ?: return EditValidation.UNTIL
        }
        if (since != null && until != null && !until.isAfter(since)) {
            return if (validUntil == original.validUntil.orEmpty() && validSince != original.validSince.orEmpty()) {
                EditValidation.SINCE_RANGE
            } else {
                EditValidation.UNTIL_RANGE
            }
        }
        return null
    }

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

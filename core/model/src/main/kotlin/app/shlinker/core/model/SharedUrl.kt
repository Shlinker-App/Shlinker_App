package app.shlinker.core.model

fun extractSharedUrl(text: String): String? {
    val entireUrl = text.trim()
    if (isValidDestinationUrl(entireUrl)) return entireUrl
    val candidates = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE).findAll(text).map { match ->
        var value = match.value
        for ((open, close) in listOf('(' to ')', '[' to ']', '{' to '}')) {
            while (value.endsWith(close) && value.count { it == close } > value.count { it == open }) {
                value = value.dropLast(1)
            }
        }
        value
    }.distinct().toList()
    return candidates.singleOrNull()?.takeIf(::isValidDestinationUrl)
}

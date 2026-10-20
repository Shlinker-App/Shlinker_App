package at.awit.linkwerk.core.network

/** Unterstützte Shlink REST API. */
object ShlinkApi {
    const val VERSION = 3
    const val API_KEY_HEADER = "X-Api-Key"

    fun basePath(serverUrl: String): String = "${serverUrl.trimEnd('/')}/rest/v$VERSION"
}

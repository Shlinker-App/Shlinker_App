package at.awit.shlinker.core.model

import java.net.URI
import java.net.URISyntaxException

data class ServerDomain(val domain: String, val isDefault: Boolean)

fun isValidShlinkDomain(value: String): Boolean {
    val uri = try {
        URI("https://$value")
    } catch (_: URISyntaxException) {
        return false
    }
    return !uri.host.isNullOrBlank() &&
        uri.rawAuthority == value &&
        uri.port == -1 &&
        uri.userInfo == null &&
        uri.rawPath.isNullOrEmpty() &&
        uri.query == null &&
        uri.fragment == null
}

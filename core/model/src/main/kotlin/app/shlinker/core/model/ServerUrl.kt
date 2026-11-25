package app.shlinker.core.model

import java.net.URI
import java.net.URISyntaxException

/** Validates and normalizes server URLs. */
object ServerUrl {

    sealed interface Result {
        data class Valid(val url: String) : Result

        data class Invalid(val reason: Reason) : Result
    }

    enum class Reason {
        EMPTY,
        MALFORMED,
        INSECURE_HTTP,
        UNSUPPORTED_SCHEME,
        HAS_QUERY_OR_FRAGMENT,
    }

    /**
     * Normalizes, for example, `  Short.Example.org/ ` to `https://short.example.org`.
     *
     * - Trim surrounding whitespace and default to `https://` when no scheme is present.
     * - Lowercase the host and omit the default port 443.
     * - Strip trailing slashes and any copied `/rest` or `/rest/vN` suffix.
     * - Reject `http://` to avoid sending API keys in cleartext.
     * - Keep base paths such as `/s` when Shlink is behind a reverse proxy.
     */
    fun normalize(input: String): Result {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result.Invalid(Reason.EMPTY)

        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"

        val uri = try {
            URI(withScheme)
        } catch (_: URISyntaxException) {
            return Result.Invalid(Reason.MALFORMED)
        }

        when (uri.scheme?.lowercase()) {
            "https" -> Unit
            "http" -> return Result.Invalid(Reason.INSECURE_HTTP)
            else -> return Result.Invalid(Reason.UNSUPPORTED_SCHEME)
        }

        val host = uri.host?.lowercase()
        if (host.isNullOrBlank() || uri.userInfo != null) return Result.Invalid(Reason.MALFORMED)
        if (uri.rawQuery != null || uri.rawFragment != null) return Result.Invalid(Reason.HAS_QUERY_OR_FRAGMENT)

        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        val path = (uri.rawPath ?: "")
            .trimEnd('/')
            .replace(REST_SUFFIX, "")
            .trimEnd('/')

        return Result.Valid("https://$host$port$path")
    }

    private val REST_SUFFIX = Regex("/rest(/v\\d+)?$", RegexOption.IGNORE_CASE)
}

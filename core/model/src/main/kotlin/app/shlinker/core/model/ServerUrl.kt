package app.shlinker.core.model

import java.net.URI
import java.net.URISyntaxException

/** Prüft und vereinheitlicht eingegebene Server-URLs. */
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
     * Normalisiert z. B. `  Short.Example.org/ ` zu `https://short.example.org`.
     *
     * - Leerzeichen entfernen, fehlendes Schema → `https://`
     * - Host in Kleinbuchstaben, Standard-Port 443 entfernen
     * - Abschließende `/` und ein versehentlich mitkopiertes `/rest` bzw. `/rest/vN` entfernen
     * - `http://` wird abgelehnt (API-Key würde im Klartext übertragen)
     * - Unterpfade bleiben erhalten (Shlink hinter Reverse Proxy unter `/s` o. ä.)
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

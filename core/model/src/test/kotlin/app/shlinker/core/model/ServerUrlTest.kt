package app.shlinker.core.model

import app.shlinker.core.model.ServerUrl.Reason
import app.shlinker.core.model.ServerUrl.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlTest {

    private fun valid(input: String, expected: String) =
        assertEquals(Result.Valid(expected), ServerUrl.normalize(input))

    private fun invalid(input: String, reason: Reason) =
        assertEquals(Result.Invalid(reason), ServerUrl.normalize(input))

    @Test fun `adds HTTPS and trims whitespace`() = valid("  s.example.org ", "https://s.example.org")

    @Test fun `lowercases the host`() = valid("https://S.Example.ORG", "https://s.example.org")

    @Test fun `strips trailing slashes`() = valid("https://s.example.org///", "https://s.example.org")

    @Test fun `omits the default port 443`() = valid("https://s.example.org:443/", "https://s.example.org")

    @Test fun `keeps a custom port`() = valid("s.example.org:8443", "https://s.example.org:8443")

    @Test fun `keeps the base path`() = valid("https://example.org/shlink/", "https://example.org/shlink")

    @Test fun `strips a copied rest v3 suffix`() = valid("https://s.example.org/rest/v3/", "https://s.example.org")

    @Test fun `strips a copied rest suffix`() = valid("https://example.org/shlink/rest", "https://example.org/shlink")

    @Test fun `accepts an IP address`() = valid("192.168.1.10:8080", "https://192.168.1.10:8080")

    @Test fun `rejects blank input`() = invalid("   ", Reason.EMPTY)

    @Test fun `rejects HTTP`() = invalid("http://s.example.org", Reason.INSECURE_HTTP)

    @Test fun `rejects an unsupported scheme`() = invalid("ftp://s.example.org", Reason.UNSUPPORTED_SCHEME)

    @Test fun `rejects a query string`() = invalid("https://s.example.org/?a=b", Reason.HAS_QUERY_OR_FRAGMENT)

    @Test fun `rejects credentials in the URL`() = invalid("https://user:pw@s.example.org", Reason.MALFORMED)

    @Test fun `rejects a malformed URL`() = invalid("https://exa mple.org", Reason.MALFORMED)
}

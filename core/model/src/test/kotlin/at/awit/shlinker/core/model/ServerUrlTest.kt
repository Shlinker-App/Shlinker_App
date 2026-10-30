package at.awit.shlinker.core.model

import at.awit.shlinker.core.model.ServerUrl.Reason
import at.awit.shlinker.core.model.ServerUrl.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlTest {

    private fun valid(input: String, expected: String) =
        assertEquals(Result.Valid(expected), ServerUrl.normalize(input))

    private fun invalid(input: String, reason: Reason) =
        assertEquals(Result.Invalid(reason), ServerUrl.normalize(input))

    @Test fun `ergaenzt https und entfernt Leerzeichen`() = valid("  s.example.org ", "https://s.example.org")

    @Test fun `Host wird kleingeschrieben`() = valid("https://S.Example.ORG", "https://s.example.org")

    @Test fun `entfernt abschliessende Slashes`() = valid("https://s.example.org///", "https://s.example.org")

    @Test fun `entfernt Standardport 443`() = valid("https://s.example.org:443/", "https://s.example.org")

    @Test fun `behaelt eigenen Port`() = valid("s.example.org:8443", "https://s.example.org:8443")

    @Test fun `behaelt Unterpfad`() = valid("https://example.org/shlink/", "https://example.org/shlink")

    @Test fun `entfernt mitkopiertes rest v3`() = valid("https://s.example.org/rest/v3/", "https://s.example.org")

    @Test fun `entfernt mitkopiertes rest`() = valid("https://example.org/shlink/rest", "https://example.org/shlink")

    @Test fun `IP-Adresse ist erlaubt`() = valid("192.168.1.10:8080", "https://192.168.1.10:8080")

    @Test fun `leer`() = invalid("   ", Reason.EMPTY)

    @Test fun `http wird abgelehnt`() = invalid("http://s.example.org", Reason.INSECURE_HTTP)

    @Test fun `fremdes Schema`() = invalid("ftp://s.example.org", Reason.UNSUPPORTED_SCHEME)

    @Test fun `Query wird abgelehnt`() = invalid("https://s.example.org/?a=b", Reason.HAS_QUERY_OR_FRAGMENT)

    @Test fun `Zugangsdaten in URL werden abgelehnt`() = invalid("https://user:pw@s.example.org", Reason.MALFORMED)

    @Test fun `kaputte URL`() = invalid("https://exa mple.org", Reason.MALFORMED)
}

package at.awit.shlinker.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerDomainTest {
    @Test fun domainValidationAcceptsOnlyHostnamesWithoutSchemePathPortOrCredentials() {
        for (domain in listOf("short.example", "s.example.org", "localhost", "127.0.0.1")) {
            assertTrue(domain, isValidShlinkDomain(domain))
        }
        for (domain in listOf(
            "", " ", "https://short.example", "short.example/path", "short.example:443",
            "user@short.example", "short.example?x=1", "short.example#fragment", "short.example ",
        )) {
            assertFalse(domain, isValidShlinkDomain(domain))
        }
    }
}

package at.awit.shlinker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedUrlTest {
    @Test fun exactHttpUrlRetainsQueryFragmentUnicodeAndBalancedParentheses() {
        for (url in listOf(
            "https://example.com/a?foo=1&bar=%2F#part",
            "http://example.com",
            "https://example.com/über?q=a+b",
            "https://example.com/wiki/Example_(test)",
        )) {
            assertEquals(url, extractSharedUrl(url))
            assertEquals(url, extractSharedUrl("Read more:\n<$url>\nShared from my browser"))
        }
        assertEquals("https://example.com/a", extractSharedUrl("See (https://example.com/a)"))
        assertEquals("https://example.com/a)", extractSharedUrl("https://example.com/a)"))
    }

    @Test fun missingAmbiguousUnsupportedAndCredentialedUrlsNeverProduceGuessedDestination() {
        for (text in listOf(
            "",
            "just text",
            "example.com",
            "ftp://example.com",
            "javascript:alert(1)",
            "https://user:password@example.com",
            "https://",
            "https://one.example https://two.example",
        )) {
            assertNull(text, extractSharedUrl(text))
        }
        assertEquals("https://same.example", extractSharedUrl("https://same.example https://same.example"))
    }
}

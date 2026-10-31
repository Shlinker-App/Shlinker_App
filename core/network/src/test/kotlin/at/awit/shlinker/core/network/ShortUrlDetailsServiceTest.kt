package at.awit.shlinker.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortUrlDetailsServiceTest {
    private val clients = mutableListOf<HttpClient>()
    private val requests = mutableListOf<HttpRequestData>()
    private val response = """
        {"shortCode":"abc","shortUrl":"https://custom.example/abc","longUrl":"https://example.org",
         "title":"Title","tags":["one","two"],"domain":"custom.example","dateCreated":"2026-10-30T12:00:00+02:00",
         "visitsSummary":{"total":12345678901,"nonBots":12345678900,"bots":1},
         "meta":{"validSince":"2026-11-01T10:00:00Z","validUntil":"2027-01-01T00:00:00Z","maxVisits":99999999999},
         "crawlable":false,"forwardQuery":true,"hasRedirectRules":true,"future":"ignored"}
    """.trimIndent()

    private fun service(
        body: String = response,
        status: HttpStatusCode = HttpStatusCode.OK,
        error: Exception? = null,
    ): ShortUrlDetailsService {
        val client = HttpClient(
            MockEngine {
                requests += it
                error?.let { failure -> throw failure }
                respond(body, status)
            },
        )
        clients += client
        return KtorShortUrlDetailsService(client, Json { ignoreUnknownKeys = true })
    }

    @After fun cleanup() = clients.forEach { it.close() }

    @Test fun authenticatedGetPreservesDomainAndLoadsAllReadOnlyFieldsWithoutFollowingShlink() = runTest {
        val details = service().get("https://api.example/shlink", "key", "abc", "custom.example")
        val request = requests.single()
        assertEquals("GET", request.method.value)
        assertEquals("api.example", request.url.host)
        assertEquals("/shlink/rest/v3/short-urls/abc", request.url.encodedPath)
        assertEquals("key", request.headers[ShlinkApi.API_KEY_HEADER])
        assertEquals("custom.example", request.url.parameters["domain"])
        assertEquals(12345678901L, details.url.visits)
        assertEquals(12345678900L, details.nonBotVisits)
        assertEquals(1L, details.botVisits)
        assertEquals("Title", details.url.title)
        assertEquals(listOf("one", "two"), details.url.tags)
        assertEquals("2026-10-30T12:00:00+02:00", details.dateCreated)
        assertEquals("2026-11-01T10:00:00Z", details.validSince)
        assertEquals("2027-01-01T00:00:00Z", details.validUntil)
        assertEquals(99999999999L, details.maxVisits)
        assertTrue(details.metaAvailable)
        assertEquals(false, details.crawlable)
        assertEquals(true, details.forwardQuery)
        assertEquals(true, details.hasRedirectRules)
    }

    @Test fun defaultDomainOmitsQueryAndUnknownFieldsDoNotBecomeFalseZeroOrUnlimited() = runTest {
        val minimal = """{"shortCode":"abc","shortUrl":"https://s.example/abc","longUrl":"https://example.org"}"""
        val details = service(minimal).get("https://api.example", "key", "abc", null)
        assertNull(requests.single().url.parameters["domain"])
        assertNull(details.url.visits)
        assertNull(details.crawlable)
        assertNull(details.forwardQuery)
        assertNull(details.hasRedirectRules)
        assertFalse(details.metaAvailable)
        val unlimited = service(
            minimal.dropLast(1) + ""","meta":{"validSince":null,"validUntil":null,"maxVisits":null}}""",
        )
            .get("https://api.example", "key", "abc", null)
        assertTrue(unlimited.metaAvailable)
        assertNull(unlimited.maxVisits)
    }

    @Test fun shortCodeIsEncodedAsOnePathSegmentNotQueryOrOtherEndpoint() = runTest {
        val code = "folder/a?b#c"
        service(response.replace("\"shortCode\":\"abc\"", "\"shortCode\":\"$code\""))
            .get("https://api.example", "key", code, "custom.example")
        assertEquals("/rest/v3/short-urls/folder%2Fa%3Fb%23c", requests.single().url.encodedPath)
        assertEquals(setOf("domain"), requests.single().url.parameters.names())
    }

    @Test fun failuresNeverBecomeSuccessfulEmptyDetails() = runTest {
        for (status in listOf(401, 403, 404, 500)) {
            try {
                service("{}", HttpStatusCode.fromValue(status)).get("https://api.example", "key", "abc", null)
                throw AssertionError("Expected HTTP failure")
            } catch (e: ShortUrlRequestException) {
                if (status in listOf(401, 403)) {
                    assertEquals(ConnectionError.InvalidApiKey, e.error)
                } else {
                    assertEquals(ConnectionError.HttpError(status, null), e.error)
                }
            }
        }
        for (body in listOf(
            "{}",
            "<html/>",
            response.replace("\"shortCode\":\"abc\"", "\"shortCode\":\"other\""),
            response.replace("\"domain\":\"custom.example\"", "\"domain\":\"other.example\""),
            response.replace("\"total\":12345678901", "\"total\":-1"),
        )) {
            try {
                service(body).get("https://api.example", "key", "abc", "custom.example")
                throw AssertionError("Expected invalid response")
            } catch (_: InvalidShortUrlResponseException) {
                // Expected.
            }
        }
    }

    @Test fun cancellationPropagatesAndNetworkFailuresAreClassified() = runTest {
        try {
            service(error = CancellationException("cancel")).get("https://api.example", "key", "abc", null)
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, requests.size)
        }
        try {
            service(error = UnknownHostException("dns")).get("https://api.example", "key", "abc", null)
            throw AssertionError("Expected network error")
        } catch (e: ShortUrlRequestException) {
            assertEquals(ConnectionError.HostNotFound, e.error)
        }
    }
}

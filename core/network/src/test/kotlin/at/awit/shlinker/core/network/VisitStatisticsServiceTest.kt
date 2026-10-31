package at.awit.shlinker.core.network

import at.awit.shlinker.core.model.VisitStatistics
import at.awit.shlinker.core.model.VisitWindow
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitStatisticsServiceTest {
    private val clients = mutableListOf<HttpClient>()
    private val requests = mutableListOf<HttpRequestData>()
    private val window = VisitWindow(Instant.parse("2026-10-01T22:00:00Z"), Instant.parse("2026-10-31T04:30:00Z"))

    private fun body(total: Long) = """{"visits":{"data":[{"future":"not decoded or retained"}],"pagination":""" +
        """{"currentPage":1,"itemsPerPage":1,"totalItems":$total}}}"""

    private fun service(
        bodies: List<String> = listOf(body(12345678901), body(12345678900)),
        status: Int = 200,
        failure: Exception? = null,
    ): VisitStatisticsService {
        var index = 0
        val client = HttpClient(
            MockEngine {
                requests += it
                failure?.let { error -> throw error }
                respond(bodies[index++.coerceAtMost(bodies.lastIndex)], HttpStatusCode.fromValue(status))
            },
        )
        clients += client
        return KtorVisitStatisticsService(
            client,
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }

    @After fun cleanup() = clients.forEach { it.close() }

    @Test fun exactPinnedDomainAndTimeWindowWithOneItemPerRequestAndNoFalseBotParameter() = runTest {
        assertEquals(
            VisitStatistics(12345678901, 12345678900),
            service().get("https://api.example/base", "key", "a/b?#", "custom.example", window),
        )
        assertEquals(2, requests.size)
        for (request in requests) {
            assertEquals("GET", request.method.value)
            assertEquals("api.example", request.url.host)
            assertEquals("/base/rest/v3/short-urls/a%2Fb%3F%23/visits", request.url.encodedPath)
            assertEquals("key", request.headers[ShlinkApi.API_KEY_HEADER])
            assertEquals("custom.example", request.url.parameters["domain"])
            assertEquals("1", request.url.parameters["page"])
            assertEquals("1", request.url.parameters["itemsPerPage"])
            assertEquals(window.since.toString(), request.url.parameters["startDate"])
            assertEquals(window.until.toString(), request.url.parameters["endDate"])
        }
        assertFalse("Presence means true on Shlink", "excludeBots" in requests.first().url.parameters.names())
        assertEquals("true", requests.last().url.parameters["excludeBots"])
    }

    @Test fun allTimeOmitsStartAndDefaultDomainWhilePreservingRealZero() = runTest {
        val all = window.copy(since = null)
        assertEquals(VisitStatistics(0, 0), service(listOf(body(0))).get("https://api.example", "key", "a", null, all))
        assertEquals(2, requests.size)
        requests.forEach {
            assertFalse("domain" in it.url.parameters.names())
            assertFalse("startDate" in it.url.parameters.names())
            assertEquals(window.until.toString(), it.url.parameters["endDate"])
        }
    }

    @Test fun malformedMissingNegativeOrInconsistentCountsNeverBecomeZeroOrPartialSuccess() = runTest {
        for (invalid in listOf(
            "{}",
            """{"visits":{}}""",
            body(-1),
            body(1).replace("totalItems", "missing"),
            body(1).replace("\"itemsPerPage\":1", "\"itemsPerPage\":100"),
            body(1).replace("\"currentPage\":1", "\"currentPage\":2"),
            body(1).replace("\"totalItems\":1", "\"totalItems\":null"),
        )) {
            try {
                service(listOf(invalid)).get("https://api.example", "key", "a", null, window)
                throw AssertionError("Expected invalid count")
            } catch (_: InvalidVisitStatisticsException) {
                assertTrue(requests.isNotEmpty())
            }
        }
        for (bodies in listOf(listOf(body(5), body(6)), listOf(body(5), "{}"))) {
            try {
                service(bodies).get("https://api.example", "key", "a", null, window)
                throw AssertionError("Expected invalid second count")
            } catch (_: InvalidVisitStatisticsException) {
                assertTrue(requests.isNotEmpty())
            }
        }
    }

    @Test fun authMissingUnsupportedServerAndNetworkErrorsAreExplicitAndCancellationPropagates() = runTest {
        for (status in listOf(401, 403, 404, 500, 204)) {
            val before = requests.size
            try {
                service(status = status).get("https://api.example", "key", "a", null, window)
                throw AssertionError("Expected HTTP failure")
            } catch (e: ShortUrlRequestException) {
                if (status in listOf(401, 403)) {
                    assertEquals(ConnectionError.InvalidApiKey, e.error)
                } else {
                    assertEquals(ConnectionError.HttpError(status, null), e.error)
                }
            }
            assertEquals(before + 1, requests.size)
        }
        try {
            service(failure = IOException("offline")).get("https://api.example", "key", "a", null, window)
            throw AssertionError("Expected network error")
        } catch (_: ShortUrlRequestException) {
            assertEquals(6, requests.size)
        }
        try {
            service(failure = CancellationException("cancel")).get("https://api.example", "key", "a", null, window)
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(7, requests.size)
        }
    }
}

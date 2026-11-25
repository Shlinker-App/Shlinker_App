package app.shlinker.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServerOverviewServiceTest {
    private val clients = mutableListOf<HttpClient>()
    private val requests = mutableListOf<HttpRequestData>()

    private fun body(root: String, total: Long) =
        """{"$root":{"data":[{"ignored":true}],"pagination":{"currentPage":1,"itemsPerPage":1,"totalItems":$total}}}"""

    private fun service(body: String, status: Int = 200): ServerOverviewService {
        val client = HttpClient(
            MockEngine {
                requests += it
                respond(body, HttpStatusCode.fromValue(status))
            },
        )
        clients += client
        return KtorServerOverviewService(client, Json { ignoreUnknownKeys = true })
    }

    @After fun cleanup() = clients.forEach { it.close() }

    @Test fun shlinkCountReadsTotalWithOneItemAndNoBotFilter() = runTest {
        assertEquals(1234L, service(body("shortUrls", 1234)).shlinkCount("https://api.example/base", "key"))
        val request = requests.single()
        assertEquals("/base/rest/v3/short-urls", request.url.encodedPath)
        assertEquals("key", request.headers[ShlinkApi.API_KEY_HEADER])
        assertEquals("1", request.url.parameters["page"])
        assertEquals("1", request.url.parameters["itemsPerPage"])
        assertFalse("excludeBots" in request.url.parameters.names())
    }

    @Test fun humanClickCountUsesNonOrphanVisitsWithoutBots() = runTest {
        assertEquals(0L, service(body("visits", 0)).humanClickCount("https://api.example", "key"))
        val request = requests.single()
        assertEquals("/rest/v3/visits/non-orphan", request.url.encodedPath)
        assertEquals("true", request.url.parameters["excludeBots"])
        assertEquals("1", request.url.parameters["itemsPerPage"])
    }

    @Test fun invalidCountsAreNeverReportedAsZero() = runTest {
        for (invalid in listOf("{}", body("visits", -1), body("visits", 1).replace("totalItems", "missing"))) {
            try {
                service(invalid).humanClickCount("https://api.example", "key")
                throw AssertionError("Expected invalid count for $invalid")
            } catch (_: InvalidServerOverviewException) {
            }
        }
    }

    @Test fun authAndHttpFailuresAreExplicit() = runTest {
        for (status in listOf(401, 403, 500)) {
            try {
                service(body("shortUrls", 1), status).shlinkCount("https://api.example", "key")
                throw AssertionError("Expected HTTP failure")
            } catch (e: ShortUrlRequestException) {
                val expected = if (status == 500) {
                    ConnectionError.HttpError(500, null)
                } else {
                    ConnectionError.InvalidApiKey
                }
                assertEquals(expected, e.error)
            }
        }
    }
}

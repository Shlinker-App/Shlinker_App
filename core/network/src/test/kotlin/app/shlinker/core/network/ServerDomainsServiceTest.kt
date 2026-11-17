package app.shlinker.core.network

import app.shlinker.core.model.ServerDomain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerDomainsServiceTest {
    private val clients = mutableListOf<HttpClient>()
    private val requests = mutableListOf<HttpRequestData>()

    private fun service(body: String, status: Int = 200, failure: Exception? = null): ServerDomainsService {
        val client = HttpClient(
            MockEngine {
                requests += it
                failure?.let { error -> throw error }
                respond(body, HttpStatusCode.fromValue(status))
            },
        )
        clients += client
        return KtorServerDomainsService(client, Json { ignoreUnknownKeys = true })
    }

    @After fun cleanup() = clients.forEach { it.close() }

    @Test fun authenticatedGetPreservesDomainDefaultFlagAndNeverInfersFromApiHost() = runTest {
        val body = """{"domains":{"data":[
            {"domain":"z.example","isDefault":false,"redirects":{}},
            {"domain":"short.example","isDefault":true,"redirects":{}},
            {"domain":"a.example","isDefault":false}],"defaultRedirects":{}}}"""
        assertEquals(
            listOf(
                ServerDomain("short.example", true),
                ServerDomain("a.example", false),
                ServerDomain("z.example", false),
            ),
            service(body).get("https://api.example/base", "key"),
        )
        val request = requests.single()
        assertEquals("/base/rest/v3/domains", request.url.encodedPath)
        assertEquals("key", request.headers[ShlinkApi.API_KEY_HEADER])
        assertEquals("GET", request.method.value)
        assertTrue(request.url.parameters.isEmpty())
    }

    @Test fun emptyOrRestrictedListDoesNotInventDefaultDomain() = runTest {
        assertTrue(service("""{"domains":{"data":[]}}""").get("https://api.example", "key").isEmpty())
        assertEquals(
            listOf(ServerDomain("custom.example", false)),
            service("""{"domains":{"data":[{"domain":"custom.example","isDefault":false}]}}""")
                .get("https://api.example", "restricted"),
        )
    }

    @Test fun missingMalformedAmbiguousAndInvalidDomainDataFailExplicitly() = runTest {
        for (body in listOf(
            "{}",
            """{"domains":{}}""",
            """{"domains":{"data":null}}""",
            """{"domains":{"data":[{"domain":"example.com"}]}}""",
            """{"domains":{"data":[{"domain":"https://example.com","isDefault":true}]}}""",
            """{"domains":{"data":[{"domain":"a.example","isDefault":true},{"domain":"b.example","isDefault":true}]}}""",
            """{"domains":{"data":[{"domain":"a.example","isDefault":false},{"domain":"A.example","isDefault":false}]}}""",
        )) {
            try {
                service(body).get("https://api.example", "key")
                throw AssertionError("Expected invalid domains")
            } catch (_: InvalidServerDomainsException) {
                assertTrue(true)
            }
        }
    }

    @Test fun authHttpAndCancellationAreNotEmptySuccessOrAutomaticallyRetried() = runTest {
        for (status in listOf(401, 403, 404, 500)) {
            val before = requests.size
            try {
                service("{}", status).get("https://api.example", "key")
                throw AssertionError("Expected status error")
            } catch (e: ShortUrlRequestException) {
                assertEquals(before + 1, requests.size)
                if (status in listOf(401, 403)) assertEquals(ConnectionError.InvalidApiKey, e.error)
            }
        }
        val before = requests.size
        try {
            service("{}", failure = CancellationException()).get("https://api.example", "key")
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(before + 1, requests.size)
        }
    }
}

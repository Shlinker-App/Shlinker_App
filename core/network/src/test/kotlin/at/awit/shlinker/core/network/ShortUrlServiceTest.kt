package at.awit.shlinker.core.network

import at.awit.shlinker.core.model.ShortUrlSort
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShortUrlServiceTest {
    private val clients = mutableListOf<HttpClient>()
    private val requests = mutableListOf<HttpRequestData>()

    @After fun tearDown() = clients.forEach { it.close() }

    private fun service(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        error: Exception? = null,
    ): ShortUrlService {
        val client = HttpClient(
            MockEngine { request ->
                requests += request
                if (error != null) throw error
                respond(body, status)
            },
        ) { expectSuccess = false }
        clients += client
        return KtorShortUrlService(client, Json { ignoreUnknownKeys = true })
    }

    private val response = """
        {"shortUrls":{"data":[{
          "shortCode":"abc","shortUrl":"https://s.example/abc",
          "longUrl":"https://example.org/article","title":"Article",
          "tags":["news","work"],"domain":null,"visitsSummary":{"total":12}
        }],"pagination":{"currentPage":2,"pagesCount":3,"totalItems":65}}}
    """.trimIndent()

    @Test fun listDecodesAndSendsAuthenticatedPaginationAndSearch() = runTest {
        val page = service(response).list("https://s.example/shlink/", "secret", 2, 30, "a & b")
        val request = requests.single()
        assertEquals("/shlink/rest/v3/short-urls", request.url.encodedPath)
        assertEquals("secret", request.headers[ShlinkApi.API_KEY_HEADER])
        assertEquals("2", request.url.parameters["page"])
        assertEquals("30", request.url.parameters["itemsPerPage"])
        assertEquals("dateCreated-DESC", request.url.parameters["orderBy"])
        assertEquals("a & b", request.url.parameters["searchTerm"])
        assertEquals(2, page.currentPage)
        assertEquals(3, page.pagesCount)
        assertEquals("Article", page.items.single().title)
        assertEquals(listOf("news", "work"), page.items.single().tags)
        assertEquals("https://example.org/article", page.items.single().longUrl)
        assertEquals(12L, page.items.single().visits)
    }

    @Test fun emptyResponseAndBlankSearch() = runTest {
        val page = service("""{"shortUrls":{"data":[],"pagination":{"currentPage":1,"pagesCount":0}}}""")
            .list("https://s.example", "secret", 1, 30, "")
        assertTrue(page.items.isEmpty())
        assertEquals(null, requests.single().url.parameters["searchTerm"])
    }

    @Test fun allSortModesSendExactShlinkOrderByWithoutLocallyReorderingResults() = runTest {
        val expected = listOf("dateCreated-DESC", "dateCreated-ASC", "visits-DESC", "visits-ASC")
        for ((index, sort) in ShortUrlSort.entries.withIndex()) {
            val page = service(response).list("https://s.example", "secret", 2, 30, "query", sort)
            assertEquals(expected[index], requests.last().url.parameters["orderBy"])
            assertEquals("query", requests.last().url.parameters["searchTerm"])
            assertEquals("2", requests.last().url.parameters["page"])
            assertEquals("abc", page.items.single().shortCode)
        }
    }

    @Test fun nullableTitleIsSupported() = runTest {
        val page = service(response.replace("\"Article\"", "null"))
            .list("https://s.example", "secret", 2, 30, "")
        assertEquals(null, page.items.single().title)
    }

    @Test fun visitCountsSupportLargeValuesZeroLegacyAndMissingWithoutGuessing() = runTest {
        for ((json, expected) in listOf(
            "\"visitsSummary\":{\"total\":12345678901}" to 12345678901L,
            "\"visitsSummary\":{\"total\":0}" to 0L,
            "\"visitsCount\":42" to 42L,
            "\"other\":true" to null,
        )) {
            val page = service(response.replace("\"visitsSummary\":{\"total\":12}", json))
                .list("https://s.example", "secret", 2, 30, "")
            assertEquals(expected, page.items.single().visits)
        }
        failure<InvalidShortUrlResponseException> {
            service(response.replace("\"total\":12", "\"total\":-1"))
                .list("https://s.example", "secret", 2, 30, "")
        }
    }

    @Test fun domainIdentityIsPreservedForDetailNavigation() = runTest {
        val page = service(response.replace("\"domain\":null", "\"domain\":\"custom.example\""))
            .list("https://s.example", "secret", 2, 30, "")
        assertEquals("custom.example", page.items.single().domain)
    }

    @Test fun authorizationErrorsAreExplicit() = runTest {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden)) {
            val error = failure<ShortUrlRequestException> {
                service("{}", status).list("https://s.example", "secret", 1, 30, "")
            }
            assertEquals(ConnectionError.InvalidApiKey, error.error)
        }
    }

    @Test fun serverErrorDoesNotBecomeEmptyList() = runTest {
        val error = failure<ShortUrlRequestException> {
            service("<html>Error</html>", HttpStatusCode.ServiceUnavailable)
                .list("https://s.example", "secret", 1, 30, "")
        }
        assertEquals(ConnectionError.HttpError(503, null), error.error)
    }

    @Test fun malformedResponsesAndWrongPageAreRejected() = runTest {
        for (body in listOf("<html/>", "{}", response.replace("\"currentPage\":2", "\"currentPage\":1"))) {
            failure<InvalidShortUrlResponseException> {
                service(body).list("https://s.example", "secret", 2, 30, "")
            }
        }
    }

    @Test fun networkFailureIsClassified() = runTest {
        val error = failure<ShortUrlRequestException> {
            service("", error = UnknownHostException("test"))
                .list("https://s.example", "secret", 1, 30, "")
        }
        assertEquals(ConnectionError.HostNotFound, error.error)
    }

    @Test fun timeoutIsClassified() = runTest {
        val error = failure<ShortUrlRequestException> {
            service("", error = SocketTimeoutException("timeout"))
                .list("https://s.example", "secret", 1, 30, "")
        }
        assertEquals(ConnectionError.Timeout, error.error)
    }

    @Test fun cancellationPropagates() = runTest {
        failure<CancellationException> {
            service("", error = CancellationException("cancelled"))
                .list("https://s.example", "secret", 1, 30, "")
        }
    }

    private suspend inline fun <reified T : Throwable> failure(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw error
        }
        fail("Expected ${T::class.simpleName}")
        error("unreachable")
    }
}

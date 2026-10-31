package at.awit.shlinker.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerTagsServiceTest {
    private val clients = mutableListOf<HttpClient>()
    private var calls = 0

    private fun service(body: String, status: Int = 200, error: Exception? = null): ServerTagsService {
        val client = HttpClient(
            MockEngine {
                calls++
                assertEquals("GET", it.method.value)
                assertEquals("api.example", it.url.host)
                assertEquals("/base/rest/v3/tags", it.url.encodedPath)
                assertEquals("key", it.headers[ShlinkApi.API_KEY_HEADER])
                assertTrue(it.url.parameters.isEmpty())
                error?.let { failure -> throw failure }
                respond(body, HttpStatusCode.fromValue(status))
            },
        )
        clients += client
        return KtorServerTagsService(client, Json { ignoreUnknownKeys = true })
    }

    @After fun cleanup() = clients.forEach { it.close() }

    @Test fun loadsAllAuthenticatedTagsWithoutPagingLimitAndPreservesTagSpelling() = runTest {
        val result = service(
            """{"tags":{"data":["work","My app","work","Alpha"],"pagination":{"pagesCount":1}},"future":true}""",
        ).get("https://api.example/base", "key")
        assertEquals(listOf("Alpha", "My app", "work"), result)
        assertEquals(1, calls)
        assertEquals(
            emptyList<String>(),
            service("""{"tags":{"data":[]}}""").get("https://api.example/base", "key"),
        )
    }

    @Test fun malformedOrMissingTagsNeverBecomeEmptySuccessfulSuggestions() = runTest {
        for (body in listOf("{}", """{"tags":{}}""", """{"tags":{"data":[""]}}""", """{"tags":{"data":[null]}}""")) {
            try {
                service(body).get("https://api.example/base", "key")
                throw AssertionError("Expected invalid tags")
            } catch (_: InvalidServerTagsException) {
                assertTrue(true)
            }
        }
    }

    @Test fun permissionsAndNetworkErrorsAreExplicitAndCancellationPropagates() = runTest {
        for (status in listOf(401, 403, 404, 500)) {
            try {
                service("{}", status).get("https://api.example/base", "key")
                throw AssertionError("Expected failure")
            } catch (e: ShortUrlRequestException) {
                if (status in listOf(401, 403)) {
                    assertEquals(ConnectionError.InvalidApiKey, e.error)
                } else {
                    assertTrue(e.error is ConnectionError.HttpError)
                }
            }
        }
        try {
            service("{}", error = IOException("network")).get("https://api.example/base", "key")
            throw AssertionError("Expected network failure")
        } catch (_: ShortUrlRequestException) {
            assertEquals(5, calls)
        }
        try {
            service("{}", error = CancellationException("cancel")).get("https://api.example/base", "key")
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(6, calls)
        }
    }
}

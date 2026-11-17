package app.shlinker.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteShortUrlServiceTest {
    private val clients = mutableListOf<HttpClient>()
    private val requests = mutableListOf<HttpRequestData>()

    private fun service(status: Int = 204, error: Exception? = null): DeleteShortUrlService {
        val client = HttpClient(
            MockEngine {
                requests += it
                error?.let { failure -> throw failure }
                respond("", HttpStatusCode.fromValue(status))
            },
        )
        clients += client
        return KtorDeleteShortUrlService(client)
    }

    @After fun cleanup() = clients.forEach { it.close() }

    @Test fun authenticatedDeleteUsesEncodedCodeAndOptionalDomainWithoutForceOrVisitingShortUrl() = runTest {
        service().delete("https://api.example/base", "key", "a/b?#", "custom.example")
        val request = requests.single()
        assertEquals("DELETE", request.method.value)
        assertEquals("api.example", request.url.host)
        assertEquals("/base/rest/v3/short-urls/a%2Fb%3F%23", request.url.encodedPath)
        assertEquals("key", request.headers[ShlinkApi.API_KEY_HEADER])
        assertEquals(setOf("domain"), request.url.parameters.names())
        assertEquals("custom.example", request.url.parameters["domain"])
        service().delete("https://api.example", "key", "abc", null)
        assertTrue(requests.last().url.parameters.isEmpty())
    }

    @Test fun only204ConfirmsDeletionAndThresholdProtectionIsNotBypassed() = runTest {
        for ((status, expected) in listOf(
            401 to DeleteShortUrlFailure.UNAUTHORIZED,
            403 to DeleteShortUrlFailure.UNAUTHORIZED,
            404 to DeleteShortUrlFailure.NOT_FOUND,
            422 to DeleteShortUrlFailure.VISITS_THRESHOLD,
            500 to DeleteShortUrlFailure.UNCERTAIN,
            200 to DeleteShortUrlFailure.UNCERTAIN,
        )) {
            try {
                service(status).delete("https://api.example", "key", "abc", null)
                throw AssertionError("Expected failure")
            } catch (e: DeleteShortUrlException) {
                assertEquals(expected, e.failure)
            }
        }
        assertEquals(6, requests.size)
    }

    @Test fun lostResponseIsUncertainAndCancellationPropagatesWithoutRetry() = runTest {
        for (error in listOf(IOException("lost"), CancellationException("cancel"))) {
            try {
                service(error = error).delete("https://api.example", "key", "abc", null)
                throw AssertionError("Expected failure")
            } catch (e: CancellationException) {
                assertTrue(error is CancellationException)
            } catch (e: DeleteShortUrlException) {
                assertEquals(DeleteShortUrlFailure.UNCERTAIN, e.failure)
            }
        }
        assertEquals(2, requests.size)
    }
}

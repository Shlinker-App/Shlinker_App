package at.awit.shlinker.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.net.ConnectException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionTesterTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val requests = mutableListOf<HttpRequestData>()

    private fun tester(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        KtorConnectionTester(
            HttpClient(
                MockEngine { req ->
                    requests += req
                    handler(req)
                },
            ) { expectSuccess = false },
            json,
        )

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        type: String = "application/json",
    ) = respond(body, status, headersOf(HttpHeaders.ContentType, type))

    private val healthy = """{"status":"pass","version":"5.1.7","links":{}}"""

    @Test
    fun success() = runTest {
        val t = tester { req ->
            when (req.url.encodedPath) {
                "/rest/health" -> json(healthy, type = "application/health+json")
                "/rest/v3/short-urls" -> json("""{"shortUrls":{"data":[],"pagination":{}}}""")
                else -> error("unexpected ${req.url}")
            }
        }
        assertEquals(ConnectionResult.Success("5.1.7"), t.test("https://s.example.org", "key"))
        val auth = requests.last()
        assertEquals("key", auth.headers[ShlinkApi.API_KEY_HEADER])
        assertEquals("1", auth.url.parameters["itemsPerPage"])
        // Health ohne Key
        assertEquals(null, requests.first().headers[ShlinkApi.API_KEY_HEADER])
    }

    @Test
    fun unterpfadWirdBeibehalten() = runTest {
        val t = tester { req ->
            if (req.url.encodedPath == "/shlink/rest/health") json(healthy) else json("{}")
        }
        t.test("https://example.org/shlink", "key")
        assertEquals("/shlink/rest/v3/short-urls", requests.last().url.encodedPath)
    }

    @Test
    fun invalidApiKey() = runTest {
        val t = tester { req ->
            if (req.url.encodedPath == "/rest/health") {
                json(healthy)
            } else {
                json(
                    """{"type":"https://shlink.io/api/error/invalid-api-key","title":"Invalid API key","status":401}""",
                    HttpStatusCode.Unauthorized,
                    "application/problem+json",
                )
            }
        }
        assertEquals(ConnectionResult.Failure(ConnectionError.InvalidApiKey), t.test("https://s.example.org", "x"))
    }

    @Test
    fun notShlinkBeiHtml404() = runTest {
        val t = tester { json("<html>Not Found</html>", HttpStatusCode.NotFound, "text/html") }
        assertEquals(ConnectionResult.Failure(ConnectionError.NotShlink(404)), t.test("https://s.example.org", "x"))
    }

    @Test
    fun unhealthy() = runTest {
        val t = tester { json("""{"status":"fail","version":"5.1.7"}""", HttpStatusCode.ServiceUnavailable) }
        assertEquals(ConnectionResult.Failure(ConnectionError.Unhealthy("5.1.7")), t.test("https://s.example.org", "x"))
    }

    @Test
    fun zuAlteVersion() = runTest {
        val t = tester { json("""{"status":"pass","version":"2.10.0"}""") }
        assertEquals(
            ConnectionResult.Failure(ConnectionError.UnsupportedVersion("2.10.0")),
            t.test("https://s.example.org", "x"),
        )
    }

    @Test
    fun serverFehler() = runTest {
        val t = tester { req ->
            if (req.url.encodedPath == "/rest/health") {
                json(healthy)
            } else {
                json("""{"title":"Internal","detail":"DB kaputt"}""", HttpStatusCode.InternalServerError)
            }
        }
        assertEquals(
            ConnectionResult.Failure(ConnectionError.HttpError(500, "DB kaputt")),
            t.test("https://s.example.org", "x"),
        )
    }

    @Test
    fun netzwerkfehlerWerdenKlassifiziert() = runTest {
        fun classify(e: Throwable) = KtorConnectionTester.classify(e)

        assertEquals(ConnectionError.HostNotFound, classify(UnknownHostException("s.example.org")))
        assertEquals(ConnectionError.Unreachable, classify(ConnectException("refused")))
        assertEquals(ConnectionError.Timeout, classify(java.net.SocketTimeoutException()))
        assertEquals(
            ConnectionError.Certificate(CertificateProblem.UNTRUSTED),
            classify(
                SSLHandshakeException("x").apply {
                    initCause(CertPathValidatorException("Trust anchor not found"))
                },
            ),
        )
        assertEquals(
            ConnectionError.Certificate(CertificateProblem.HOSTNAME_MISMATCH),
            classify(SSLPeerUnverifiedException("Hostname not verified")),
        )
        assertEquals(ConnectionError.Certificate(CertificateProblem.OTHER), classify(SSLHandshakeException("x")))
    }

    @Test
    fun exceptionAusEngineWirdZuFailure() = runTest {
        val t = tester { throw UnknownHostException("nope") }
        assertEquals(ConnectionResult.Failure(ConnectionError.HostNotFound), t.test("https://nope.example", "x"))
    }
}

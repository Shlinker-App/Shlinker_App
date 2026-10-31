package at.awit.shlinker.core.network

import at.awit.shlinker.core.model.CreateShortUrl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateShortUrlServiceTest {
    private val response = """
        {"shortCode":"abc","shortUrl":"https://s.example/abc",
         "longUrl":"https://example.org","tags":["work"]}
    """.trimIndent()

    @Test fun authenticatedPostIncludesOptionalFieldsAndDecodesResult() = runTest {
        var request: HttpRequestData? = null
        val client = HttpClient(
            MockEngine {
                request = it
                respond(response, HttpStatusCode.Created)
            },
        )
        try {
            val service = KtorCreateShortUrlService(
                client,
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
            val result = service.create(
                "https://s.example/shlink",
                "secret",
                CreateShortUrl(
                    "https://example.org",
                    "Title",
                    "slug",
                    "custom.example",
                    listOf("work"),
                    forwardQuery = false,
                    crawlable = false,
                ),
            )
            val sent = requireNotNull(request)
            assertEquals("POST", sent.method.value)
            assertEquals("/shlink/rest/v3/short-urls", sent.url.encodedPath)
            assertEquals("secret", sent.headers[ShlinkApi.API_KEY_HEADER])
            val body = Json.parseToJsonElement(
                (sent.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
            ).jsonObject
            assertEquals("slug", body["customSlug"]?.jsonPrimitive?.content)
            assertEquals("custom.example", body["domain"]?.jsonPrimitive?.content)
            assertEquals("Title", body["title"]?.jsonPrimitive?.content)
            assertEquals("false", body["forwardQuery"]?.jsonPrimitive?.content)
            assertEquals("false", body["crawlable"]?.jsonPrimitive?.content)
            assertEquals("https://s.example/abc", result.shortUrl)
            assertEquals(listOf("work"), result.tags)
        } finally {
            client.close()
        }
    }

    @Test fun emptyOptionalFieldsAreOmittedSoShlinkUsesDefaults() = runTest {
        val client = HttpClient(
            MockEngine {
                val body = Json.parseToJsonElement(
                    (it.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                ).jsonObject
                assertEquals(setOf("longUrl"), body.keys)
                assertFalse(body.containsKey("findIfExists"))
                respond(response, HttpStatusCode.Created)
            },
        )
        try {
            KtorCreateShortUrlService(client, Json { explicitNulls = false })
                .create("https://s.example", "key", CreateShortUrl("https://example.org"))
        } finally {
            client.close()
        }
    }

    @Test fun rejectedInputConflictAndPermissionsAreExplicit() = runTest {
        for ((status, body, expected) in listOf(
            Triple(
                400,
                """{"type":"https://shlink.io/api/error/non-unique-slug"}""",
                CreateShortUrlFailure.SLUG_CONFLICT,
            ),
            Triple(400, "{}", CreateShortUrlFailure.INVALID_INPUT),
            Triple(403, "{}", CreateShortUrlFailure.UNAUTHORIZED),
            Triple(500, "{}", CreateShortUrlFailure.SERVER),
            Triple(201, "{}", CreateShortUrlFailure.UNCERTAIN),
        )) {
            val client = HttpClient(MockEngine { respond(body, HttpStatusCode.fromValue(status)) })
            try {
                KtorCreateShortUrlService(
                    client,
                    Json,
                ).create("https://s.example", "key", CreateShortUrl("https://example.org"))
                throw AssertionError("Expected failure")
            } catch (e: CreateShortUrlException) {
                assertEquals(expected, e.failure)
            } finally {
                client.close()
            }
        }
    }

    @Test fun lostResponseIsUncertainAndCancellationPropagatesWithoutRetry() = runTest {
        for (error in listOf(IOException("lost"), CancellationException("cancel"))) {
            var requests = 0
            val client = HttpClient(
                MockEngine {
                    requests++
                    throw error
                },
            )
            try {
                KtorCreateShortUrlService(
                    client,
                    Json,
                ).create("https://s.example", "key", CreateShortUrl("https://example.org"))
                throw AssertionError("Expected failure")
            } catch (e: Exception) {
                if (error is CancellationException) {
                    assertTrue(e is CancellationException)
                } else {
                    assertEquals(CreateShortUrlFailure.UNCERTAIN, (e as CreateShortUrlException).failure)
                }
                assertEquals(1, requests)
            } finally {
                client.close()
            }
        }
    }
}

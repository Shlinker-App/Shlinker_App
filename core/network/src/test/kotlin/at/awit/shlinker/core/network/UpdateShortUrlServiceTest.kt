package at.awit.shlinker.core.network

import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.model.ShortUrlDetails
import at.awit.shlinker.core.model.ShortUrlEdit
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateShortUrlServiceTest {
    private val clients = mutableListOf<HttpClient>()
    private val requests = mutableListOf<HttpRequestData>()
    private val original = ShortUrlDetails(
        ShortUrl(
            "a/b?#",
            "https://custom.example/a",
            "https://example.org",
            "Title",
            listOf("old"),
            domain = "custom.example",
        ),
        validSince = "2026-11-01T10:00:00Z",
        maxVisits = 10,
        metaAvailable = true,
        crawlable = true,
        forwardQuery = true,
        hasRedirectRules = true,
    )
    private val response = """
        {"shortCode":"a/b?#","shortUrl":"https://custom.example/a","longUrl":"https://new.example",
         "title":null,"tags":[],"domain":"custom.example","meta":{"validSince":null,"validUntil":null,"maxVisits":null},
         "crawlable":false,"forwardQuery":false,"hasRedirectRules":true}
    """.trimIndent()

    private fun service(body: String = response, status: Int = 200, error: Exception? = null): UpdateShortUrlService {
        val client = HttpClient(
            MockEngine {
                requests += it
                error?.let { failure -> throw failure }
                respond(body, HttpStatusCode.fromValue(status))
            },
        )
        clients += client
        return KtorUpdateShortUrlService(
            client,
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }

    @After fun cleanup() = clients.forEach { it.close() }

    @Test fun patchUsesPinnedIdentityEncodedCodeAndExplicitNullsToClearFields() = runTest {
        val result = service().update(
            "https://api.example/base",
            "key",
            ShortUrlEdit(
                original,
                longUrl = "https://new.example",
                title = null,
                tags = emptyList(),
                validSince = null,
                maxVisits = null,
                crawlable = false,
                forwardQuery = false,
            ),
        )
        val request = requests.single()
        assertEquals("PATCH", request.method.value)
        assertEquals("/base/rest/v3/short-urls/a%2Fb%3F%23", request.url.encodedPath)
        assertEquals("custom.example", request.url.parameters["domain"])
        assertEquals("key", request.headers[ShlinkApi.API_KEY_HEADER])
        val body = Json.parseToJsonElement(
            (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
        ).jsonObject
        assertEquals(
            setOf("longUrl", "title", "tags", "validSince", "maxVisits", "crawlable", "forwardQuery"),
            body.keys,
        )
        for (field in listOf("title", "validSince", "maxVisits")) assertEquals(JsonNull, body[field])
        assertEquals("[]", body["tags"].toString())
        assertEquals("false", body["forwardQuery"]?.jsonPrimitive?.content)
        assertEquals("https://new.example", result.url.longUrl)
        assertEquals(true, result.hasRedirectRules)
        assertFalse(body.containsKey("domain"))
    }

    @Test fun unchangedFieldsAndUnknownFlagsAreNeverOverwrittenAndNoOpSendsNothing() = runTest {
        val unknown = ShortUrlDetails(original.url.copy(shortCode = "abc", domain = null))
        val svc = service(response.replace("a/b?#", "abc").replace("\"custom.example\"", "null"))
        assertEquals(unknown, svc.update("https://api.example", "key", ShortUrlEdit(unknown)))
        assertTrue(requests.isEmpty())
        svc.update("https://api.example", "key", ShortUrlEdit(unknown, title = "Changed"))
        val request = requests.single()
        assertEquals(null, request.url.parameters["domain"])
        val body = Json.parseToJsonElement(
            (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
        ).jsonObject
        assertEquals(setOf("title"), body.keys)
    }

    @Test fun invalidInputNeverSendsPatch() = runTest {
        for (input in listOf(
            ShortUrlEdit(original, longUrl = "javascript:alert(1)"),
            ShortUrlEdit(original, maxVisits = 0),
            ShortUrlEdit(original, validUntil = "yesterday"),
            ShortUrlEdit(original, validUntil = "2026-01-01T00:00:00Z"),
        )) {
            try {
                service().update("https://api.example", "key", input)
                throw AssertionError("Expected validation error")
            } catch (e: UpdateShortUrlException) {
                assertEquals(UpdateShortUrlFailure.INVALID_INPUT, e.failure)
            }
        }
        assertTrue(requests.isEmpty())
    }

    @Test fun httpErrorsMalformedAndWrongIdentityResponsesAreExplicit() = runTest {
        for ((status, body, expected) in listOf(
            Triple(400, "{}", UpdateShortUrlFailure.INVALID_INPUT),
            Triple(422, "{}", UpdateShortUrlFailure.INVALID_INPUT),
            Triple(401, "{}", UpdateShortUrlFailure.UNAUTHORIZED),
            Triple(403, "{}", UpdateShortUrlFailure.UNAUTHORIZED),
            Triple(404, "{}", UpdateShortUrlFailure.NOT_FOUND),
            Triple(500, "{}", UpdateShortUrlFailure.SERVER),
            Triple(200, "{}", UpdateShortUrlFailure.UNCERTAIN),
            Triple(200, response.replace("a/b?#", "wrong"), UpdateShortUrlFailure.UNCERTAIN),
            Triple(200, response.replace("\"custom.example\"", "\"wrong.example\""), UpdateShortUrlFailure.UNCERTAIN),
        )) {
            try {
                service(body, status).update("https://api.example", "key", ShortUrlEdit(original, title = "New"))
                throw AssertionError("Expected update failure")
            } catch (e: UpdateShortUrlException) {
                assertEquals(expected, e.failure)
            }
        }
        assertEquals(9, requests.size)
    }

    @Test fun lostResponseIsUncertainAndCancellationPropagatesWithoutRetry() = runTest {
        for (error in listOf(IOException("lost"), CancellationException("cancel"))) {
            try {
                service(error = error).update("https://api.example", "key", ShortUrlEdit(original, title = "New"))
                throw AssertionError("Expected failure")
            } catch (e: CancellationException) {
                assertTrue(error is CancellationException)
            } catch (e: UpdateShortUrlException) {
                assertEquals(UpdateShortUrlFailure.UNCERTAIN, e.failure)
            }
        }
        assertEquals(2, requests.size)
    }
}

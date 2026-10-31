package at.awit.shlinker.core.network

import at.awit.shlinker.core.model.RedirectCondition
import at.awit.shlinker.core.model.RedirectConditionType
import at.awit.shlinker.core.model.RedirectRule
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedirectRulesServiceTest {
    private val clients = mutableListOf<HttpClient>()
    private val requests = mutableListOf<HttpRequestData>()
    private val rule = RedirectRule("https://android.example", listOf(RedirectCondition("device", null, "android")))
    private val body = """
        {"defaultLongUrl":"https://default.example","redirectRules":[
        {"longUrl":"https://android.example","priority":1,"conditions":[
        {"type":"device","matchKey":null,"matchValue":"android"}]}]}
    """.trimIndent()

    private fun service(response: String = body, status: Int = 200, failure: Exception? = null): RedirectRulesService {
        val client = HttpClient(
            MockEngine {
                requests += it
                failure?.let { error -> throw error }
                respond(response, HttpStatusCode.fromValue(status))
            },
        )
        clients += client
        return KtorRedirectRulesService(
            client,
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }

    @After fun cleanup() = clients.forEach { it.close() }

    @Test fun getUsesEncodedIdentityKeyDomainAndKeepsUnknownConditions() = runTest {
        val result = service(
            body.replace("device", "future-type"),
        ).get("https://api.example/base", "key", "a/b?#", "custom.example")
        val request = requests.single()
        assertEquals("GET", request.method.value)
        assertEquals("/base/rest/v3/short-urls/a%2Fb%3F%23/redirect-rules", request.url.encodedPath)
        assertEquals("custom.example", request.url.parameters["domain"])
        assertEquals("key", request.headers[ShlinkApi.API_KEY_HEADER])
        assertTrue(result.hasUnknownConditions)
        assertEquals("android", result.rules.single().conditions.single().matchValue)
    }

    @Test fun postReplacesCompleteOrderedListWithExplicitNullsAndNoDefaultMutation() = runTest {
        assertEquals(listOf(rule), service().set("https://api.example", "key", "same", null, listOf(rule)).rules)
        val request = requests.single()
        assertEquals("POST", request.method.value)
        assertFalse(request.url.parameters.contains("domain"))
        val payload = Json.parseToJsonElement(
            (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
        ).jsonObject
        assertEquals(setOf("redirectRules"), payload.keys)
        val sent = payload.getValue("redirectRules").jsonArray.single().jsonObject
        assertEquals(setOf("longUrl", "conditions"), sent.keys)
        assertEquals(JsonNull, sent.getValue("conditions").jsonArray.single().jsonObject["matchKey"])
    }

    @Test fun emptyListIsExplicitlySentAndEmptyResponseIsNotConfusedWithMissingData() = runTest {
        val empty = """{"defaultLongUrl":"https://default.example","redirectRules":[]}"""
        assertTrue(service(empty).set("https://api.example", "key", "same", null, emptyList()).rules.isEmpty())
        val payload = (requests.single().body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        assertEquals("""{"redirectRules":[]}""", payload)
        for (bad in listOf(
            "{}",
            """{"defaultLongUrl":"https://default.example"}""",
            body.replace("\"priority\":1", "\"priority\":0"),
        )) {
            try {
                service(bad).get("https://api.example", "key", "same", null)
                throw AssertionError("Expected malformed response")
            } catch (e: RedirectRulesException) {
                assertEquals(RedirectRulesFailure.INVALID_RESPONSE, e.failure)
            }
        }
    }

    @Test fun prioritiesAreSortedAndDuplicatesRejected() = runTest {
        val two = body.replace(
            "\"redirectRules\":[",
            """"redirectRules":[{"longUrl":"https://second.example","priority":2,"conditions":[]},""",
        )
        val result = service(two).get("https://api.example", "key", "same", null)
        assertEquals(listOf("https://android.example", "https://second.example"), result.rules.map { it.longUrl })
        try {
            service(two.replace("\"priority\":2", "\"priority\":1")).get("https://api.example", "key", "same", null)
            throw AssertionError("Expected duplicate priority failure")
        } catch (e: RedirectRulesException) {
            assertEquals(RedirectRulesFailure.INVALID_RESPONSE, e.failure)
        }
    }

    @Test fun allConditionTypesAndRuleOrderRoundTripWithoutDroppingFields() = runTest {
        val conditions = RedirectConditionType.entries.map { type ->
            RedirectCondition(
                type.apiValue,
                if (type.hasKey) "campaign" else null,
                when (type) {
                    RedirectConditionType.DEVICE -> "android"
                    RedirectConditionType.BROWSER -> "chrome"
                    RedirectConditionType.COUNTRY -> "AT"
                    RedirectConditionType.BEFORE, RedirectConditionType.AFTER -> "2026-11-01T10:00:00Z"
                    else -> if (type.hasValue) "example" else null
                },
            )
        }
        val rules = listOf(rule.copy(conditions = conditions), RedirectRule("https://second.example", emptyList()))
        val conditionsJson = conditions.joinToString(",") { c ->
            val key = c.matchKey?.let { "\"$it\"" } ?: "null"
            val value = c.matchValue?.let { "\"$it\"" } ?: "null"
            """{"type":"${c.type}","matchKey":$key,"matchValue":$value}"""
        }
        val response = """{"defaultLongUrl":"https://default.example","redirectRules":[
                {"longUrl":"https://android.example","priority":1,"conditions":[$conditionsJson]},
                {"longUrl":"https://second.example","priority":2,"conditions":[]}]}"""
        assertEquals(rules, service(response).set("https://api.example", "key", "same", null, rules).rules)
        val payload = Json.parseToJsonElement(
            (requests.single().body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
        ).jsonObject.getValue("redirectRules").jsonArray
        assertEquals(
            rules.map {
                it.longUrl
            },
            payload.map { it.jsonObject.getValue("longUrl").jsonPrimitive.content },
        )
        assertEquals(
            RedirectConditionType.entries.map { it.apiValue },
            payload.first().jsonObject.getValue("conditions").jsonArray.map {
                it.jsonObject.getValue("type").jsonPrimitive.content
            },
        )
    }

    @Test fun writeErrorsAreExplicitAndUncertainResponsesCannotBeRetriedAsSuccess() = runTest {
        for ((status, expected) in listOf(
            400 to RedirectRulesFailure.INVALID_INPUT,
            422 to RedirectRulesFailure.INVALID_INPUT,
            401 to RedirectRulesFailure.UNAUTHORIZED,
            403 to RedirectRulesFailure.UNAUTHORIZED,
            404 to RedirectRulesFailure.NOT_FOUND,
            405 to RedirectRulesFailure.UNSUPPORTED,
            500 to RedirectRulesFailure.UNCERTAIN,
            204 to RedirectRulesFailure.UNCERTAIN,
        )) {
            try {
                service(status = status).set("https://api.example", "key", "same", "custom.example", listOf(rule))
                throw AssertionError("Expected status error")
            } catch (e: RedirectRulesException) {
                assertEquals(expected, e.failure)
            }
        }
        for (service in listOf(
            service("{}"),
            service(failure = IOException()),
            service(body.replace("android.example", "other.example")),
        )) {
            val before = requests.size
            try {
                service.set("https://api.example", "key", "same", null, listOf(rule))
                throw AssertionError("Expected uncertain result")
            } catch (e: RedirectRulesException) {
                assertEquals(RedirectRulesFailure.UNCERTAIN, e.failure)
                assertEquals(before + 1, requests.size)
            }
        }
    }

    @Test fun invalidInputNeverWritesAndCancellationPropagates() = runTest {
        try {
            service().set("https://api.example", "key", "same", null, listOf(rule.copy(longUrl = "bad")))
            throw AssertionError("Expected input failure")
        } catch (e: RedirectRulesException) {
            assertEquals(RedirectRulesFailure.INVALID_INPUT, e.failure)
            assertTrue(requests.isEmpty())
        }
        try {
            service(failure = CancellationException()).set("https://api.example", "key", "same", null, listOf(rule))
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, requests.size)
        }
    }
}

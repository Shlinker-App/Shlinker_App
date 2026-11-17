package app.shlinker.core.network

import app.shlinker.core.model.RedirectCondition
import app.shlinker.core.model.RedirectRule
import app.shlinker.core.model.RedirectRules
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

interface RedirectRulesService {
    suspend fun get(serverUrl: String, apiKey: String, shortCode: String, domain: String?): RedirectRules
    suspend fun set(
        serverUrl: String,
        apiKey: String,
        shortCode: String,
        domain: String?,
        rules: List<RedirectRule>,
    ): RedirectRules
}

enum class RedirectRulesFailure {
    INVALID_INPUT,
    UNAUTHORIZED,
    NOT_FOUND,
    UNSUPPORTED,
    SERVER,
    INVALID_RESPONSE,
    UNCERTAIN,
}

class RedirectRulesException(val failure: RedirectRulesFailure) : IOException("Redirect rules request failed: $failure")

internal class KtorRedirectRulesService @Inject constructor(private val client: HttpClient, private val json: Json) :
    RedirectRulesService {
    override suspend fun get(serverUrl: String, apiKey: String, shortCode: String, domain: String?): RedirectRules {
        val response = client.get("${ShlinkApi.basePath(serverUrl)}/short-urls") {
            url { appendPathSegments(shortCode, "redirect-rules", encodeSlash = true) }
            header(ShlinkApi.API_KEY_HEADER, apiKey)
            domain?.let { parameter("domain", it) }
        }
        checkStatus(response, writing = false)
        return decode(response, writing = false)
    }

    override suspend fun set(
        serverUrl: String,
        apiKey: String,
        shortCode: String,
        domain: String?,
        rules: List<RedirectRule>,
    ): RedirectRules {
        if (rules.any { !it.isValid }) throw RedirectRulesException(RedirectRulesFailure.INVALID_INPUT)
        val body = buildJsonObject {
            put(
                "redirectRules",
                JsonArray(
                    rules.map { rule ->
                        buildJsonObject {
                            put("longUrl", JsonPrimitive(rule.longUrl))
                            put(
                                "conditions",
                                JsonArray(
                                    rule.conditions.map { condition ->
                                        buildJsonObject {
                                            put("type", JsonPrimitive(condition.type))
                                            put("matchKey", JsonPrimitive(condition.matchKey))
                                            put("matchValue", JsonPrimitive(condition.matchValue))
                                        }
                                    },
                                ),
                            )
                        }
                    },
                ),
            )
        }
        val response = try {
            client.post("${ShlinkApi.basePath(serverUrl)}/short-urls") {
                url { appendPathSegments(shortCode, "redirect-rules", encodeSlash = true) }
                header(ShlinkApi.API_KEY_HEADER, apiKey)
                domain?.let { parameter("domain", it) }
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        } catch (_: IOException) {
            throw RedirectRulesException(RedirectRulesFailure.UNCERTAIN)
        }
        checkStatus(response, writing = true)
        val result = decode(response, writing = true)
        if (result.rules != rules) throw RedirectRulesException(RedirectRulesFailure.UNCERTAIN)
        return result
    }

    private fun checkStatus(response: HttpResponse, writing: Boolean) {
        val failure = when (response.status.value) {
            200 -> return
            400, 422 -> RedirectRulesFailure.INVALID_INPUT
            401, 403 -> RedirectRulesFailure.UNAUTHORIZED
            404 -> RedirectRulesFailure.NOT_FOUND
            405, 501 -> RedirectRulesFailure.UNSUPPORTED
            else -> if (writing) RedirectRulesFailure.UNCERTAIN else RedirectRulesFailure.SERVER
        }
        throw RedirectRulesException(failure)
    }

    private suspend fun decode(response: HttpResponse, writing: Boolean): RedirectRules {
        val failure = if (writing) RedirectRulesFailure.UNCERTAIN else RedirectRulesFailure.INVALID_RESPONSE
        val dto = try {
            json.decodeFromString<RulesDto>(response.bodyAsText())
        } catch (_: SerializationException) {
            throw RedirectRulesException(failure)
        } catch (_: IOException) {
            throw RedirectRulesException(failure)
        }
        if (dto.defaultLongUrl.isBlank() ||
            dto.redirectRules.any { it.priority < 1 || it.longUrl.isBlank() } ||
            dto.redirectRules.map { it.priority }.distinct().size != dto.redirectRules.size
        ) {
            throw RedirectRulesException(failure)
        }
        return RedirectRules(
            dto.defaultLongUrl,
            dto.redirectRules.sortedBy { it.priority }.map { rule ->
                RedirectRule(
                    rule.longUrl,
                    rule.conditions.map {
                        RedirectCondition(it.type, it.matchKey, it.matchValue)
                    },
                )
            },
        )
    }
}

@Serializable
private data class RulesDto(val defaultLongUrl: String, val redirectRules: List<RuleDto>)

@Serializable
private data class RuleDto(val longUrl: String, val priority: Int, val conditions: List<ConditionDto>)

@Serializable
private data class ConditionDto(val type: String, val matchKey: String?, val matchValue: String?)

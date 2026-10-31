package at.awit.shlinker.core.network

import at.awit.shlinker.core.model.ShortUrlDetails
import at.awit.shlinker.core.model.ShortUrlEdit
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

interface UpdateShortUrlService {
    suspend fun update(serverUrl: String, apiKey: String, input: ShortUrlEdit): ShortUrlDetails
}

enum class UpdateShortUrlFailure { INVALID_INPUT, UNAUTHORIZED, NOT_FOUND, SERVER, UNCERTAIN }

class UpdateShortUrlException(val failure: UpdateShortUrlFailure) : IOException("Could not update shlink: $failure")

internal class KtorUpdateShortUrlService @Inject constructor(private val client: HttpClient, private val json: Json) :
    UpdateShortUrlService {
    override suspend fun update(serverUrl: String, apiKey: String, input: ShortUrlEdit): ShortUrlDetails {
        if (!input.isValid) throw UpdateShortUrlException(UpdateShortUrlFailure.INVALID_INPUT)
        val original = input.original
        val body = buildJsonObject {
            fun <T> changed(name: String, before: T, after: T, value: JsonElement) {
                if (before != after) put(name, value)
            }
            changed("longUrl", original.url.longUrl, input.longUrl, JsonPrimitive(input.longUrl))
            changed("title", original.url.title, input.title, JsonPrimitive(input.title))
            changed("tags", original.url.tags, input.tags, JsonArray(input.tags.map(::JsonPrimitive)))
            changed("validSince", original.validSince, input.validSince, JsonPrimitive(input.validSince))
            changed("validUntil", original.validUntil, input.validUntil, JsonPrimitive(input.validUntil))
            changed("maxVisits", original.maxVisits, input.maxVisits, input.maxVisits?.let(::JsonPrimitive) ?: JsonNull)
            if (input.crawlable != null) {
                changed("crawlable", original.crawlable, input.crawlable, JsonPrimitive(input.crawlable))
            }
            if (input.forwardQuery != null) {
                changed("forwardQuery", original.forwardQuery, input.forwardQuery, JsonPrimitive(input.forwardQuery))
            }
        }
        if (body.isEmpty()) return original
        val response = try {
            client.patch("${ShlinkApi.basePath(serverUrl)}/short-urls") {
                url { appendPathSegments(original.url.shortCode, encodeSlash = true) }
                header(ShlinkApi.API_KEY_HEADER, apiKey)
                original.url.domain?.let { parameter("domain", it) }
                contentType(ContentType.Application.Json)
                // Explicit JSON null clears a value; omitted fields must remain untouched.
                setBody(body.toString())
            }
        } catch (_: IOException) {
            throw UpdateShortUrlException(UpdateShortUrlFailure.UNCERTAIN)
        }
        when (response.status.value) {
            400, 422 -> throw UpdateShortUrlException(UpdateShortUrlFailure.INVALID_INPUT)
            401, 403 -> throw UpdateShortUrlException(UpdateShortUrlFailure.UNAUTHORIZED)
            404 -> throw UpdateShortUrlException(UpdateShortUrlFailure.NOT_FOUND)
            !in 200..299 -> throw UpdateShortUrlException(UpdateShortUrlFailure.SERVER)
        }
        val details = try {
            json.decodeFromString<ShortUrlDto>(response.bodyAsText()).toDetails()
        } catch (_: SerializationException) {
            throw UpdateShortUrlException(UpdateShortUrlFailure.UNCERTAIN)
        } catch (_: IOException) {
            throw UpdateShortUrlException(UpdateShortUrlFailure.UNCERTAIN)
        }
        if (details.url.shortCode != original.url.shortCode || details.url.domain != original.url.domain) {
            throw UpdateShortUrlException(UpdateShortUrlFailure.UNCERTAIN)
        }
        return details
    }
}

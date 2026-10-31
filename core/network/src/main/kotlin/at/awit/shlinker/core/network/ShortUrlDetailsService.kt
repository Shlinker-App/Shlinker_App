package at.awit.shlinker.core.network

import at.awit.shlinker.core.model.ShortUrlDetails
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.appendPathSegments
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

interface ShortUrlDetailsService {
    suspend fun get(serverUrl: String, apiKey: String, shortCode: String, domain: String?): ShortUrlDetails
}

internal class KtorShortUrlDetailsService @Inject constructor(private val client: HttpClient, private val json: Json) :
    ShortUrlDetailsService {
    override suspend fun get(serverUrl: String, apiKey: String, shortCode: String, domain: String?): ShortUrlDetails {
        val response = try {
            client.get("${ShlinkApi.basePath(serverUrl)}/short-urls") {
                url { appendPathSegments(shortCode, encodeSlash = true) }
                header(ShlinkApi.API_KEY_HEADER, apiKey)
                domain?.let { parameter("domain", it) }
            }
        } catch (e: IOException) {
            throw ShortUrlRequestException(KtorConnectionTester.classify(e))
        }
        when (response.status.value) {
            401, 403 -> throw ShortUrlRequestException(ConnectionError.InvalidApiKey)
            !in 200..299 -> throw ShortUrlRequestException(ConnectionError.HttpError(response.status.value, null))
        }
        val dto = try {
            json.decodeFromString<ShortUrlDto>(response.bodyAsText())
        } catch (_: SerializationException) {
            throw InvalidShortUrlResponseException()
        } catch (e: IOException) {
            throw ShortUrlRequestException(KtorConnectionTester.classify(e))
        }
        if (dto.shortCode != shortCode || dto.domain != domain) throw InvalidShortUrlResponseException()
        return dto.toDetails()
    }
}

package at.awit.shlinker.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

interface ServerTagsService {
    suspend fun get(serverUrl: String, apiKey: String): List<String>
}

class InvalidServerTagsException : IOException("The server did not return valid tags")

internal class KtorServerTagsService @Inject constructor(private val client: HttpClient, private val json: Json) :
    ServerTagsService {
    override suspend fun get(serverUrl: String, apiKey: String): List<String> {
        val response = try {
            // Omitting itemsPerPage requests all tags, including on older API versions.
            client.get("${ShlinkApi.basePath(serverUrl)}/tags") { header(ShlinkApi.API_KEY_HEADER, apiKey) }
        } catch (e: IOException) {
            throw ShortUrlRequestException(KtorConnectionTester.classify(e))
        }
        when (response.status.value) {
            401, 403 -> throw ShortUrlRequestException(ConnectionError.InvalidApiKey)
            !in 200..299 -> throw ShortUrlRequestException(ConnectionError.HttpError(response.status.value, null))
        }
        val data = try {
            json.decodeFromString<TagsResponseDto>(response.bodyAsText()).tags.data
        } catch (_: SerializationException) {
            throw InvalidServerTagsException()
        } catch (e: IOException) {
            throw ShortUrlRequestException(KtorConnectionTester.classify(e))
        }
        if (data.any { it.isBlank() }) throw InvalidServerTagsException()
        return data.distinct().sortedWith(compareBy<String> { it.lowercase(java.util.Locale.ROOT) }.thenBy { it })
    }
}

@Serializable
private data class TagsResponseDto(val tags: TagsDto)

@Serializable
private data class TagsDto(val data: List<String>)

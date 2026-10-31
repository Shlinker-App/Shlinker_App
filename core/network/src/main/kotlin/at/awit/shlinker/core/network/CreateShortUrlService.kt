package at.awit.shlinker.core.network

import at.awit.shlinker.core.model.CreateShortUrl
import at.awit.shlinker.core.model.ShortUrl
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface CreateShortUrlService {
    suspend fun create(serverUrl: String, apiKey: String, input: CreateShortUrl): ShortUrl
}

enum class CreateShortUrlFailure { INVALID_INPUT, SLUG_CONFLICT, UNAUTHORIZED, SERVER, UNCERTAIN }

class CreateShortUrlException(val failure: CreateShortUrlFailure) : IOException("Could not create shlink: $failure")

internal class KtorCreateShortUrlService @Inject constructor(private val client: HttpClient, private val json: Json) :
    CreateShortUrlService {
    override suspend fun create(serverUrl: String, apiKey: String, input: CreateShortUrl): ShortUrl {
        val response = try {
            client.post("${ShlinkApi.basePath(serverUrl)}/short-urls") {
                header(ShlinkApi.API_KEY_HEADER, apiKey)
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        CreateDto(
                            input.longUrl,
                            input.title,
                            input.customSlug,
                            input.domain,
                            input.tags.takeIf {
                                it.isNotEmpty()
                            },
                            input.forwardQuery,
                            input.crawlable,
                        ),
                    ),
                )
            }
        } catch (_: IOException) {
            // A lost response does not prove that the server did not create the shlink.
            throw CreateShortUrlException(CreateShortUrlFailure.UNCERTAIN)
        }
        if (response.status.value !in 200..299) {
            val failure = when (response.status.value) {
                401, 403 -> CreateShortUrlFailure.UNAUTHORIZED
                409 -> CreateShortUrlFailure.SLUG_CONFLICT
                400, 422 -> {
                    val problem = try {
                        json.decodeFromString<ProblemDto>(response.bodyAsText())
                    } catch (_: SerializationException) {
                        null
                    } catch (_: IOException) {
                        null
                    }
                    if (problem?.type?.endsWith("/non-unique-slug") == true) {
                        CreateShortUrlFailure.SLUG_CONFLICT
                    } else {
                        CreateShortUrlFailure.INVALID_INPUT
                    }
                }
                else -> CreateShortUrlFailure.SERVER
            }
            throw CreateShortUrlException(failure)
        }
        val dto = try {
            json.decodeFromString<ShortUrlDto>(response.bodyAsText())
        } catch (_: SerializationException) {
            throw CreateShortUrlException(CreateShortUrlFailure.UNCERTAIN)
        } catch (_: IOException) {
            throw CreateShortUrlException(CreateShortUrlFailure.UNCERTAIN)
        }
        return try {
            dto.toModel()
        } catch (_: InvalidShortUrlResponseException) {
            throw CreateShortUrlException(CreateShortUrlFailure.UNCERTAIN)
        }
    }
}

@Serializable
private data class CreateDto(
    val longUrl: String,
    val title: String? = null,
    val customSlug: String? = null,
    val domain: String? = null,
    val tags: List<String>? = null,
    val forwardQuery: Boolean? = null,
    val crawlable: Boolean? = null,
)

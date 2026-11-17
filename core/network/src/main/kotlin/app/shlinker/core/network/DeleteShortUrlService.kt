package app.shlinker.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.appendPathSegments
import java.io.IOException
import javax.inject.Inject

interface DeleteShortUrlService {
    suspend fun delete(serverUrl: String, apiKey: String, shortCode: String, domain: String?)
}

enum class DeleteShortUrlFailure { UNAUTHORIZED, NOT_FOUND, VISITS_THRESHOLD, UNCERTAIN }

class DeleteShortUrlException(val failure: DeleteShortUrlFailure) : IOException("Could not delete shlink: $failure")

internal class KtorDeleteShortUrlService @Inject constructor(private val client: HttpClient) : DeleteShortUrlService {
    override suspend fun delete(serverUrl: String, apiKey: String, shortCode: String, domain: String?) {
        val response = try {
            client.delete("${ShlinkApi.basePath(serverUrl)}/short-urls") {
                url { appendPathSegments(shortCode, encodeSlash = true) }
                header(ShlinkApi.API_KEY_HEADER, apiKey)
                domain?.let { parameter("domain", it) }
            }
        } catch (_: IOException) {
            throw DeleteShortUrlException(DeleteShortUrlFailure.UNCERTAIN)
        }
        when (response.status.value) {
            204 -> Unit
            401, 403 -> throw DeleteShortUrlException(DeleteShortUrlFailure.UNAUTHORIZED)
            404 -> throw DeleteShortUrlException(DeleteShortUrlFailure.NOT_FOUND)
            422 -> throw DeleteShortUrlException(DeleteShortUrlFailure.VISITS_THRESHOLD)
            else -> throw DeleteShortUrlException(DeleteShortUrlFailure.UNCERTAIN)
        }
    }
}

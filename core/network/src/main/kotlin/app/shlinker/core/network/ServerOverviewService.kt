package app.shlinker.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

interface ServerOverviewService {
    suspend fun shlinkCount(serverUrl: String, apiKey: String): Long

    /** Counts visits to existing shlinks, excluding bots. */
    suspend fun humanClickCount(serverUrl: String, apiKey: String): Long
}

class InvalidServerOverviewException : IOException("The server did not return valid overview counts")

internal class KtorServerOverviewService @Inject constructor(private val client: HttpClient, private val json: Json) :
    ServerOverviewService {

    override suspend fun shlinkCount(serverUrl: String, apiKey: String): Long =
        count("${ShlinkApi.basePath(serverUrl)}/short-urls", apiKey, excludeBots = false) {
            json.decodeFromString<ShortUrlsCountResponse>(it).shortUrls.pagination
        }

    override suspend fun humanClickCount(serverUrl: String, apiKey: String): Long =
        count("${ShlinkApi.basePath(serverUrl)}/visits/non-orphan", apiKey, excludeBots = true) {
            json.decodeFromString<VisitsCountResponse>(it).visits.pagination
        }

    private suspend fun count(
        url: String,
        apiKey: String,
        excludeBots: Boolean,
        pagination: (String) -> CountPagination,
    ): Long {
        val response = try {
            client.get(url) {
                header(ShlinkApi.API_KEY_HEADER, apiKey)
                parameter("page", 1)
                parameter("itemsPerPage", 1)
                // Shlink treats the presence of excludeBots as true, even for the value "false".
                if (excludeBots) parameter("excludeBots", "true")
            }
        } catch (e: IOException) {
            throw ShortUrlRequestException(KtorConnectionTester.classify(e))
        }
        when (response.status.value) {
            401, 403 -> throw ShortUrlRequestException(ConnectionError.InvalidApiKey)
            200 -> Unit
            else -> throw ShortUrlRequestException(ConnectionError.HttpError(response.status.value, null))
        }
        val result = try {
            pagination(response.bodyAsText())
        } catch (_: SerializationException) {
            throw InvalidServerOverviewException()
        } catch (_: IllegalArgumentException) {
            throw InvalidServerOverviewException()
        } catch (e: IOException) {
            throw ShortUrlRequestException(KtorConnectionTester.classify(e))
        }
        if (result.currentPage != 1 || result.totalItems < 0) throw InvalidServerOverviewException()
        return result.totalItems
    }
}

@Serializable
private data class ShortUrlsCountResponse(val shortUrls: CountPage)

@Serializable
private data class VisitsCountResponse(val visits: CountPage)

@Serializable
private data class CountPage(val pagination: CountPagination)

@Serializable
private data class CountPagination(val currentPage: Int, val totalItems: Long)

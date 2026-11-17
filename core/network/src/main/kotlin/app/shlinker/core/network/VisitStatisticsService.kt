package app.shlinker.core.network

import app.shlinker.core.model.VisitStatistics
import app.shlinker.core.model.VisitWindow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.appendPathSegments
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

interface VisitStatisticsService {
    suspend fun get(
        serverUrl: String,
        apiKey: String,
        shortCode: String,
        domain: String?,
        window: VisitWindow,
    ): VisitStatistics
}

class InvalidVisitStatisticsException : IOException("The server did not return valid visit counts")

internal class KtorVisitStatisticsService @Inject constructor(private val client: HttpClient, private val json: Json) :
    VisitStatisticsService {
    override suspend fun get(
        serverUrl: String,
        apiKey: String,
        shortCode: String,
        domain: String?,
        window: VisitWindow,
    ): VisitStatistics {
        val total = count(serverUrl, apiKey, shortCode, domain, window, excludeBots = false)
        val nonBots = count(serverUrl, apiKey, shortCode, domain, window, excludeBots = true)
        if (nonBots > total) throw InvalidVisitStatisticsException()
        return VisitStatistics(total, nonBots)
    }

    private suspend fun count(
        serverUrl: String,
        apiKey: String,
        shortCode: String,
        domain: String?,
        window: VisitWindow,
        excludeBots: Boolean,
    ): Long {
        val response = try {
            client.get("${ShlinkApi.basePath(serverUrl)}/short-urls") {
                url {
                    appendPathSegments(shortCode, encodeSlash = true)
                    appendPathSegments("visits")
                }
                header(ShlinkApi.API_KEY_HEADER, apiKey)
                domain?.let { parameter("domain", it) }
                parameter("page", 1)
                parameter("itemsPerPage", 1)
                window.since?.let { parameter("startDate", it.toString()) }
                parameter("endDate", window.until.toString())
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
        val pagination = try {
            json.decodeFromString<VisitsResponse>(response.bodyAsText()).visits.pagination
        } catch (_: SerializationException) {
            throw InvalidVisitStatisticsException()
        } catch (e: IOException) {
            throw ShortUrlRequestException(KtorConnectionTester.classify(e))
        }
        if (pagination.currentPage != 1 || pagination.itemsPerPage != 1 || pagination.totalItems < 0) {
            throw InvalidVisitStatisticsException()
        }
        return pagination.totalItems
    }
}

@Serializable
private data class VisitsResponse(val visits: VisitCountsResponse)

@Serializable
private data class VisitCountsResponse(val pagination: VisitCountPagination)

@Serializable
private data class VisitCountPagination(val currentPage: Int, val itemsPerPage: Int, val totalItems: Long)

package at.awit.shlinker.core.network

import at.awit.shlinker.core.model.ShortUrlPage
import at.awit.shlinker.core.model.ShortUrlSort
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

interface ShortUrlService {
    suspend fun list(
        serverUrl: String,
        apiKey: String,
        page: Int,
        itemsPerPage: Int,
        searchTerm: String,
        sort: ShortUrlSort = ShortUrlSort.NEWEST,
    ): ShortUrlPage
}

class ShortUrlRequestException(val error: ConnectionError) : IOException("Short URL request failed")

class InvalidShortUrlResponseException : IOException("Invalid short URL response")

internal class KtorShortUrlService @Inject constructor(private val client: HttpClient, private val json: Json) :
    ShortUrlService {

    override suspend fun list(
        serverUrl: String,
        apiKey: String,
        page: Int,
        itemsPerPage: Int,
        searchTerm: String,
        sort: ShortUrlSort,
    ): ShortUrlPage {
        require(page > 0 && itemsPerPage > 0)
        val response = try {
            client.get("${ShlinkApi.basePath(serverUrl)}/short-urls") {
                header(ShlinkApi.API_KEY_HEADER, apiKey)
                parameter("page", page)
                parameter("itemsPerPage", itemsPerPage)
                parameter("orderBy", sort.orderBy)
                if (searchTerm.isNotBlank()) parameter("searchTerm", searchTerm)
            }
        } catch (e: IOException) {
            throw ShortUrlRequestException(KtorConnectionTester.classify(e))
        }
        when (response.status.value) {
            401, 403 -> throw ShortUrlRequestException(ConnectionError.InvalidApiKey)
            !in 200..299 -> throw ShortUrlRequestException(ConnectionError.HttpError(response.status.value, null))
        }
        val dto = try {
            json.decodeFromString<ShortUrlListDto>(response.bodyAsText()).shortUrls
        } catch (_: SerializationException) {
            throw InvalidShortUrlResponseException()
        }
        val pagination = dto.pagination
        if (pagination.currentPage != page ||
            pagination.pagesCount < 0 ||
            (pagination.pagesCount > 0 && page > pagination.pagesCount) ||
            (pagination.pagesCount == 0 && dto.data.isNotEmpty())
        ) {
            throw InvalidShortUrlResponseException()
        }
        return ShortUrlPage(
            items = dto.data.map { it.toModel() },
            currentPage = pagination.currentPage,
            pagesCount = pagination.pagesCount,
        )
    }
}

@Serializable
private data class ShortUrlListDto(val shortUrls: ShortUrlsDto)

@Serializable
private data class ShortUrlsDto(val data: List<ShortUrlDto>, val pagination: PaginationDto)

@Serializable
private data class PaginationDto(val currentPage: Int, val pagesCount: Int)

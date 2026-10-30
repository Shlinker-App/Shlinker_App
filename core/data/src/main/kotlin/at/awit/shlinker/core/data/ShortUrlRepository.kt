package at.awit.shlinker.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import at.awit.shlinker.core.model.ServerProfile
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.network.ShortUrlService
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class ShortUrlRepository @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val service: ShortUrlService,
) {
    fun list(server: ServerProfile, searchTerm: String): Flow<PagingData<ShortUrl>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { ShortUrlPagingSource(server, searchTerm, profiles, service) },
    ).flow

    internal companion object {
        const val PAGE_SIZE = 30
    }
}

class ShortUrlApiKeyException : IOException("API key missing or unavailable")

internal class ShortUrlPagingSource(
    private val server: ServerProfile,
    private val searchTerm: String,
    private val profiles: ServerProfileRepository,
    private val service: ShortUrlService,
) : PagingSource<Int, ShortUrl>() {
    // Eine Aktualisierung beginnt bewusst wieder bei den neuesten Kurz-URLs.
    override fun getRefreshKey(state: PagingState<Int, ShortUrl>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ShortUrl> = try {
        val page = params.key ?: 1
        val key = when (val result = profiles.getApiKey(server.id)) {
            is ApiKeyResult.Available -> result.apiKey
            ApiKeyResult.Missing, ApiKeyResult.Unavailable -> throw ShortUrlApiKeyException()
        }
        val result = service.list(server.url, key, page, ShortUrlRepository.PAGE_SIZE, searchTerm)
        LoadResult.Page(
            data = result.items,
            prevKey = if (page > 1) page - 1 else null,
            nextKey = if (page < result.pagesCount) page + 1 else null,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LoadResult.Error(e)
    }
}

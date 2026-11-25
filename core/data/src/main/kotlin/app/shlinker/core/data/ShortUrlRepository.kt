package app.shlinker.core.data

import androidx.datastore.core.DataStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.shlinker.core.datastore.StoredServers
import app.shlinker.core.model.ServerProfile
import app.shlinker.core.model.ShortUrl
import app.shlinker.core.model.ShortUrlSort
import app.shlinker.core.network.ShortUrlService
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ShortUrlRepository @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val service: ShortUrlService,
    private val settings: DataStore<StoredServers>,
) {
    val sort: Flow<ShortUrlSort> = settings.data.map { it.shortUrlSort }.distinctUntilChanged()

    suspend fun setSort(sort: ShortUrlSort) {
        settings.updateData { it.copy(shortUrlSort = sort) }
    }

    fun list(
        server: ServerProfile,
        searchTerm: String,
        sort: ShortUrlSort = ShortUrlSort.NEWEST,
    ): Flow<PagingData<ShortUrl>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            prefetchDistance = PREFETCH_DISTANCE,
            initialLoadSize = PAGE_SIZE,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = { ShortUrlPagingSource(server, searchTerm, profiles, service, sort) },
    ).flow

    internal companion object {
        const val PAGE_SIZE = 50

        // Loads the next page while 30 rows remain, but not before scrolling starts on a phone-sized list.
        const val PREFETCH_DISTANCE = 30
    }
}

class ShortUrlApiKeyException : IOException("API key missing or unavailable")

internal class ShortUrlPagingSource(
    private val server: ServerProfile,
    private val searchTerm: String,
    private val profiles: ServerProfileRepository,
    private val service: ShortUrlService,
    private val sort: ShortUrlSort = ShortUrlSort.NEWEST,
) : PagingSource<Int, ShortUrl>() {
    // Refresh starts at page one of the selected server-side order.
    override fun getRefreshKey(state: PagingState<Int, ShortUrl>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ShortUrl> = try {
        val page = params.key ?: 1
        val key = when (val result = profiles.getApiKey(server.id)) {
            is ApiKeyResult.Available -> result.apiKey
            ApiKeyResult.Missing, ApiKeyResult.Unavailable -> throw ShortUrlApiKeyException()
        }
        val result = service.list(server.url, key, page, ShortUrlRepository.PAGE_SIZE, searchTerm, sort)
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

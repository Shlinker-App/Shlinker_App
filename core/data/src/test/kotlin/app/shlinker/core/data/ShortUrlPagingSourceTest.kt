package app.shlinker.core.data

import androidx.paging.PagingSource
import app.shlinker.core.datastore.StoredSecrets
import app.shlinker.core.datastore.StoredServers
import app.shlinker.core.model.ShortUrl
import app.shlinker.core.model.ShortUrlPage
import app.shlinker.core.model.ShortUrlSort
import app.shlinker.core.network.ShortUrlService
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShortUrlPagingSourceTest {
    private val secrets = InMemoryDataStore(StoredSecrets())
    private val cipher = FakeCipher()
    private val storedServers = InMemoryDataStore(StoredServers())
    private val profiles = DefaultServerProfileRepository(
        storedServers,
        secrets,
        cipher,
        UnconfinedTestDispatcher(),
    )
    private val url = ShortUrl("abc", "https://s.example/abc", "https://example.org", null, listOf("work"))
    private val calls = mutableListOf<List<Any>>()
    private var pagesCount = 3
    private var failure: Exception? = null
    private val service = object : ShortUrlService {
        override suspend fun list(
            serverUrl: String,
            apiKey: String,
            page: Int,
            itemsPerPage: Int,
            searchTerm: String,
            sort: ShortUrlSort,
        ): ShortUrlPage {
            calls += listOf(serverUrl, apiKey, page, itemsPerPage, searchTerm)
            sorts += sort
            failure?.let { throw it }
            return ShortUrlPage(if (pagesCount == 0) emptyList() else listOf(url), page, pagesCount)
        }
    }

    private val refresh = PagingSource.LoadParams.Refresh<Int>(null, 90, false)
    private val sorts = mutableListOf<ShortUrlSort>()

    @Test fun globalSortSurvivesRecreationAndProfileChangesWithoutChangingSelection() = runTest {
        val first = profiles.add("First", "first.example", "key")
        val second = profiles.add("Second", "second.example", "key", makeActive = false)
        profiles.reorder(listOf(second.id, first.id))
        val repository = ShortUrlRepository(profiles, service, storedServers)
        repository.setSort(ShortUrlSort.LEAST_VISITED)
        assertEquals(first, profiles.activeProfile.first())
        assertEquals(listOf(second, first), profiles.profiles.first())
        profiles.update(second.id, name = "Renamed")
        profiles.delete(first.id)
        val recreated = ShortUrlRepository(profiles, service, storedServers)
        assertEquals(ShortUrlSort.LEAST_VISITED, recreated.sort.first())
        assertEquals(second.id, profiles.activeProfile.first()?.id)
        assertEquals(ApiKeyResult.Available("key"), profiles.getApiKey(second.id))
    }

    @Test fun everyPageUsesSelectedServerSideSort() = runTest {
        val server = profiles.add("Test", "https://s.example", "key")
        for (sort in ShortUrlSort.entries) {
            val source = ShortUrlPagingSource(server, "query", profiles, service, sort)
            source.load(refresh)
            source.load(PagingSource.LoadParams.Append(2, 30, false))
        }
        assertEquals(ShortUrlSort.entries.flatMap { listOf(it, it) }, sorts)
    }

    @Test fun firstAndSubsequentPagesUseFixedPageSize() = runTest {
        val server = profiles.add("Test", "https://s.example", "key")
        val source = ShortUrlPagingSource(server, "query", profiles, service)
        assertEquals(PagingSource.LoadResult.Page(listOf(url), null, 2), source.load(refresh))
        assertEquals(
            PagingSource.LoadResult.Page(listOf(url), 1, 3),
            source.load(
                PagingSource.LoadParams.Append(2, 30, false),
            ),
        )
        assertEquals(
            PagingSource.LoadResult.Page(listOf(url), 2, null),
            source.load(
                PagingSource.LoadParams.Append(3, 30, false),
            ),
        )
        assertEquals(listOf("https://s.example", "key", 1, 30, "query"), calls.first())
        assertEquals(listOf(1, 2, 3), calls.map { it[2] })
    }

    @Test fun emptyServerEndsPagination() = runTest {
        pagesCount = 0
        val server = profiles.add("Test", "https://s.example", "key")
        val result = ShortUrlPagingSource(server, "", profiles, service).load(refresh)
        assertEquals(PagingSource.LoadResult.Page<Int, ShortUrl>(emptyList(), null, null), result)
    }

    @Test fun apiKeyIsReadAgainForEveryRequest() = runTest {
        val server = profiles.add("Test", "https://s.example", "old")
        val source = ShortUrlPagingSource(server, "", profiles, service)
        source.load(refresh)
        profiles.update(server.id, apiKey = "new")
        source.load(PagingSource.LoadParams.Append(2, 30, false))
        assertEquals(listOf("old", "new"), calls.map { it[1] })
    }

    @Test fun missingKeyDoesNotSendRequest() = runTest {
        val server = profiles.add("Test", "https://s.example", "key")
        secrets.updateData { StoredSecrets() }
        val result = ShortUrlPagingSource(server, "", profiles, service).load(refresh)
        assertTrue(result is PagingSource.LoadResult.Error && result.throwable is ShortUrlApiKeyException)
        assertTrue(calls.isEmpty())
    }

    @Test fun unreadableKeyDoesNotSendRequest() = runTest {
        val server = profiles.add("Test", "https://s.example", "key")
        cipher.broken = true
        val result = ShortUrlPagingSource(server, "", profiles, service).load(refresh)
        assertTrue(result is PagingSource.LoadResult.Error && result.throwable is ShortUrlApiKeyException)
        assertTrue(calls.isEmpty())
    }

    @Test fun failedPageCanBeRetried() = runTest {
        val server = profiles.add("Test", "https://s.example", "key")
        val source = ShortUrlPagingSource(server, "", profiles, service)
        failure = IOException("offline")
        assertTrue(source.load(refresh) is PagingSource.LoadResult.Error)
        failure = null
        assertTrue(source.load(refresh) is PagingSource.LoadResult.Page)
    }

    @Test fun cancellationIsNotConvertedToError() = runTest {
        val server = profiles.add("Test", "https://s.example", "key")
        failure = CancellationException("cancelled")
        try {
            ShortUrlPagingSource(server, "", profiles, service).load(refresh)
            org.junit.Assert.fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, calls.size)
        }
    }
}

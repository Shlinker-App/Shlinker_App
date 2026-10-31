package at.awit.shlinker.feature.shorturls

import androidx.datastore.core.DataStore
import androidx.lifecycle.SavedStateHandle
import androidx.paging.AsyncPagingDataDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import at.awit.shlinker.core.data.ApiKeyResult
import at.awit.shlinker.core.data.ServerProfileRepository
import at.awit.shlinker.core.data.ShortUrlRepository
import at.awit.shlinker.core.datastore.StoredServers
import at.awit.shlinker.core.model.ServerProfile
import at.awit.shlinker.core.model.ServerSort
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.model.ShortUrlPage
import at.awit.shlinker.core.model.ShortUrlSort
import at.awit.shlinker.core.network.ShortUrlService
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShortUrlsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val first = ServerProfile("first", "First", "https://first.example", 0)
    private val second = ServerProfile("second", "Second", "https://second.example", 1)
    private val profiles = FakeProfiles(first)
    private val calls = mutableListOf<Pair<String, String>>()
    private var blockFirstServer = false
    private var firstCancelled = false
    private val sortCalls = mutableListOf<ShortUrlSort>()
    private var settingsFailure = false
    private val stored = MutableStateFlow(StoredServers())
    private val settings = object : DataStore<StoredServers> {
        override val data = stored
        override suspend fun updateData(transform: suspend (StoredServers) -> StoredServers): StoredServers {
            if (settingsFailure) throw IOException("disk")
            return transform(stored.value).also { stored.value = it }
        }
    }
    private val service = object : ShortUrlService {
        override suspend fun list(
            serverUrl: String,
            apiKey: String,
            page: Int,
            itemsPerPage: Int,
            searchTerm: String,
            sort: ShortUrlSort,
        ): ShortUrlPage {
            calls += serverUrl to searchTerm
            sortCalls += sort
            assertEquals("$serverUrl/key", apiKey)
            if (blockFirstServer && serverUrl == first.url) {
                try {
                    awaitCancellation()
                } finally {
                    firstCancelled = true
                }
            }
            return ShortUrlPage(
                listOf(ShortUrl("abc", "$serverUrl/abc", "https://example.org", null, emptyList())),
                1,
                1,
            )
        }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun differ() = AsyncPagingDataDiffer(
        diffCallback = object : DiffUtil.ItemCallback<ShortUrl>() {
            override fun areItemsTheSame(oldItem: ShortUrl, newItem: ShortUrl) = oldItem.shortUrl == newItem.shortUrl

            override fun areContentsTheSame(oldItem: ShortUrl, newItem: ShortUrl) = oldItem == newItem
        },
        updateCallback = object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) = Unit
            override fun onRemoved(position: Int, count: Int) = Unit
            override fun onMoved(fromPosition: Int, toPosition: Int) = Unit
            override fun onChanged(position: Int, count: Int, payload: Any?) = Unit
        },
        mainDispatcher = dispatcher,
        workerDispatcher = dispatcher,
    )

    @Test fun searchIsDebouncedTrimmedAndPersisted() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = ShortUrlsViewModel(profiles, ShortUrlRepository(profiles, service, settings), saved)
        val differ = differ()
        val job = launch { vm.urls.collectLatest { differ.submitData(it) } }
        try {
            advanceUntilIdle()
            assertEquals(listOf(first.url to ""), calls)
            vm.onSearchChange("a")
            runCurrent()
            advanceTimeBy(200)
            vm.onSearchChange("  article  ")
            runCurrent()
            advanceTimeBy(299)
            runCurrent()
            assertEquals(1, calls.size)
            advanceTimeBy(1)
            advanceUntilIdle()
            assertEquals(first.url to "article", calls.last())
            assertEquals("  article  ", saved.get<String>("search"))
            vm.onSearchChange("article")
            advanceUntilIdle()
            assertEquals(2, calls.size)
        } finally {
            job.cancel()
        }
    }

    @Test fun serverSwitchCancelsOldRequestAndUsesNewCredentials() = runTest(dispatcher) {
        blockFirstServer = true
        val vm = ShortUrlsViewModel(profiles, ShortUrlRepository(profiles, service, settings), SavedStateHandle())
        val differ = differ()
        val job = launch { vm.urls.collectLatest { differ.submitData(it) } }
        try {
            advanceUntilIdle()
            profiles.activeProfile.value = second
            advanceUntilIdle()
            assertTrue(firstCancelled)
            assertEquals(listOf(first.url to "", second.url to ""), calls)
            assertEquals("${second.url}/abc", differ.snapshot().items.single().shortUrl)
        } finally {
            job.cancel()
        }
    }

    @Test fun noActiveServerProducesLoadedEmptyListWithoutRequests() = runTest(dispatcher) {
        profiles.activeProfile.value = null
        val vm = ShortUrlsViewModel(profiles, ShortUrlRepository(profiles, service, settings), SavedStateHandle())
        val differ = differ()
        val job = launch { vm.urls.collectLatest { differ.submitData(it) } }
        try {
            advanceUntilIdle()
            assertTrue(calls.isEmpty())
            assertTrue(differ.snapshot().items.isEmpty())
            assertEquals(androidx.paging.LoadState.NotLoading(true), differ.loadStateFlow.first().refresh)
        } finally {
            job.cancel()
        }
    }

    @Test fun changingSortCancelsOldLoadAndKeepsSearchAndServer() = runTest(dispatcher) {
        blockFirstServer = true
        val saved = SavedStateHandle(mapOf("search" to "article"))
        val vm = ShortUrlsViewModel(profiles, ShortUrlRepository(profiles, service, settings), saved)
        val differ = differ()
        val job = launch { vm.urls.collectLatest { differ.submitData(it) } }
        try {
            advanceUntilIdle()
            vm.onSortChange(ShortUrlSort.MOST_VISITED)
            advanceUntilIdle()
            assertTrue(firstCancelled)
            assertEquals(listOf(ShortUrlSort.NEWEST, ShortUrlSort.MOST_VISITED), sortCalls)
            assertEquals(listOf(first.url to "article", first.url to "article"), calls)
            assertEquals("article", vm.search.value)
            assertEquals(first, profiles.activeProfile.value)
            assertEquals(ShortUrlSort.MOST_VISITED, stored.value.shortUrlSort)
        } finally {
            job.cancel()
        }
    }

    @Test fun persistedSortAppliesOnRecreationAndServerSwitch() = runTest(dispatcher) {
        val repository = ShortUrlRepository(profiles, service, settings)
        repository.setSort(ShortUrlSort.OLDEST)
        val vm = ShortUrlsViewModel(profiles, ShortUrlRepository(profiles, service, settings), SavedStateHandle())
        val differ = differ()
        val job = launch { vm.urls.collectLatest { differ.submitData(it) } }
        try {
            advanceUntilIdle()
            profiles.activeProfile.value = second
            advanceUntilIdle()
            vm.onSortChange(ShortUrlSort.LEAST_VISITED)
            advanceUntilIdle()
            assertEquals(
                listOf(ShortUrlSort.OLDEST, ShortUrlSort.OLDEST, ShortUrlSort.LEAST_VISITED),
                sortCalls,
            )
            assertEquals(second.url to "", calls.last())
        } finally {
            job.cancel()
        }
    }

    @Test fun failedSortSaveKeepsPreviousSelectionAndIsRetryable() = runTest(dispatcher) {
        val vm = ShortUrlsViewModel(profiles, ShortUrlRepository(profiles, service, settings), SavedStateHandle())
        settingsFailure = true
        vm.onSortChange(ShortUrlSort.MOST_VISITED)
        advanceUntilIdle()
        assertTrue(vm.sortError.value)
        assertEquals(false, vm.sortSaving.value)
        assertEquals(ShortUrlSort.NEWEST, stored.value.shortUrlSort)
        vm.dismissSortError()
        assertEquals(false, vm.sortError.value)
        settingsFailure = false
        vm.onSortChange(ShortUrlSort.MOST_VISITED)
        advanceUntilIdle()
        assertEquals(false, vm.sortError.value)
        assertEquals(ShortUrlSort.MOST_VISITED, stored.value.shortUrlSort)
    }

    private class FakeProfiles(server: ServerProfile) : ServerProfileRepository {
        override val sort = MutableStateFlow(ServerSort.MANUAL)
        override suspend fun setSort(sort: ServerSort) = error("unused")
        override suspend fun reorder(ids: List<String>) = error("unused")
        override val profiles = MutableStateFlow(listOf(server))
        override val activeProfile = MutableStateFlow<ServerProfile?>(server)

        override suspend fun getApiKey(id: String) = ApiKeyResult.Available("${activeProfile.value!!.url}/key")

        override suspend fun add(name: String, url: String, apiKey: String, makeActive: Boolean): ServerProfile =
            error("Unused")

        override suspend fun update(id: String, name: String?, url: String?, apiKey: String?): ServerProfile =
            error("Unused")

        override suspend fun delete(id: String): Unit = error("Unused")

        override suspend fun setActive(id: String): Unit = error("Unused")
    }
}

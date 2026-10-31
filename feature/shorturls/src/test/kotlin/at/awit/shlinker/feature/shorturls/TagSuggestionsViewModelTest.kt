package at.awit.shlinker.feature.shorturls

import at.awit.shlinker.core.data.ApiKeyResult
import at.awit.shlinker.core.data.ServerProfileRepository
import at.awit.shlinker.core.data.ServerTagsRepository
import at.awit.shlinker.core.model.ServerProfile
import at.awit.shlinker.core.model.ServerSort
import at.awit.shlinker.core.network.ServerTagsService
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagSuggestionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val calls = mutableListOf<String>()
    private var failure: IOException? = null
    private var gate: CompletableDeferred<Unit>? = null
    private val profiles = Profiles()
    private lateinit var vm: TagSuggestionsViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        vm = TagSuggestionsViewModel(
            ServerTagsRepository(
                profiles,
                object : ServerTagsService {
                    override suspend fun get(serverUrl: String, apiKey: String): List<String> {
                        assertEquals("key", apiKey)
                        calls += serverUrl
                        gate?.await()
                        failure?.let { throw it }
                        return listOf(serverUrl)
                    }
                },
            ),
        )
    }

    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun failedSuggestionsAreVisibleAndRetryIsExplicitWithoutRepeatedLoads() = runTest(dispatcher) {
        failure = IOException("offline")
        vm.load("1")
        advanceUntilIdle()
        assertEquals(failure, vm.state.value.error)
        assertFalse(vm.state.value.loading)
        vm.load("1")
        assertEquals(1, calls.size)
        failure = null
        vm.load("1", retry = true)
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(listOf("https://first.example"), vm.state.value.tags)
        assertEquals(2, calls.size)
        assertEquals("2", profiles.activeProfile.value?.id)
    }

    @Test fun switchingProfileCancelsOldResultsAndDoesNotLeakSuggestionsOrDuplicatePendingRequests() = runTest(
        dispatcher,
    ) {
        gate = CompletableDeferred()
        vm.load("1")
        runCurrent()
        vm.load("1", retry = true)
        assertEquals(1, calls.size)
        gate = null
        vm.load("2")
        assertTrue(vm.state.value.tags.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf("https://second.example"), vm.state.value.tags)
        assertEquals(listOf("https://first.example", "https://second.example"), calls)
    }

    @Test fun matchingExcludesSelectedTagsPreservesCaseAndCapsDisplayWithoutLimitingSearch() {
        val tags = listOf("My app", "work", "WORK") + (1..20).map { "tag-$it" }
        assertEquals(listOf("WORK"), matchingTagSuggestions(tags, listOf("work"), " woR "))
        assertEquals(8, matchingTagSuggestions(tags, emptyList(), "").size)
        assertEquals(listOf("tag-20"), matchingTagSuggestions(tags, emptyList(), "tag-20"))
        assertEquals(emptyList<String>(), matchingTagSuggestions(tags, emptyList(), "absent"))
    }

    private class Profiles : ServerProfileRepository {
        override val profiles = MutableStateFlow(
            listOf(
                ServerProfile("1", "First", "https://first.example", 0),
                ServerProfile("2", "Second", "https://second.example", 1),
            ),
        )
        override val activeProfile = MutableStateFlow<ServerProfile?>(profiles.value.last())
        override val sort = MutableStateFlow(ServerSort.MANUAL)
        override suspend fun getApiKey(id: String) = ApiKeyResult.Available("key")
        override suspend fun setSort(sort: ServerSort) = error("unused")
        override suspend fun reorder(ids: List<String>) = error("unused")
        override suspend fun setActive(id: String) = error("unused")
        override suspend fun delete(id: String) = error("unused")
        override suspend fun add(name: String, url: String, apiKey: String, makeActive: Boolean): ServerProfile =
            error("unused")
        override suspend fun update(id: String, name: String?, url: String?, apiKey: String?): ServerProfile =
            error("unused")
    }
}

package at.awit.shlinker.feature.shorturls

import at.awit.shlinker.core.data.ApiKeyResult
import at.awit.shlinker.core.data.ServerNotFoundException
import at.awit.shlinker.core.data.ServerProfileRepository
import at.awit.shlinker.core.data.ShortUrlDetailsRepository
import at.awit.shlinker.core.model.ServerProfile
import at.awit.shlinker.core.model.ServerSort
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.model.ShortUrlDetails
import at.awit.shlinker.core.network.ConnectionError
import at.awit.shlinker.core.network.ShortUrlDetailsService
import at.awit.shlinker.core.network.ShortUrlRequestException
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
class ShortUrlDetailsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val profiles = Profiles()
    private val calls = mutableListOf<String?>()
    private var failure: IOException? = null
    private var gate: CompletableDeferred<Unit>? = null
    private var cancelled = false
    private val service = object : ShortUrlDetailsService {
        override suspend fun get(
            serverUrl: String,
            apiKey: String,
            shortCode: String,
            domain: String?,
        ): ShortUrlDetails {
            assertEquals("https://s.example", serverUrl)
            assertEquals("key", apiKey)
            calls += domain
            try {
                gate?.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw e
            }
            failure?.let { throw it }
            return ShortUrlDetails(
                ShortUrl(
                    shortCode,
                    "https://${domain ?: "s.example"}/$shortCode",
                    "https://example.org",
                    null,
                    emptyList(),
                ),
            )
        }
    }
    private lateinit var vm: ShortUrlDetailsViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        vm = ShortUrlDetailsViewModel(profiles, ShortUrlDetailsRepository(profiles, service))
    }

    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun loadAndExplicitRefreshRemainPinnedToProfileAndDomain() = runTest(dispatcher) {
        vm.load("1", "abc", "custom.example")
        advanceUntilIdle()
        assertEquals("Home", vm.state.value.serverName)
        assertEquals("https://custom.example/abc", vm.state.value.details?.url?.shortUrl)
        assertFalse(vm.state.value.loading)
        vm.load("1", "abc", "custom.example")
        advanceUntilIdle()
        assertEquals(1, calls.size)
        vm.load("1", "abc", "custom.example", refresh = true)
        advanceUntilIdle()
        assertEquals(2, calls.size)
        assertEquals("other", profiles.activeProfile.value?.id)
    }

    @Test fun failureIsVisibleAndExplicitRetryRecovers() = runTest(dispatcher) {
        failure = ShortUrlRequestException(ConnectionError.HttpError(404, null))
        vm.load("1", "abc", null)
        advanceUntilIdle()
        assertEquals(failure, vm.state.value.error)
        assertNull(vm.state.value.details)
        assertFalse(vm.state.value.loading)
        failure = null
        vm.load("1", "abc", null, refresh = true)
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals("abc", vm.state.value.details?.url?.shortCode)
    }

    @Test fun changingIdentityCancelsPreviousRequestAndDoesNotShowOldDetails() = runTest(dispatcher) {
        gate = CompletableDeferred()
        vm.load("1", "abc", "first.example")
        runCurrent()
        assertTrue(vm.state.value.loading)
        gate = null
        vm.load("1", "abc", "second.example")
        advanceUntilIdle()
        assertTrue(cancelled)
        assertEquals("https://second.example/abc", vm.state.value.details?.url?.shortUrl)
        assertEquals(listOf("first.example", "second.example"), calls)
    }

    @Test fun missingProfileNeverMakesNetworkRequest() = runTest(dispatcher) {
        vm.load("missing", "abc", null)
        advanceUntilIdle()
        assertTrue(vm.state.value.error is ServerNotFoundException)
        assertTrue(calls.isEmpty())
    }

    private class Profiles : ServerProfileRepository {
        override val profiles = MutableStateFlow(listOf(ServerProfile("1", "Home", "https://s.example", 0)))
        override val activeProfile =
            MutableStateFlow<ServerProfile?>(ServerProfile("other", "Other", "https://other", 1))
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

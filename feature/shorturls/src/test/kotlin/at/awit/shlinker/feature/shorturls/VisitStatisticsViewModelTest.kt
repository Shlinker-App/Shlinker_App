package at.awit.shlinker.feature.shorturls

import at.awit.shlinker.core.data.ApiKeyResult
import at.awit.shlinker.core.data.ServerProfileRepository
import at.awit.shlinker.core.data.VisitStatisticsRepository
import at.awit.shlinker.core.model.ServerProfile
import at.awit.shlinker.core.model.ServerSort
import at.awit.shlinker.core.model.VisitPeriod
import at.awit.shlinker.core.model.VisitStatistics
import at.awit.shlinker.core.model.VisitWindow
import at.awit.shlinker.core.network.VisitStatisticsService
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisitStatisticsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val calls = mutableListOf<List<String?>>()
    private val windows = mutableListOf<VisitWindow>()
    private var gate: CompletableDeferred<Unit>? = null
    private var failure: IOException? = null
    private lateinit var vm: VisitStatisticsViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        vm = VisitStatisticsViewModel(
            VisitStatisticsRepository(
                Profiles(),
                object : VisitStatisticsService {
                    override suspend fun get(
                        serverUrl: String,
                        apiKey: String,
                        shortCode: String,
                        domain: String?,
                        window: VisitWindow,
                    ): VisitStatistics {
                        assertEquals("key", apiKey)
                        calls += listOf(serverUrl, shortCode, domain)
                        windows += window
                        gate?.await()
                        failure?.let { throw it }
                        return VisitStatistics(calls.size.toLong(), 0)
                    }
                },
            ),
        )
    }

    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun loadDefaultsTo30DaysPinsIdentityAndRefreshDoesNotDuplicatePendingLoads() = runTest(dispatcher) {
        gate = CompletableDeferred()
        vm.load("1", "same", "custom.example")
        runCurrent()
        assertTrue(vm.state.value.loading)
        assertNotNull(vm.state.value.window?.since)
        vm.load("1", "same", "custom.example")
        vm.refresh()
        assertEquals(1, calls.size)
        gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(VisitPeriod.LAST_30_DAYS, vm.state.value.period)
        assertEquals("First", vm.state.value.serverName)
        assertEquals(VisitStatistics(1, 0), vm.state.value.counts)
        vm.refresh()
        assertNull(vm.state.value.counts)
        advanceUntilIdle()
        assertEquals(2, calls.size)
        assertTrue(windows.last().until >= windows.first().until)
        assertEquals(listOf("https://first.example", "same", "custom.example"), calls.last())
    }

    @Test fun periodOrProfileChangeCancelsOldRequestAndNeverKeepsStaleCounts() = runTest(dispatcher) {
        gate = CompletableDeferred()
        vm.load("1", "same", null)
        runCurrent()
        val oldGate = gate
        gate = null
        vm.select(VisitPeriod.ALL_TIME)
        assertNull(vm.state.value.counts)
        advanceUntilIdle()
        oldGate?.complete(Unit)
        runCurrent()
        assertEquals(VisitPeriod.ALL_TIME, vm.state.value.period)
        assertNull(vm.state.value.window?.since)
        assertEquals(VisitStatistics(2, 0), vm.state.value.counts)
        vm.select(VisitPeriod.ALL_TIME)
        assertEquals(2, calls.size)
        vm.load("2", "same", "other.example")
        assertNull(vm.state.value.counts)
        advanceUntilIdle()
        assertEquals("Second", vm.state.value.serverName)
        assertEquals(listOf("https://second.example", "same", "other.example"), calls.last())
        assertEquals(VisitStatistics(3, 0), vm.state.value.counts)
    }

    @Test fun errorDropsPreviousCountsAndCanBeRetriedWithoutSilentlyBecomingZero() = runTest(dispatcher) {
        vm.load("1", "a", null)
        advanceUntilIdle()
        failure = IOException("offline")
        vm.select(VisitPeriod.TODAY)
        advanceUntilIdle()
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.counts)
        assertEquals(failure, vm.state.value.error)
        vm.load("1", "a", null)
        assertEquals(2, calls.size)
        failure = null
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(VisitPeriod.TODAY, vm.state.value.period)
        assertEquals(VisitStatistics(3, 0), vm.state.value.counts)
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

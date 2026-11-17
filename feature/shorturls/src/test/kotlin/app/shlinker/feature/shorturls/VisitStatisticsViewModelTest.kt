package app.shlinker.feature.shorturls

import app.shlinker.core.data.ApiKeyResult
import app.shlinker.core.data.ServerProfileRepository
import app.shlinker.core.data.VisitStatisticsRepository
import app.shlinker.core.data.VisitTimelineRepository
import app.shlinker.core.model.ServerProfile
import app.shlinker.core.model.ServerSort
import app.shlinker.core.model.ShortUrl
import app.shlinker.core.model.ShortUrlDetails
import app.shlinker.core.model.VisitPeriod
import app.shlinker.core.model.VisitStatistics
import app.shlinker.core.model.VisitWindow
import app.shlinker.core.network.ShortUrlDetailsService
import app.shlinker.core.network.VisitStatisticsService
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
    private var timelineGate: CompletableDeferred<Unit>? = null
    private var timelineFailure: IOException? = null
    private var timelineCalls = 0
    private var emptySummary = false
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
                        return VisitStatistics(if (emptySummary) 0 else calls.size.toLong(), 0)
                    }
                },
            ),
            VisitTimelineRepository(
                Profiles(),
                object : VisitStatisticsService {
                    override suspend fun get(
                        serverUrl: String,
                        apiKey: String,
                        shortCode: String,
                        domain: String?,
                        window: VisitWindow,
                    ): VisitStatistics {
                        timelineCalls++
                        timelineGate?.await()
                        timelineFailure?.let { throw it }
                        return VisitStatistics(0, 0)
                    }
                },
                object : ShortUrlDetailsService {
                    override suspend fun get(serverUrl: String, apiKey: String, shortCode: String, domain: String?) =
                        ShortUrlDetails(
                            ShortUrl(
                                shortCode,
                                "https://s.example/$shortCode",
                                "https://dest.example",
                                null,
                                emptyList(),
                            ),
                            dateCreated = "2026-01-01T00:00:00Z",
                        )
                },
            ),
        )
    }

    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun loadDefaultsTo4WeeksPinsIdentityAndRefreshDoesNotDuplicatePendingLoads() = runTest(dispatcher) {
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
        assertEquals(VisitPeriod.LAST_4_WEEKS, vm.state.value.period)
        assertEquals("First", vm.state.value.serverName)
        assertEquals(VisitStatistics(1, 0), vm.state.value.counts)
        vm.refresh()
        assertNull(vm.state.value.counts)
        assertEquals("First", vm.state.value.serverName)
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
        assertNull(vm.state.value.serverName)
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
        assertEquals("First", vm.state.value.serverName)
        advanceUntilIdle()
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.counts)
        assertEquals(failure, vm.state.value.error)
        assertEquals("First", vm.state.value.serverName)
        vm.load("1", "a", null)
        assertEquals(2, calls.size)
        failure = null
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(VisitPeriod.TODAY, vm.state.value.period)
        assertEquals(VisitStatistics(3, 0), vm.state.value.counts)
    }

    @Test fun changingLinkOrDomainOnTheSameServerKeepsItsNameButNotCounts() = runTest(dispatcher) {
        vm.load("1", "same", null)
        advanceUntilIdle()
        gate = CompletableDeferred()
        vm.load("1", "other", "custom.example")
        runCurrent()
        assertEquals("First", vm.state.value.serverName)
        assertNull(vm.state.value.counts)
        assertTrue(vm.state.value.loading)
        gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("https://first.example", "other", "custom.example"), calls.last())
    }

    @Test fun summaryRemainsVisibleWhileTimelineLoadsAndTimelineFailureDoesNotEraseIt() = runTest(dispatcher) {
        timelineGate = CompletableDeferred()
        vm.load("1", "same", null)
        runCurrent()
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.timelineLoading)
        assertEquals(VisitStatistics(1, 0), vm.state.value.counts)
        assertEquals("First", vm.state.value.serverName)
        vm.refresh()
        assertEquals(1, calls.size)
        timelineFailure = IOException("timeline offline")
        timelineGate?.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.state.value.timelineLoading)
        assertEquals(timelineFailure, vm.state.value.timelineError)
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.timeline)
        assertEquals(VisitStatistics(1, 0), vm.state.value.counts)
        timelineFailure = null
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.state.value.timelineError)
        assertEquals(7, vm.state.value.timeline?.size)
    }

    @Test fun changingPeriodCancelsPendingTimelineAndDropsPreviousBars() = runTest(dispatcher) {
        timelineGate = CompletableDeferred()
        vm.load("1", "same", null)
        runCurrent()
        val oldGate = timelineGate
        timelineGate = null
        vm.select(VisitPeriod.TODAY)
        assertNull(vm.state.value.timeline)
        assertNull(vm.state.value.counts)
        advanceUntilIdle()
        oldGate?.complete(Unit)
        runCurrent()
        assertEquals(VisitPeriod.TODAY, vm.state.value.period)
        assertEquals(7, vm.state.value.timeline?.size)
        assertEquals(VisitStatistics(2, 0), vm.state.value.counts)
        assertEquals(7, timelineCalls)
    }

    @Test fun emptySummarySkipsAllTimelineRequestsAndShowsAnHonestEmptyResult() = runTest(dispatcher) {
        emptySummary = true
        vm.load("1", "same", null)
        advanceUntilIdle()
        assertEquals(0, timelineCalls)
        assertFalse(vm.state.value.timelineLoading)
        assertEquals(emptyList<Any>(), vm.state.value.timeline)
        assertEquals(VisitStatistics(0, 0), vm.state.value.counts)
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

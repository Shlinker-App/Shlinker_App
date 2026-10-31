package at.awit.shlinker.feature.shorturls

import at.awit.shlinker.core.data.ApiKeyResult
import at.awit.shlinker.core.data.RedirectRulesConflictException
import at.awit.shlinker.core.data.RedirectRulesRepository
import at.awit.shlinker.core.data.ServerProfileRepository
import at.awit.shlinker.core.model.RedirectCondition
import at.awit.shlinker.core.model.RedirectRule
import at.awit.shlinker.core.model.RedirectRules
import at.awit.shlinker.core.model.ServerProfile
import at.awit.shlinker.core.model.ServerSort
import at.awit.shlinker.core.network.RedirectRulesException
import at.awit.shlinker.core.network.RedirectRulesFailure
import at.awit.shlinker.core.network.RedirectRulesService
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
class RedirectRulesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val rule = RedirectRule("https://android.example", listOf(RedirectCondition("device", null, "android")))
    private var server = RedirectRules("https://default.example", listOf(rule))
    private var gate: CompletableDeferred<Unit>? = null
    private var failure: IOException? = null
    private var reads = 0
    private var writes = 0
    private val identities = mutableListOf<List<String?>>()
    private lateinit var vm: RedirectRulesViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val profiles = Profiles()
        val service = object : RedirectRulesService {
            override suspend fun get(
                serverUrl: String,
                apiKey: String,
                shortCode: String,
                domain: String?,
            ): RedirectRules {
                reads++
                identities += listOf(serverUrl, shortCode, domain)
                gate?.await()
                return server
            }
            override suspend fun set(
                serverUrl: String,
                apiKey: String,
                shortCode: String,
                domain: String?,
                rules: List<RedirectRule>,
            ): RedirectRules {
                writes++
                identities += listOf(serverUrl, shortCode, domain)
                gate?.await()
                failure?.let { throw it }
                server = server.copy(rules = rules)
                return server
            }
        }
        vm = RedirectRulesViewModel(profiles, RedirectRulesRepository(profiles, service))
    }

    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun loadAndSaveKeepProfileCodeDomainAndOnlyWriteAfterValidDirtyConfirmation() = runTest(dispatcher) {
        vm.load("1", "same", "custom.example")
        advanceUntilIdle()
        vm.edit()
        assertFalse(vm.state.value.dirty)
        vm.requestSave()
        vm.save()
        assertEquals(0, writes)
        vm.change(listOf(rule.copy(longUrl = "bad")))
        vm.requestSave()
        assertTrue(vm.state.value.validation)
        assertFalse(vm.state.value.confirmSave)
        vm.change(emptyList())
        vm.requestSave()
        assertTrue(vm.state.value.confirmSave)
        vm.dismissSave()
        vm.save()
        assertEquals(0, writes)
        vm.requestSave()
        vm.save()
        vm.save()
        vm.load("2", "other", null, refresh = true)
        advanceUntilIdle()
        assertEquals(1, writes)
        assertEquals(2, reads)
        assertNull(vm.state.value.draft)
        assertEquals(emptyList<RedirectRule>(), vm.state.value.original?.rules)
        assertEquals(1, vm.state.value.changeRevision)
        assertTrue(identities.all { it == listOf("https://first.example", "same", "custom.example") })
    }

    @Test fun concurrentChangeBlocksWriteAndRequiresReload() = runTest(dispatcher) {
        vm.load("1", "same", null)
        advanceUntilIdle()
        vm.edit()
        vm.change(emptyList())
        server = server.copy(defaultLongUrl = "https://changed.example")
        vm.requestSave()
        vm.save()
        advanceUntilIdle()
        assertEquals(0, writes)
        assertTrue(vm.state.value.error is RedirectRulesConflictException)
        assertTrue(vm.state.value.needsReload)
        vm.change(listOf(rule))
        vm.requestSave()
        vm.save()
        assertEquals(0, writes)
        vm.load("1", "same", null, refresh = true)
        advanceUntilIdle()
        assertFalse(vm.state.value.needsReload)
        assertNull(vm.state.value.draft)
        assertEquals("https://changed.example", vm.state.value.original?.defaultLongUrl)
    }

    @Test fun uncertainWriteLocksDraftUntilReadAndNotifiesPossibleServerChange() = runTest(dispatcher) {
        vm.load("1", "same", null)
        advanceUntilIdle()
        vm.edit()
        vm.change(emptyList())
        failure = RedirectRulesException(RedirectRulesFailure.UNCERTAIN)
        vm.requestSave()
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.needsReload)
        assertFalse(vm.state.value.saving)
        assertEquals(1, vm.state.value.changeRevision)
        vm.save()
        vm.cancel()
        assertEquals(1, writes)
        assertTrue(vm.state.value.dirty)
        failure = null
        vm.load("1", "same", null, refresh = true)
        advanceUntilIdle()
        assertFalse(vm.state.value.needsReload)
        assertEquals(1, writes)
    }

    @Test fun rejectedInputKeepsDraftAndCanBeCorrectedWithoutFalseSuccess() = runTest(dispatcher) {
        vm.load("1", "same", null)
        advanceUntilIdle()
        vm.edit()
        vm.change(emptyList())
        failure = RedirectRulesException(RedirectRulesFailure.INVALID_INPUT)
        vm.requestSave()
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.state.value.needsReload)
        assertTrue(vm.state.value.dirty)
        assertEquals(0, vm.state.value.changeRevision)
        vm.cancel()
        assertNull(vm.state.value.draft)
    }

    @Test fun unknownConditionsAreReadOnlyAndChangingIdentityCancelsStaleLoad() = runTest(dispatcher) {
        server =
            server.copy(rules = listOf(rule.copy(conditions = listOf(RedirectCondition("future", "key", "value")))))
        gate = CompletableDeferred()
        vm.load("1", "same", null)
        runCurrent()
        val oldGate = gate
        gate = null
        vm.load("2", "other", "custom.example")
        advanceUntilIdle()
        oldGate?.complete(Unit)
        runCurrent()
        assertEquals("Second", vm.state.value.serverName)
        assertTrue(vm.state.value.original?.hasUnknownConditions == true)
        vm.edit()
        assertNull(vm.state.value.draft)
        assertEquals(0, writes)
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

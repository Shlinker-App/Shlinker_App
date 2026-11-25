package app.shlinker.home

import app.shlinker.core.data.ApiKeyResult
import app.shlinker.core.data.ServerNameConflictException
import app.shlinker.core.data.ServerNotFoundException
import app.shlinker.core.data.ServerOrderChangedException
import app.shlinker.core.data.ServerProfileRepository
import app.shlinker.core.model.ServerProfile
import app.shlinker.core.model.ServerSort
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val first = ServerProfile("1", "Home", "https://s.example", 1)
    private val second = ServerProfile("2", "Work", "https://s.example", 2)
    private val repository = FakeProfiles(listOf(first, second))
    private lateinit var vm: HomeViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = HomeViewModel(repository)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun sortAndManualOrderAreSavedWithoutChangingSelection() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.setSort(ServerSort.HOSTNAME_DESC)
        advanceUntilIdle()
        assertEquals(ServerSort.HOSTNAME_DESC, vm.state.value.sort)
        vm.setSort(ServerSort.MANUAL)
        advanceUntilIdle()
        vm.reorder(listOf(second.id, first.id))
        advanceUntilIdle()
        assertEquals(listOf(second, first), vm.state.value.servers)
        assertEquals(first.id, vm.state.value.activeId)
    }

    @Test fun sortAndReorderFailuresRemainVisible() = runTest(dispatcher) {
        repository.failure = IOException("disk")
        vm.setSort(ServerSort.NAME_DESC)
        advanceUntilIdle()
        assertEquals(ServerActionError.STORAGE, vm.action.value.error)
        assertEquals(ServerSort.MANUAL, repository.sort.value)
        repository.failure = ServerOrderChangedException()
        vm.reorder(listOf(second.id, first.id))
        advanceUntilIdle()
        assertEquals(ServerActionError.ORDER_CHANGED, vm.action.value.error)
        assertEquals(listOf(first, second), repository.profiles.value)
        repository.failure = null
        vm.reorder(listOf(second.id, first.id))
        advanceUntilIdle()
        assertNull(vm.action.value.error)
        assertEquals(listOf(second, first), repository.profiles.value)
    }

    @Test fun renameSavesNameOnlyAndKeepsActiveServer() = runTest(dispatcher) {
        vm.rename(first)
        vm.changeName("Personal")
        vm.confirm()
        advanceUntilIdle()
        assertEquals(first.copy(name = "Personal"), repository.profiles.value.first())
        assertEquals(first.copy(name = "Personal"), repository.activeProfile.value)
        assertNull(vm.action.value.server)
        assertEquals(1, repository.updates)
    }

    @Test fun conflictAndBlankNameAreCorrectable() = runTest(dispatcher) {
        vm.rename(first)
        vm.changeName(" ")
        vm.confirm()
        assertEquals(ServerActionError.EMPTY_NAME, vm.action.value.error)
        assertEquals(0, repository.updates)
        vm.changeName("Work")
        repository.failure = ServerNameConflictException()
        vm.confirm()
        advanceUntilIdle()
        assertEquals(ServerActionError.NAME_CONFLICT, vm.action.value.error)
        assertFalse(vm.action.value.busy)
        vm.changeName("Personal")
        assertNull(vm.action.value.error)
        repository.failure = null
        vm.confirm()
        advanceUntilIdle()
        assertEquals("Personal", repository.profiles.value.first().name)
    }

    @Test fun cancelDoesNotModifyProfiles() = runTest(dispatcher) {
        vm.rename(first)
        vm.changeName("Unsaved")
        vm.dismiss()
        vm.delete(first)
        vm.dismiss()
        advanceUntilIdle()
        assertEquals(listOf(first, second), repository.profiles.value)
        assertEquals(0, repository.deletes)
        assertEquals(0, repository.updates)
    }

    @Test fun deletingActiveAndLastServerUpdatesOverview() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.delete(first)
        vm.confirm()
        advanceUntilIdle()
        assertEquals(second.id, vm.state.value.activeId)
        assertEquals(listOf(second), vm.state.value.servers)
        vm.delete(second)
        vm.confirm()
        advanceUntilIdle()
        assertTrue(vm.state.value.servers.isEmpty())
        assertNull(vm.state.value.activeId)
        assertNull(vm.action.value.server)
    }

    @Test fun deletingInactiveServerPreservesSelection() = runTest(dispatcher) {
        vm.delete(second)
        vm.confirm()
        advanceUntilIdle()
        assertEquals(first, repository.activeProfile.value)
    }

    @Test fun storageFailureRemainsVisibleAndCanBeRetried() = runTest(dispatcher) {
        repository.failure = IOException("disk")
        vm.delete(first)
        vm.confirm()
        advanceUntilIdle()
        assertEquals(ServerActionError.STORAGE, vm.action.value.error)
        assertEquals(first, vm.action.value.server)
        assertEquals(2, repository.profiles.value.size)
        repository.failure = null
        vm.confirm()
        advanceUntilIdle()
        assertEquals(listOf(second), repository.profiles.value)
    }

    @Test fun selectingServerSavesItBeforeReportingSelection() = runTest(dispatcher) {
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.selected.collect { events += it } }
        vm.select(second.id)
        advanceUntilIdle()
        assertEquals(second, repository.activeProfile.value)
        assertEquals(1, events.size)
    }

    @Test fun selectionFailureAndMissingProfileAreVisible() = runTest(dispatcher) {
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.selected.collect { events += it } }
        repository.failure = IOException("disk")
        vm.select(second.id)
        advanceUntilIdle()
        assertEquals(ServerActionError.STORAGE, vm.action.value.error)
        assertNull(vm.action.value.server)
        assertTrue(events.isEmpty())
        vm.dismiss()
        repository.failure = ServerNotFoundException(first.id)
        vm.rename(first)
        vm.confirm()
        advanceUntilIdle()
        assertEquals(ServerActionError.NOT_FOUND, vm.action.value.error)
    }

    @Test fun busyOperationCannotBeSubmittedTwiceOrDismissed() = runTest(dispatcher) {
        repository.gate = CompletableDeferred()
        vm.delete(first)
        vm.confirm()
        runCurrent()
        vm.confirm()
        vm.dismiss()
        vm.rename(second)
        assertTrue(vm.action.value.busy)
        assertEquals(first, vm.action.value.server)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repository.deletes)
    }

    private class FakeProfiles(initial: List<ServerProfile>) : ServerProfileRepository {
        override val sort = MutableStateFlow(ServerSort.MANUAL)
        override suspend fun setSort(sort: ServerSort) {
            check()
            this.sort.value = sort
        }
        override suspend fun reorder(ids: List<String>) {
            check()
            profiles.value = ids.map { id -> profiles.value.first { it.id == id } }
        }
        override val profiles = MutableStateFlow(initial)
        override val activeProfile = MutableStateFlow(initial.firstOrNull())
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        var updates = 0
        var deletes = 0

        private suspend fun check() {
            gate?.await()
            failure?.let { throw it }
        }

        override suspend fun update(id: String, name: String?, url: String?, apiKey: String?): ServerProfile {
            check()
            assertNull(url)
            assertNull(apiKey)
            updates++
            val updated = profiles.value.first { it.id == id }.copy(name = name!!.trim())
            profiles.value = profiles.value.map { if (it.id == id) updated else it }
            if (activeProfile.value?.id == id) activeProfile.value = updated
            return updated
        }

        override suspend fun delete(id: String) {
            check()
            deletes++
            profiles.value = profiles.value.filterNot { it.id == id }
            if (activeProfile.value?.id == id) activeProfile.value = profiles.value.firstOrNull()
        }

        override suspend fun setActive(id: String) {
            check()
            activeProfile.value = profiles.value.first { it.id == id }
        }

        override suspend fun add(name: String, url: String, apiKey: String, makeActive: Boolean): ServerProfile =
            error("Unused")

        override suspend fun getApiKey(id: String): ApiKeyResult = error("Unused")
    }
}

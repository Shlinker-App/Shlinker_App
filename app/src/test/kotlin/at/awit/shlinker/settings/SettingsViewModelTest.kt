package at.awit.shlinker.settings

import androidx.datastore.core.DataStore
import at.awit.shlinker.core.data.AppTagSettingsRepository
import at.awit.shlinker.core.datastore.StoredServers
import at.awit.shlinker.core.model.AppTagSettings
import at.awit.shlinker.core.model.ShlinkDefaults
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var stored = StoredServers()
    private var failRead = false
    private var failSave = false
    private var gate: CompletableDeferred<Unit>? = null
    private var writes = 0
    private val repository = AppTagSettingsRepository(object : DataStore<StoredServers> {
        override val data = flow {
            if (failRead) throw IOException("read")
            emit(stored)
        }
        override suspend fun updateData(transform: suspend (StoredServers) -> StoredServers): StoredServers {
            writes++
            gate?.await()
            if (failSave) throw IOException("write")
            return transform(stored).also { stored = it }
        }
    })

    @Before fun setup() = Dispatchers.setMain(dispatcher)

    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun savesSharedCustomNameAndOptOutAndReloadsSavedValues() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        advanceUntilIdle()
        assertEquals(AppTagSettings(), vm.state.value.appTag)
        assertEquals(ShlinkDefaults(false, null), vm.state.value.defaults)
        vm.change(AppTagSettings(false, " custom app "))
        vm.changeDefaults(ShlinkDefaults(true, false))
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.saved)
        assertEquals(AppTagSettings(false, "custom app"), vm.state.value.appTag)
        val reopened = SettingsViewModel(repository)
        advanceUntilIdle()
        assertEquals(vm.state.value.appTag, reopened.state.value.appTag)
        assertEquals(ShlinkDefaults(true, false), reopened.state.value.defaults)
        assertEquals(1, writes)
    }

    @Test fun loadFailureCannotSaveDefaultsAndRetryLoadsPersistedValues() = runTest(dispatcher) {
        stored = stored.copy(appTagEnabled = false, appTagName = "custom")
        failRead = true
        val vm = SettingsViewModel(repository)
        advanceUntilIdle()
        assertTrue(vm.state.value.loadFailed)
        vm.save()
        vm.change(AppTagSettings())
        assertEquals(0, writes)
        failRead = false
        vm.load()
        advanceUntilIdle()
        assertFalse(vm.state.value.loadFailed)
        assertEquals(AppTagSettings(false, "custom"), vm.state.value.appTag)
    }

    @Test fun validationAndSaveFailuresPreserveDraftAndRetryDoesNotReportFalseSuccess() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        advanceUntilIdle()
        for (invalid in listOf(" ", "one,two", "one\ntwo")) {
            vm.change(AppTagSettings(name = invalid))
            vm.save()
            assertTrue(vm.state.value.invalidName)
        }
        assertEquals(0, writes)
        vm.change(AppTagSettings(name = "custom"))
        failSave = true
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.saveFailed)
        assertFalse(vm.state.value.saved)
        assertEquals("custom", vm.state.value.appTag.name)
        assertEquals("shlinker", stored.appTagName)
        failSave = false
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.saved)
        assertFalse(vm.state.value.saveFailed)
        assertEquals("custom", stored.appTagName)
    }

    @Test fun pendingSaveRejectsDuplicateSubmitAndEdits() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        advanceUntilIdle()
        gate = CompletableDeferred()
        vm.change(AppTagSettings(name = "first"))
        vm.save()
        runCurrent()
        vm.save()
        vm.change(AppTagSettings(name = "second"))
        assertTrue(vm.state.value.saving)
        gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, writes)
        assertEquals("first", stored.appTagName)
    }
}

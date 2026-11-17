package app.shlinker.feature.shorturls

import androidx.lifecycle.SavedStateHandle
import app.shlinker.core.data.ApiKeyResult
import app.shlinker.core.data.ServerProfileRepository
import app.shlinker.core.model.ServerProfile
import app.shlinker.core.model.ServerSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
class SharedCreateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val profiles = Profiles()

    @Before fun setup() = Dispatchers.setMain(dispatcher)

    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun activeProfileIsInitiallySelectedAndDropdownDoesNotChangeGlobalActiveServer() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = SharedCreateViewModel(profiles, saved)
        runCurrent()
        assertEquals("2", vm.state.value.selectedId)
        vm.select("1")
        profiles.activeProfile.value = profiles.profiles.value.first()
        runCurrent()
        profiles.activeProfile.value = profiles.profiles.value.last()
        runCurrent()
        assertEquals("1", vm.state.value.selectedId)
        assertEquals("2", profiles.activeProfile.value?.id)
        vm.select("missing")
        assertEquals("1", vm.state.value.selectedId)
    }

    @Test fun removedSelectionRequiresExplicitChoiceAndEmptySetupCanRecover() = runTest(dispatcher) {
        val vm = SharedCreateViewModel(profiles, SavedStateHandle())
        runCurrent()
        profiles.profiles.value = profiles.profiles.value.take(1)
        runCurrent()
        assertNull(vm.state.value.selectedId)
        vm.select("1")
        assertEquals("1", vm.state.value.selectedId)
        val empty = Profiles().apply {
            profiles.value = emptyList()
            activeProfile.value = null
        }
        val setup = SharedCreateViewModel(empty, SavedStateHandle())
        runCurrent()
        assertTrue(setup.state.value.profiles.isEmpty())
        val added = ServerProfile("3", "New", "https://new.example", 0)
        empty.profiles.value = listOf(added)
        empty.activeProfile.value = added
        runCurrent()
        assertEquals("3", setup.state.value.selectedId)
    }

    @Test fun draftRestoresWithoutCredentialsAndUnfinishedAttemptDisablesRepeatAfterProcessRecreation() =
        runTest(dispatcher) {
            val saved = SavedStateHandle()
            val vm = SharedCreateViewModel(profiles, saved)
            runCurrent()
            vm.select("1")
            vm.rememberDraft(CreateShortUrlState(longUrl = "https://example.com", title = "Title", slug = "slug"))
            vm.markAttempt()
            assertFalse(vm.interrupted)
            val restored = SharedCreateViewModel(profiles, saved)
            runCurrent()
            assertTrue(restored.interrupted)
            assertEquals("1", restored.state.value.selectedId)
            assertEquals("https://example.com", restored.draftUrl)
            assertEquals("Title", restored.draftTitle)
            assertEquals("slug", restored.draftSlug)
            assertTrue(saved.keys().none { it.contains("key", ignoreCase = true) })
            restored.clearAttempt()
            assertTrue(SharedCreateViewModel(profiles, saved).interrupted)
            vm.clearAttempt()
            assertFalse(SharedCreateViewModel(profiles, saved).interrupted)
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
        override suspend fun setActive(id: String) = error("Share dropdown must not change active profile")
        override suspend fun delete(id: String) = error("unused")
        override suspend fun add(name: String, url: String, apiKey: String, makeActive: Boolean): ServerProfile =
            error("unused")
        override suspend fun update(id: String, name: String?, url: String?, apiKey: String?): ServerProfile =
            error("unused")
    }
}

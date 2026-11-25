package app.shlinker.feature.servers

import app.shlinker.core.data.ApiKeyResult
import app.shlinker.core.data.DuplicateCheckUnavailableException
import app.shlinker.core.data.DuplicateServerException
import app.shlinker.core.data.ServerNameConflictException
import app.shlinker.core.data.ServerProfileRepository
import app.shlinker.core.model.ServerProfile
import app.shlinker.core.model.ServerSort
import app.shlinker.core.model.ServerUrl
import app.shlinker.core.network.ConnectionError
import app.shlinker.core.network.ConnectionResult
import app.shlinker.core.network.ConnectionTester
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddServerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeRepository()
    private val tester = FakeTester()
    private lateinit var vm: AddServerViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = AddServerViewModel(repo, tester)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun editLoadsWithoutExposingKeyAndBlankKeyPreservesCredentials() = runTest(dispatcher) {
        val original = repo.add("Home", "https://s.example.org", "saved-key", true)
        vm.load(original.id)
        advanceUntilIdle()
        assertEquals("", vm.state.value.apiKey)
        assertEquals("Home", vm.state.value.name)
        assertTrue(vm.state.value.canSubmit)
        vm.onUrlChange("new.example.org")
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals("https://new.example.org" to "saved-key", tester.calls.single())
        assertNull(repo.updatedKey)
        assertEquals("saved-key", repo.key)
        assertEquals(original.id, repo.profiles.first().single().id)
        assertEquals("https://new.example.org", repo.profiles.first().single().url)
        assertEquals(AddServerEvent.Saved(original.id), vm.events.first())
    }

    @Test fun editReplacesKeyOnlyAfterSuccessfulConnectionAndRejectsDuplicate() = runTest(dispatcher) {
        val original = repo.add("Home", "https://s.example.org", "saved-key", true)
        vm.load(original.id)
        advanceUntilIdle()
        vm.onApiKeyChange("new-key")
        tester.result = ConnectionResult.Failure(ConnectionError.InvalidApiKey)
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals("saved-key", repo.key)
        assertEquals(0, repo.updates)
        tester.result = ConnectionResult.Success("5")
        repo.addFailure = DuplicateServerException()
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(ServerSaveError.DUPLICATE, vm.state.value.saveError)
        assertEquals("saved-key", repo.key)
        repo.addFailure = null
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals("new-key", repo.key)
        assertEquals(1, repo.updates)
    }

    @Test fun editMissingKeyRequiresReplacementAndMissingProfileCannotBeSaved() = runTest(dispatcher) {
        vm.load("missing")
        advanceUntilIdle()
        assertEquals(ServerSaveError.NOT_FOUND, vm.state.value.saveError)
        assertFalse(vm.state.value.canSubmit)
        val original = repo.add("Home", "https://s.example.org", "key", true)
        vm.load(original.id)
        advanceUntilIdle()
        repo.key = null
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(ServerSaveError.KEY_UNAVAILABLE, vm.state.value.saveError)
        assertTrue(tester.calls.isEmpty())
        vm.onApiKeyChange("replacement")
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals("replacement", repo.key)
    }

    @Test fun cancellingEditConnectionKeepsOriginalProfileAndKey() = runTest(dispatcher) {
        val original = repo.add("Home", "https://s.example.org", "saved-key", true)
        vm.load(original.id)
        advanceUntilIdle()
        vm.onUrlChange("new.example.org")
        vm.onApiKeyChange("replacement")
        tester.gate = CompletableDeferred()
        vm.onSubmit()
        runCurrent()
        vm.onSubmit()
        assertEquals(1, tester.calls.size)
        vm.onCancelConnect()
        tester.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.state.value.isConnecting)
        assertEquals(0, repo.updates)
        assertEquals(original, repo.profiles.first().single())
        assertEquals("saved-key", repo.key)
    }

    @Test
    fun `a successful connection saves the profile and emits Saved`() = runTest(dispatcher) {
        vm.onUrlChange("S.example.org/")
        vm.onApiKeyChange(" key ")
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals("https://s.example.org" to "key", tester.calls.single())
        assertEquals(AddServerEvent.Saved("id-1"), vm.events.first())
        assertEquals("https://s.example.org", repo.added.single().url)
        assertFalse(vm.state.value.isConnecting)
    }

    @Test
    fun `a failed connection sets an error without saving`() = runTest(dispatcher) {
        tester.result = ConnectionResult.Failure(ConnectionError.InvalidApiKey)
        vm.onUrlChange("s.example.org")
        vm.onApiKeyChange("invalid-key")
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(ConnectionError.InvalidApiKey, vm.state.value.connectionError)
        assertTrue(repo.added.isEmpty())
    }

    @Test
    fun `HTTP is rejected before any network request`() = runTest(dispatcher) {
        vm.onUrlChange("http://s.example.org")
        vm.onApiKeyChange("key")
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(ServerUrl.Reason.INSECURE_HTTP, vm.state.value.urlError)
        assertTrue(tester.calls.isEmpty())
    }

    @Test
    fun `a missing API key is flagged`() = runTest(dispatcher) {
        vm.onUrlChange("s.example.org")
        vm.onSubmit()
        assertTrue(vm.state.value.apiKeyMissing)
        assertTrue(tester.calls.isEmpty())
    }

    @Test
    fun `changing the URL clears the error and shows the preview`() = runTest(dispatcher) {
        tester.result = ConnectionResult.Failure(ConnectionError.Timeout)
        vm.onUrlChange("s.example.org")
        vm.onApiKeyChange("k")
        vm.onSubmit()
        advanceUntilIdle()
        vm.onUrlChange("s.example.org/rest/v3")

        assertNull(vm.state.value.connectionError)
        assertEquals("https://s.example.org", vm.state.value.normalizedUrlPreview)
    }

    @Test
    fun `URL validation waits until the field loses focus`() = runTest(dispatcher) {
        vm.onUrlChange("ftp://x")
        assertNull(vm.state.value.urlError)
        vm.onUrlFocusLost()
        assertEquals(ServerUrl.Reason.UNSUPPORTED_SCHEME, vm.state.value.urlError)
    }

    private class FakeTester : ConnectionTester {
        var result: ConnectionResult = ConnectionResult.Success("5.1.7")
        var gate: CompletableDeferred<Unit>? = null
        val calls = mutableListOf<Pair<String, String>>()
        override suspend fun test(serverUrl: String, apiKey: String): ConnectionResult {
            calls += serverUrl to apiKey
            gate?.await()
            return result
        }
    }

    private class FakeRepository : ServerProfileRepository {
        override val sort = MutableStateFlow(ServerSort.MANUAL)
        override suspend fun setSort(sort: ServerSort) = error("unused")
        override suspend fun reorder(ids: List<String>) = error("unused")
        var addFailure: Exception? = null
        val added = mutableListOf<ServerProfile>()
        var key: String? = null
        var updatedKey: String? = null
        var updates = 0
        private val state = MutableStateFlow<List<ServerProfile>>(emptyList())
        override val profiles: Flow<List<ServerProfile>> = state
        override val activeProfile: Flow<ServerProfile?> = state.map { it.lastOrNull() }

        override suspend fun add(name: String, url: String, apiKey: String, makeActive: Boolean): ServerProfile {
            addFailure?.let { throw it }
            val p = ServerProfile("id-${added.size + 1}", name.ifBlank { url }, url, 0L)
            added += p
            key = apiKey
            state.value = added.toList()
            return p
        }

        override suspend fun update(id: String, name: String?, url: String?, apiKey: String?): ServerProfile {
            addFailure?.let { throw it }
            val original = state.value.first { it.id == id }
            val updated = original.copy(name = name ?: original.name, url = url ?: original.url)
            state.value = state.value.map { if (it.id == id) updated else it }
            updatedKey = apiKey
            if (apiKey != null) key = apiKey
            updates++
            return updated
        }
        override suspend fun delete(id: String) = error("unused")
        override suspend fun setActive(id: String) = error("unused")
        override suspend fun getApiKey(id: String): ApiKeyResult =
            key?.let(ApiKeyResult::Available) ?: ApiKeyResult.Missing
    }

    @Test
    fun `a duplicate error keeps the input and does not emit Saved`() = runTest(dispatcher) {
        repo.addFailure = DuplicateServerException()
        vm.onNameChange("Other")
        vm.onUrlChange("s.example.org")
        vm.onApiKeyChange("key")
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(ServerSaveError.DUPLICATE, vm.state.value.saveError)
        assertFalse(vm.state.value.isConnecting)
        assertEquals("Other", vm.state.value.name)
        assertEquals("key", vm.state.value.apiKey)
        assertTrue(repo.added.isEmpty())
        assertNull(withTimeoutOrNull(1) { vm.events.firstOrNull() })

        vm.onNameChange("Another name")
        assertEquals(ServerSaveError.DUPLICATE, vm.state.value.saveError)
        vm.onApiKeyChange("different-key")
        assertNull(vm.state.value.saveError)
        repo.addFailure = null
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(AddServerEvent.Saved("id-1"), vm.events.first())
    }

    @Test
    fun `duplicate check and storage failures set distinct errors`() = runTest(dispatcher) {
        vm.onUrlChange("s.example.org")
        vm.onApiKeyChange("key")
        for ((error, expected) in listOf(
            DuplicateCheckUnavailableException() to ServerSaveError.CANNOT_CHECK_DUPLICATE,
            IOException("disk") to ServerSaveError.STORAGE,
        )) {
            repo.addFailure = error
            vm.onSubmit()
            advanceUntilIdle()
            assertEquals(expected, vm.state.value.saveError)
            assertFalse(vm.state.value.isConnecting)
        }
        vm.onUrlChange("other.example.org")
        assertNull(vm.state.value.saveError)
        assertTrue(repo.added.isEmpty())
    }

    @Test
    fun `a name conflict can be corrected and does not emit Saved`() = runTest(dispatcher) {
        repo.addFailure = ServerNameConflictException()
        vm.onNameChange("Home")
        vm.onUrlChange("s.example.org")
        vm.onApiKeyChange("different-key")
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(ServerSaveError.NAME_CONFLICT, vm.state.value.saveError)
        assertFalse(vm.state.value.isConnecting)
        assertTrue(repo.added.isEmpty())
        assertNull(withTimeoutOrNull(1) { vm.events.firstOrNull() })
        vm.onNameChange("Work")
        assertNull(vm.state.value.saveError)
        assertEquals("different-key", vm.state.value.apiKey)
        repo.addFailure = null
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals("Work", repo.added.single().name)
        assertEquals(AddServerEvent.Saved("id-1"), vm.events.first())
    }
}

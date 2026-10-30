package at.awit.shlinker.feature.servers

import at.awit.shlinker.core.data.ApiKeyResult
import at.awit.shlinker.core.data.DuplicateCheckUnavailableException
import at.awit.shlinker.core.data.DuplicateServerException
import at.awit.shlinker.core.data.ServerProfileRepository
import at.awit.shlinker.core.model.ServerProfile
import at.awit.shlinker.core.model.ServerUrl
import at.awit.shlinker.core.network.ConnectionError
import at.awit.shlinker.core.network.ConnectionResult
import at.awit.shlinker.core.network.ConnectionTester
import java.io.IOException
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

    @Test
    fun `erfolgreiche Verbindung speichert und meldet Saved`() = runTest(dispatcher) {
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
    fun `Fehler wird angezeigt und nichts gespeichert`() = runTest(dispatcher) {
        tester.result = ConnectionResult.Failure(ConnectionError.InvalidApiKey)
        vm.onUrlChange("s.example.org")
        vm.onApiKeyChange("falsch")
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(ConnectionError.InvalidApiKey, vm.state.value.connectionError)
        assertTrue(repo.added.isEmpty())
    }

    @Test
    fun `http wird vor dem Netzwerkzugriff abgelehnt`() = runTest(dispatcher) {
        vm.onUrlChange("http://s.example.org")
        vm.onApiKeyChange("key")
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(ServerUrl.Reason.INSECURE_HTTP, vm.state.value.urlError)
        assertTrue(tester.calls.isEmpty())
    }

    @Test
    fun `fehlender Key wird markiert`() = runTest(dispatcher) {
        vm.onUrlChange("s.example.org")
        vm.onSubmit()
        assertTrue(vm.state.value.apiKeyMissing)
        assertTrue(tester.calls.isEmpty())
    }

    @Test
    fun `Eingabe setzt Fehler zurueck und zeigt Vorschau`() = runTest(dispatcher) {
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
    fun `URL-Pruefung erst beim Verlassen des Feldes`() = runTest(dispatcher) {
        vm.onUrlChange("ftp://x")
        assertNull(vm.state.value.urlError)
        vm.onUrlFocusLost()
        assertEquals(ServerUrl.Reason.UNSUPPORTED_SCHEME, vm.state.value.urlError)
    }

    private class FakeTester : ConnectionTester {
        var result: ConnectionResult = ConnectionResult.Success("5.1.7")
        val calls = mutableListOf<Pair<String, String>>()
        override suspend fun test(serverUrl: String, apiKey: String): ConnectionResult {
            calls += serverUrl to apiKey
            return result
        }
    }

    private class FakeRepository : ServerProfileRepository {
        var addFailure: Exception? = null
        val added = mutableListOf<ServerProfile>()
        private val state = MutableStateFlow<List<ServerProfile>>(emptyList())
        override val profiles: Flow<List<ServerProfile>> = state
        override val activeProfile: Flow<ServerProfile?> = state.map { it.lastOrNull() }

        override suspend fun add(name: String, url: String, apiKey: String, makeActive: Boolean): ServerProfile {
            addFailure?.let { throw it }
            val p = ServerProfile("id-${added.size + 1}", name.ifBlank { url }, url, 0L)
            added += p
            state.value = added.toList()
            return p
        }

        override suspend fun update(id: String, name: String?, url: String?, apiKey: String?) = error("unused")
        override suspend fun delete(id: String) = error("unused")
        override suspend fun setActive(id: String) = error("unused")
        override suspend fun getApiKey(id: String): ApiKeyResult = ApiKeyResult.Missing
    }

    @Test
    fun `Dublettenfehler wird angezeigt ohne Saved Event und Eingaben bleiben erhalten`() = runTest(dispatcher) {
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
    fun `Pruef und Speicherfehler werden explizit angezeigt`() = runTest(dispatcher) {
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
}

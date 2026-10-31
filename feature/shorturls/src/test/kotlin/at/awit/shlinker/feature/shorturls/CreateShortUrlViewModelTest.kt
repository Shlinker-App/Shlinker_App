package at.awit.shlinker.feature.shorturls

import androidx.datastore.core.DataStore
import at.awit.shlinker.core.data.ApiKeyResult
import at.awit.shlinker.core.data.AppTagSettingsRepository
import at.awit.shlinker.core.data.ServerProfileRepository
import at.awit.shlinker.core.data.ShortUrlCreator
import at.awit.shlinker.core.datastore.StoredServers
import at.awit.shlinker.core.model.CreateShortUrl
import at.awit.shlinker.core.model.ServerProfile
import at.awit.shlinker.core.model.ServerSort
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.network.CreateShortUrlException
import at.awit.shlinker.core.network.CreateShortUrlFailure
import at.awit.shlinker.core.network.CreateShortUrlService
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
class CreateShortUrlViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val profiles = Profiles()
    private var input: CreateShortUrl? = null
    private var calls = 0
    private var failure: CreateShortUrlException? = null
    private var gate: CompletableDeferred<Unit>? = null
    private val stored = MutableStateFlow(StoredServers())
    private val appTags = AppTagSettingsRepository(object : DataStore<StoredServers> {
        override val data = stored
        override suspend fun updateData(transform: suspend (StoredServers) -> StoredServers): StoredServers =
            transform(stored.value).also { stored.value = it }
    })
    private val service = object : CreateShortUrlService {
        override suspend fun create(serverUrl: String, apiKey: String, input: CreateShortUrl): ShortUrl {
            assertEquals("https://s.example", serverUrl)
            assertEquals("key", apiKey)
            calls++
            gate?.await()
            failure?.let { throw it }
            this@CreateShortUrlViewModelTest.input = input
            return ShortUrl("abc", "https://s.example/abc", input.longUrl, input.title, input.tags)
        }
    }
    private lateinit var vm: CreateShortUrlViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        vm = CreateShortUrlViewModel(profiles, ShortUrlCreator(profiles, service, appTags), appTags)
    }

    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun createUsesChosenServerAndNormalizesOptionalInputWithoutDuplicates() = runTest(dispatcher) {
        vm.load("1")
        advanceUntilIdle()
        vm.change(
            vm.state.value.copy(
                longUrl = " https://example.org/path?a=1 ",
                title = " Title ",
                slug = " custom ",
                domain = "s.example.org",
                tags = listOf("work"),
                tagInput = " news,work, , ",
            ),
        )
        gate = CompletableDeferred()
        vm.submit()
        runCurrent()
        vm.submit()
        assertTrue(vm.state.value.busy)
        gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(
            CreateShortUrl(
                "https://example.org/path?a=1",
                "Title",
                "custom",
                "s.example.org",
                listOf("work", "news", "shlinker"),
                forwardQuery = false,
            ),
            input,
        )
        assertEquals("https://s.example/abc", vm.state.value.created?.shortUrl)
        vm.submit()
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test fun invalidUrlOrDomainMakesNoRequestAndIsCorrectable() = runTest(dispatcher) {
        vm.load("1")
        advanceUntilIdle()
        for (url in listOf("example.org", "ftp://example.org", "https://user:pass@example.org", "")) {
            vm.change(vm.state.value.copy(longUrl = url))
            vm.submit()
            assertEquals(CreateError.URL, vm.state.value.error)
        }
        for (domain in listOf(
            "https://s.example",
            "s.example/path",
            "s.example:443",
            "user@s.example",
            "s.example?x=1",
        )) {
            vm.change(vm.state.value.copy(longUrl = "https://example.org", domain = domain))
            vm.submit()
            assertEquals(CreateError.DOMAIN, vm.state.value.error)
        }
        assertEquals(0, calls)
        vm.change(vm.state.value.copy(domain = ""))
        vm.submit()
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(CreateShortUrl("https://example.org", tags = listOf("shlinker"), forwardQuery = false), input)
    }

    @Test fun serviceErrorsPreserveInputAndCanBeCorrected() = runTest(dispatcher) {
        vm.load("1")
        advanceUntilIdle()
        vm.change(vm.state.value.copy(longUrl = "https://example.org", slug = "taken"))
        for ((failureKind, expected) in listOf(
            CreateShortUrlFailure.SLUG_CONFLICT to CreateError.CONFLICT,
            CreateShortUrlFailure.INVALID_INPUT to CreateError.INVALID,
            CreateShortUrlFailure.UNAUTHORIZED to CreateError.AUTH,
            CreateShortUrlFailure.SERVER to CreateError.SERVER,
            CreateShortUrlFailure.UNCERTAIN to CreateError.UNCERTAIN,
        )) {
            failure = CreateShortUrlException(failureKind)
            vm.submit()
            advanceUntilIdle()
            assertEquals(expected, vm.state.value.error)
            assertEquals("taken", vm.state.value.slug)
            assertFalse(vm.state.value.busy)
            assertNull(vm.state.value.created)
        }
    }

    @Test fun missingKeyAndDeletedProfileNeverSendPost() = runTest(dispatcher) {
        vm.load("1")
        advanceUntilIdle()
        vm.change(vm.state.value.copy(longUrl = "https://example.org"))
        profiles.key = ApiKeyResult.Unavailable
        vm.submit()
        advanceUntilIdle()
        assertEquals(CreateError.MISSING_KEY, vm.state.value.error)
        profiles.profiles.value = emptyList()
        vm.submit()
        advanceUntilIdle()
        assertEquals(CreateError.MISSING_SERVER, vm.state.value.error)
        assertEquals(0, calls)
    }

    @Test fun uncertainResultCannotBeResubmittedEvenAfterEditing() = runTest(dispatcher) {
        vm.load("1")
        advanceUntilIdle()
        vm.change(vm.state.value.copy(longUrl = "https://example.org"))
        failure = CreateShortUrlException(CreateShortUrlFailure.UNCERTAIN)
        vm.submit()
        advanceUntilIdle()
        vm.change(vm.state.value.copy(title = "Updated"))
        vm.submit()
        advanceUntilIdle()
        assertEquals(CreateError.UNCERTAIN, vm.state.value.error)
        assertEquals("Updated", vm.state.value.title)
        assertEquals(1, calls)
    }

    private class Profiles : ServerProfileRepository {
        override val profiles = MutableStateFlow(listOf(ServerProfile("1", "Home", "https://s.example", 0)))
        override val activeProfile = MutableStateFlow<ServerProfile?>(profiles.value.single())
        override val sort = MutableStateFlow(ServerSort.MANUAL)
        var key: ApiKeyResult = ApiKeyResult.Available("key")
        override suspend fun getApiKey(id: String) = key
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

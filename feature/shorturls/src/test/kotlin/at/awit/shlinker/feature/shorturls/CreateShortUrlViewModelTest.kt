package at.awit.shlinker.feature.shorturls

import androidx.datastore.core.DataStore
import at.awit.shlinker.core.data.ApiKeyResult
import at.awit.shlinker.core.data.AppTagSettingsRepository
import at.awit.shlinker.core.data.ServerDomainsRepository
import at.awit.shlinker.core.data.ServerProfileRepository
import at.awit.shlinker.core.data.ShortUrlCreator
import at.awit.shlinker.core.datastore.StoredServers
import at.awit.shlinker.core.model.CreateShortUrl
import at.awit.shlinker.core.model.ServerDomain
import at.awit.shlinker.core.model.ServerProfile
import at.awit.shlinker.core.model.ServerSort
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.network.CreateShortUrlException
import at.awit.shlinker.core.network.CreateShortUrlFailure
import at.awit.shlinker.core.network.CreateShortUrlService
import at.awit.shlinker.core.network.ServerDomainsService
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
    private var domains = listOf(ServerDomain("s.example", true))
    private var domainsFailure: IOException? = null
    private var domainsGate: CompletableDeferred<Unit>? = null
    private var domainCalls = 0

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        vm = CreateShortUrlViewModel(
            profiles,
            ShortUrlCreator(profiles, service, appTags),
            appTags,
            ServerDomainsRepository(
                profiles,
                object : ServerDomainsService {
                    override suspend fun get(serverUrl: String, apiKey: String): List<ServerDomain> {
                        domainCalls++
                        domainsGate?.await()
                        domainsFailure?.let { throw it }
                        return domains
                    }
                },
            ),
        )
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
                manualDomain = true,
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
            vm.change(vm.state.value.copy(longUrl = "https://example.org", domain = domain, manualDomain = true))
            vm.submit()
            assertEquals(CreateError.DOMAIN, vm.state.value.error)
        }
        assertEquals(0, calls)
        vm.change(vm.state.value.copy(domain = "s.example", manualDomain = false))
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

    @Test fun soleCustomDomainIsExplicitlySentInsteadOfOmittingDomainForWrongServerDefault() = runTest(dispatcher) {
        domains = listOf(ServerDomain("custom.example", false))
        vm.load("1")
        advanceUntilIdle()
        assertEquals("custom.example", vm.state.value.domain)
        assertTrue(vm.state.value.domainReady)
        vm.change(vm.state.value.copy(longUrl = "https://example.org"))
        vm.submit()
        advanceUntilIdle()
        assertEquals("custom.example", input?.domain)
    }

    @Test fun multipleDomainsPreferDefaultButRequireChoiceWhenNoDefaultIsVisible() = runTest(dispatcher) {
        domains = listOf(ServerDomain("a.example", false), ServerDomain("s.example", true))
        vm.load("1")
        advanceUntilIdle()
        assertEquals("s.example", vm.state.value.domain)
        domains = listOf(ServerDomain("a.example", false), ServerDomain("b.example", false))
        vm.loadDomains()
        advanceUntilIdle()
        assertEquals("", vm.state.value.domain)
        assertFalse(vm.state.value.domainReady)
        vm.change(vm.state.value.copy(longUrl = "https://example.org"))
        vm.submit()
        assertEquals(CreateError.DOMAIN, vm.state.value.error)
        assertEquals(0, calls)
        vm.change(vm.state.value.copy(domain = "b.example"))
        vm.submit()
        advanceUntilIdle()
        assertEquals("b.example", input?.domain)
    }

    @Test fun domainFailureNeverAssumesApiHostnameAndExplicitManualEntryCanContinue() = runTest(dispatcher) {
        domainsFailure = IOException("offline")
        vm.load("1")
        advanceUntilIdle()
        assertTrue(vm.state.value.domainError is IOException)
        assertFalse(vm.state.value.domainReady)
        assertEquals("", vm.state.value.domain)
        vm.change(vm.state.value.copy(longUrl = "https://example.org"))
        vm.submit()
        assertEquals(0, calls)
        vm.change(vm.state.value.copy(manualDomain = true, domain = "manual.example"))
        vm.submit()
        advanceUntilIdle()
        assertEquals("manual.example", input?.domain)
    }

    @Test fun retryPreservesFormAndExplicitManualDomainAndDuplicateLoadsAreBounded() = runTest(dispatcher) {
        domainsGate = CompletableDeferred()
        vm.load("1")
        runCurrent()
        vm.loadDomains()
        assertEquals(1, domainCalls)
        vm.change(vm.state.value.copy(longUrl = "https://example.org", title = "Keep"))
        assertFalse(vm.state.value.domainReady)
        domainsGate?.complete(Unit)
        advanceUntilIdle()
        vm.change(vm.state.value.copy(manualDomain = true, domain = "manual.example"))
        vm.loadDomains()
        advanceUntilIdle()
        assertEquals("Keep", vm.state.value.title)
        assertEquals("manual.example", vm.state.value.domain)
        assertTrue(vm.state.value.manualDomain)
        assertEquals(2, domainCalls)
    }

    @Test fun profileSwitchCancelsStaleDomainResultsAndResetsDomainAndDraft() = runTest(dispatcher) {
        domainsGate = CompletableDeferred()
        vm.load("1")
        runCurrent()
        val oldGate = domainsGate
        vm.change(vm.state.value.copy(longUrl = "https://old.example", manualDomain = true, domain = "old.example"))
        domainsGate = null
        domains = listOf(ServerDomain("new.example", false))
        profiles.profiles.value += ServerProfile("2", "Second", "https://second.example", 1)
        vm.load("2")
        advanceUntilIdle()
        oldGate?.complete(Unit)
        runCurrent()
        assertEquals("Second", vm.state.value.serverName)
        assertEquals("new.example", vm.state.value.domain)
        assertFalse(vm.state.value.manualDomain)
        assertEquals("", vm.state.value.longUrl)
    }

    @Test fun emptyDomainsNeverCreateWithAnInferredDefaultAndRetrySelectsNewKnownDefault() = runTest(dispatcher) {
        domains = emptyList()
        vm.load("1")
        advanceUntilIdle()
        assertEquals(emptyList<ServerDomain>(), vm.state.value.domains)
        assertEquals("", vm.state.value.domain)
        vm.change(vm.state.value.copy(longUrl = "https://example.org"))
        vm.submit()
        assertEquals(CreateError.DOMAIN, vm.state.value.error)
        assertEquals(0, calls)
        domains = listOf(ServerDomain("short.example", true))
        vm.loadDomains()
        advanceUntilIdle()
        assertEquals("short.example", vm.state.value.domain)
        vm.submit()
        advanceUntilIdle()
        assertNull(input?.domain)
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

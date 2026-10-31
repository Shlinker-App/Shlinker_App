package at.awit.shlinker.feature.shorturls

import at.awit.shlinker.core.data.ApiKeyResult
import at.awit.shlinker.core.data.ServerNotFoundException
import at.awit.shlinker.core.data.ServerProfileRepository
import at.awit.shlinker.core.data.ShortUrlDeleter
import at.awit.shlinker.core.data.ShortUrlDetailsRepository
import at.awit.shlinker.core.data.ShortUrlUpdater
import at.awit.shlinker.core.model.ServerProfile
import at.awit.shlinker.core.model.ServerSort
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.model.ShortUrlDetails
import at.awit.shlinker.core.model.ShortUrlEdit
import at.awit.shlinker.core.network.ConnectionError
import at.awit.shlinker.core.network.DeleteShortUrlException
import at.awit.shlinker.core.network.DeleteShortUrlFailure
import at.awit.shlinker.core.network.DeleteShortUrlService
import at.awit.shlinker.core.network.ShortUrlDetailsService
import at.awit.shlinker.core.network.ShortUrlRequestException
import at.awit.shlinker.core.network.UpdateShortUrlException
import at.awit.shlinker.core.network.UpdateShortUrlFailure
import at.awit.shlinker.core.network.UpdateShortUrlService
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
class ShortUrlDetailsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val profiles = Profiles()
    private val calls = mutableListOf<String?>()
    private var failure: IOException? = null
    private var gate: CompletableDeferred<Unit>? = null
    private var cancelled = false
    private val updates = mutableListOf<ShortUrlEdit>()
    private var updateFailure: IOException? = null
    private var updateGate: CompletableDeferred<Unit>? = null
    private val deletions = mutableListOf<Pair<String, String?>>()
    private var deleteFailure: IOException? = null
    private var deleteGate: CompletableDeferred<Unit>? = null
    private val deleteService = object : DeleteShortUrlService {
        override suspend fun delete(serverUrl: String, apiKey: String, shortCode: String, domain: String?) {
            assertEquals("https://s.example", serverUrl)
            assertEquals("key", apiKey)
            deletions += shortCode to domain
            deleteGate?.await()
            deleteFailure?.let { throw it }
        }
    }
    private val updateService = object : UpdateShortUrlService {
        override suspend fun update(serverUrl: String, apiKey: String, input: ShortUrlEdit): ShortUrlDetails {
            assertEquals("https://s.example", serverUrl)
            assertEquals("key", apiKey)
            updates += input
            updateGate?.await()
            updateFailure?.let { throw it }
            return input.original.copy(
                url = input.original.url.copy(longUrl = input.longUrl, title = input.title, tags = input.tags),
                validSince = input.validSince,
                validUntil = input.validUntil,
                maxVisits = input.maxVisits,
                crawlable = input.crawlable,
                forwardQuery = input.forwardQuery,
            )
        }
    }
    private val service = object : ShortUrlDetailsService {
        override suspend fun get(
            serverUrl: String,
            apiKey: String,
            shortCode: String,
            domain: String?,
        ): ShortUrlDetails {
            assertEquals("https://s.example", serverUrl)
            assertEquals("key", apiKey)
            calls += domain
            try {
                gate?.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw e
            }
            failure?.let { throw it }
            return ShortUrlDetails(
                ShortUrl(
                    shortCode,
                    "https://${domain ?: "s.example"}/$shortCode",
                    "https://example.org",
                    null,
                    emptyList(),
                    domain = domain,
                ),
            )
        }
    }
    private lateinit var vm: ShortUrlDetailsViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        vm = ShortUrlDetailsViewModel(
            profiles,
            ShortUrlDetailsRepository(profiles, service),
            ShortUrlUpdater(profiles, updateService),
            ShortUrlDeleter(profiles, deleteService),
        )
    }

    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun loadAndExplicitRefreshRemainPinnedToProfileAndDomain() = runTest(dispatcher) {
        vm.load("1", "abc", "custom.example")
        advanceUntilIdle()
        assertEquals("Home", vm.state.value.serverName)
        assertEquals("https://custom.example/abc", vm.state.value.details?.url?.shortUrl)
        assertFalse(vm.state.value.loading)
        vm.load("1", "abc", "custom.example")
        advanceUntilIdle()
        assertEquals(1, calls.size)
        vm.load("1", "abc", "custom.example", refresh = true)
        advanceUntilIdle()
        assertEquals(2, calls.size)
        assertEquals("other", profiles.activeProfile.value?.id)
    }

    @Test fun failureIsVisibleAndExplicitRetryRecovers() = runTest(dispatcher) {
        failure = ShortUrlRequestException(ConnectionError.HttpError(404, null))
        vm.load("1", "abc", null)
        advanceUntilIdle()
        assertEquals(failure, vm.state.value.error)
        assertNull(vm.state.value.details)
        assertFalse(vm.state.value.loading)
        failure = null
        vm.load("1", "abc", null, refresh = true)
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals("abc", vm.state.value.details?.url?.shortCode)
    }

    @Test fun changingIdentityCancelsPreviousRequestAndDoesNotShowOldDetails() = runTest(dispatcher) {
        gate = CompletableDeferred()
        vm.load("1", "abc", "first.example")
        runCurrent()
        assertTrue(vm.state.value.loading)
        gate = null
        vm.load("1", "abc", "second.example")
        advanceUntilIdle()
        assertTrue(cancelled)
        assertEquals("https://second.example/abc", vm.state.value.details?.url?.shortUrl)
        assertEquals(listOf("first.example", "second.example"), calls)
    }

    @Test fun missingProfileNeverMakesNetworkRequest() = runTest(dispatcher) {
        vm.load("missing", "abc", null)
        advanceUntilIdle()
        assertTrue(vm.state.value.error is ServerNotFoundException)
        assertTrue(calls.isEmpty())
    }

    @Test fun editsPinnedDomainSavesPendingTagsClearsLimitsAndDoesNotApplyAppDefaults() = runTest(dispatcher) {
        vm.load("1", "abc", "custom.example")
        advanceUntilIdle()
        vm.edit()
        val initial = vm.state.value.form!!
        assertNull(initial.forwardQuery)
        vm.change(
            initial.copy(
                title = " New title ",
                longUrl = "https://new.example/path",
                tagInput = " one, two ",
                maxVisits = "12",
                forwardQuery = false,
            ),
        )
        vm.save()
        advanceUntilIdle()
        assertEquals("custom.example", updates.single().original.url.domain)
        assertEquals(listOf("one", "two"), updates.single().tags)
        assertEquals(false, updates.single().forwardQuery)
        assertEquals("New title", vm.state.value.details?.url?.title)
        assertEquals(12L, vm.state.value.details?.maxVisits)
        assertTrue(vm.state.value.saved)
        assertNull(vm.state.value.form)
        assertEquals(1, vm.state.value.changeRevision)
        assertEquals("other", profiles.activeProfile.value?.id)
    }

    @Test fun validationRejectsInvalidUrlsDatesRangesAndVisitLimitsWithoutRequest() = runTest(dispatcher) {
        vm.load("1", "abc", null)
        advanceUntilIdle()
        vm.edit()
        val form = vm.state.value.form!!
        val cases = listOf(
            form.copy(longUrl = "file:///tmp/no") to EditValidation.URL,
            form.copy(longUrl = "https://user:pass@example.org") to EditValidation.URL,
            form.copy(validSince = "2026-12-01") to EditValidation.SINCE,
            form.copy(validUntil = "2026-12-01") to EditValidation.UNTIL,
            form.copy(
                validSince = "2026-12-02T00:00:00Z",
                validUntil = "2026-12-01T00:00:00Z",
            ) to EditValidation.UNTIL_RANGE,
            form.copy(maxVisits = "0") to EditValidation.LIMIT,
            form.copy(maxVisits = "-1") to EditValidation.LIMIT,
            form.copy(maxVisits = "1.5") to EditValidation.LIMIT,
            form.copy(maxVisits = "9223372036854775808") to EditValidation.LIMIT,
        )
        for ((invalid, expected) in cases) {
            vm.change(invalid)
            vm.save()
            assertEquals(expected, vm.state.value.validation)
        }
        assertTrue(updates.isEmpty())
        vm.cancelEdit()
        assertNull(vm.state.value.form)
        assertEquals(0, vm.state.value.changeRevision)
    }

    @Test fun pendingSaveBlocksDuplicateSubmitEditingCancelAndRefresh() = runTest(dispatcher) {
        vm.load("1", "abc", null)
        advanceUntilIdle()
        vm.edit()
        val form = vm.state.value.form!!.copy(title = "First")
        vm.change(form)
        updateGate = CompletableDeferred()
        vm.save()
        runCurrent()
        vm.save()
        vm.change(form.copy(title = "Second"))
        vm.cancelEdit()
        vm.load("1", "abc", null, refresh = true)
        assertTrue(vm.state.value.saving)
        assertEquals("First", vm.state.value.form?.title)
        updateGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, updates.size)
        assertEquals(1, calls.size)
    }

    @Test fun lostResponseRequiresExplicitReloadAndNeverAutomaticallyResubmits() = runTest(dispatcher) {
        vm.load("1", "abc", null)
        advanceUntilIdle()
        vm.edit()
        vm.change(vm.state.value.form!!.copy(title = "Changed"))
        updateFailure = UpdateShortUrlException(UpdateShortUrlFailure.UNCERTAIN)
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.needsReload)
        assertFalse(vm.state.value.saved)
        assertEquals(1, vm.state.value.changeRevision)
        vm.save()
        vm.cancelEdit()
        assertEquals(1, updates.size)
        assertTrue(vm.state.value.needsReload)
        vm.load("1", "abc", null, refresh = true)
        advanceUntilIdle()
        assertFalse(vm.state.value.needsReload)
        assertNull(vm.state.value.form)
        assertEquals(1, updates.size)
    }

    @Test fun rejectedSaveKeepsDraftAndAllowsExplicitCorrection() = runTest(dispatcher) {
        vm.load("1", "abc", null)
        advanceUntilIdle()
        vm.edit()
        vm.change(vm.state.value.form!!.copy(title = "Changed"))
        updateFailure = UpdateShortUrlException(UpdateShortUrlFailure.INVALID_INPUT)
        vm.save()
        advanceUntilIdle()
        assertEquals("Changed", vm.state.value.form?.title)
        assertFalse(vm.state.value.needsReload)
        assertEquals(0, vm.state.value.changeRevision)
        updateFailure = null
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.saved)
        assertEquals(2, updates.size)
    }

    @Test fun deleteRequiresConfirmationAndUsesPinnedDomainWithoutDuplicateOrForcedRequest() = runTest(dispatcher) {
        vm.load("1", "same", "custom.example")
        advanceUntilIdle()
        vm.delete()
        assertTrue(deletions.isEmpty())
        vm.requestDelete()
        assertTrue(vm.state.value.confirmingDelete)
        vm.cancelDelete()
        vm.delete()
        assertTrue(deletions.isEmpty())
        vm.requestDelete()
        deleteGate = CompletableDeferred()
        vm.delete()
        runCurrent()
        assertTrue(vm.state.value.saving)
        vm.delete()
        vm.edit()
        vm.requestDelete()
        vm.load("1", "same", "custom.example", refresh = true)
        assertNull(vm.state.value.form)
        deleteGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("same" to "custom.example"), deletions)
        assertTrue(vm.state.value.deleted)
        assertNull(vm.state.value.details)
        assertEquals(1, vm.state.value.changeRevision)
        assertEquals("other", profiles.activeProfile.value?.id)
        vm.requestDelete()
        vm.delete()
        assertEquals(1, deletions.size)
    }

    @Test fun uncertainDeletionRequiresReloadInsteadOfAnotherDeleteOrEdit() = runTest(dispatcher) {
        vm.load("1", "abc", null)
        advanceUntilIdle()
        vm.requestDelete()
        deleteFailure = DeleteShortUrlException(DeleteShortUrlFailure.UNCERTAIN)
        vm.delete()
        advanceUntilIdle()
        assertTrue(vm.state.value.needsReload)
        assertFalse(vm.state.value.deleted)
        assertEquals(1, vm.state.value.changeRevision)
        vm.requestDelete()
        vm.delete()
        vm.edit()
        assertNull(vm.state.value.form)
        assertEquals(1, deletions.size)
        vm.load("1", "abc", null, refresh = true)
        advanceUntilIdle()
        assertFalse(vm.state.value.needsReload)
        assertFalse(vm.state.value.confirmingDelete)
        assertEquals(1, deletions.size)
    }

    @Test fun thresholdRefusalIsVisibleAndDoesNotDeleteOrBypassProtection() = runTest(dispatcher) {
        vm.load("1", "abc", null)
        advanceUntilIdle()
        vm.requestDelete()
        deleteFailure = DeleteShortUrlException(DeleteShortUrlFailure.VISITS_THRESHOLD)
        vm.delete()
        advanceUntilIdle()
        assertEquals(deleteFailure, vm.state.value.error)
        assertFalse(vm.state.value.deleted)
        assertFalse(vm.state.value.needsReload)
        assertEquals(0, vm.state.value.changeRevision)
        assertEquals(1, deletions.size)
        assertEquals("abc", vm.state.value.details?.url?.shortCode)
    }

    @Test fun deleteIsUnavailableWhileEditingAndNotFoundIsNotClaimedAsOwnSuccessfulDeletion() = runTest(dispatcher) {
        vm.load("1", "abc", null)
        advanceUntilIdle()
        vm.edit()
        vm.requestDelete()
        vm.delete()
        assertFalse(vm.state.value.confirmingDelete)
        assertTrue(deletions.isEmpty())
        vm.cancelEdit()
        vm.requestDelete()
        deleteFailure = DeleteShortUrlException(DeleteShortUrlFailure.NOT_FOUND)
        vm.delete()
        advanceUntilIdle()
        assertFalse(vm.state.value.deleted)
        assertTrue(vm.state.value.needsReload)
        assertEquals(1, vm.state.value.changeRevision)
    }

    private class Profiles : ServerProfileRepository {
        override val profiles = MutableStateFlow(listOf(ServerProfile("1", "Home", "https://s.example", 0)))
        override val activeProfile =
            MutableStateFlow<ServerProfile?>(ServerProfile("other", "Other", "https://other", 1))
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

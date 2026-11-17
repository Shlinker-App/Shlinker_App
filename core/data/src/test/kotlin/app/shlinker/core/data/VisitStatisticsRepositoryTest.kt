package app.shlinker.core.data

import app.shlinker.core.datastore.StoredSecrets
import app.shlinker.core.datastore.StoredServers
import app.shlinker.core.model.VisitStatistics
import app.shlinker.core.model.VisitWindow
import app.shlinker.core.network.VisitStatisticsService
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VisitStatisticsRepositoryTest {
    @Test fun chosenProfileFreshKeyAndDomainAreUsedWithoutChangingActiveServerOrWindow() = runTest {
        val cipher = FakeCipher()
        val profiles = DefaultServerProfileRepository(
            InMemoryDataStore(StoredServers()),
            InMemoryDataStore(StoredSecrets()),
            cipher,
            StandardTestDispatcher(testScheduler),
        )
        val first = profiles.add("First", "first.example", "first-key")
        val active = profiles.add("Active", "active.example", "active-key")
        val calls = mutableListOf<List<String?>>()
        val window = VisitWindow(null, Instant.parse("2026-10-31T00:00:00Z"))
        val repository = VisitStatisticsRepository(
            profiles,
            object : VisitStatisticsService {
                override suspend fun get(
                    serverUrl: String,
                    apiKey: String,
                    shortCode: String,
                    domain: String?,
                    window: VisitWindow,
                ): VisitStatistics {
                    calls += listOf(serverUrl, apiKey, shortCode, domain)
                    assertEquals(Instant.parse("2026-10-31T00:00:00Z"), window.until)
                    return VisitStatistics(9, 7)
                }
            },
        )
        assertEquals(
            ServerVisitStatistics("First", VisitStatistics(9, 7)),
            repository.get(first.id, "same", "x", window),
        )
        profiles.update(first.id, name = "Renamed", apiKey = "replacement")
        assertEquals("Renamed", repository.get(first.id, "same", null, window).serverName)
        assertEquals(
            listOf(
                listOf(first.url, "first-key", "same", "x"),
                listOf(first.url, "replacement", "same", null),
            ),
            calls,
        )
        assertEquals(active, profiles.activeProfile.first())
        cipher.broken = true
        try {
            repository.get(first.id, "same", null, window)
            throw AssertionError("Expected unavailable key")
        } catch (_: ShortUrlApiKeyException) {
            assertEquals(2, calls.size)
        }
        profiles.delete(first.id)
        try {
            repository.get(first.id, "same", null, window)
            throw AssertionError("Expected missing profile")
        } catch (_: ServerNotFoundException) {
            assertEquals(2, calls.size)
        }
    }
}

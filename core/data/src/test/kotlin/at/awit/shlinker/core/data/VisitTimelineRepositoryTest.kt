package at.awit.shlinker.core.data

import at.awit.shlinker.core.datastore.StoredSecrets
import at.awit.shlinker.core.datastore.StoredServers
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.model.ShortUrlDetails
import at.awit.shlinker.core.model.VisitStatistics
import at.awit.shlinker.core.model.VisitWindow
import at.awit.shlinker.core.network.InvalidVisitStatisticsException
import at.awit.shlinker.core.network.ShortUrlDetailsService
import at.awit.shlinker.core.network.VisitStatisticsService
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitTimelineRepositoryTest {
    private val start = Instant.parse("2026-01-01T00:00:00Z")
    private val end = start.plusSeconds(119)
    private val windows = mutableListOf<VisitWindow>()
    private var detailCalls = 0
    private var created: String? = start.toString()
    private var failure: Exception? = null
    private var cumulative: (VisitWindow) -> VisitStatistics = { window ->
        val n = window.until.epochSecond - start.epochSecond + 1
        VisitStatistics(n, n * 7 / 10)
    }

    private fun repository(profiles: ServerProfileRepository) = VisitTimelineRepository(
        profiles,
        object : VisitStatisticsService {
            override suspend fun get(
                serverUrl: String,
                apiKey: String,
                shortCode: String,
                domain: String?,
                window: VisitWindow,
            ): VisitStatistics {
                assertEquals("https://first.example", serverUrl)
                assertEquals("replacement", apiKey)
                assertEquals("same", shortCode)
                assertEquals("custom.example", domain)
                windows += window
                failure?.let { throw it }
                return cumulative(window)
            }
        },
        object : ShortUrlDetailsService {
            override suspend fun get(serverUrl: String, apiKey: String, shortCode: String, domain: String?) =
                ShortUrlDetails(
                    ShortUrl(shortCode, "https://custom.example/same", "https://dest.example", null, emptyList()),
                    dateCreated = created,
                ).also {
                    detailCalls++
                    assertEquals("https://first.example", serverUrl)
                    assertEquals("replacement", apiKey)
                    assertEquals("custom.example", domain)
                }
        },
    )

    @Test fun cumulativeDifferencesUse6BoundedQueriesAndSumExactlyWithoutBoundaryDoubleCounting() = runTest {
        val profiles = DefaultServerProfileRepository(
            InMemoryDataStore(StoredServers()),
            InMemoryDataStore(StoredSecrets()),
            FakeCipher(),
            StandardTestDispatcher(testScheduler),
        )
        val profile = profiles.add("First", "first.example", "old")
        val active = profiles.add("Active", "active.example", "key")
        profiles.update(profile.id, apiKey = "replacement")
        val buckets = repository(profiles).get(
            profile.id,
            "same",
            "custom.example",
            VisitWindow(start, end),
            VisitStatistics(120, 84),
        )
        assertEquals(7, buckets.size)
        assertEquals(6, windows.size)
        assertEquals(0, detailCalls)
        assertTrue(windows.all { it.since == start })
        assertEquals(120, buckets.sumOf { it.counts.total })
        assertEquals(84, buckets.sumOf { it.counts.nonBots })
        assertEquals(listOf(17L, 17L, 17L, 17L, 17L, 17L, 18L), buckets.map { it.counts.total })
        assertEquals(listOf(11L, 12L, 12L, 12L, 12L, 12L, 13L), buckets.map { it.counts.nonBots })
        assertFalse(buckets.first().sinceExclusive)
        assertTrue(buckets.drop(1).all { it.sinceExclusive })
        assertTrue(buckets.zipWithNext().all { (first, second) -> first.until == second.since })
        assertEquals(active, profiles.activeProfile.first())
    }

    @Test fun allTimeUsesCreationForSpacingButNeverFiltersOutEarlierImportedVisits() = runTest {
        val profiles = DefaultServerProfileRepository(
            InMemoryDataStore(StoredServers()),
            InMemoryDataStore(StoredSecrets()),
            FakeCipher(),
            StandardTestDispatcher(testScheduler),
        )
        val profile = profiles.add("First", "first.example", "replacement")
        val buckets = repository(profiles).get(
            profile.id,
            "same",
            "custom.example",
            VisitWindow(null, end),
            VisitStatistics(120, 84),
        )
        assertEquals(1, detailCalls)
        assertTrue(windows.all { it.since == null })
        assertNull(buckets.first().since)
        assertEquals(end, buckets.last().until)
        for (invalid in listOf(null, "bad", end.plusSeconds(1).toString())) {
            created = invalid
            try {
                repository(profiles).get(
                    profile.id,
                    "same",
                    "custom.example",
                    VisitWindow(null, end),
                    VisitStatistics(120, 84),
                )
                throw AssertionError("Expected start failure")
            } catch (_: TimelineStartUnavailableException) {
                assertEquals(6, windows.size)
            }
        }
    }

    @Test fun nonMonotonicBotCountsOrOvershootAreExplicitFailuresNotNegativeBars() = runTest {
        val profiles = DefaultServerProfileRepository(
            InMemoryDataStore(StoredServers()),
            InMemoryDataStore(StoredSecrets()),
            FakeCipher(),
            StandardTestDispatcher(testScheduler),
        )
        val profile = profiles.add("First", "first.example", "replacement")
        for (values in listOf(
            { _: VisitWindow -> VisitStatistics(130, 90) },
            { w: VisitWindow ->
                if (w.until <
                    start.plusSeconds(20)
                ) {
                    VisitStatistics(10, 0)
                } else {
                    VisitStatistics(10, 10)
                }
            },
        )) {
            cumulative = values
            try {
                repository(profiles).get(
                    profile.id,
                    "same",
                    "custom.example",
                    VisitWindow(start, end),
                    VisitStatistics(120, 84),
                )
                throw AssertionError("Expected inconsistent timeline")
            } catch (_: InvalidVisitStatisticsException) {
                assertTrue(windows.isNotEmpty())
            }
        }
    }

    @Test fun networkFailureAndCancellationPropagateWithoutRetryOrPartialResult() = runTest {
        val cipher = FakeCipher()
        val profiles = DefaultServerProfileRepository(
            InMemoryDataStore(StoredServers()),
            InMemoryDataStore(StoredSecrets()),
            cipher,
            StandardTestDispatcher(testScheduler),
        )
        val profile = profiles.add("First", "first.example", "replacement")
        for (error in listOf(IOException("offline"), CancellationException("cancel"))) {
            failure = error
            try {
                repository(profiles).get(
                    profile.id,
                    "same",
                    "custom.example",
                    VisitWindow(start, end),
                    VisitStatistics(120, 84),
                )
                throw AssertionError("Expected failure")
            } catch (e: Exception) {
                assertEquals(error, e)
            }
        }
        assertEquals(2, windows.size)
        cipher.broken = true
        try {
            repository(
                profiles,
            ).get(profile.id, "same", "custom.example", VisitWindow(start, end), VisitStatistics(1, 1))
            throw AssertionError("Expected missing key")
        } catch (_: ShortUrlApiKeyException) {
            assertEquals(2, windows.size)
        }
        profiles.delete(profile.id)
        try {
            repository(
                profiles,
            ).get(profile.id, "same", "custom.example", VisitWindow(start, end), VisitStatistics(1, 1))
            throw AssertionError("Expected missing profile")
        } catch (_: ServerNotFoundException) {
            assertEquals(2, windows.size)
        }
    }
}

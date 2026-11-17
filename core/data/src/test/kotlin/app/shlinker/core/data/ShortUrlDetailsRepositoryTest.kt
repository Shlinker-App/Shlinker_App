package app.shlinker.core.data

import app.shlinker.core.datastore.StoredSecrets
import app.shlinker.core.datastore.StoredServers
import app.shlinker.core.model.ShortUrl
import app.shlinker.core.model.ShortUrlDetails
import app.shlinker.core.network.ShortUrlDetailsService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortUrlDetailsRepositoryTest {
    @Test fun detailsUseChosenProfileFreshKeyAndDomainWithoutChangingActiveServer() = runTest {
        val profiles = DefaultServerProfileRepository(
            InMemoryDataStore(StoredServers()),
            InMemoryDataStore(StoredSecrets()),
            FakeCipher(),
            StandardTestDispatcher(testScheduler),
        )
        val first = profiles.add("First", "first.example", "first-key")
        val active = profiles.add("Active", "active.example", "active-key")
        val calls = mutableListOf<List<String?>>()
        val repo = ShortUrlDetailsRepository(
            profiles,
            object : ShortUrlDetailsService {
                override suspend fun get(
                    serverUrl: String,
                    apiKey: String,
                    shortCode: String,
                    domain: String?,
                ): ShortUrlDetails {
                    calls += listOf(serverUrl, apiKey, shortCode, domain)
                    return ShortUrlDetails(
                        ShortUrl(shortCode, "$serverUrl/$shortCode", "https://example.org", null, emptyList()),
                    )
                }
            },
        )
        repo.get(first.id, "same", "custom.example")
        profiles.update(first.id, apiKey = "replacement")
        repo.get(first.id, "same", null)
        assertEquals(
            listOf(
                listOf(first.url, "first-key", "same", "custom.example"),
                listOf(first.url, "replacement", "same", null),
            ),
            calls,
        )
        assertEquals(active, profiles.activeProfile.first())
        profiles.delete(first.id)
        try {
            repo.get(first.id, "same", null)
            throw AssertionError("Expected missing profile")
        } catch (_: ServerNotFoundException) {
            assertEquals(2, calls.size)
        }
    }

    @Test fun unreadableKeyNeverInvokesDetailsService() = runTest {
        val cipher = FakeCipher()
        val profiles = DefaultServerProfileRepository(
            InMemoryDataStore(StoredServers()),
            InMemoryDataStore(StoredSecrets()),
            cipher,
            StandardTestDispatcher(testScheduler),
        )
        val profile = profiles.add("Home", "s.example", "key")
        cipher.broken = true
        val repo = ShortUrlDetailsRepository(
            profiles,
            object : ShortUrlDetailsService {
                override suspend fun get(serverUrl: String, apiKey: String, shortCode: String, domain: String?) =
                    error("No request expected")
            },
        )
        try {
            repo.get(profile.id, "abc", null)
            throw AssertionError("Expected unavailable key")
        } catch (_: ShortUrlApiKeyException) {
            assertEquals(profile, profiles.activeProfile.first())
        }
    }
}

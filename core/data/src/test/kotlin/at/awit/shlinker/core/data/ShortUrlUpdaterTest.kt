package at.awit.shlinker.core.data

import at.awit.shlinker.core.datastore.StoredSecrets
import at.awit.shlinker.core.datastore.StoredServers
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.model.ShortUrlDetails
import at.awit.shlinker.core.model.ShortUrlEdit
import at.awit.shlinker.core.network.UpdateShortUrlService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortUrlUpdaterTest {
    @Test fun updatesUseFreshKeyChosenProfileAndDomainWithoutChangingActiveOrApplyingCreationSettings() = runTest {
        val cipher = FakeCipher()
        val profiles = DefaultServerProfileRepository(
            InMemoryDataStore(StoredServers()),
            InMemoryDataStore(StoredSecrets()),
            cipher,
            StandardTestDispatcher(testScheduler),
        )
        val first = profiles.add("First", "first.example", "first-key")
        val active = profiles.add("Active", "active.example", "active-key")
        val original = ShortUrlDetails(
            ShortUrl(
                "same",
                "https://custom.example/same",
                "https://example.org",
                null,
                emptyList(),
                domain = "custom.example",
            ),
            forwardQuery = true,
        )
        val calls = mutableListOf<Pair<String, String>>()
        val updater = ShortUrlUpdater(
            profiles,
            object : UpdateShortUrlService {
                override suspend fun update(serverUrl: String, apiKey: String, input: ShortUrlEdit): ShortUrlDetails {
                    calls += serverUrl to apiKey
                    assertEquals(original.url.domain, input.original.url.domain)
                    assertEquals(emptyList<String>(), input.tags)
                    assertEquals(true, input.forwardQuery)
                    return original
                }
            },
        )
        updater.update(first.id, ShortUrlEdit(original, title = "New"))
        profiles.update(first.id, apiKey = "replacement")
        updater.update(first.id, ShortUrlEdit(original, title = "New"))
        assertEquals(listOf(first.url to "first-key", first.url to "replacement"), calls)
        assertEquals(active, profiles.activeProfile.first())
        cipher.broken = true
        try {
            updater.update(first.id, ShortUrlEdit(original))
            throw AssertionError("Expected unavailable key")
        } catch (_: ShortUrlApiKeyException) {
            assertEquals(2, calls.size)
        }
        profiles.delete(first.id)
        try {
            updater.update(first.id, ShortUrlEdit(original))
            throw AssertionError("Expected missing profile")
        } catch (_: ServerNotFoundException) {
            assertEquals(2, calls.size)
        }
    }
}

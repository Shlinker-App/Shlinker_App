package at.awit.shlinker.core.data

import at.awit.shlinker.core.datastore.StoredSecrets
import at.awit.shlinker.core.datastore.StoredServers
import at.awit.shlinker.core.network.DeleteShortUrlService
import at.awit.shlinker.core.network.ServerTagsService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerTagsAndDeletionTest {
    @Test fun bothOperationsUseChosenProfileFreshKeyAndNeverChangeActiveProfile() = runTest {
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
        val tags = ServerTagsRepository(
            profiles,
            object : ServerTagsService {
                override suspend fun get(serverUrl: String, apiKey: String): List<String> {
                    calls += listOf("tags", serverUrl, apiKey)
                    return listOf("work")
                }
            },
        )
        val deleter = ShortUrlDeleter(
            profiles,
            object : DeleteShortUrlService {
                override suspend fun delete(serverUrl: String, apiKey: String, shortCode: String, domain: String?) {
                    calls += listOf("delete", serverUrl, apiKey, shortCode, domain)
                }
            },
        )
        assertEquals(listOf("work"), tags.get(first.id))
        deleter.delete(first.id, "same", "custom.example")
        profiles.update(first.id, apiKey = "replacement")
        tags.get(first.id)
        deleter.delete(first.id, "same", null)
        assertEquals(
            listOf(
                listOf("tags", first.url, "first-key"),
                listOf("delete", first.url, "first-key", "same", "custom.example"),
                listOf("tags", first.url, "replacement"),
                listOf("delete", first.url, "replacement", "same", null),
            ),
            calls,
        )
        assertEquals(active, profiles.activeProfile.first())
        cipher.broken = true
        try {
            tags.get(first.id)
            throw AssertionError("Expected unavailable key")
        } catch (_: ShortUrlApiKeyException) {
            assertEquals(4, calls.size)
        }
        try {
            deleter.delete(first.id, "same", null)
            throw AssertionError("Expected unavailable key")
        } catch (_: ShortUrlApiKeyException) {
            assertEquals(4, calls.size)
        }
        profiles.delete(first.id)
        try {
            tags.get(first.id)
            throw AssertionError("Expected missing profile")
        } catch (_: ServerNotFoundException) {
            assertEquals(4, calls.size)
        }
        try {
            deleter.delete(first.id, "same", null)
            throw AssertionError("Expected missing profile")
        } catch (_: ServerNotFoundException) {
            assertEquals(4, calls.size)
        }
    }
}

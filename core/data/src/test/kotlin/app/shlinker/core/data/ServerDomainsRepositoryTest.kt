package app.shlinker.core.data

import app.shlinker.core.datastore.StoredSecrets
import app.shlinker.core.datastore.StoredServers
import app.shlinker.core.model.ServerDomain
import app.shlinker.core.network.ServerDomainsService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerDomainsRepositoryTest {
    @Test fun selectedProfileFreshKeyAndMissingCredentialsAreHandledWithoutChangingActiveProfile() = runTest {
        val secrets = InMemoryDataStore(StoredSecrets())
        val profiles = DefaultServerProfileRepository(
            InMemoryDataStore(StoredServers()),
            secrets,
            FakeCipher(),
            StandardTestDispatcher(testScheduler),
        )
        val selected = profiles.add("Selected", "api.example", "old")
        val active = profiles.add("Active", "other.example", "other")
        profiles.update(selected.id, apiKey = "replacement")
        var calls = 0
        val repo = ServerDomainsRepository(
            profiles,
            object : ServerDomainsService {
                override suspend fun get(serverUrl: String, apiKey: String): List<ServerDomain> {
                    assertEquals("https://api.example", serverUrl)
                    assertEquals("replacement", apiKey)
                    calls++
                    return listOf(ServerDomain("custom.example", false))
                }
            },
        )
        assertEquals(listOf(ServerDomain("custom.example", false)), repo.get(selected.id))
        assertEquals(active, profiles.activeProfile.first())
        secrets.updateData { StoredSecrets() }
        try {
            repo.get(selected.id)
            throw AssertionError("Expected key failure")
        } catch (_: ShortUrlApiKeyException) {
            assertEquals(1, calls)
        }
        profiles.delete(selected.id)
        try {
            repo.get(selected.id)
            throw AssertionError("Expected missing profile")
        } catch (_: ServerNotFoundException) {
            assertEquals(1, calls)
        }
    }
}

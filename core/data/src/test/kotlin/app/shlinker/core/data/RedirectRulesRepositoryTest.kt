package app.shlinker.core.data

import app.shlinker.core.datastore.StoredSecrets
import app.shlinker.core.datastore.StoredServers
import app.shlinker.core.model.RedirectRule
import app.shlinker.core.model.RedirectRules
import app.shlinker.core.network.RedirectRulesService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedirectRulesRepositoryTest {
    @Test fun operationsUseChosenProfileFreshKeyAndDomainAndLeaveActiveProfileUnchanged() = runTest {
        val profiles = DefaultServerProfileRepository(
            InMemoryDataStore(StoredServers()),
            InMemoryDataStore(StoredSecrets()),
            FakeCipher(),
            StandardTestDispatcher(testScheduler),
        )
        val chosen = profiles.add("First", "first.example", "old")
        val active = profiles.add("Active", "active.example", "other")
        profiles.update(chosen.id, apiKey = "replacement")
        val original = RedirectRules("https://default.example", emptyList())
        val desired = listOf(RedirectRule("https://new.example", emptyList()))
        var reads = 0
        var writes = 0
        val service = object : RedirectRulesService {
            override suspend fun get(
                serverUrl: String,
                apiKey: String,
                shortCode: String,
                domain: String?,
            ): RedirectRules {
                assertEquals("https://first.example", serverUrl)
                assertEquals("replacement", apiKey)
                assertEquals("same", shortCode)
                assertEquals("custom.example", domain)
                reads++
                return original
            }
            override suspend fun set(
                serverUrl: String,
                apiKey: String,
                shortCode: String,
                domain: String?,
                rules: List<RedirectRule>,
            ): RedirectRules {
                assertEquals("https://first.example", serverUrl)
                assertEquals("replacement", apiKey)
                assertEquals("same", shortCode)
                assertEquals("custom.example", domain)
                assertEquals(desired, rules)
                writes++
                return original.copy(rules = rules)
            }
        }
        val repo = RedirectRulesRepository(profiles, service)
        assertEquals(original, repo.get(chosen.id, "same", "custom.example"))
        assertEquals(desired, repo.set(chosen.id, "same", "custom.example", original, desired).rules)
        assertEquals(2, reads)
        assertEquals(1, writes)
        assertEquals(active, profiles.activeProfile.first())
        try {
            repo.set(
                chosen.id,
                "same",
                "custom.example",
                original.copy(defaultLongUrl = "https://changed.example"),
                desired,
            )
            throw AssertionError("Expected conflict")
        } catch (_: RedirectRulesConflictException) {
            assertEquals(1, writes)
        }
        profiles.delete(chosen.id)
        try {
            repo.get(chosen.id, "same", null)
            throw AssertionError("Expected missing profile")
        } catch (_: ServerNotFoundException) {
            assertEquals(3, reads)
        }
    }

    @Test fun missingKeyDoesNotCallService() = runTest {
        val servers = InMemoryDataStore(StoredServers())
        val secrets = InMemoryDataStore(StoredSecrets())
        val profiles =
            DefaultServerProfileRepository(servers, secrets, FakeCipher(), StandardTestDispatcher(testScheduler))
        val profile = profiles.add("First", "first.example", "key")
        secrets.updateData { StoredSecrets() }
        var called = false
        val service = object : RedirectRulesService {
            override suspend fun get(
                serverUrl: String,
                apiKey: String,
                shortCode: String,
                domain: String?,
            ): RedirectRules {
                called = true
                error("Must not call")
            }
            override suspend fun set(
                serverUrl: String,
                apiKey: String,
                shortCode: String,
                domain: String?,
                rules: List<RedirectRule>,
            ): RedirectRules = error("Must not call")
        }
        try {
            RedirectRulesRepository(profiles, service).get(profile.id, "same", null)
            throw AssertionError("Expected missing key")
        } catch (_: ShortUrlApiKeyException) {
            assertTrue(!called)
        }
    }
}

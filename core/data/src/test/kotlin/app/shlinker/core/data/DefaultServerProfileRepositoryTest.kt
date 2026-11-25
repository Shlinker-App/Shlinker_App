package app.shlinker.core.data

import app.shlinker.core.datastore.StoredSecrets
import app.shlinker.core.datastore.StoredServer
import app.shlinker.core.datastore.StoredServers
import app.shlinker.core.model.ServerSort
import app.shlinker.core.model.ServerUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultServerProfileRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val servers = InMemoryDataStore(StoredServers())
    private val secrets = InMemoryDataStore(StoredSecrets())
    private val cipher = FakeCipher()
    private val repo = DefaultServerProfileRepository(servers, secrets, cipher, dispatcher)

    @Test
    fun existingProfilesWithoutManualOrderKeepCreationOrder() = scope.runTest {
        servers.updateData {
            StoredServers(
                servers = listOf(
                    StoredServer("new", "New", "https://new.example.org", 200),
                    StoredServer("old", "Old", "https://old.example.org", 100),
                ),
                activeServerId = "new",
            )
        }
        assertEquals(ServerSort.MANUAL, repo.sort.first())
        assertEquals(listOf("old", "new"), repo.profiles.first().map { it.id })
        assertEquals("new", repo.activeProfile.first()?.id)
        val third = repo.add("Third", "third.example.org", "key", makeActive = false)
        assertEquals(listOf("old", "new", third.id), repo.profiles.first().map { it.id })
    }

    @Test
    fun manualOrderSurvivesRepositoryRecreationAndSortSwitching() = scope.runTest {
        val a = repo.add("Alpha", "a.example.org", "key")
        val b = repo.add("Beta", "b.example.org", "key")
        val c = repo.add("Charlie", "c.example.org", "key")
        repo.reorder(listOf(c.id, a.id, b.id))
        repo.setSort(ServerSort.NAME_ASC)
        assertEquals(listOf(a, b, c), repo.profiles.first())
        val reopened = DefaultServerProfileRepository(servers, secrets, cipher, dispatcher)
        assertEquals(ServerSort.NAME_ASC, reopened.sort.first())
        reopened.setSort(ServerSort.MANUAL)
        assertEquals(listOf(c, a, b), reopened.profiles.first())
        assertEquals(c, reopened.activeProfile.first())
        assertEquals(ApiKeyResult.Available("key"), reopened.getApiKey(a.id))
    }

    @Test
    fun manualOrderAppendsNewProfilesEvenWhileAutomaticallySortedAndRemovesDeletedIds() = scope.runTest {
        val a = repo.add("Alpha", "a.example.org", "key")
        val b = repo.add("Beta", "b.example.org", "key")
        repo.reorder(listOf(b.id, a.id))
        repo.setSort(ServerSort.NAME_DESC)
        val c = repo.add("Charlie", "c.example.org", "key", makeActive = false)
        repo.delete(a.id)
        repo.setSort(ServerSort.MANUAL)
        assertEquals(listOf(b, c), repo.profiles.first())
        assertEquals(listOf(b.id, c.id), servers.current.manualOrder)
        assertEquals(b, repo.activeProfile.first())
    }

    @Test
    fun nameSortIsCaseInsensitiveAndRenameUpdatesAutomaticOrder() = scope.runTest {
        val z = repo.add("Zulu", "z.example.org", "key")
        val a = repo.add("alpha", "a.example.org", "key")
        repo.setSort(ServerSort.NAME_ASC)
        assertEquals(listOf(a, z), repo.profiles.first())
        repo.setSort(ServerSort.NAME_DESC)
        assertEquals(listOf(z, a), repo.profiles.first())
        val renamed = repo.update(z.id, name = "Aardvark")
        assertEquals(listOf(a, renamed), repo.profiles.first())
        assertEquals(a, repo.activeProfile.first())
    }

    @Test
    fun hostnameSortIgnoresPathAndPortAndUsesNameForEqualHosts() = scope.runTest {
        val z = repo.add("Alpha", "z.example.org", "key")
        val b = repo.add("Beta", "a.example.org:8443/aaa", "key")
        val a = repo.add("alpha", "a.example.org/zzz", "key")
        repo.setSort(ServerSort.HOSTNAME_ASC)
        assertEquals(listOf(a, b, z), repo.profiles.first())
        repo.setSort(ServerSort.HOSTNAME_DESC)
        assertEquals(listOf(z, b, a), repo.profiles.first())
        assertEquals(a, repo.activeProfile.first())
    }

    @Test
    fun invalidOrOutdatedReorderIsRejectedWithoutChangingStoredOrder() = scope.runTest {
        val a = repo.add("Alpha", "a.example.org", "key")
        val b = repo.add("Beta", "b.example.org", "key")
        for (ids in listOf(listOf(a.id), listOf(a.id, a.id), listOf(a.id, "missing"))) {
            try {
                repo.reorder(ids)
                throw AssertionError("Invalid order accepted")
            } catch (_: ServerOrderChangedException) {
                assertEquals(listOf(a, b), repo.profiles.first())
            }
        }
        repo.setSort(ServerSort.NAME_ASC)
        try {
            repo.reorder(listOf(b.id, a.id))
            throw AssertionError("Reorder accepted outside manual mode")
        } catch (_: ServerOrderChangedException) {
            assertEquals(listOf(a.id, b.id), servers.current.manualOrder)
        }
    }

    @Test
    fun `add normalizes the URL, defaults to the hostname and activates the profile`() = scope.runTest {
        val p = repo.add(name = " ", url = "S.Example.org/rest/v3/", apiKey = " key-123 ")

        assertEquals("https://s.example.org", p.url)
        assertEquals("s.example.org", p.name)
        assertEquals(p, repo.activeProfile.first())
        assertEquals(ApiKeyResult.Available("key-123"), repo.getApiKey(p.id))
    }

    @Test
    fun `API key is encrypted and stored separately from the profile`() = scope.runTest {
        val p = repo.add("Home", "https://s.example.org", "secret")

        val raw = secrets.current.apiKeys.getValue(p.id)
        assertFalse(raw.contains("secret"))
        assertFalse(servers.current.toString().contains("secret"))
    }

    @Test
    fun `add keeps the active profile when makeActive is false`() = scope.runTest {
        val a = repo.add("A", "a.example.org", "k1")
        repo.add("B", "b.example.org", "k2", makeActive = false)

        assertEquals(a, repo.activeProfile.first())
        assertEquals(listOf("A", "B"), repo.profiles.first().map { it.name })
    }

    @Test
    fun `delete removes the API key and activates the next profile`() = scope.runTest {
        val a = repo.add("A", "a.example.org", "k1")
        val b = repo.add("B", "b.example.org", "k2", makeActive = false)

        repo.delete(a.id)

        assertEquals(b, repo.activeProfile.first())
        assertEquals(ApiKeyResult.Missing, repo.getApiKey(a.id))
        assertFalse(secrets.current.apiKeys.containsKey(a.id))
    }

    @Test
    fun `deleting the last profile clears the active profile`() = scope.runTest {
        val a = repo.add("A", "a.example.org", "k1")
        repo.delete(a.id)
        assertNull(repo.activeProfile.first())
        assertTrue(repo.profiles.first().isEmpty())
    }

    @Test
    fun `update changes the name, URL and API key`() = scope.runTest {
        val a = repo.add("A", "a.example.org", "k1")
        val u = repo.update(a.id, name = "New", url = "https://n.example.org/", apiKey = "k2")

        assertEquals("New", u.name)
        assertEquals("https://n.example.org", u.url)
        assertEquals(ApiKeyResult.Available("k2"), repo.getApiKey(a.id))
    }

    @Test
    fun `getApiKey returns Unavailable when the key cannot be decrypted`() = scope.runTest {
        val a = repo.add("A", "a.example.org", "k1")
        cipher.broken = true
        assertEquals(ApiKeyResult.Unavailable, repo.getApiKey(a.id))
    }

    @Test
    fun `getApiKey rejects swapped ciphertexts`() = scope.runTest {
        val a = repo.add("A", "a.example.org", "k1")
        val b = repo.add("B", "b.example.org", "k2")
        secrets.updateData {
            it.copy(
                apiKeys = mapOf(
                    a.id to it.apiKeys.getValue(b.id),
                    b.id to it.apiKeys.getValue(a.id),
                ),
            )
        }
        assertEquals(ApiKeyResult.Unavailable, repo.getApiKey(a.id))
    }

    @Test
    fun `add rejects HTTP URLs without writing anything`() = scope.runTest {
        val e = runCatching { repo.add("A", "http://a.example.org", "k1") }.exceptionOrNull()
        assertEquals(ServerUrl.Reason.INSECURE_HTTP, (e as InvalidServerUrlException).reason)
        assertTrue(servers.current.servers.isEmpty())
        assertTrue(secrets.current.apiKeys.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `add rejects a blank API key`() = scope.runTest {
        repo.add("A", "a.example.org", "   ")
    }

    @Test
    fun `toString does not expose the API key`() {
        assertFalse(ApiKeyResult.Available("secret").toString().contains("secret"))
    }

    @Test
    fun `add rejects duplicate credentials before writing regardless of name`() = scope.runTest {
        val original = repo.add("Home", "s.example.org", "key")
        val beforeServers = servers.current
        val beforeSecrets = secrets.current
        for (name in listOf("Home", "Other")) {
            val error = runCatching { repo.add(name, "S.Example.org/rest/v3/", " key ") }.exceptionOrNull()
            assertTrue(error is DuplicateServerException)
            assertEquals(beforeServers, servers.current)
            assertEquals(beforeSecrets, secrets.current)
            assertEquals(original, repo.activeProfile.first())
        }
    }

    @Test
    fun `add allows different API keys or base URLs`() = scope.runTest {
        repo.add("Same", "s.example.org", "key-1")
        repo.add("Other", "s.example.org", "key-2")
        repo.add("Same", "s.example.org/subpath", "key-1")
        repo.add("Same", "other.example.org", "key-1")
        assertEquals(4, repo.profiles.first().size)
    }

    @Test
    fun `add rejects duplicate names with a different key without writing`() = scope.runTest {
        repo.add("Home", "s.example.org", "key-1")
        val beforeServers = servers.current
        val beforeSecrets = secrets.current
        for (name in listOf("Home", " home ", "HOME")) {
            assertTrue(
                runCatching { repo.add(name, "S.Example.org/rest/v3/", "key-2") }
                    .exceptionOrNull() is ServerNameConflictException,
            )
        }
        assertEquals(beforeServers, servers.current)
        assertEquals(beforeSecrets, secrets.current)
    }

    @Test
    fun `add checks for name conflicts when defaulting to the hostname`() = scope.runTest {
        repo.add("", "s.example.org", "key-1")
        assertTrue(
            runCatching { repo.add(" ", "s.example.org", "key-2") }
                .exceptionOrNull() is ServerNameConflictException,
        )
        assertEquals("Work", repo.add("Work", "s.example.org", "key-2").name)
    }

    @Test
    fun `update checks for name conflicts when the name or address changes`() = scope.runTest {
        repo.add("Home", "s.example.org", "key-1")
        val second = repo.add("Work", "s.example.org", "key-2")
        val third = repo.add("Home", "other.example.org", "key-3")
        val beforeServers = servers.current
        val beforeSecrets = secrets.current
        assertTrue(
            runCatching { repo.update(second.id, name = " home ") }
                .exceptionOrNull() is ServerNameConflictException,
        )
        assertTrue(
            runCatching { repo.update(third.id, url = "s.example.org", apiKey = "key-4") }
                .exceptionOrNull() is ServerNameConflictException,
        )
        assertEquals(beforeServers, servers.current)
        assertEquals(beforeSecrets, secrets.current)
        assertEquals(
            "Guest",
            repo.update(third.id, name = "Guest", url = "s.example.org", apiKey = "key-4").name,
        )
    }

    @Test
    fun `concurrent adds with different API keys keep profile names unique`() = scope.runTest {
        val results = listOf("key-1", "key-2").map { key ->
            async { runCatching { repo.add("Home", "s.example.org", key) } }
        }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is ServerNameConflictException)
        assertEquals(1, secrets.current.apiKeys.size)
    }

    @Test
    fun `concurrent adds cannot bypass duplicate checks`() = scope.runTest {
        val results = List(2) {
            async { runCatching { repo.add("Home", "s.example.org", "key") } }
        }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is DuplicateServerException)
        assertEquals(1, servers.current.servers.size)
        assertEquals(1, secrets.current.apiKeys.size)
    }

    @Test
    fun `update rejects duplicate credentials from key or URL changes without writing`() = scope.runTest {
        repo.add("A", "s.example.org", "key-1")
        val second = repo.add("B", "s.example.org", "key-2")
        val third = repo.add("C", "other.example.org", "key-1")
        val beforeServers = servers.current
        val beforeSecrets = secrets.current
        assertTrue(
            runCatching {
                repo.update(second.id, apiKey = "key-1")
            }.exceptionOrNull() is DuplicateServerException,
        )
        assertTrue(
            runCatching { repo.update(third.id, url = "S.Example.org/") }.exceptionOrNull() is DuplicateServerException,
        )
        assertEquals(beforeServers, servers.current)
        assertEquals(beforeSecrets, secrets.current)
    }

    @Test
    fun `update excludes the current profile from duplicate checks and allows renaming`() = scope.runTest {
        val original = repo.add("A", "s.example.org", "key")
        val changed = repo.update(original.id, name = "New", url = "S.Example.org/", apiKey = " key ")
        assertEquals("New", changed.name)
        assertEquals(1, servers.current.servers.size)
        cipher.broken = true
        assertEquals("Renamed", repo.update(original.id, name = "Renamed").name)
    }

    @Test
    fun `add fails when a missing or unreadable key prevents duplicate checks`() = scope.runTest {
        repo.add("A", "s.example.org", "key")
        val beforeServers = servers.current
        cipher.broken = true
        assertTrue(
            runCatching { repo.add("B", "s.example.org", "key") }
                .exceptionOrNull() is DuplicateCheckUnavailableException,
        )
        cipher.broken = false
        secrets.updateData { StoredSecrets() }
        assertTrue(
            runCatching { repo.add("B", "s.example.org", "key") }
                .exceptionOrNull() is DuplicateCheckUnavailableException,
        )
        assertEquals(beforeServers, servers.current)
        assertTrue(secrets.current.apiKeys.isEmpty())
    }
}

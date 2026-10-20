package at.awit.linkwerk.core.data

import at.awit.linkwerk.core.datastore.StoredSecrets
import at.awit.linkwerk.core.datastore.StoredServers
import at.awit.linkwerk.core.model.ServerUrl
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
    fun `add normalisiert URL, setzt Hostname als Name und aktiviert`() = scope.runTest {
        val p = repo.add(name = " ", url = "S.Example.org/rest/v3/", apiKey = " key-123 ")

        assertEquals("https://s.example.org", p.url)
        assertEquals("s.example.org", p.name)
        assertEquals(p, repo.activeProfile.first())
        assertEquals(ApiKeyResult.Available("key-123"), repo.getApiKey(p.id))
    }

    @Test
    fun `API-Key liegt nur verschluesselt und getrennt vom Profil`() = scope.runTest {
        val p = repo.add("Home", "https://s.example.org", "geheim")

        val raw = secrets.current.apiKeys.getValue(p.id)
        assertFalse(raw.contains("geheim"))
        assertFalse(servers.current.toString().contains("geheim"))
    }

    @Test
    fun `zweiter Server ohne makeActive bleibt inaktiv`() = scope.runTest {
        val a = repo.add("A", "a.example.org", "k1")
        repo.add("B", "b.example.org", "k2", makeActive = false)

        assertEquals(a, repo.activeProfile.first())
        assertEquals(listOf("A", "B"), repo.profiles.first().map { it.name })
    }

    @Test
    fun `delete entfernt Key und aktiviert naechsten Server`() = scope.runTest {
        val a = repo.add("A", "a.example.org", "k1")
        val b = repo.add("B", "b.example.org", "k2", makeActive = false)

        repo.delete(a.id)

        assertEquals(b, repo.activeProfile.first())
        assertEquals(ApiKeyResult.Missing, repo.getApiKey(a.id))
        assertFalse(secrets.current.apiKeys.containsKey(a.id))
    }

    @Test
    fun `letzten Server loeschen ergibt keinen aktiven`() = scope.runTest {
        val a = repo.add("A", "a.example.org", "k1")
        repo.delete(a.id)
        assertNull(repo.activeProfile.first())
        assertTrue(repo.profiles.first().isEmpty())
    }

    @Test
    fun `update aendert Name, URL und Key`() = scope.runTest {
        val a = repo.add("A", "a.example.org", "k1")
        val u = repo.update(a.id, name = "Neu", url = "https://n.example.org/", apiKey = "k2")

        assertEquals("Neu", u.name)
        assertEquals("https://n.example.org", u.url)
        assertEquals(ApiKeyResult.Available("k2"), repo.getApiKey(a.id))
    }

    @Test
    fun `nicht entschluesselbarer Key ergibt Unavailable`() = scope.runTest {
        val a = repo.add("A", "a.example.org", "k1")
        cipher.broken = true
        assertEquals(ApiKeyResult.Unavailable, repo.getApiKey(a.id))
    }

    @Test
    fun `vertauschte Ciphertexte werden erkannt`() = scope.runTest {
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
    fun `http URL wird abgelehnt und nichts gespeichert`() = scope.runTest {
        val e = runCatching { repo.add("A", "http://a.example.org", "k1") }.exceptionOrNull()
        assertEquals(ServerUrl.Reason.INSECURE_HTTP, (e as InvalidServerUrlException).reason)
        assertTrue(servers.current.servers.isEmpty())
        assertTrue(secrets.current.apiKeys.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `leerer API-Key wird abgelehnt`() = scope.runTest {
        repo.add("A", "a.example.org", "   ")
    }

    @Test
    fun `toString verraet den Key nicht`() {
        assertFalse(ApiKeyResult.Available("geheim").toString().contains("geheim"))
    }
}

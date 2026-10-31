package at.awit.shlinker.core.data

import androidx.datastore.core.DataStore
import at.awit.shlinker.core.datastore.StoredSecrets
import at.awit.shlinker.core.datastore.StoredServers
import at.awit.shlinker.core.model.AppTagSettings
import at.awit.shlinker.core.model.CreateShortUrl
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.network.CreateShortUrlService
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortUrlCreatorTest {
    @Test fun sharedTagDefaultsCustomNameDuplicatesAndOptOutApplyOnlyToNewRequests() = runTest {
        val store = InMemoryDataStore(StoredServers())
        val profiles = DefaultServerProfileRepository(
            store,
            InMemoryDataStore(StoredSecrets()),
            FakeCipher(),
            StandardTestDispatcher(testScheduler),
        )
        val first = profiles.add("First", "first.example", "first-key")
        val second = profiles.add("Second", "second.example", "second-key")
        val active = profiles.activeProfile.first()
        val settings = AppTagSettingsRepository(store)
        val sent = mutableListOf<CreateShortUrl>()
        val hosts = mutableListOf<Pair<String, String>>()
        val creator = ShortUrlCreator(
            profiles,
            object : CreateShortUrlService {
                override suspend fun create(serverUrl: String, apiKey: String, input: CreateShortUrl): ShortUrl {
                    sent += input
                    hosts += serverUrl to apiKey
                    return ShortUrl("abc", "$serverUrl/abc", input.longUrl, input.title, input.tags)
                }
            },
            settings,
        )
        val input = CreateShortUrl("https://example.org", tags = listOf("work", "shlinker"))
        creator.create(first.id, input)
        assertEquals(listOf("work", "shlinker"), sent.last().tags)
        settings.save(AppTagSettings(name = " my app "))
        val created = creator.create(second.id, input)
        assertEquals(listOf("work", "shlinker", "my app"), created.tags)
        settings.save(AppTagSettings(false, "my app"))
        creator.create(first.id, input)
        assertEquals(listOf("work", "shlinker"), sent.last().tags)
        creator.create(first.id, CreateShortUrl("https://example.org"))
        assertTrue(sent.last().tags.isEmpty())
        assertEquals(listOf("work", "shlinker", "my app"), created.tags)
        assertEquals(listOf("work", "shlinker"), input.tags)
        assertEquals(
            listOf(
                first.url to "first-key",
                second.url to "second-key",
                first.url to "first-key",
                first.url to "first-key",
            ),
            hosts,
        )
        assertEquals(active, profiles.activeProfile.first())
    }

    @Test fun unreadableSettingsNeverSendPostOrSilentlyOmitAppTag() = runTest {
        val store = InMemoryDataStore(StoredServers())
        val profiles = DefaultServerProfileRepository(
            store,
            InMemoryDataStore(StoredSecrets()),
            FakeCipher(),
            StandardTestDispatcher(testScheduler),
        )
        val server = profiles.add("Home", "s.example", "key")
        val brokenStore = object : DataStore<StoredServers> {
            override val data: Flow<StoredServers> = flow { throw IOException("disk") }
            override suspend fun updateData(transform: suspend (StoredServers) -> StoredServers): StoredServers =
                error("unused")
        }
        var calls = 0
        val creator = ShortUrlCreator(
            profiles,
            object : CreateShortUrlService {
                override suspend fun create(serverUrl: String, apiKey: String, input: CreateShortUrl): ShortUrl {
                    calls++
                    error("No POST expected")
                }
            },
            AppTagSettingsRepository(brokenStore),
        )
        try {
            creator.create(server.id, CreateShortUrl("https://example.org"))
            throw AssertionError("Expected storage error")
        } catch (_: IOException) {
            assertEquals(0, calls)
        }
    }
}

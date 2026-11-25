package app.shlinker.core.datastore

import androidx.datastore.core.CorruptionException
import app.shlinker.core.model.ServerSort
import app.shlinker.core.model.ShortUrlSort
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonSerializerTest {

    private val serializer = JsonSerializer(StoredServers.serializer(), StoredServers())

    @Test
    fun roundTrip() = runTest {
        val data = StoredServers(
            servers = listOf(StoredServer("id1", "Home", "https://s.example.org", 42L)),
            activeServerId = "id1",
            sort = ServerSort.HOSTNAME_DESC,
            manualOrder = listOf("id1"),
            shortUrlSort = ShortUrlSort.MOST_VISITED,
            appTagEnabled = false,
            appTagName = "my app",
            forwardQuery = true,
            crawlable = false,
        )
        val out = ByteArrayOutputStream()
        serializer.writeTo(data, out)
        assertEquals(data, serializer.readFrom(ByteArrayInputStream(out.toByteArray())))
    }

    @Test
    fun emptyFileGivesDefault() = runTest {
        assertEquals(StoredServers(), serializer.readFrom(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun profilesFromOlderVersionsKeepTheirDataWithManualSortDefaults() = runTest {
        val json = """
            {"version":1,
             "servers":[{"id":"id1","name":"Home","url":"https://s.example.org","createdAt":42}],
             "activeServerId":"id1"}
        """.trimIndent()
        val stored = serializer.readFrom(ByteArrayInputStream(json.encodeToByteArray()))
        assertEquals(true, stored.appTagEnabled)
        assertEquals("shlinker", stored.appTagName)
        assertEquals(false, stored.forwardQuery)
        assertEquals(null, stored.crawlable)
        assertEquals(
            StoredServers(
                servers = listOf(StoredServer("id1", "Home", "https://s.example.org", 42)),
                activeServerId = "id1",
            ),
            stored,
        )
    }

    @Test
    fun unknownFieldsAreIgnored() = runTest {
        val json = """{"version":1,"servers":[],"activeServerId":null,"future":true}"""
        assertEquals(StoredServers(), serializer.readFrom(ByteArrayInputStream(json.encodeToByteArray())))
    }

    @Test(expected = CorruptionException::class)
    fun garbageThrowsCorruption() = runTest {
        serializer.readFrom(ByteArrayInputStream("broken{".encodeToByteArray()))
    }
}

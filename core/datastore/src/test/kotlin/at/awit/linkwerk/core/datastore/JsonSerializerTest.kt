package at.awit.linkwerk.core.datastore

import androidx.datastore.core.CorruptionException
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
    fun unknownFieldsAreIgnored() = runTest {
        val json = """{"version":1,"servers":[],"activeServerId":null,"future":true}"""
        assertEquals(StoredServers(), serializer.readFrom(ByteArrayInputStream(json.encodeToByteArray())))
    }

    @Test(expected = CorruptionException::class)
    fun garbageThrowsCorruption() = runTest {
        serializer.readFrom(ByteArrayInputStream("kaputt{".encodeToByteArray()))
    }
}

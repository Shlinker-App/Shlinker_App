package app.shlinker.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Generischer DataStore-Serializer auf Basis von kotlinx.serialization (JSON). */
internal class JsonSerializer<T>(private val serializer: KSerializer<T>, override val defaultValue: T) :
    Serializer<T> {

    override suspend fun readFrom(input: InputStream): T = try {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) defaultValue else json.decodeFromString(serializer, bytes.decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Ungültige Daten im DataStore", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Ungültige Daten im DataStore", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(json.encodeToString(serializer, t).encodeToByteArray())
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

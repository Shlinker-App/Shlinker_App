package at.awit.shlinker.core.data

import at.awit.shlinker.core.model.ServerProfile
import at.awit.shlinker.core.model.ServerUrl
import kotlinx.coroutines.flow.Flow

/** Verwaltung der Shlink-Server inkl. verschlüsselter API-Keys. */
interface ServerProfileRepository {

    /** Alle Server, sortiert nach Anlagezeitpunkt. */
    val profiles: Flow<List<ServerProfile>>

    /** Aktuell gewählter Server (oder `null`, wenn noch keiner angelegt ist). */
    val activeProfile: Flow<ServerProfile?>

    /**
     * Legt einen Server an. Die URL wird über [ServerUrl.normalize] vereinheitlicht.
     * Ist [name] leer, wird der Hostname verwendet.
     *
     * @throws InvalidServerUrlException bei ungültiger URL
     * @throws IllegalArgumentException bei leerem API-Key
     */
    suspend fun add(name: String, url: String, apiKey: String, makeActive: Boolean = true): ServerProfile

    /** Ändert Name, URL und/oder API-Key. `null` = unverändert. */
    suspend fun update(id: String, name: String? = null, url: String? = null, apiKey: String? = null): ServerProfile

    /** Löscht Server und zugehörigen API-Key. Ist er aktiv, wird der nächste Server aktiv. */
    suspend fun delete(id: String)

    suspend fun setActive(id: String)

    /** Entschlüsselt den API-Key – nur unmittelbar für Requests verwenden, nicht cachen/loggen. */
    suspend fun getApiKey(id: String): ApiKeyResult
}

sealed interface ApiKeyResult {
    data class Available(val apiKey: String) : ApiKeyResult {
        override fun toString() = "Available(apiKey=***)"
    }

    /** Für diesen Server ist kein Key gespeichert. */
    data object Missing : ApiKeyResult

    /** Key vorhanden, aber nicht mehr entschlüsselbar (z. B. nach Wiederherstellung) → neu eingeben. */
    data object Unavailable : ApiKeyResult
}

class InvalidServerUrlException(val reason: ServerUrl.Reason) :
    IllegalArgumentException("Ungültige Server-URL: $reason")

class ServerNotFoundException(id: String) : NoSuchElementException("Server $id nicht gefunden")

package app.shlinker.core.data

import app.shlinker.core.model.ServerProfile
import app.shlinker.core.model.ServerSort
import app.shlinker.core.model.ServerUrl
import kotlinx.coroutines.flow.Flow

/** Verwaltung der Shlink-Server inkl. verschlüsselter API-Keys. */
interface ServerProfileRepository {

    /** Alle Server in der gespeicherten Anzeige-Reihenfolge. */
    val profiles: Flow<List<ServerProfile>>

    val sort: Flow<ServerSort>

    suspend fun setSort(sort: ServerSort)

    /** Speichert eine vollständige manuelle Reihenfolge; aktiviert keine anderen Profile. */
    suspend fun reorder(ids: List<String>)

    /** Aktuell gewählter Server (oder `null`, wenn noch keiner angelegt ist). */
    val activeProfile: Flow<ServerProfile?>

    /**
     * Legt einen Server an. Die URL wird über [ServerUrl.normalize] vereinheitlicht.
     * Ist [name] leer, wird der Hostname verwendet.
     *
     * @throws InvalidServerUrlException bei ungültiger URL
     * @throws IllegalArgumentException bei leerem API-Key
     * @throws DuplicateServerException bei gleicher normalisierter URL und gleichem API-Key
     * @throws ServerNameConflictException bei gleicher normalisierter URL und gleichem Namen (ohne Beachtung der Groß-/Kleinschreibung)
     * @throws DuplicateCheckUnavailableException wenn ein passender gespeicherter Key nicht lesbar ist
     */
    suspend fun add(name: String, url: String, apiKey: String, makeActive: Boolean = true): ServerProfile

    /**
     * Ändert Name, URL und/oder API-Key. `null` = unverändert.
     * URL-/Key-Änderungen unterliegen derselben Dublettenprüfung wie [add].
     * Auch Namensänderungen müssen pro normalisierter Serveradresse eindeutig sein.
     */
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

class DuplicateServerException : IllegalArgumentException("Server profile already exists")

class ServerNameConflictException : IllegalArgumentException("Server profile name already exists for this address")

class DuplicateCheckUnavailableException : IllegalStateException("Cannot verify existing server credentials")

class ServerOrderChangedException : IllegalStateException("Server list changed; please try again")

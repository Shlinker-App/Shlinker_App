package app.shlinker.core.data

import app.shlinker.core.model.ServerProfile
import app.shlinker.core.model.ServerSort
import app.shlinker.core.model.ServerUrl
import kotlinx.coroutines.flow.Flow

/** Manages Shlink server profiles and their encrypted API keys. */
interface ServerProfileRepository {

    /** All profiles in the saved display order. */
    val profiles: Flow<List<ServerProfile>>

    val sort: Flow<ServerSort>

    suspend fun setSort(sort: ServerSort)

    /** Saves the complete manual order without changing the active profile. */
    suspend fun reorder(ids: List<String>)

    /** The active profile, or `null` when there are no profiles. */
    val activeProfile: Flow<ServerProfile?>

    /**
     * Adds a server profile using [ServerUrl.normalize]. A blank [name] defaults to the hostname.
     *
     * @throws InvalidServerUrlException if the URL is invalid
     * @throws IllegalArgumentException if the API key is blank
     * @throws DuplicateServerException if the normalized URL and API key match an existing profile
     * @throws ServerNameConflictException if another profile has the same normalized URL and name (case-insensitive)
     * @throws DuplicateCheckUnavailableException if a matching stored key cannot be read
     */
    suspend fun add(name: String, url: String, apiKey: String, makeActive: Boolean = true): ServerProfile

    /**
     * Updates the name, URL, or API key. A `null` argument leaves the corresponding field unchanged.
     * URL and key changes use the same duplicate checks as [add].
     * Names must remain unique per normalized server address.
     */
    suspend fun update(id: String, name: String? = null, url: String? = null, apiKey: String? = null): ServerProfile

    /** Deletes the profile and its API key. If it was active, selects a replacement if available. */
    suspend fun delete(id: String)

    suspend fun setActive(id: String)

    /** Decrypts the API key for immediate use in a request. Do not cache or log the key. */
    suspend fun getApiKey(id: String): ApiKeyResult
}

sealed interface ApiKeyResult {
    data class Available(val apiKey: String) : ApiKeyResult {
        override fun toString() = "Available(apiKey=***)"
    }

    /** No API key is stored for this profile. */
    data object Missing : ApiKeyResult

    /** The stored key can no longer be decrypted, for example after a restore. Enter the API key again. */
    data object Unavailable : ApiKeyResult
}

class InvalidServerUrlException(val reason: ServerUrl.Reason) :
    IllegalArgumentException("Invalid server URL: $reason")

class ServerNotFoundException(id: String) : NoSuchElementException("Server $id not found")

class DuplicateServerException : IllegalArgumentException("Server profile already exists")

class ServerNameConflictException : IllegalArgumentException("Server profile name already exists for this address")

class DuplicateCheckUnavailableException : IllegalStateException("Cannot verify existing server credentials")

class ServerOrderChangedException : IllegalStateException("Server list changed; please try again")

package app.shlinker.core.datastore

import app.shlinker.core.model.ServerSort
import app.shlinker.core.model.ShortUrlSort
import kotlinx.serialization.Serializable

/** Versioned storage format for server profiles without their secrets. */
@Serializable
data class StoredServers(
    val version: Int = CURRENT_VERSION,
    val servers: List<StoredServer> = emptyList(),
    val activeServerId: String? = null,
    val sort: ServerSort = ServerSort.MANUAL,
    val manualOrder: List<String> = emptyList(),
    val shortUrlSort: ShortUrlSort = ShortUrlSort.NEWEST,
    val appTagEnabled: Boolean = true,
    val appTagName: String = "shlinker",
    val forwardQuery: Boolean = false,
    val crawlable: Boolean? = null,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class StoredServer(val id: String, val name: String, val url: String, val createdAt: Long)

/**
 * Encrypted API keys indexed by server ID.
 * Each value is Base64(IV || ciphertext + tag); encryption uses AES-256-GCM with a Keystore key.
 */
@Serializable
data class StoredSecrets(val version: Int = 1, val apiKeys: Map<String, String> = emptyMap())

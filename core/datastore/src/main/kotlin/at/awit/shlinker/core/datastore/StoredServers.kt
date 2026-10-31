package at.awit.shlinker.core.datastore

import at.awit.shlinker.core.model.ServerSort
import at.awit.shlinker.core.model.ShortUrlSort
import kotlinx.serialization.Serializable

/** Persistiertes Format der Server-Profile (ohne Secrets). Versioniert für spätere Migrationen. */
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
 * Verschlüsselte API-Keys, Schlüssel = Server-ID.
 * Wert = Base64(IV || Ciphertext+Tag), AES-256-GCM mit Keystore-Schlüssel.
 */
@Serializable
data class StoredSecrets(val version: Int = 1, val apiKeys: Map<String, String> = emptyMap())

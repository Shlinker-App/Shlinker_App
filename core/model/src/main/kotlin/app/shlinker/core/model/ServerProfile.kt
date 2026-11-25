package app.shlinker.core.model

/**
 * A saved Shlink server profile.
 *
 * The API key is stored separately and encrypted. It is decrypted only when needed for requests.
 */
data class ServerProfile(val id: String, val name: String, val url: String, val createdAtEpochMillis: Long)

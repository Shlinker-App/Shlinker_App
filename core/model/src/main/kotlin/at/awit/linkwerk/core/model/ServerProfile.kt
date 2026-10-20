package at.awit.linkwerk.core.model

/**
 * Ein gespeicherter Shlink-Server.
 *
 * Der API-Key ist bewusst NICHT Teil des Modells: Er liegt verschlüsselt getrennt
 * und wird nur bei Bedarf (für Requests) entschlüsselt.
 */
data class ServerProfile(val id: String, val name: String, val url: String, val createdAtEpochMillis: Long)

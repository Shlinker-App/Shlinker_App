package at.awit.shlinker.core.datastore

/** Ver-/Entschlüsselung kleiner Geheimnisse (API-Keys). Abstrahiert für Tests. */
interface SecretCipher {
    /** @param associatedData wird mit-authentifiziert (z. B. Server-ID), verhindert Vertauschen von Einträgen. */
    fun encrypt(plaintext: String, associatedData: String): String

    /** @throws SecretUnavailableException wenn der Schlüssel fehlt/ungültig ist oder Daten manipuliert wurden. */
    fun decrypt(ciphertext: String, associatedData: String): String
}

/**
 * Das Geheimnis kann nicht mehr entschlüsselt werden – z. B. nach Zurücksetzen der
 * Bildschirmsperre, Wiederherstellung auf anderem Gerät oder Datenmanipulation.
 * Die App muss den API-Key dann neu abfragen.
 */
class SecretUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

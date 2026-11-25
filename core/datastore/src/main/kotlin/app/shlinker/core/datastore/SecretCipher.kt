package app.shlinker.core.datastore

/** Encrypts and decrypts small secrets such as API keys. */
interface SecretCipher {
    /** @param associatedData authenticated context, such as a server ID, that prevents swapping entries. */
    fun encrypt(plaintext: String, associatedData: String): String

    /** @throws SecretUnavailableException if the key is missing or invalid, or the ciphertext was tampered with. */
    fun decrypt(ciphertext: String, associatedData: String): String
}

/**
 * The secret can no longer be decrypted, for example after a screen-lock reset,
 * a restore on another device, or tampering. Prompt for the API key again.
 */
class SecretUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

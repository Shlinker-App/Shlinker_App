package at.awit.linkwerk.core.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM mit einem nicht exportierbaren Schlüssel im Android Keystore
 * (wenn verfügbar in StrongBox / Secure Element, sonst TEE).
 *
 * Ersetzt das veraltete `EncryptedSharedPreferences` (androidx.security:security-crypto).
 */
@Singleton
class KeystoreSecretCipher @Inject constructor() : SecretCipher {

    override fun encrypt(plaintext: String, associatedData: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(associatedData.encodeToByteArray())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.encodeToByteArray())
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    override fun decrypt(ciphertext: String, associatedData: String): String {
        try {
            val key = loadKey() ?: throw SecretUnavailableException("Kein Schlüssel im Keystore")
            val bytes = Base64.decode(ciphertext, Base64.NO_WRAP)
            require(bytes.size > IV_LENGTH) { "Ciphertext zu kurz" }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, IV_LENGTH))
            cipher.updateAAD(associatedData.encodeToByteArray())
            return cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH).decodeToString()
        } catch (e: GeneralSecurityException) {
            throw SecretUnavailableException("API-Key kann nicht entschlüsselt werden", e)
        } catch (e: IllegalArgumentException) {
            throw SecretUnavailableException("API-Key-Daten sind beschädigt", e)
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun loadKey(): SecretKey? = (keyStore().getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    @Synchronized
    private fun getOrCreateKey(): SecretKey = loadKey() ?: try {
        generateKey(strongBox = true)
    } catch (_: StrongBoxUnavailableException) {
        generateKey(strongBox = false)
    }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "linkwerk_api_keys_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}

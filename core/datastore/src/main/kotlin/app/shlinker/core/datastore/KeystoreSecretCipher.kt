package app.shlinker.core.datastore

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
 * Uses AES-256-GCM with a non-exportable Android Keystore key.
 * Prefers StrongBox and falls back to the default Keystore implementation.
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
            val key = loadKey() ?: throw SecretUnavailableException("No key in Android Keystore")
            val bytes = Base64.decode(ciphertext, Base64.NO_WRAP)
            require(bytes.size > IV_LENGTH) { "Ciphertext is too short" }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, IV_LENGTH))
            cipher.updateAAD(associatedData.encodeToByteArray())
            return cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH).decodeToString()
        } catch (e: GeneralSecurityException) {
            throw SecretUnavailableException("Cannot decrypt API key", e)
        } catch (e: IllegalArgumentException) {
            throw SecretUnavailableException("API key data is corrupt", e)
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
        const val KEY_ALIAS = "shlinker_api_keys_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}

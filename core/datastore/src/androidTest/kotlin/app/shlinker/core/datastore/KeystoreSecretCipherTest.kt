package app.shlinker.core.datastore

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Läuft auf Gerät/Emulator gegen den echten Android Keystore (CI: API 29 + 36). */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretCipherTest {

    private val cipher = KeystoreSecretCipher()

    @Test
    fun roundTrip() {
        val encrypted = cipher.encrypt("mein-api-key", associatedData = "server-1")
        assertFalse(encrypted.contains("mein-api-key"))
        assertEquals("mein-api-key", cipher.decrypt(encrypted, associatedData = "server-1"))
    }

    @Test
    fun randomIvPerEncryption() {
        assertNotEquals(cipher.encrypt("x", "s"), cipher.encrypt("x", "s"))
    }

    @Test(expected = SecretUnavailableException::class)
    fun wrongAssociatedDataFails() {
        val encrypted = cipher.encrypt("mein-api-key", associatedData = "server-1")
        cipher.decrypt(encrypted, associatedData = "server-2")
    }

    @Test(expected = SecretUnavailableException::class)
    fun tamperedCiphertextFails() {
        val encrypted = cipher.encrypt("mein-api-key", associatedData = "server-1")
        val chars = encrypted.toCharArray()
        val i = chars.size - 5
        chars[i] = if (chars[i] == 'A') 'B' else 'A'
        cipher.decrypt(String(chars), associatedData = "server-1")
    }

    @Test(expected = SecretUnavailableException::class)
    fun garbageFails() {
        cipher.decrypt("kein base64 !!!", associatedData = "server-1")
    }
}

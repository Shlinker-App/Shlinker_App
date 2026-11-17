package app.shlinker.core.data

import androidx.datastore.core.DataStore
import app.shlinker.core.datastore.SecretCipher
import app.shlinker.core.datastore.SecretUnavailableException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InMemoryDataStore<T>(initial: T) : DataStore<T> {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()
    override val data: Flow<T> = state
    val current: T get() = state.value

    override suspend fun updateData(transform: suspend (t: T) -> T): T = mutex.withLock {
        transform(state.value).also { state.value = it }
    }
}

/** Nicht-kryptografischer Fake: prüft aber AAD wie AES-GCM. */
internal class FakeCipher : SecretCipher {
    var broken = false

    override fun encrypt(plaintext: String, associatedData: String) = "enc[$associatedData]:${plaintext.reversed()}"

    override fun decrypt(ciphertext: String, associatedData: String): String {
        val prefix = "enc[$associatedData]:"
        if (broken || !ciphertext.startsWith(prefix)) throw SecretUnavailableException("fake")
        return ciphertext.removePrefix(prefix).reversed()
    }
}

package at.awit.shlinker.core.data

import androidx.datastore.core.DataStore
import at.awit.shlinker.core.datastore.SecretCipher
import at.awit.shlinker.core.datastore.SecretUnavailableException
import at.awit.shlinker.core.datastore.StoredSecrets
import at.awit.shlinker.core.datastore.StoredServer
import at.awit.shlinker.core.datastore.StoredServers
import at.awit.shlinker.core.model.ServerProfile
import at.awit.shlinker.core.model.ServerUrl
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
internal class DefaultServerProfileRepository @Inject constructor(
    private val servers: DataStore<StoredServers>,
    private val secrets: DataStore<StoredSecrets>,
    private val cipher: SecretCipher,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ServerProfileRepository {
    private val mutationMutex = Mutex()

    override val profiles: Flow<List<ServerProfile>> = servers.data
        .map { stored -> stored.servers.sortedBy { it.createdAt }.map { it.toModel() } }
        .distinctUntilChanged()

    override val activeProfile: Flow<ServerProfile?> = servers.data
        .map { stored -> stored.servers.firstOrNull { it.id == stored.activeServerId }?.toModel() }
        .distinctUntilChanged()

    override suspend fun add(name: String, url: String, apiKey: String, makeActive: Boolean): ServerProfile =
        withContext(io) {
            mutationMutex.withLock {
                val normalizedUrl = normalizeOrThrow(url)
                val key = requireApiKey(apiKey)
                checkDuplicate(normalizedUrl, key)
                val server = StoredServer(
                    id = UUID.randomUUID().toString(),
                    name = name.trim().ifEmpty { hostOf(normalizedUrl) },
                    url = normalizedUrl,
                    createdAt = System.currentTimeMillis(),
                )
                // Zuerst den Secret schreiben: Ein Profil ohne Key soll es nie geben.
                putSecret(server.id, key)
                servers.updateData { stored ->
                    stored.copy(
                        servers = stored.servers + server,
                        activeServerId = if (makeActive ||
                            stored.activeServerId == null
                        ) {
                            server.id
                        } else {
                            stored.activeServerId
                        },
                    )
                }
                server.toModel()
            }
        }

    override suspend fun update(id: String, name: String?, url: String?, apiKey: String?): ServerProfile =
        withContext(io) {
            mutationMutex.withLock {
                val normalizedUrl = url?.let(::normalizeOrThrow)
                val key = apiKey?.let(::requireApiKey)
                val existing = servers.data.first().servers.firstOrNull { it.id == id }
                    ?: throw ServerNotFoundException(id)
                if (normalizedUrl != null || key != null) {
                    val effectiveKey = key ?: when (val result = getApiKey(id)) {
                        is ApiKeyResult.Available -> result.apiKey
                        ApiKeyResult.Missing, ApiKeyResult.Unavailable -> throw DuplicateCheckUnavailableException()
                    }
                    checkDuplicate(normalizedUrl ?: existing.url, effectiveKey, excludingId = id)
                }

                if (key != null) putSecret(id, key)

                val updated = servers.updateData { stored ->
                    stored.copy(
                        servers = stored.servers.map { s ->
                            if (s.id != id) {
                                s
                            } else {
                                val newUrl = normalizedUrl ?: s.url
                                s.copy(
                                    url = newUrl,
                                    name = name?.trim()?.ifEmpty { hostOf(newUrl) } ?: s.name,
                                )
                            }
                        },
                    )
                }
                updated.servers.first { it.id == id }.toModel()
            }
        }

    override suspend fun delete(id: String) {
        withContext(io) {
            mutationMutex.withLock {
                servers.updateData { stored ->
                    val remaining = stored.servers.filterNot { it.id == id }
                    stored.copy(
                        servers = remaining,
                        activeServerId = if (stored.activeServerId == id) {
                            remaining.minByOrNull { it.createdAt }?.id
                        } else {
                            stored.activeServerId
                        },
                    )
                }
                secrets.updateData { it.copy(apiKeys = it.apiKeys - id) }
            }
        }
    }

    override suspend fun setActive(id: String) {
        withContext(io) {
            servers.updateData { stored ->
                if (stored.servers.none { it.id == id }) throw ServerNotFoundException(id)
                stored.copy(activeServerId = id)
            }
        }
    }

    override suspend fun getApiKey(id: String): ApiKeyResult = withContext(io) {
        val encrypted = secrets.data.first().apiKeys[id] ?: return@withContext ApiKeyResult.Missing
        try {
            ApiKeyResult.Available(cipher.decrypt(encrypted, associatedData = id))
        } catch (_: SecretUnavailableException) {
            ApiKeyResult.Unavailable
        }
    }

    private suspend fun putSecret(id: String, apiKey: String) {
        val encrypted = cipher.encrypt(apiKey, associatedData = id)
        secrets.updateData { it.copy(apiKeys = it.apiKeys + (id to encrypted)) }
    }

    private suspend fun checkDuplicate(url: String, apiKey: String, excludingId: String? = null) {
        val candidates = servers.data.first().servers.filter { it.id != excludingId && it.url == url }
        for (server in candidates) {
            when (val result = getApiKey(server.id)) {
                is ApiKeyResult.Available -> if (result.apiKey == apiKey) throw DuplicateServerException()
                ApiKeyResult.Missing, ApiKeyResult.Unavailable -> throw DuplicateCheckUnavailableException()
            }
        }
    }

    private fun normalizeOrThrow(url: String): String = when (val result = ServerUrl.normalize(url)) {
        is ServerUrl.Result.Valid -> result.url
        is ServerUrl.Result.Invalid -> throw InvalidServerUrlException(result.reason)
    }

    private fun requireApiKey(apiKey: String): String = apiKey.trim().also {
        require(it.isNotEmpty()) { "API-Key darf nicht leer sein" }
    }

    private fun hostOf(url: String): String = URI(url).host ?: url

    private fun StoredServer.toModel() =
        ServerProfile(id = id, name = name, url = url, createdAtEpochMillis = createdAt)
}

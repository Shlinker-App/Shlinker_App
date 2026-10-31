package at.awit.shlinker.core.data

import at.awit.shlinker.core.network.ServerTagsService
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ServerTagsRepository @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val service: ServerTagsService,
) {
    suspend fun get(serverId: String): List<String> {
        val server = profiles.profiles.first().firstOrNull { it.id == serverId }
            ?: throw ServerNotFoundException(serverId)
        val key = when (val result = profiles.getApiKey(serverId)) {
            is ApiKeyResult.Available -> result.apiKey
            else -> throw ShortUrlApiKeyException()
        }
        return service.get(server.url, key)
    }
}

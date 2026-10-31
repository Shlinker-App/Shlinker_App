package at.awit.shlinker.core.data

import at.awit.shlinker.core.network.DeleteShortUrlService
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ShortUrlDeleter @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val service: DeleteShortUrlService,
) {
    suspend fun delete(serverId: String, shortCode: String, domain: String?) {
        val server = profiles.profiles.first().firstOrNull { it.id == serverId }
            ?: throw ServerNotFoundException(serverId)
        val key = when (val result = profiles.getApiKey(serverId)) {
            is ApiKeyResult.Available -> result.apiKey
            else -> throw ShortUrlApiKeyException()
        }
        service.delete(server.url, key, shortCode, domain)
    }
}

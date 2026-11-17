package app.shlinker.core.data

import app.shlinker.core.model.ShortUrlDetails
import app.shlinker.core.network.ShortUrlDetailsService
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ShortUrlDetailsRepository @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val service: ShortUrlDetailsService,
) {
    suspend fun get(serverId: String, shortCode: String, domain: String?): ShortUrlDetails {
        val server = profiles.profiles.first().firstOrNull { it.id == serverId }
            ?: throw ServerNotFoundException(serverId)
        val key = when (val result = profiles.getApiKey(serverId)) {
            is ApiKeyResult.Available -> result.apiKey
            else -> throw ShortUrlApiKeyException()
        }
        return service.get(server.url, key, shortCode, domain)
    }
}

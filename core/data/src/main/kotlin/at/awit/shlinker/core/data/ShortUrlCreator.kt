package at.awit.shlinker.core.data

import at.awit.shlinker.core.model.CreateShortUrl
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.network.CreateShortUrlService
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ShortUrlCreator @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val service: CreateShortUrlService,
) {
    suspend fun create(serverId: String, input: CreateShortUrl): ShortUrl {
        val server = profiles.profiles.first().firstOrNull { it.id == serverId }
            ?: throw ServerNotFoundException(serverId)
        val key = when (val result = profiles.getApiKey(serverId)) {
            is ApiKeyResult.Available -> result.apiKey
            else -> throw ShortUrlApiKeyException()
        }
        return service.create(server.url, key, input)
    }
}

package at.awit.shlinker.core.data

import at.awit.shlinker.core.model.ShortUrlDetails
import at.awit.shlinker.core.model.ShortUrlEdit
import at.awit.shlinker.core.network.UpdateShortUrlService
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ShortUrlUpdater @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val service: UpdateShortUrlService,
) {
    suspend fun update(serverId: String, input: ShortUrlEdit): ShortUrlDetails {
        val server = profiles.profiles.first().firstOrNull { it.id == serverId }
            ?: throw ServerNotFoundException(serverId)
        val key = when (val result = profiles.getApiKey(serverId)) {
            is ApiKeyResult.Available -> result.apiKey
            else -> throw ShortUrlApiKeyException()
        }
        return service.update(server.url, key, input)
    }
}

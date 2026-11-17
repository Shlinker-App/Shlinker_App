package app.shlinker.core.data

import app.shlinker.core.model.ServerDomain
import app.shlinker.core.network.ServerDomainsService
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ServerDomainsRepository @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val service: ServerDomainsService,
) {
    suspend fun get(serverId: String): List<ServerDomain> {
        val profile = profiles.profiles.first().firstOrNull { it.id == serverId }
            ?: throw ServerNotFoundException(serverId)
        val key = when (val result = profiles.getApiKey(serverId)) {
            is ApiKeyResult.Available -> result.apiKey
            else -> throw ShortUrlApiKeyException()
        }
        return service.get(profile.url, key)
    }
}

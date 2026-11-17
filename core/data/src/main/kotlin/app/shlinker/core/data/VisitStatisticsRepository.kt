package app.shlinker.core.data

import app.shlinker.core.model.VisitStatistics
import app.shlinker.core.model.VisitWindow
import app.shlinker.core.network.VisitStatisticsService
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class ServerVisitStatistics(val serverName: String, val counts: VisitStatistics)

class VisitStatisticsRepository @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val service: VisitStatisticsService,
) {
    suspend fun get(serverId: String, shortCode: String, domain: String?, window: VisitWindow): ServerVisitStatistics {
        val server = profiles.profiles.first().firstOrNull { it.id == serverId }
            ?: throw ServerNotFoundException(serverId)
        val key = when (val result = profiles.getApiKey(serverId)) {
            is ApiKeyResult.Available -> result.apiKey
            else -> throw ShortUrlApiKeyException()
        }
        return ServerVisitStatistics(server.name, service.get(server.url, key, shortCode, domain, window))
    }
}

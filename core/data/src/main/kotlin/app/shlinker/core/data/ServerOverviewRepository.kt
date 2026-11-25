package app.shlinker.core.data

import app.shlinker.core.model.ServerOverview
import app.shlinker.core.model.ServerProfile
import app.shlinker.core.network.ServerOverviewService
import app.shlinker.core.network.ServerTagsService
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class ServerOverviewRepository @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val service: ServerOverviewService,
    private val tags: ServerTagsService,
) {
    suspend fun get(server: ServerProfile): ServerOverview {
        val key = when (val result = profiles.getApiKey(server.id)) {
            is ApiKeyResult.Available -> result.apiKey
            ApiKeyResult.Missing, ApiKeyResult.Unavailable -> throw ShortUrlApiKeyException()
        }
        return coroutineScope {
            val shlinks = async { service.shlinkCount(server.url, key) }
            val clicks = async { service.humanClickCount(server.url, key) }
            val tagCount = async { tags.get(server.url, key).size }
            ServerOverview(shlinks.await(), clicks.await(), tagCount.await())
        }
    }
}

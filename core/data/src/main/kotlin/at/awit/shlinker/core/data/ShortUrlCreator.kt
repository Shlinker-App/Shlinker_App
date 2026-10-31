package at.awit.shlinker.core.data

import at.awit.shlinker.core.model.CreateShortUrl
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.network.CreateShortUrlService
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ShortUrlCreator @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val service: CreateShortUrlService,
    private val appTags: AppTagSettingsRepository,
) {
    suspend fun create(serverId: String, input: CreateShortUrl): ShortUrl {
        val server = profiles.profiles.first().firstOrNull { it.id == serverId }
            ?: throw ServerNotFoundException(serverId)
        val key = when (val result = profiles.getApiKey(serverId)) {
            is ApiKeyResult.Available -> result.apiKey
            else -> throw ShortUrlApiKeyException()
        }
        val preferences = appTags.preferences.first()
        val settings = preferences.appTag
        val tags = if (settings.enabled) (input.tags + settings.name.trim()).distinct() else input.tags
        return service.create(
            server.url,
            key,
            input.copy(
                tags = tags,
                forwardQuery = preferences.shlinkDefaults.forwardQuery,
                crawlable = preferences.shlinkDefaults.crawlable,
            ),
        )
    }
}

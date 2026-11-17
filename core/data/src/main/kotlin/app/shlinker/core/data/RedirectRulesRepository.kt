package app.shlinker.core.data

import app.shlinker.core.model.RedirectRule
import app.shlinker.core.model.RedirectRules
import app.shlinker.core.network.RedirectRulesService
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class RedirectRulesConflictException : IOException("Redirect rules changed on the server. Reload before saving.")

class RedirectRulesRepository @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val service: RedirectRulesService,
) {
    private suspend fun credentials(serverId: String): Pair<String, String> {
        val server = profiles.profiles.first().firstOrNull { it.id == serverId }
            ?: throw ServerNotFoundException(serverId)
        val key = when (val result = profiles.getApiKey(serverId)) {
            is ApiKeyResult.Available -> result.apiKey
            else -> throw ShortUrlApiKeyException()
        }
        return server.url to key
    }

    suspend fun get(serverId: String, shortCode: String, domain: String?): RedirectRules {
        val (url, key) = credentials(serverId)
        return service.get(url, key, shortCode, domain)
    }

    suspend fun set(
        serverId: String,
        shortCode: String,
        domain: String?,
        original: RedirectRules,
        rules: List<RedirectRule>,
    ): RedirectRules {
        val (url, key) = credentials(serverId)
        if (service.get(url, key, shortCode, domain) != original) throw RedirectRulesConflictException()
        return service.set(url, key, shortCode, domain, rules)
    }
}

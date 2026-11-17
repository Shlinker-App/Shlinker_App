package app.shlinker.core.network

import app.shlinker.core.model.ServerDomain
import app.shlinker.core.model.isValidShlinkDomain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

interface ServerDomainsService {
    suspend fun get(serverUrl: String, apiKey: String): List<ServerDomain>
}

class InvalidServerDomainsException : IOException("The server did not return valid domains")

internal class KtorServerDomainsService @Inject constructor(private val client: HttpClient, private val json: Json) :
    ServerDomainsService {
    override suspend fun get(serverUrl: String, apiKey: String): List<ServerDomain> {
        val response = try {
            client.get("${ShlinkApi.basePath(serverUrl)}/domains") { header(ShlinkApi.API_KEY_HEADER, apiKey) }
        } catch (e: IOException) {
            throw ShortUrlRequestException(KtorConnectionTester.classify(e))
        }
        when (response.status.value) {
            401, 403 -> throw ShortUrlRequestException(ConnectionError.InvalidApiKey)
            !in 200..299 -> throw ShortUrlRequestException(ConnectionError.HttpError(response.status.value, null))
        }
        val domains = try {
            json.decodeFromString<DomainsResponseDto>(response.bodyAsText()).domains.data
        } catch (_: SerializationException) {
            throw InvalidServerDomainsException()
        } catch (e: IOException) {
            throw ShortUrlRequestException(KtorConnectionTester.classify(e))
        }
        if (domains.any { !isValidShlinkDomain(it.domain) } ||
            domains.count { it.isDefault } > 1 ||
            domains.map { it.domain.lowercase(java.util.Locale.ROOT) }.distinct().size != domains.size
        ) {
            throw InvalidServerDomainsException()
        }
        return domains.map { ServerDomain(it.domain, it.isDefault) }
            .sortedWith(compareByDescending<ServerDomain> { it.isDefault }.thenBy { it.domain })
    }
}

@Serializable
private data class DomainsResponseDto(val domains: DomainsDto)

@Serializable
private data class DomainsDto(val data: List<DomainDto>)

@Serializable
private data class DomainDto(val domain: String, val isDefault: Boolean)

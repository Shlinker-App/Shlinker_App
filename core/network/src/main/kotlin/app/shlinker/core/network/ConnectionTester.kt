package app.shlinker.core.network

import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import javax.inject.Inject
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Checks that a Shlink server is reachable and healthy, and that the API key is valid.
 *
 * 1. `GET /rest/health` without the key checks reachability, TLS, Shlink health, and version.
 * 2. `GET /rest/v3/short-urls?itemsPerPage=1` with the key checks authentication.
 */
interface ConnectionTester {
    /** @param serverUrl a normalized server URL; see [app.shlinker.core.model.ServerUrl.normalize]. */
    suspend fun test(serverUrl: String, apiKey: String): ConnectionResult
}

internal class KtorConnectionTester @Inject constructor(private val client: HttpClient, private val json: Json) :
    ConnectionTester {

    override suspend fun test(serverUrl: String, apiKey: String): ConnectionResult {
        val base = serverUrl.trimEnd('/')

        val health = request { client.get("$base/rest/health") }
            .getOrElse { return ConnectionResult.Failure(classify(it)) }

        val healthDto = health.decodeOrNull(HealthDto.serializer())
            ?: return ConnectionResult.Failure(ConnectionError.NotShlink(health.status.value))

        if (!healthDto.status.equals("pass", ignoreCase = true)) {
            return ConnectionResult.Failure(ConnectionError.Unhealthy(healthDto.version))
        }
        val major = healthDto.version?.substringBefore('.')?.toIntOrNull()
        if (major != null && major < MIN_SHLINK_MAJOR) {
            return ConnectionResult.Failure(ConnectionError.UnsupportedVersion(healthDto.version))
        }

        val auth = request {
            client.get("${ShlinkApi.basePath(base)}/short-urls") {
                parameter("itemsPerPage", 1)
                header(ShlinkApi.API_KEY_HEADER, apiKey)
            }
        }.getOrElse { return ConnectionResult.Failure(classify(it)) }

        return when (auth.status.value) {
            in 200..299 -> ConnectionResult.Success(healthDto.version)
            401, 403 -> ConnectionResult.Failure(ConnectionError.InvalidApiKey)
            else -> {
                val problem = auth.decodeOrNull(ProblemDto.serializer())
                ConnectionResult.Failure(
                    ConnectionError.HttpError(auth.status.value, problem?.detail ?: problem?.title),
                )
            }
        }
    }

    private suspend inline fun request(block: () -> HttpResponse): Result<HttpResponse> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // Decode explicitly because Shlink can return application/health+json or application/problem+json.
    private suspend fun <T> HttpResponse.decodeOrNull(serializer: kotlinx.serialization.KSerializer<T>): T? = try {
        json.decodeFromString(serializer, bodyAsText())
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    internal companion object {
        const val MIN_SHLINK_MAJOR = 3

        fun classify(error: Throwable): ConnectionError {
            val chain = generateSequence(error) { it.cause }.take(10).toList()
            fun has(predicate: (Throwable) -> Boolean) = chain.any(predicate)

            return when {
                has { it is CertificateExpiredException || it is CertificateNotYetValidException } ->
                    ConnectionError.Certificate(CertificateProblem.EXPIRED)
                has { it is SSLPeerUnverifiedException } ->
                    ConnectionError.Certificate(CertificateProblem.HOSTNAME_MISMATCH)
                has { it is CertPathValidatorException } ->
                    ConnectionError.Certificate(CertificateProblem.UNTRUSTED)
                has { it is SSLException } ->
                    ConnectionError.Certificate(CertificateProblem.OTHER)
                has { it is UnknownHostException } -> ConnectionError.HostNotFound
                has {
                    it is HttpRequestTimeoutException || it is ConnectTimeoutException || it is SocketTimeoutException
                } -> ConnectionError.Timeout
                has { it is ConnectException || it is NoRouteToHostException } -> ConnectionError.Unreachable
                else -> ConnectionError.Unknown(error.message ?: error::class.simpleName.orEmpty())
            }
        }
    }
}

sealed interface ConnectionResult {
    data class Success(val shlinkVersion: String?) : ConnectionResult

    data class Failure(val error: ConnectionError) : ConnectionResult
}

sealed interface ConnectionError {
    /** DNS could not resolve the host. */
    data object HostNotFound : ConnectionError

    /** The connection was refused or no route to the host exists. */
    data object Unreachable : ConnectionError

    data object Timeout : ConnectionError

    data class Certificate(val problem: CertificateProblem) : ConnectionError

    /** The response is not a Shlink health response, such as a proxy error page. */
    data class NotShlink(val httpStatus: Int) : ConnectionError

    /** Shlink reports a failed health check, often due to an unreachable database. */
    data class Unhealthy(val version: String?) : ConnectionError

    data class UnsupportedVersion(val version: String?) : ConnectionError

    data object InvalidApiKey : ConnectionError

    data class HttpError(val status: Int, val detail: String?) : ConnectionError

    data class Unknown(val message: String) : ConnectionError
}

enum class CertificateProblem {
    /** The certificate is self-signed or issued by an untrusted CA. */
    UNTRUSTED,

    /** The certificate does not match the hostname. */
    HOSTNAME_MISMATCH,

    EXPIRED,
    OTHER,
}

@Serializable
internal data class HealthDto(val status: String, val version: String? = null)

@Serializable
internal data class ProblemDto(
    val type: String? = null,
    val title: String? = null,
    val detail: String? = null,
    val status: Int? = null,
)

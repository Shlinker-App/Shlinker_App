package at.awit.shlinker.core.network

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Basis-HttpClient. Server-URL und API-Key werden pro Request bzw. pro Server
     * gesetzt (Multi-Server), daher hier kein defaultRequest.
     */
    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        engine {
            config {
                // Retrying a POST after a lost response could create a second shlink.
                retryOnConnectionFailure(false)
            }
        }
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ConnectionModule {
    @Binds
    abstract fun bindShortUrlDetailsService(impl: KtorShortUrlDetailsService): ShortUrlDetailsService

    @Binds
    abstract fun bindCreateShortUrlService(impl: KtorCreateShortUrlService): CreateShortUrlService

    @Binds
    abstract fun bindShortUrlService(impl: KtorShortUrlService): ShortUrlService

    @Binds
    abstract fun bindConnectionTester(impl: KtorConnectionTester): ConnectionTester
}

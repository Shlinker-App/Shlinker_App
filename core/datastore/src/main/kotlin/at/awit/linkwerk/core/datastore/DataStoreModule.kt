package at.awit.linkwerk.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideServersDataStore(@ApplicationContext context: Context): DataStore<StoredServers> =
        DataStoreFactory.create(
            serializer = JsonSerializer(StoredServers.serializer(), StoredServers()),
            corruptionHandler = ReplaceFileCorruptionHandler { StoredServers() },
            produceFile = { context.dataStoreFile("servers.json") },
        )

    @Provides
    @Singleton
    fun provideSecretsDataStore(@ApplicationContext context: Context): DataStore<StoredSecrets> =
        DataStoreFactory.create(
            serializer = JsonSerializer(StoredSecrets.serializer(), StoredSecrets()),
            corruptionHandler = ReplaceFileCorruptionHandler { StoredSecrets() },
            produceFile = { context.dataStoreFile("secrets.json") },
        )
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CipherModule {
    @Binds
    abstract fun bindSecretCipher(impl: KeystoreSecretCipher): SecretCipher
}

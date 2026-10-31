package at.awit.shlinker.core.data

import androidx.datastore.core.DataStore
import at.awit.shlinker.core.datastore.StoredServers
import at.awit.shlinker.core.model.AppTagSettings
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class AppTagSettingsRepository @Inject constructor(private val store: DataStore<StoredServers>) {
    val settings = store.data.map {
        AppTagSettings(it.appTagEnabled, it.appTagName).also { settings ->
            if (!settings.isValid) throw IOException("Invalid app tag settings")
        }
    }.distinctUntilChanged()

    suspend fun save(settings: AppTagSettings) {
        require(settings.isValid) { "Enter a single non-empty app tag" }
        store.updateData { it.copy(appTagEnabled = settings.enabled, appTagName = settings.name.trim()) }
    }
}

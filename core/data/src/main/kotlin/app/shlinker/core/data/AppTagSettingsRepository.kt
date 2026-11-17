package app.shlinker.core.data

import androidx.datastore.core.DataStore
import app.shlinker.core.datastore.StoredServers
import app.shlinker.core.model.AppPreferences
import app.shlinker.core.model.AppTagSettings
import app.shlinker.core.model.ShlinkDefaults
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class AppTagSettingsRepository @Inject constructor(private val store: DataStore<StoredServers>) {
    val preferences = store.data.map {
        val tag = AppTagSettings(it.appTagEnabled, it.appTagName)
        if (!tag.isValid) throw IOException("Invalid app tag settings")
        AppPreferences(tag, ShlinkDefaults(it.forwardQuery, it.crawlable))
    }.distinctUntilChanged()
    val settings = preferences.map { it.appTag }.distinctUntilChanged()

    suspend fun save(settings: AppTagSettings) {
        require(settings.isValid) { "Enter a single non-empty app tag" }
        store.updateData { it.copy(appTagEnabled = settings.enabled, appTagName = settings.name.trim()) }
    }

    suspend fun save(settings: AppTagSettings, defaults: ShlinkDefaults) {
        require(settings.isValid) { "Enter a single non-empty app tag" }
        store.updateData {
            it.copy(
                appTagEnabled = settings.enabled,
                appTagName = settings.name.trim(),
                forwardQuery = defaults.forwardQuery,
                crawlable = defaults.crawlable,
            )
        }
    }
}

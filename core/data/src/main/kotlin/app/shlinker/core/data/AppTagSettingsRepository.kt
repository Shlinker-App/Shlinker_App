package app.shlinker.core.data

import androidx.datastore.core.DataStore
import app.shlinker.core.datastore.StoredServers
import app.shlinker.core.model.AppPreferences
import app.shlinker.core.model.AppTagSettings
import app.shlinker.core.model.DisplaySettings
import app.shlinker.core.model.ShlinkDefaults
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class AppTagSettingsRepository @Inject constructor(private val store: DataStore<StoredServers>) {
    val preferences = store.data.map {
        val tag = AppTagSettings(it.appTagEnabled, it.appTagName)
        if (!tag.isValid) throw IOException("Invalid app tag settings")
        AppPreferences(tag, ShlinkDefaults(it.forwardQuery, it.crawlable), it.display())
    }.distinctUntilChanged()
    val settings = preferences.map { it.appTag }.distinctUntilChanged()

    /** Falls back to the defaults if the settings cannot be read, so the Shlink list keeps working. */
    val display: Flow<DisplaySettings> = store.data.map { it.display() }
        .catch { error ->
            if (error !is IOException) throw error
            emit(DisplaySettings())
        }
        .distinctUntilChanged()

    suspend fun save(settings: AppTagSettings) {
        require(settings.isValid) { "Enter a single non-empty app tag" }
        store.updateData { it.copy(appTagEnabled = settings.enabled, appTagName = settings.name.trim()) }
    }

    suspend fun save(settings: AppTagSettings, defaults: ShlinkDefaults, display: DisplaySettings? = null) {
        require(settings.isValid) { "Enter a single non-empty app tag" }
        store.updateData {
            it.copy(
                appTagEnabled = settings.enabled,
                appTagName = settings.name.trim(),
                forwardQuery = defaults.forwardQuery,
                crawlable = defaults.crawlable,
                showServerOverview = display?.showServerOverview ?: it.showServerOverview,
                navigationMenu = display?.menuStyle ?: it.navigationMenu,
            )
        }
    }

    private fun StoredServers.display() = DisplaySettings(showServerOverview, navigationMenu)
}

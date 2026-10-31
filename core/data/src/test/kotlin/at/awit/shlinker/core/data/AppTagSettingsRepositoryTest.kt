package at.awit.shlinker.core.data

import at.awit.shlinker.core.datastore.StoredServer
import at.awit.shlinker.core.datastore.StoredServers
import at.awit.shlinker.core.model.AppTagSettings
import at.awit.shlinker.core.model.ServerSort
import at.awit.shlinker.core.model.ShlinkDefaults
import at.awit.shlinker.core.model.ShortUrlSort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppTagSettingsRepositoryTest {
    @Test fun defaultsAndSavedSettingsSurviveRecreationWithoutChangingProfilesOrSort() = runTest {
        val original = StoredServers(
            servers = listOf(StoredServer("1", "Home", "https://s.example", 1)),
            activeServerId = "1",
            sort = ServerSort.NAME_DESC,
            manualOrder = listOf("1"),
            shortUrlSort = ShortUrlSort.LEAST_VISITED,
        )
        val store = InMemoryDataStore(original)
        val repo = AppTagSettingsRepository(store)
        assertEquals(AppTagSettings(), repo.settings.first())
        repo.save(AppTagSettings(false, "  my app  "))
        assertEquals(AppTagSettings(false, "my app"), AppTagSettingsRepository(store).settings.first())
        assertEquals(original.copy(appTagEnabled = false, appTagName = "my app"), store.current)
    }

    @Test fun invalidNameNeverChangesSettings() = runTest {
        val store = InMemoryDataStore(StoredServers())
        val repo = AppTagSettingsRepository(store)
        for (name in listOf("", " ", "one,two", "one\ntwo", "one\rtwo")) {
            try {
                repo.save(AppTagSettings(name = name))
                throw AssertionError("Expected validation error")
            } catch (_: IllegalArgumentException) {
                assertEquals(StoredServers(), store.current)
            }
        }
    }

    @Test fun creationDefaultsPersistAtomicallyWithTagAndPreserveUnrelatedState() = runTest {
        val original = StoredServers(
            activeServerId = "1",
            manualOrder = listOf("1"),
            shortUrlSort = ShortUrlSort.MOST_VISITED,
        )
        val store = InMemoryDataStore(original)
        val repo = AppTagSettingsRepository(store)
        assertEquals(ShlinkDefaults(false, null), repo.preferences.first().shlinkDefaults)
        repo.save(AppTagSettings(false, " custom "), ShlinkDefaults(true, false))
        assertEquals(
            original.copy(appTagEnabled = false, appTagName = "custom", forwardQuery = true, crawlable = false),
            store.current,
        )
        assertEquals(
            ShlinkDefaults(true, false),
            AppTagSettingsRepository(store).preferences.first().shlinkDefaults,
        )
        repo.save(AppTagSettings(true, "changed"))
        assertEquals(ShlinkDefaults(true, false), repo.preferences.first().shlinkDefaults)
        repo.save(AppTagSettings(), ShlinkDefaults(false, null))
        assertEquals(ShlinkDefaults(false, null), repo.preferences.first().shlinkDefaults)
    }
}

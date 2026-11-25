package app.shlinker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.shlinker.about.AboutRoute as AboutScreenRoute
import app.shlinker.feature.servers.AddServerRoute as AddServerScreenRoute
import app.shlinker.feature.shorturls.CreateShortUrlRoute as CreateShortUrlScreenRoute
import app.shlinker.feature.shorturls.RedirectRulesRoute as RedirectRulesScreenRoute
import app.shlinker.feature.shorturls.ShortUrlDetailsRoute as ShortUrlDetailsScreenRoute
import app.shlinker.feature.shorturls.ShortUrlsRoute as ShortUrlsScreenRoute
import app.shlinker.feature.shorturls.VisitStatisticsRoute as VisitStatisticsScreenRoute
import app.shlinker.home.ServersScreen
import app.shlinker.navigation.AboutRoute
import app.shlinker.navigation.AddServerRoute
import app.shlinker.navigation.CreateShortUrlRoute
import app.shlinker.navigation.EditServerRoute
import app.shlinker.navigation.RedirectRulesRoute
import app.shlinker.navigation.ServersRoute
import app.shlinker.navigation.SettingsRoute
import app.shlinker.navigation.ShortUrlDetailsRoute
import app.shlinker.navigation.ShortUrlsRoute
import app.shlinker.navigation.VisitStatisticsRoute
import app.shlinker.settings.SettingsRoute as SettingsScreenRoute

@Composable
fun ShlinkerApp(mainViewModel: MainViewModel) {
    val startState by mainViewModel.startState.collectAsStateWithLifecycle()
    if (startState != StartState.Loading) {
        AppNavigation(start = if (startState == StartState.NoServer) AddServerRoute else ShortUrlsRoute)
    }
}

@Composable
private fun AppNavigation(start: NavKey) {
    // Keep subsequent start-state changes from resetting the back stack.
    val initial = remember { start }
    val backStack = rememberNavBackStack(initial)
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    var rulesRevision by rememberSaveable { mutableIntStateOf(0) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<ServersRoute> {
                ServersScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onAddServer = { backStack.add(AddServerRoute) },
                    onServerSelected = { backStack.removeLastOrNull() },
                    onEditServer = { backStack.add(EditServerRoute(it)) },
                )
            }
            entry<AboutRoute> {
                AboutScreenRoute(onBack = { backStack.removeLastOrNull() })
            }
            entry<SettingsRoute> {
                SettingsScreenRoute(onBack = { backStack.removeLastOrNull() })
            }
            entry<ShortUrlsRoute> {
                ShortUrlsScreenRoute(
                    onServers = { backStack.add(ServersRoute) },
                    onSettings = { backStack.add(SettingsRoute) },
                    onAbout = { backStack.add(AboutRoute) },
                    onAddServer = { backStack.add(AddServerRoute) },
                    onCreate = { backStack.add(CreateShortUrlRoute(it)) },
                    onDetails = { serverId, url ->
                        backStack.add(ShortUrlDetailsRoute(serverId, url.shortCode, url.domain))
                    },
                    refreshKey = refreshKey,
                )
            }
            entry<ShortUrlDetailsRoute> { route ->
                ShortUrlDetailsScreenRoute(
                    serverId = route.serverId,
                    shortCode = route.shortCode,
                    domain = route.domain,
                    onBack = { backStack.removeLastOrNull() },
                    onChanged = { refreshKey++ },
                    onStatistics = {
                        backStack.add(VisitStatisticsRoute(route.serverId, route.shortCode, route.domain))
                    },
                    onRules = {
                        backStack.add(RedirectRulesRoute(route.serverId, route.shortCode, route.domain))
                    },
                    rulesRevision = rulesRevision,
                )
            }
            entry<RedirectRulesRoute> { route ->
                RedirectRulesScreenRoute(
                    serverId = route.serverId,
                    shortCode = route.shortCode,
                    domain = route.domain,
                    onBack = { backStack.removeLastOrNull() },
                    onChanged = {
                        refreshKey++
                        rulesRevision++
                    },
                )
            }
            entry<VisitStatisticsRoute> { route ->
                VisitStatisticsScreenRoute(
                    serverId = route.serverId,
                    shortCode = route.shortCode,
                    domain = route.domain,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<CreateShortUrlRoute> { route ->
                CreateShortUrlScreenRoute(
                    serverId = route.serverId,
                    onBack = { backStack.removeLastOrNull() },
                    onCreated = {
                        refreshKey++
                        backStack.removeLastOrNull()
                    },
                )
            }
            entry<EditServerRoute> { route ->
                AddServerScreenRoute(
                    serverId = route.id,
                    onSaved = { backStack.removeLastOrNull() },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<AddServerRoute> {
                val isFirstServer = backStack.size == 1
                AddServerScreenRoute(
                    onSaved = {
                        if (isFirstServer) {
                            backStack.clear()
                            backStack.add(ShortUrlsRoute)
                        } else {
                            backStack.removeLastOrNull()
                        }
                    },
                    onBack = if (isFirstServer) null else ({ backStack.removeLastOrNull() }),
                )
            }
        },
    )
}

package at.awit.shlinker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import at.awit.shlinker.feature.servers.AddServerRoute as AddServerScreenRoute
import at.awit.shlinker.feature.shorturls.ShortUrlsRoute as ShortUrlsScreenRoute
import at.awit.shlinker.home.HomeScreen
import at.awit.shlinker.navigation.AddServerRoute
import at.awit.shlinker.navigation.HomeRoute
import at.awit.shlinker.navigation.ShortUrlsRoute

@Composable
fun ShlinkerApp(mainViewModel: MainViewModel) {
    val startState by mainViewModel.startState.collectAsStateWithLifecycle()
    if (startState != StartState.Loading) {
        AppNavigation(start = if (startState == StartState.NoServer) AddServerRoute else HomeRoute)
    }
}

@Composable
private fun AppNavigation(start: NavKey) {
    // Startziel nur beim ersten Aufbau festlegen; spätere Änderungen von startState ignorieren.
    val initial = remember { start }
    val backStack = rememberNavBackStack(initial)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeScreen(
                    onAddServer = { backStack.add(AddServerRoute) },
                    onShortUrls = { backStack.add(ShortUrlsRoute) },
                )
            }
            entry<ShortUrlsRoute> {
                ShortUrlsScreenRoute(onBack = { backStack.removeLastOrNull() })
            }
            entry<AddServerRoute> {
                val isFirstServer = backStack.size == 1
                AddServerScreenRoute(
                    onSaved = {
                        if (isFirstServer) {
                            backStack.clear()
                            backStack.add(HomeRoute)
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

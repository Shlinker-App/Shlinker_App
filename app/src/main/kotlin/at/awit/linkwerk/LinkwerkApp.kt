package at.awit.linkwerk

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
import at.awit.linkwerk.feature.servers.AddServerRoute as AddServerScreenRoute
import at.awit.linkwerk.home.HomeScreen
import at.awit.linkwerk.navigation.AddServerRoute
import at.awit.linkwerk.navigation.HomeRoute

@Composable
fun LinkwerkApp(mainViewModel: MainViewModel) {
    val startState by mainViewModel.startState.collectAsStateWithLifecycle()
    when (startState) {
        // Splash/Fensterhintergrund bleibt sichtbar, bis klar ist, ob Server existieren (wenige ms).
        StartState.Loading -> Unit
        StartState.NoServer -> AppNavigation(start = AddServerRoute)
        StartState.Ready -> AppNavigation(start = HomeRoute)
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
                HomeScreen(onAddServer = { backStack.add(AddServerRoute) })
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

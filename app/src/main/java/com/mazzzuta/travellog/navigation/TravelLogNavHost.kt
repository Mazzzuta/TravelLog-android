package com.mazzzuta.travellog.navigation

import android.os.SystemClock
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mazzzuta.travellog.ui.components.TravelBottomNav
import com.mazzzuta.travellog.ui.createentry.CreateEntryScreen
import com.mazzzuta.travellog.ui.entrydetail.EntryDetailScreen
import com.mazzzuta.travellog.ui.feed.FeedScreen
import com.mazzzuta.travellog.ui.map.MapScreen
import com.mazzzuta.travellog.ui.settings.SettingsScreen
import com.mazzzuta.travellog.ui.statistics.StatisticsScreen
import com.mazzzuta.travellog.ui.trips.TripsScreen
import androidx.compose.runtime.remember

@Composable
fun TravelLogNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val bottomTabRoutes = remember { BottomTab.entries.map { it.route::class.qualifiedName } }
    val showBottomBar = currentRoute in bottomTabRoutes
    // Пауза чуть длиннее анимации: серия быстрых нажатий не запускает несколько переходов сразу.
    var lastNavigationAt by remember { mutableLongStateOf(0L) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                TravelBottomNav(
                    currentRoute = currentRoute,
                    onTabSelected = { route ->
                        val now = SystemClock.uptimeMillis()
                        if (now - lastNavigationAt >= 250 && currentRoute != route::class.qualifiedName) {
                            lastNavigationAt = now
                            navController.navigate(route) {
                                // Возвращаясь во вкладку, восстанавливаем её состояние без накопления копий в стеке.
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onCreateClick = {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastNavigationAt >= 250) {
                            lastNavigationAt = now
                            navController.navigate(Route.CreateEntry)
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.Feed,
            enterTransition = { slideInHorizontally(tween(180)) { it } },
            exitTransition = { slideOutHorizontally(tween(180)) { -it } },
            popEnterTransition = { slideInHorizontally(tween(180)) { -it } },
            popExitTransition = { slideOutHorizontally(tween(180)) { it } },
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
                .padding(bottom = if (showBottomBar) padding.calculateBottomPadding() else 0.dp)
        ) {
            composable<Route.Feed> {
                FeedScreen(
                    onEntryClick = { id -> navController.navigate(Route.EntryDetail(id)) },
                    onTripsClick = { navController.navigate(Route.Trips) },
                    onCreateClick = { navController.navigate(Route.CreateEntry) }
                )
            }
            composable<Route.MapScreen> {
                MapScreen(onEntryClick = { id -> navController.navigate(Route.EntryDetail(id)) })
            }
            composable<Route.EntryMap> { backStackEntry ->
                val args = backStackEntry.toRoute<Route.EntryMap>()
                MapScreen(
                    focusEntryId = args.entryId,
                    onBack = { navController.popBackStack() },
                    onEntryClick = { id ->
                        navController.popBackStack()
                        if (id != args.entryId) navController.navigate(Route.EntryDetail(id))
                    },
                )
            }
            composable<Route.CreateEntry> {
                CreateEntryScreen(
                    onSaved = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable<Route.EntryDetail> { backStackEntry ->
                val args = backStackEntry.toRoute<Route.EntryDetail>()
                EntryDetailScreen(
                    entryId = args.entryId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Route.EditEntry(args.entryId)) },
                    onMapClick = { navController.navigate(Route.EntryMap(args.entryId)) }
                )
            }
            composable<Route.EditEntry> { backStackEntry ->
                val args = backStackEntry.toRoute<Route.EditEntry>()
                CreateEntryScreen(
                    entryId = args.entryId,
                    onSaved = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable<Route.Statistics> {
                StatisticsScreen(onCreateClick = { navController.navigate(Route.CreateEntry) })
            }
            composable<Route.Settings> { SettingsScreen() }
            composable<Route.Trips> {
                TripsScreen(
                    onBack = { navController.popBackStack() },
                    onEntryClick = { id -> navController.navigate(Route.EntryDetail(id)) }
                )
            }
        }
    }
}

package com.mazzzuta.travellog.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                TravelBottomNav(
                    currentRoute = currentRoute,
                    onTabSelected = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onCreateClick = { navController.navigate(Route.CreateEntry) }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.Feed,
            modifier = Modifier.padding(bottom = if (showBottomBar) padding.calculateBottomPadding() else 0.dp)
        ) {
            composable<Route.Feed> {
                FeedScreen(
                    onEntryClick = { id -> navController.navigate(Route.EntryDetail(id)) },
                    onTripsClick = { navController.navigate(Route.Trips) }
                )
            }
            composable<Route.MapScreen> {
                MapScreen(onEntryClick = { id -> navController.navigate(Route.EntryDetail(id)) })
            }
            composable<Route.CreateEntry> {
                CreateEntryScreen(
                    onSaved = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable<Route.EntryDetail> { backStackEntry ->
                val args = backStackEntry.toRoute<Route.EntryDetail>()
                EntryDetailScreen(entryId = args.entryId, onBack = { navController.popBackStack() })
            }
            composable<Route.Statistics> { StatisticsScreen() }
            composable<Route.Settings> { SettingsScreen() }
            composable<Route.Trips> {
                TripsScreen(
                    onBack = { navController.popBackStack() },
                    onTripClick = { }
                )
            }
        }
    }
}
package com.mazzzuta.travellog.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mazzzuta.travellog.navigation.BottomTab
import com.mazzzuta.travellog.navigation.Route

@Composable
fun TravelBottomNav(
    currentRoute: String?,
    onTabSelected: (Route) -> Unit,
    onCreateClick: () -> Unit,
) {
    NavigationBar {
        BottomTab.entries.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route::class.qualifiedName,
                onClick = { onTabSelected(tab.route) },
                icon = { Text(tab.label.first().toString()) },
                label = { Text(tab.label) }
            )
            if (index == 1) {
                NavigationBarItem(
                    selected = false,
                    onClick = onCreateClick,
                    icon = {
                        FloatingActionButton(onClick = onCreateClick, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Новая запись")
                        }
                    },
                    label = null
                )
            }
        }
    }
}
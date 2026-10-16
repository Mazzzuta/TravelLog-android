package com.mazzzuta.travellog.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazzzuta.travellog.navigation.BottomTab
import com.mazzzuta.travellog.navigation.Route

@Composable
fun TravelBottomNav(
    currentRoute: String?,
    onTabSelected: (Route) -> Unit,
    onCreateClick: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale = ((maxWidth.value - 16f) / 400f).coerceIn(0.8333333f, 1f)
        NavigationBar {
            Spacer(Modifier.width(8.dp))
            BottomTab.entries.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = currentRoute == tab.route::class.qualifiedName,
                    onClick = { onTabSelected(tab.route) },
                    icon = {
                        Icon(when (tab) {
                            BottomTab.FEED -> Icons.Default.Article
                            BottomTab.MAP -> Icons.Default.Map
                            BottomTab.STATISTICS -> Icons.Default.BarChart
                            BottomTab.SETTINGS -> Icons.Default.Settings
                        }, contentDescription = null, modifier = Modifier.size((24 * scale).dp))
                    },
                    label = {
                        BasicText(
                            tab.label,
                            style = LocalTextStyle.current.copy(color = LocalContentColor.current),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = (12 * scale).sp, stepSize = 0.5.sp)
                        )
                    }
                )
                if (index == 1) {
                    NavigationBarItem(
                        selected = false,
                        onClick = onCreateClick,
                        icon = {
                            FloatingActionButton(onClick = onCreateClick, modifier = Modifier.size((48 * scale).dp)) {
                                Icon(Icons.Default.Add, contentDescription = "Новая запись", modifier = Modifier.size((24 * scale).dp))
                            }
                        },
                        label = null
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

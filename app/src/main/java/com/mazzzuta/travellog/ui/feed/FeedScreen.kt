package com.mazzzuta.travellog.ui.feed

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun FeedScreen(
    onEntryClick: (Long) -> Unit,
    onTripsClick: () -> Unit
) {
    Text("Feed Screen")
}
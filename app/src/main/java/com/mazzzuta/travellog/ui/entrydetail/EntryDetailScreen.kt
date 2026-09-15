package com.mazzzuta.travellog.ui.entrydetail

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun EntryDetailScreen(entryId: Long, onBack: () -> Unit) {
    Text("Entry Detail Screen: $entryId")
}
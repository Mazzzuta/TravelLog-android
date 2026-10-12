package com.mazzzuta.travellog.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mazzzuta.travellog.viewmodels.BackupViewModel
import java.time.LocalDate
import org.koin.androidx.compose.koinViewModel

@Composable
fun BackupSection(viewModel: BackupViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        if (it != null) viewModel.export(it)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) viewModel.prepareImport(it)
    }
    val enabled = !state.busy && state.preview == null
    Text("РЕЗЕРВНАЯ КОПИЯ", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Сохраните дневник с фотографиями и настройками или перенесите его на другое устройство.")
            Button(enabled = enabled, modifier = Modifier.fillMaxWidth(), onClick = {
                export.launch("TravelLog-${LocalDate.now()}.zip")
            }) { Text("Экспортировать ZIP") }
            OutlinedButton(enabled = enabled, modifier = Modifier.fillMaxWidth(), onClick = {
                import.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }) { Text("Импортировать ZIP") }
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.operation)
            }
            state.message?.let { Text(it) }
        }
    }
    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text("Заменить дневник?") },
            text = { Text("В архиве: записей — ${preview.entries}, поездок — ${preview.trips}, фотографий — ${preview.photos}.\n\n" +
                "Текущие записи, поездки, теги и настройки будут заменены содержимым архива. Объединения данных не будет. " +
                "Если нужен текущий дневник, сначала отмените импорт и экспортируйте его.") },
            confirmButton = { TextButton(onClick = viewModel::confirmImport) { Text("Заменить дневник") } },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Отмена") } },
        )
    }
}

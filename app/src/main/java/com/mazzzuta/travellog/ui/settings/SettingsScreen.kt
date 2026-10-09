package com.mazzzuta.travellog.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mazzzuta.travellog.database.ThemeMode
import com.mazzzuta.travellog.ui.theme.AppPalette
import com.mazzzuta.travellog.ui.theme.Palettes
import com.mazzzuta.travellog.utils.DateFormatOption
import com.mazzzuta.travellog.utils.formatMonthYear
import com.mazzzuta.travellog.viewmodels.SettingsViewModel
import com.mazzzuta.travellog.viewmodels.SortOption
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val prefs by viewModel.prefs.collectAsState()
    var showNicknameDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()

    val p = prefs ?: return

    val activePalette = Palettes.resolve(p, systemDark)
    val customSelected = activePalette in Palettes.additional

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        Text("TRAVEL LOG", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Настройки", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)

        Spacer(Modifier.height(20.dp))

        // Профиль: ник + дата первого запуска
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    p.nickname.first().uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    p.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (p.registeredAt > 0) {
                    Text(
                        "Путешественник с ${formatMonthYear(p.registeredAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { showNicknameDialog = true }) {
                Icon(Icons.Default.Edit, contentDescription = "Изменить никнейм")
            }
        }

        Spacer(Modifier.height(24.dp))

        // Режим темы
        SectionLabel("ВНЕШНИЙ ВИД")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val light = Palettes.LightBase
            val dark = Palettes.DarkBase
            ThemeModeCard(
                "Светлая",
                Brush.horizontalGradient(listOf(light.bg, light.primaryDim, light.primary)),
                !customSelected && p.themeMode == ThemeMode.LIGHT, Modifier.weight(1f)
            ) { viewModel.setThemeMode(ThemeMode.LIGHT) }
            ThemeModeCard(
                "Тёмная",
                Brush.horizontalGradient(listOf(dark.bg, dark.primaryDim, dark.primary)),
                !customSelected && p.themeMode == ThemeMode.DARK, Modifier.weight(1f)
            ) { viewModel.setThemeMode(ThemeMode.DARK) }
            ThemeModeCard(
                "Системная",
                // левая половина светлая, правая тёмная
                Brush.horizontalGradient(0f to light.bg, 0.5f to light.primary, 0.5f to dark.bg, 1f to dark.primary),
                !customSelected && p.themeMode == ThemeMode.SYSTEM, Modifier.weight(1f)
            ) { viewModel.setThemeMode(ThemeMode.SYSTEM) }
        }

        Spacer(Modifier.height(24.dp))

        // Дополнительные палитры доступны при любом стандартном режиме.
        SectionLabel("ЦВЕТОВАЯ СХЕМА")
        Text(
            "Выберите готовое оформление или используйте стандартную тему выше",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Palettes.additional.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                rowItems.forEach { palette ->
                    PaletteTile(palette, palette == activePalette, Modifier.weight(1f)) { viewModel.setScheme(palette) }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))

        // Записи
        SectionLabel("ЗАПИСИ")
        SettingsCard {
            DropdownSettingRow(
                title = "Сортировка по умолчанию",
                current = SortOption.fromSql(p.defaultSort).label,
                options = SortOption.entries.map { it.label to { viewModel.setDefaultSort(it) } }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
            DropdownSettingRow(
                title = "Формат даты",
                current = DateFormatOption.fromId(p.dateFormat).label,
                options = DateFormatOption.entries.map { it.label to { viewModel.setDateFormat(it) } }
            )
        }

        Spacer(Modifier.height(24.dp))

        // Прочее
        SectionLabel("ПРОЧЕЕ")
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showAboutDialog = true }.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text("О приложении", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showNicknameDialog) {
        var draft by remember { mutableStateOf(p.nickname) }
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text("Ваш никнейм") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 30) draft = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        viewModel.setNickname(draft)
                        showNicknameDialog = false
                    }
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showNicknameDialog = false }) { Text("Отмена") } }
        )
    }

    if (showAboutDialog) {
        val context = LocalContext.current
        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
        }
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("TravelLog") },
            text = { Text("Дневник путешествий с фотографиями и картой.\nВерсия $version") },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("Закрыть") } }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface),
        content = content
    )
}

@Composable
private fun DropdownSettingRow(title: String, current: String, options: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Box {
            Text(current, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (label, action) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { action(); expanded = false })
                }
            }
        }
    }
}

/** Карточка режима темы: градиент своих цветов, выбранная получает рамку и галочку. */
@Composable
private fun ThemeModeCard(label: String, brush: Brush, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)).background(brush),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Выбрано", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/** Три основных цвета помогают увидеть оформление до выбора. */
@Composable
private fun PaletteTile(palette: AppPalette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.clip(RoundedCornerShape(12.dp))) {
            listOf(palette.bg, palette.surface, palette.primary).forEach { color ->
                Box(modifier = Modifier.weight(1f).height(56.dp).background(color))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                palette.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (selected) Icon(Icons.Default.Check, contentDescription = "Выбрано", tint = palette.primary, modifier = Modifier.size(18.dp))
        }
    }
}

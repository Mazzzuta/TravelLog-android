package com.mazzzuta.travellog.ui.settings

import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mazzzuta.travellog.database.ThemeMode
import com.mazzzuta.travellog.ui.theme.AppPalette
import com.mazzzuta.travellog.ui.theme.Palettes
import com.mazzzuta.travellog.utils.DateFormatOption
import com.mazzzuta.travellog.utils.AvatarImage
import com.mazzzuta.travellog.utils.formatMonthYear
import com.mazzzuta.travellog.viewmodels.SettingsViewModel
import com.mazzzuta.travellog.viewmodels.SortOption
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    var showNicknameDialog by rememberSaveable { mutableStateOf(false) }
    var showAvatarDialog by rememberSaveable { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setAvatar(uri)
    }
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0.9.0"
    }
    var showTagsDialog by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()

    val p = state.prefs
    if (p == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            if (state.isLoading) CircularProgressIndicator()
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.loadError ?: "Не удалось загрузить настройки", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::load) { Text("Повторить") }
            }
        }
        return
    }

    val activePalette = Palettes.resolve(p, systemDark)
    val avatar = remember(p.avatar) { AvatarImage.decode(p.avatar)?.asImageBitmap() }
    val customSelected = activePalette in Palettes.additional

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)

        // Место под индикатор занято всегда, чтобы сохранение настройки не сдвигало весь экран
        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
            if (state.isSaving || state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        state.loadError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::load) { Text("Повторить загрузку") }
        }
        state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(enabled = !state.isSaving, role = Role.Button) { showAvatarDialog = true }
                    .semantics { contentDescription = "Изменить аватар" },
                contentAlignment = Alignment.Center
            ) {
                if (avatar != null) Image(avatar, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(
                    p.nickname.firstOrNull()?.uppercase() ?: "П",
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

        SectionLabel("ВНЕШНИЙ ВИД")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val light = Palettes.LightBase
            val dark = Palettes.DarkBase
            ThemeModeCard(
                "Светлая",
                listOf(light.bg, light.surface, light.primary),
                !customSelected && p.themeMode == ThemeMode.LIGHT, Modifier.weight(1f)
            ) { viewModel.setThemeMode(ThemeMode.LIGHT) }
            ThemeModeCard(
                "Тёмная",
                listOf(dark.bg, dark.surface, dark.primary),
                !customSelected && p.themeMode == ThemeMode.DARK, Modifier.weight(1f)
            ) { viewModel.setThemeMode(ThemeMode.DARK) }
            ThemeModeCard(
                "Системная",
                if (systemDark) listOf(dark.bg, dark.surface, dark.primary) else listOf(light.bg, light.surface, light.primary),
                !customSelected && p.themeMode == ThemeMode.SYSTEM, Modifier.weight(1f)
            ) { viewModel.setThemeMode(ThemeMode.SYSTEM) }
        }

        Spacer(Modifier.height(24.dp))

        SectionLabel("ЦВЕТОВАЯ СХЕМА")
        Text(
            "Выберите готовое оформление или используйте стандартную тему выше",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Palettes.additional.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp).height(IntrinsicSize.Min)) {
                rowItems.forEach { palette ->
                    PaletteTile(palette, palette == activePalette, Modifier.weight(1f).fillMaxHeight()) { viewModel.setScheme(palette) }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))

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

        Spacer(Modifier.height(12.dp))
        SettingsCard {
            Row(Modifier.fillMaxWidth().clickable { viewModel.loadTags(); showTagsDialog = true }.padding(16.dp)) {
                Text("Управление тегами", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))

        BackupSection()
        Spacer(Modifier.height(24.dp))

        SectionLabel("ХРАНИЛИЩЕ")
        SettingsCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ненужные фотографии удаляются автоматически после изменения дневника. Можно повторить очистку вручную.")
                OutlinedButton(onClick = viewModel::cleanPhotos, enabled = !state.isCleaning) {
                    Text("Очистить ненужные фотографии")
                }
                if (state.isCleaning) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.cleanupMessage?.let { Text(it) }
                HorizontalDivider()
                OutlinedButton(onClick = { viewModel.clearResetError(); showResetDialog = true },
                    enabled = !state.isCleaning && !state.isSaving && !state.isResetting && !state.isDeletingTags,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Удалить все данные")
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        SectionLabel("ПРОЧЕЕ")
        SettingsCard {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Версия", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(version, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showAboutDialog = true }.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text("О приложении", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showTagsDialog) TagManagementDialog(viewModel, onDismiss = { showTagsDialog = false })

    if (showResetDialog) {
        AlertDialog(onDismissRequest = { if (!state.isResetting) showResetDialog = false },
            title = { Text("Удалить все данные?") },
            text = { Column {
                Text("Все записи, поездки, теги, сохранённые фотографии, аватар и настройки будут удалены без возможности отмены. Сначала сохраните ZIP, если нужен дневник. Приложение закроется; при следующем запуске оно будет пустым. Фотографии в галерее и ранее экспортированные архивы останутся.")
                state.resetError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.isResetting) LinearProgressIndicator(Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(enabled = !state.isResetting, onClick = viewModel::clearAllData) { Text("Удалить всё") } },
            dismissButton = { TextButton(enabled = !state.isResetting, onClick = { showResetDialog = false }) { Text("Отмена") } })
    }

    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            title = { Text("Аватар") },
            text = { Text("Выберите фотографию. Изображение будет обрезано по центру.") },
            confirmButton = {
                TextButton(onClick = {
                    showAvatarDialog = false
                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Выбрать фото") }
            },
            dismissButton = {
                Row {
                    if (p.avatar != null) TextButton(onClick = {
                        showAvatarDialog = false
                        viewModel.setAvatar(null)
                    }) { Text("Удалить") }
                    TextButton(onClick = { showAvatarDialog = false }) { Text("Отмена") }
                }
            }
        )
    }

    if (showNicknameDialog) {
        var draft by rememberSaveable(p.nickname) { mutableStateOf(p.nickname) }
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text("Ваш никнейм") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 30) draft = it },
                    singleLine = true,
                    enabled = !state.isSaving,
                    supportingText = {
                        Text(state.saveError ?: "${draft.length}/30", color = if (state.saveError == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank() && !state.isSaving,
                    onClick = {
                        viewModel.setNickname(draft) { showNicknameDialog = false }
                    }
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showNicknameDialog = false }) { Text("Отмена") } }
        )
    }

    if (showAboutDialog) {
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

@Composable
private fun ThemeModeCard(label: String, colors: List<Color>, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(Modifier.fillMaxSize()) {
                colors.forEach { color -> Box(Modifier.weight(1f).fillMaxHeight().background(color)) }
            }
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

@Composable
private fun PaletteTile(palette: AppPalette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.clip(RoundedCornerShape(12.dp))) {
            listOf(palette.bg, palette.surface, palette.primary).forEach { color ->
                Box(modifier = Modifier.weight(1f).height(56.dp).background(color))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            palette.name,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

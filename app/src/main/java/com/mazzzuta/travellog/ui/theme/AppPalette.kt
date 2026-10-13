package com.mazzzuta.travellog.ui.theme

import androidx.compose.ui.graphics.Color
import com.mazzzuta.travellog.database.ThemeMode
import com.mazzzuta.travellog.database.UserPrefs

data class AppPalette(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val stroke: Color,
    val ink: Color,
    val inkMuted: Color,
    val primary: Color,
    val primaryDim: Color,
    val onPrimary: Color,
    val success: Color,
    val danger: Color,
)

object Palettes {
    val LightBase = AppPalette(
        "light_base", "Светлая", false,
        Color(0xFFFFFBF8), Color(0xFFFFFFFF), Color(0xFFF0E4DA), Color(0xFF2B2118), Color(0xFF8A7A6D),
        Color(0xFFFF7A30), Color(0xFFFFE4D1), Color(0xFFFFFFFF), Color(0xFF6B9E6F), Color(0xFFC4614A)
    )
    val Force = AppPalette(
        "force", "Сила", false,
        Color(0xFFF8F9FA), Color(0xFFFFFFFF), Color(0xFFE3E6E9), Color(0xFF1F2933), Color(0xFF5F6B76),
        Color(0xFFE63946), Color(0xFFFBDDE0), Color(0xFFFFFFFF), Color(0xFF1E8458), Color(0xFFA4161A)
    )
    val DarkBase = AppPalette(
        "dark_base", "Тёмная", true,
        Color(0xFF1A1512), Color(0xFF241D18), Color(0xFF3A2E27), Color(0xFFF5EDE6), Color(0xFFA8998C),
        Color(0xFFFF8C42), Color(0xFF3D2010), Color(0xFFFFFFFF), Color(0xFF5A8A5E), Color(0xFFB0513C)
    )
    val Berries = AppPalette(
        "berries", "Ягоды в бирюзовой дымке", true,
        Color(0xFF0B2224), Color(0xFF153235), Color(0xFF336269), Color(0xFFE6F5F5), Color(0xFF93D4D8),
        Color(0xFFF87474), Color(0xFF941D1D), Color(0xFF0B2224), Color(0xFF5FBF9A), Color(0xFFFFB4A8)
    )
    val Jellyfish = AppPalette(
        "jellyfish", "Медузы в глубине", true,
        Color(0xFF0B1112), Color(0xFF1B2628), Color(0xFF314041), Color(0xFFEEF3EF), Color(0xFF91A19C),
        Color(0xFFC9D3CA), Color(0xFF4C5D5C), Color(0xFF0B1112), Color(0xFF6B7D7B), Color(0xFFD98B7C)
    )

    val Neon = AppPalette(
        "neon", "Неон", true,
        Color(0xFF0A0A0A), Color(0xFF181C12), Color(0xFF39442A), Color(0xFFF2F6EA), Color(0xFFADB89B),
        Color(0xFFB6FF00), Color(0xFF293A0B), Color(0xFF0A0A0A), Color(0xFF88D894), Color(0xFFFFA89A)
    )
    val RoseGraphite = AppPalette(
        "rose_graphite", "Розовый графит", true,
        Color(0xFF292929), Color(0xFF343033), Color(0xFF58474D), Color(0xFFF8EDF0), Color(0xFFC7B4BA),
        Color(0xFFC88E9B), Color(0xFF50353E), Color(0xFF292929), Color(0xFF9ED5B3), Color(0xFFFFB4A8)
    )
    val ElectricBlue = AppPalette(
        "electric_blue", "Электрический синий", true,
        Color(0xFF101A46), Color(0xFF192554), Color(0xFF354777), Color(0xFFF0F3FF), Color(0xFFB4C2F1),
        Color(0xFF245BFF), Color(0xFF193477), Color(0xFFFFFFFF), Color(0xFF8ADCC0), Color(0xFFFFB4A8)
    )

    val all = listOf(LightBase, Force, DarkBase, Berries, Jellyfish, Neon, RoseGraphite, ElectricBlue)
    val additional = all.filter { it != LightBase && it != DarkBase }

    fun resolve(prefs: UserPrefs, systemDark: Boolean): AppPalette {
        val isDark = when (prefs.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemDark
        }
        // Новая палитра не зависит от режима; раздельные старые ключи нужны только для совместимости.
        val id = prefs.schemeId ?: if (isDark) prefs.darkSchemeId else prefs.lightSchemeId
        return all.firstOrNull { it.id == id } ?: if (isDark) DarkBase else LightBase
    }
}

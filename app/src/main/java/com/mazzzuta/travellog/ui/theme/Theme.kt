package com.mazzzuta.travellog.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Применяет выбранную палитру ко всему приложению через Material 3 ColorScheme. */
@Composable
fun TravelLogTheme(palette: AppPalette, content: @Composable () -> Unit) {
    // на светлом цвете ошибки (например, у «Ягод») текст поверх неё должен быть тёмным
    val onError = if (palette.danger.luminance() > 0.4f) palette.bg else Color.White
    val base = if (palette.isDark) darkColorScheme() else lightColorScheme()

    val colorScheme = base.copy(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primaryDim,
        onPrimaryContainer = palette.primary,
        secondary = palette.primary,
        onSecondary = palette.onPrimary,
        secondaryContainer = palette.primaryDim,
        onSecondaryContainer = palette.primary,
        tertiary = palette.success,
        background = palette.bg,
        onBackground = palette.ink,
        surface = palette.surface,
        onSurface = palette.ink,
        surfaceVariant = palette.stroke,
        onSurfaceVariant = palette.inkMuted,
        surfaceContainerLowest = palette.surface,
        surfaceContainerLow = palette.surface,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.surface,
        surfaceContainerHighest = palette.surface,
        outline = palette.inkMuted,
        outlineVariant = palette.stroke,
        error = palette.danger,
        onError = onError,
        errorContainer = palette.primaryDim,
        onErrorContainer = palette.danger,
    )

    // иконки статус-бара должны контрастировать с фоном выбранной темы, а не системной
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !palette.isDark
                isAppearanceLightNavigationBars = !palette.isDark
            }
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
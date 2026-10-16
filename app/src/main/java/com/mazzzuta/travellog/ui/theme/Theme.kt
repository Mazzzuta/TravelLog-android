package com.mazzzuta.travellog.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun TravelLogTheme(palette: AppPalette, content: @Composable () -> Unit) {
    val colorScheme = paletteColorScheme(palette)

    // Системные значки подстраиваем под палитру приложения: она может отличаться от темы Android.
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

internal fun paletteColorScheme(palette: AppPalette): androidx.compose.material3.ColorScheme {
    val primary = readableColor(palette.primary, listOf(palette.bg, palette.surface))
    val error = readableColor(palette.danger, listOf(palette.bg, palette.surface, palette.primaryDim))
    val base = if (palette.isDark) darkColorScheme() else lightColorScheme()

    return base.copy(
        primary = primary,
        onPrimary = readableColor(palette.onPrimary, listOf(primary)),
        primaryContainer = palette.primaryDim,
        onPrimaryContainer = palette.ink,
        secondary = primary,
        onSecondary = readableColor(palette.onPrimary, listOf(primary)),
        secondaryContainer = palette.primaryDim,
        onSecondaryContainer = palette.ink,
        tertiary = palette.success,
        onTertiary = readableColor(palette.ink, listOf(palette.success)),
        background = palette.bg,
        onBackground = palette.ink,
        surface = palette.surface,
        onSurface = palette.ink,
        surfaceVariant = palette.stroke,
        onSurfaceVariant = readableColor(palette.inkMuted, listOf(palette.bg, palette.surface, palette.stroke)),
        surfaceContainerLowest = palette.surface,
        surfaceContainerLow = palette.surface,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.surface,
        surfaceContainerHighest = palette.surface,
        outline = palette.inkMuted,
        outlineVariant = palette.stroke,
        error = error,
        onError = readableColor(Color.White, listOf(error)),
        errorContainer = palette.primaryDim,
        onErrorContainer = error,
    )
}

internal fun contrastRatio(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
}

private fun readableColor(color: Color, backgrounds: List<Color>): Color {
    val target = listOf(Color.Black, Color.White).maxBy { candidate ->
        backgrounds.minOf { contrastRatio(candidate, it) }
    }
    for (step in 0..20) {
        val candidate = lerp(color, target, step / 20f)
        if (backgrounds.all { contrastRatio(candidate, it) >= 4.5f }) return candidate
    }
    return target
}

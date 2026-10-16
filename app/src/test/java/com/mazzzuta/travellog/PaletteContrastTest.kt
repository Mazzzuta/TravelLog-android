package com.mazzzuta.travellog

import com.mazzzuta.travellog.ui.theme.Palettes
import com.mazzzuta.travellog.ui.theme.contrastRatio
import com.mazzzuta.travellog.ui.theme.paletteColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteContrastTest {
    @Test fun textHasReadableContrastInEveryPalette() {
        for (palette in Palettes.all) {
            val scheme = paletteColorScheme(palette)
            val pairs = listOf(
                scheme.onBackground to scheme.background,
                scheme.onSurface to scheme.surface,
                scheme.onSurfaceVariant to scheme.surface,
                scheme.onSurfaceVariant to scheme.surfaceVariant,
                scheme.primary to scheme.background,
                scheme.primary to scheme.surface,
                scheme.onPrimary to scheme.primary,
                scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSecondary to scheme.secondary,
                scheme.onSecondaryContainer to scheme.secondaryContainer,
                scheme.onTertiary to scheme.tertiary,
                scheme.error to scheme.background,
                scheme.error to scheme.surface,
                scheme.onError to scheme.error,
                scheme.onErrorContainer to scheme.errorContainer
            )
            for ((text, background) in pairs) {
                val ratio = contrastRatio(text, background)
                assertTrue("${palette.id}: $text on $background has contrast $ratio", ratio >= 4.5f)
            }
        }
    }
}

package com.mazzzuta.travellog

import com.mazzzuta.travellog.database.ThemeMode
import com.mazzzuta.travellog.database.UserPrefs
import com.mazzzuta.travellog.ui.theme.Palettes
import org.junit.Assert.assertEquals
import org.junit.Test

class PaletteSelectionTest {
    @Test
    fun additionalPalettesWorkInEveryModeRegardlessOfSystemTheme() {
        for (mode in ThemeMode.entries) {
            for (systemDark in listOf(false, true)) {
                for (palette in Palettes.additional) {
                    assertEquals(palette, Palettes.resolve(UserPrefs(themeMode = mode, schemeId = palette.id), systemDark))
                }
            }
        }
    }

    @Test
    fun standardModesRestoreBasePalettesEvenWithLegacyCustomSettings() {
        val prefs = UserPrefs(schemeId = "default", lightSchemeId = "force", darkSchemeId = "berries")
        assertEquals(Palettes.LightBase, Palettes.resolve(prefs.copy(themeMode = ThemeMode.LIGHT), true))
        assertEquals(Palettes.DarkBase, Palettes.resolve(prefs.copy(themeMode = ThemeMode.DARK), false))
        assertEquals(Palettes.LightBase, Palettes.resolve(prefs.copy(themeMode = ThemeMode.SYSTEM), false))
        assertEquals(Palettes.DarkBase, Palettes.resolve(prefs.copy(themeMode = ThemeMode.SYSTEM), true))
    }

    @Test
    fun legacySelectionIsPreservedUntilUserChoosesNewTheme() {
        assertEquals(Palettes.Force, Palettes.resolve(UserPrefs(lightSchemeId = "force"), false))
        assertEquals(Palettes.Berries, Palettes.resolve(UserPrefs(themeMode = ThemeMode.DARK, darkSchemeId = "berries"), true))
    }

    @Test
    fun unknownSchemeFallsBackToStandardMode() {
        assertEquals(Palettes.DarkBase, Palettes.resolve(UserPrefs(themeMode = ThemeMode.DARK, schemeId = "missing"), false))
    }
}

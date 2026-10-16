package com.mazzzuta.travellog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mazzzuta.travellog.navigation.Route
import com.mazzzuta.travellog.ui.components.TravelBottomNav
import com.mazzzuta.travellog.ui.theme.Palettes
import com.mazzzuta.travellog.ui.theme.TravelLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class CompactBottomNavTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tabsRemainAccessibleAtCompactWidths() {
        val width = mutableStateOf(320.dp)
        var selected: Route? = null
        compose.setContent {
            TravelLogTheme(Palettes.LightBase) {
                Box(Modifier.width(width.value)) {
                    TravelBottomNav(null, { selected = it }, {})
                }
            }
        }
        for (size in listOf(320, 360, 400)) {
            compose.runOnIdle { width.value = size.dp }
            for (label in listOf("Лента", "Карта", "Статистика", "Настройки")) {
                compose.onNodeWithText(label).assertIsDisplayed().performClick()
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertFalse("$label overflows at $size dp", layouts.single().hasVisualOverflow)
            }
            compose.runOnIdle { assertEquals(Route.Settings, selected) }
        }
    }
}

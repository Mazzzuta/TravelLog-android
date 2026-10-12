package com.mazzzuta.travellog

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mazzzuta.travellog.ui.createentry.CreateEntryScreen
import com.mazzzuta.travellog.ui.theme.Palettes
import com.mazzzuta.travellog.ui.theme.TravelLogTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateEntryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backAsksBeforeDiscardingAndStayPreservesInput() {
        var left = false
        compose.setContent {
            TravelLogTheme(Palettes.LightBase) {
                CreateEntryScreen(onSaved = {}, onCancel = { left = true })
            }
        }
        compose.onNodeWithText("Название записи *").performTextInput("Байкал")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Выйти без сохранения?").assertIsDisplayed()
        assertFalse(left)
        compose.onNodeWithText("Остаться").performClick()
        compose.onNodeWithText("Байкал").assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Выйти", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertTrue(left) }
    }
}

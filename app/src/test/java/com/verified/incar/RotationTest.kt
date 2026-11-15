package com.verified.incar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "sw360dp-w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RotationTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun historyTabAndDraft_survivePortraitLandscapePortraitAndRecreation() {
        RotationHarness(rule).use { app ->
            app.start()
            app.go("HISTORY")
            val value = "2026-11-15 14:30"
            rule.onNodeWithTag("fine_input").performScrollTo().performTextInput(value)
            app.shot("history_portrait_before")
            app.rotate(800, 360)
            app.shot("history_landscape")
            app.rotate(360, 800)
            rule.onNodeWithTag("fine_input").assertTextContains(value)
            rule.onNodeWithTag("pane_1").performScrollTo().performClick()
            app.rotate(800, 360)
            app.rotate(360, 800)
            rule.onNodeWithTag("pane_1").assertIsSelected()
            app.recreate()
            rule.onNodeWithTag("pane_1").assertIsSelected()
            rule.onNodeWithTag("pane_0").performClick()
            rule.onNodeWithTag("fine_input").assertTextContains(value)
            app.shot("history_portrait_restored")
        }
    }
}

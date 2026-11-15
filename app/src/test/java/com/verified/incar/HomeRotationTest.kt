package com.verified.incar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import com.verified.incar.core.QUICK_DEFAULT
import com.verified.incar.core.normalizeQuickOrder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "sw360dp-w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeRotationTest {
    @get:Rule val rule = createEmptyComposeRule()

    private fun phoneControlsRemain() {
        rule.onNodeWithTag("quick_horn").assertExists()
        rule.onNodeWithTag("quick_lock").assertExists()
        rule.onNodeWithTag("carquick_seat").assertDoesNotExist()
        rule.onNodeWithTag("home_car_actions").assertDoesNotExist()
    }

    @Test fun homeQuickEdit_survivesRotationAndRecreation() {
        RotationHarness(rule).use { app ->
            app.vm.settings { it.copy(quickOrder = QUICK_DEFAULT) }
            app.start()
            rule.onNodeWithTag("quick_horn").performScrollTo().performTouchInput { longClick() }
            rule.onNodeWithTag("quick_left_HORN").performScrollTo().performClick()
            app.rotate(800, 360)
            phoneControlsRemain()
            rule.onNodeWithTag("quick_done").assertExists()
            rule.onNodeWithTag("quick_left_HORN").performScrollTo().performClick()
            assertEquals(listOf("LOCK", "HORN", "AC", "HAZARD"), normalizeQuickOrder(app.vm.ui.value.settings.quickOrder))
            app.shot("home_landscape_editing")
            app.rotate(360, 800)
            rule.onNodeWithTag("quick_done").assertExists()
            app.recreate()
            rule.onNodeWithTag("quick_done").performScrollTo().assertIsDisplayed().performClick()
            phoneControlsRemain()
            rule.onNodeWithTag("quick_left_HORN").assertDoesNotExist()
            app.shot("home_portrait_restored")
        }
    }

    @Test fun controlInteriorAndEditing_surviveMovingBetweenColumns() {
        RotationHarness(rule).use { app ->
            app.start()
            app.go("CONTROL")
            rule.onNodeWithTag("control_interior").performClick()
            val before = app.vm.ui.value.signals.hvacTempC
            rule.onNodeWithTag("cabin_temp_up").performScrollTo().performClick()
            val heat = app.vm.ui.value.signals.seatHeatDriver
            rule.onNodeWithTag("topview_seat_DRIVER").performScrollTo().performClick()
            app.rotate(800, 360)
            rule.onNodeWithTag("control_interior").assertIsSelected()
            assertEquals(before + 0.5f, app.vm.ui.value.signals.hvacTempC, 0.01f)
            assertEquals((heat + 1) % 4, app.vm.ui.value.signals.seatHeatDriver)
            rule.onNodeWithTag("cabin_temp_down").performScrollTo().performClick()
            assertEquals(before, app.vm.ui.value.signals.hvacTempC, 0.01f)
            app.shot("control_landscape_interior")
            rule.onNodeWithTag("control_exterior").performScrollTo().performClick()
            phoneControlsRemain()
            rule.onNodeWithTag("quick_horn").performScrollTo().performTouchInput { longClick() }
            app.rotate(360, 800)
            rule.onNodeWithTag("quick_done").performScrollTo().assertIsDisplayed()
            app.recreate()
            rule.onNodeWithTag("quick_done").performScrollTo().performClick()
            rule.onNodeWithTag("control_exterior").performScrollTo().assertIsSelected()
            phoneControlsRemain()
            app.shot("control_portrait_restored")
        }
    }

    @Test @Config(qualifiers = "sw320dp-w320dp-h720dp-port-mdpi")
    fun smallPhoneLargeText_controlsStayReachable() {
        RuntimeEnvironment.setFontScale(1.5f)
        RotationHarness(rule).use { app ->
            app.start()
            phoneControlsRemain()
            rule.onNodeWithTag("quick_horn").performScrollTo().performTouchInput { longClick() }
            rule.onNodeWithTag("quick_left_HORN").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("quick_done").performScrollTo().performClick()
            rule.onNodeWithTag("home_hand_valet").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("대리 맡기기").assertIsDisplayed()
            app.shot("home_320_font150")
            app.go("CONTROL")
            rule.onNodeWithTag("control_interior").performClick()
            rule.onNodeWithTag("topview_seat_DRIVER").performScrollTo().assertWidthIsAtLeast(44.dp)
            rule.onNodeWithTag("cabin_temp_up").performScrollTo().assertIsDisplayed().performClick()
            rule.onNodeWithTag("cabin_power").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("control_mission").performScrollTo().assertIsDisplayed()
            app.shot("control_320_font150")
        }
    }
}

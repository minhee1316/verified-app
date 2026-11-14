package com.verified.incar

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.verified.incar.core.CarModel
import com.verified.incar.core.Persona
import com.verified.incar.core.QUICK_DEFAULT
import com.verified.incar.core.Settings
import com.verified.incar.core.moveQuick
import com.verified.incar.core.normalizeQuickOrder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-mdpi")
class QuickOrderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun order_normalizesAndMoves() {
        assertEquals(QUICK_DEFAULT, normalizeQuickOrder(listOf("X", "LOCK", "LOCK")))
        assertEquals(listOf("HORN", "LOCK", "AC", "HAZARD"), normalizeQuickOrder(listOf("HORN")))
        assertEquals(listOf("AC", "LOCK", "HAZARD", "HORN"), moveQuick(QUICK_DEFAULT, "AC", -1))
        assertEquals(QUICK_DEFAULT, moveQuick(QUICK_DEFAULT, "LOCK", -1)) // 맨 앞은 그대로
    }

    @Test fun order_survivesRestart() {
        Store(app).saveSettings(Settings(quickOrder = listOf("HORN", "LOCK", "AC", "HAZARD")))
        assertEquals(listOf("HORN", "LOCK", "AC", "HAZARD"), Store(app).loadSettings().quickOrder)
    }

    @Test fun longPress_thenArrowMovesButton() {
        val vm = VerifiedRuntime(app, simTicker = false)
        vm.settings { it.copy(personas = Persona.entries.toSet(), carModel = CarModel.GRANDEUR) }
        rule.setContent { MaterialTheme { App(vm) } }
        rule.waitForIdle()
        rule.onNodeWithTag("quick_horn").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag("quick_left_HORN").performClick()
        rule.waitForIdle()
        assertEquals(listOf("LOCK", "AC", "HORN", "HAZARD"), normalizeQuickOrder(vm.ui.value.settings.quickOrder))
        rule.onNodeWithTag("quick_done").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("quick_left_HORN").assertDoesNotExist()
        vm.close()
    }
}

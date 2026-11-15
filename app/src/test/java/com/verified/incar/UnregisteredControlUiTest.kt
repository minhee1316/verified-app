package com.verified.incar

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.verified.incar.core.CarModel
import com.verified.incar.core.ControlKind
import com.verified.incar.ui.QuickControls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 등록 절차 없이 홈에서 시연 조작을 실행하고, 실차 경계에서는 같은 버튼이 명령을 만들지 않는지 확인한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "sw411dp-w411dp-h891dp-port-mdpi")
class UnregisteredControlUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun freshInstall_homeControlsChangeDemoState_withoutRegistration() {
        val app = RuntimeEnvironment.getApplication()
        val flags = app.applicationInfo.flags
        app.applicationInfo.flags = flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE.inv()
        val vm = VerifiedRuntime(app, simTicker = false)
        try {
            // 차종·페르소나·지원 기능을 테스트에서 등록하지 않는다.
            rule.setContent { MaterialTheme { App(vm) } }
            rule.waitForIdle()
            assertTrue(vm.ui.value.simAllowed)
            assertEquals(CarModel.GRANDEUR, vm.ui.value.settings.carModel)
            rule.onNodeWithTag("home_hero").assertIsDisplayed()
            rule.onNodeWithTag("quick_controls").assertIsDisplayed()
            rule.onNodeWithTag("home_vehicle_status").assertTextContains("시연", substring = true)

            val locked = vm.ui.value.signals.doorsLocked
            rule.onNodeWithTag("quick_lock").performClick()
            rule.waitForIdle()
            assertEquals(!locked, vm.ui.value.signals.doorsLocked)
            rule.onNodeWithTag("quick_feedback").performScrollTo().assertIsDisplayed()

            val climate = vm.ui.value.signals.acOn
            rule.onNodeWithTag("quick_ac").performScrollTo().performClick()
            rule.waitForIdle()
            assertEquals(!climate, vm.ui.value.signals.acOn)

            val hazard = vm.ui.value.signals.hazardOn
            rule.onNodeWithTag("quick_hazard").performScrollTo().performClick()
            rule.waitForIdle()
            assertEquals(!hazard, vm.ui.value.signals.hazardOn)

            rule.onNodeWithTag("quick_horn").performScrollTo().performClick()
            rule.waitForIdle()
            assertEquals(ControlKind.HORN, vm.ui.value.engine.controlLog.first().kind)
            rule.onNodeWithTag("quick_feedback").performScrollTo()
            rule.onNodeWithText("경적 시연 요청됨").assertIsDisplayed()
        } finally {
            vm.close()
            app.applicationInfo.flags = flags
        }
    }

    @Test fun connectedVehicle_quickControlsNeverSendDemoCommands() {
        val vm = VerifiedRuntime(RuntimeEnvironment.getApplication(), simTicker = false)
        try {
            val connected = vm.ui.value.copy(carConnected = true, simAllowed = false, canControl = true)
            val before = vm.ui.value.signals
            rule.setContent { MaterialTheme { QuickControls(vm, connected) } }
            listOf("quick_lock", "quick_ac", "quick_hazard", "quick_horn").forEach { tag ->
                rule.onNodeWithTag(tag).performClick()
                rule.waitForIdle()
            }
            assertTrue(vm.ui.value.engine.controlLog.isEmpty())
            assertEquals(before, vm.ui.value.signals)
            assertTrue(vm.ui.value.toast.contains("마이현대"))
        } finally { vm.close() }
    }
}

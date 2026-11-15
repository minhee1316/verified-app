package com.verified.incar

import android.content.pm.ApplicationInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.verified.incar.core.DoorPart
import com.verified.incar.core.SeatPart
import com.verified.incar.ui.CarTopView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 실제 서비스 연결과 신호 수신은 다르다. 미수신 값을 안전한 상태처럼 그리지 않는다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "sw360dp-w360dp-h800dp-port-mdpi")
class NativeVehicleStateTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun withConnectedVehicle(interior: Boolean, verify: (VerifiedRuntime) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val originalFlags = app.applicationInfo.flags
        app.applicationInfo.flags = originalFlags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        val vm = VerifiedRuntime(app, false, PhoneRelay::publish) { _, _ -> ConnectedWithoutReadings() }
        try {
            assertTrue(vm.ui.value.carConnected)
            assertFalse(vm.ui.value.simAllowed)
            assertTrue(vm.ui.value.signals.seenAt.isEmpty())
            rule.setContent {
                MaterialTheme {
                    CarTopView(vm, vm.ui.collectAsState().value, interior, Modifier.fillMaxWidth())
                }
            }
            rule.waitForIdle()
            verify(vm)
        } finally {
            vm.close()
            app.applicationInfo.flags = originalFlags
        }
    }

    @Test fun connectedWithoutReadings_doesNotClaimDoorsLockedOrTrunkClosed() {
        withConnectedVehicle(interior = false) { vm ->
            rule.onNodeWithTag("topview_lock").assertTextContains("확인 불가")
            rule.onAllNodesWithText("미수신").assertCountEquals(4)
            DoorPart.entries.filter { it != DoorPart.TRUNK }.forEach { door ->
                rule.onNodeWithTag("topview_door_${door.name}")
                    .assertContentDescriptionContains("열림 확인 불가", substring = true)
                    .assertContentDescriptionContains("잠금 확인 불가", substring = true)
            }
            rule.onNodeWithText("트렁크 미수신").assertExists()
            rule.onNodeWithText("트렁크 닫힘").assertDoesNotExist()
            rule.onAllNodesWithText("잠김").assertCountEquals(0)

            val before = vm.ui.value.signals
            val history = vm.ui.value.engine.controlLog
            // 안내를 볼 수는 있어도 실차 연결에서 시연 명령이 만들어지면 안 된다.
            rule.onNodeWithTag("topview_lock").performClick()
            rule.waitForIdle()
            assertEquals(before, vm.ui.value.signals)
            assertEquals(history, vm.ui.value.engine.controlLog)
        }
    }

    @Test fun connectedWithoutSeatHeatSupport_showsUnknownAndCannotApplyDemoHeat() {
        withConnectedVehicle(interior = true) { vm ->
            rule.onAllNodesWithText("—").assertCountEquals(5)
            rule.onAllNodesWithText("끔").assertCountEquals(0)
            SeatPart.entries.forEach { seat ->
                rule.onNodeWithTag("topview_seat_${seat.name}")
                    .assertContentDescriptionContains("열선 확인 불가", substring = true)
            }
            val before = vm.ui.value.signals
            val history = vm.ui.value.engine.controlLog
            // 기존 UX는 누르면 미지원 안내를 보여준다. 상태 변경 차단을 검증한다.
            rule.onNodeWithTag("topview_seat_DRIVER").performClick()
            rule.waitForIdle()
            assertEquals(before, vm.ui.value.signals)
            assertEquals(history, vm.ui.value.engine.controlLog)
        }
    }
}

package com.verified.incar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.verified.incar.core.EvRoute
import com.verified.incar.core.Fence
import com.verified.incar.core.Level
import com.verified.incar.core.Persona
import com.verified.incar.core.fenceVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1300dp-port-mdpi")
class DemoScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun open(route: String) {
        val vm = VerifiedRuntime(RuntimeEnvironment.getApplication(), simTicker = false)
        vm.settings { it.copy(personas = Persona.entries.toSet()) }
        rule.mainClock.autoAdvance = false
        rule.setContent { MaterialTheme { App(vm) } }
        idle()
        tap("demo_$route")
    }

    private fun idle() = repeat(4) { rule.mainClock.advanceTimeBy(400); rule.waitForIdle() }
    private fun tap(tag: String) { rule.onNodeWithTag(tag).performScrollTo().performClick(); idle() }

    private fun shot(name: String) {
        idle()
        val root = rule.activity.window.decorView
        val b = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(b))
        File(File("../screenshots").apply { mkdirs() }, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun routeBatteryReactsToColdAndClimb() {
        val mild = EvRoute.batteryAlong(80.0, cold = false)
        val cold = EvRoute.batteryAlong(80.0, cold = true)
        assertEquals(EvRoute.ELEVATION.size, mild.size)
        assertTrue(cold.last() < mild.last())
        assertTrue(EvRoute.extraUse(80.0, false) > 0)
        assertEquals(Level.OK, EvRoute.level(50.0))
        assertEquals(Level.WARN, EvRoute.level(20.0))
        assertEquals(Level.DANGER, EvRoute.level(19.9))
    }

    @Test fun fenceBreachNeedsIgnitionAndDistance() {
        assertEquals(Fence.INSIDE, fenceVerdict(false, true, 3.0))
        assertEquals(Fence.BREACH, fenceVerdict(true, true, 0.5))
        assertEquals(Fence.GPS_NOISE, fenceVerdict(true, false, 0.5))
        assertEquals(Fence.GPS_NOISE, fenceVerdict(true, true, 0.0))
    }

    @Test fun evScreenCollapsesInDrivingMode() {
        open("EV_ROUTE")
        rule.onNodeWithTag("ev_chart").assertExists()
        shot("63_폰_전기차경로")
        tap("ev_배터리 부족")
        shot("64_폰_전기차_배터리부족")
        tap("ev_sheet_handle")
        rule.onNodeWithTag("ev_chart").assertDoesNotExist()
        tap("ev_sheet_handle")
        rule.onNodeWithTag("ev_chart").assertExists()
        tap("ev_drive")
        rule.onNodeWithTag("ev_chart").assertDoesNotExist()
        rule.onNodeWithTag("ev_simple").assertExists()
        shot("65_폰_전기차_주행모드")
    }

    @Test fun valetScreenSeparatesBreachFromGpsNoise() {
        open("VALET_WATCH")
        tap("valet_구역 이탈")
        rule.onNodeWithText("지정 구역 이탈 감지").assertExists()
        shot("66_폰_발레감시_이탈")
        tap("valet_GPS 오차")
        rule.onNodeWithText("지하 주차장 신호 미약으로 위치 보정 중이에요").assertExists()
        rule.onNodeWithText("지정 구역 이탈 감지").assertDoesNotExist()
        shot("67_폰_발레감시_GPS오차")
    }

    @Test fun radioScreenShowsBannerAndHelp() {
        open("RADIO_DIAG")
        rule.onNodeWithTag("radio_banner").assertDoesNotExist()
        tap("radio_간섭 구역 진입")
        rule.onNodeWithTag("radio_banner").assertExists()
        shot("68_폰_무선진단_간섭")
        tap("radio_item_Wi-Fi 5GHz 채널")
        rule.onNodeWithTag("radio_help").assertExists()
    }
}

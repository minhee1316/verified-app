package com.verified.incar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.verified.incar.core.CarModel
import com.verified.incar.core.Persona
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
@Config(sdk = [34], qualifiers = "w1408dp-h792dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TopViewTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun shot(name: String) {
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(File("../screenshots").apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun start(): VerifiedRuntime {
        val vm = VerifiedRuntime(app, simTicker = false)
        vm.settings { it.copy(personas = Persona.entries.toSet(), carModel = CarModel.GRANDEUR) }
        rule.setContent { MaterialTheme { App(vm) } }
        rule.waitForIdle()
        return vm
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun home_phone_carFirstThenOrderedList() {
        start()
        rule.onNodeWithTag("home_hero").assertIsDisplayed()
        rule.onNodeWithTag("quick_controls").assertIsDisplayed()
        rule.onNodeWithTag("home_list").assertExists()
        shot("79_홈_차_빠른제어_목록")
    }

    @Test fun control_exterior_lockInCenterSendsCommand() {
        val vm = start()
        rule.onNodeWithTag("nav_CONTROL").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("topview_exterior").assertIsDisplayed()
        shot("80_제어_위에서_외부")
        rule.onNodeWithTag("topview_lock").performClick()
        rule.waitForIdle()
        val e = vm.ui.value.engine
        assertTrue("잠금 명령이 기록되거나 결과를 기다린다", e.controlLog.isNotEmpty() || e.pendingControl != null)
    }

    @Test fun control_interior_tempAndDriverSeatHeat() {
        val vm = start()
        rule.onNodeWithTag("nav_CONTROL").performClick()
        rule.onNodeWithTag("control_interior").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("topview_interior").assertIsDisplayed()
        val t0 = vm.ui.value.signals.hvacTempC
        rule.onNodeWithTag("cabin_temp_up").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(t0 + 0.5f, vm.ui.value.signals.hvacTempC, 0.01f)
        val h0 = vm.ui.value.signals.seatHeatDriver
        rule.onNodeWithTag("topview_seat_DRIVER").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals((h0 + 1) % 4, vm.ui.value.signals.seatHeatDriver)
        shot("81_제어_위에서_실내")
    }
}

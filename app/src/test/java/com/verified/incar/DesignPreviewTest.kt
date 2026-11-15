package com.verified.incar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.verified.incar.core.CarModel
import com.verified.incar.core.Persona
import com.verified.incar.ui.C
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 실제 화면 크기에서 내비게이션 동작과 낮/밤 디자인을 렌더링한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1408dp-h792dp-land-car-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignPreviewTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(File("../screenshots/design").apply { mkdirs() }, "$name.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bmp.recycle()
    }

    private fun preview(prefix: String) {
        val vm = VerifiedRuntime(RuntimeEnvironment.getApplication(), simTicker = false)
        vm.settings { it.copy(personas = Persona.entries.toSet(), carModel = CarModel.GRANDEUR) }
        try {
            rule.setContent { MaterialTheme { App(vm) } }
            rule.waitForIdle()
            rule.onNodeWithTag("home_hero").assertIsDisplayed()
            shot("${prefix}_home")
            rule.onNodeWithTag("nav_CONTROL").performClick()
            rule.onNodeWithTag("topview_exterior").assertIsDisplayed()
            shot("${prefix}_control")
            rule.onNodeWithTag("control_interior").performClick()
            if (prefix.startsWith("phone")) {
                rule.onNodeWithTag("cabin_temp_up").assertIsDisplayed()
                rule.onNodeWithTag("cabin_power").assertIsDisplayed()
                rule.onNodeWithTag("control_mission").assertIsDisplayed()
                val temperature = vm.ui.value.signals.hvacTempC
                rule.onNodeWithTag("cabin_temp_up").performClick()
                rule.waitForIdle()
                assertEquals(temperature + 0.5f, vm.ui.value.signals.hvacTempC, 0.01f)
            }
            shot("${prefix}_interior")
            rule.onNodeWithTag("nav_HISTORY").performClick()
            shot("${prefix}_records")
            rule.onNodeWithTag("nav_MYPAGE").performClick()
            shot("${prefix}_more")
        } finally { vm.close(); C.forceDark = null }
    }

    @Test fun carLight() { C.forceDark = false; preview("car_light") }
    @Test fun carDark() { C.forceDark = true; preview("car_dark") }
    @Test @Config(qualifiers = "w411dp-h891dp-port-mdpi")
    fun phoneLight() { C.forceDark = false; preview("phone_light") }
    @Test @Config(qualifiers = "w411dp-h891dp-port-night-mdpi")
    fun phoneDark() { C.forceDark = true; preview("phone_dark") }
}
